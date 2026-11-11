import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standalone PNG-to-ASCII conversion utility for the YAKU asset pipeline.
 *
 * <p>The tool scans the raw sprite PNGs of the project (player, Yokai, Omamori,
 * Hanko and Hanafuda cards), downsamples every sprite onto a small glyph grid,
 * maps pixel luminosity onto the fixed Unicode ramp
 * {@code . : ░ ▒ ▓ █ @} and snaps every cell colour to one index of the
 * project's 128-colour palette ({@code master_palette_128.act}).</p>
 *
 * <p>Output: one {@code AsciiAsset.json} matrix per sprite in
 * {@code assets/ascii/} plus a {@code manifest.json} summary. The JSON matches
 * the schema documented in {@code yaku_gdd.md} and is understood by
 * {@code com.lychcs.koikoi.graphics.AsciiAssetLoader}.</p>
 *
 * <p>The utility is intentionally dependency-free (JDK only) and is executed
 * standalone, e.g. {@code java tools/PngToAsciiConverter.java} from the project
 * root. It never needs a running LibGDX/OpenGL context.</p>
 *
 * <p>Source directories are scanned in priority order; the first file that
 * produces a given asset id wins:
 * {@code assets/} (canonical runtime assets), {@code assets_solo/}
 * (single-sprite exports) and {@code assets_raw/} (large source renders).</p>
 */
public final class PngToAsciiConverter {

    /** Palette file used to clamp colours to the project's 128-colour palette. */
    public static final String DEFAULT_PALETTE_FILE = "master_palette_128.act";

    /** Output directory of the generated ASCII asset matrices. */
    public static final String DEFAULT_OUTPUT_DIR = "assets/ascii";

    /** Output path of the 128-colour palette texture used by the palette-swap shader. */
    public static final String DEFAULT_PALETTE_TEXTURE = "assets/palette/palette_128.png";

    /** Number of palette entries used by the project (the remaining ACT entries stay black). */
    public static final int PALETTE_SIZE = 128;

    /**
     * Luminosity ramp of the ASCII hybrid renderer. Index 6 ({@code @}) is the
     * key-accent glyph and is only used for the brightest pixels.
     */
    private static final char[] GLYPH_RAMP = {'.', ':', '\u2591', '\u2592', '\u2593', '\u2588', '@'};

    /** Exclusive upper luminosity bound of the first six ramp glyphs. */
    private static final double[] GLYPH_BOUNDS = {0.15, 0.35, 0.55, 0.75, 0.90, 0.985};

    /** Directories that contain no single-sprite art and are therefore never scanned. */
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
        "packed", "backgrounds", "backgrounds_raw", "fonts", "shaders", "ascii", "map", "maps",
        ".git", ".gradle", "build", ".idea", "misc", "tools");

    /** Alpha threshold below which a pixel counts as fully transparent when trimming. */
    private static final int ALPHA_TRIM_THRESHOLD = 8;

    /** Matches the first Aseprite frame rectangle inside a sibling JSON metadata file. */
    private static final Pattern FIRST_FRAME = Pattern.compile(
        "\"frame\"\\s*:\\s*\\{\\s*\"x\"\\s*:\\s*(-?\\d+)\\s*,\\s*\"y\"\\s*:\\s*(-?\\d+)\\s*,"
            + "\\s*\"w\"\\s*:\\s*(\\d+)\\s*,\\s*\"h\"\\s*:\\s*(\\d+)");

    private PngToAsciiConverter() {
    }

    /** Sprite categories of the asset pipeline with their target glyph height. */
    private enum Category {
        PLAYER("player", 32),
        YOKAI("yokai", 32),
        OMAMORI("omamori", 24),
        HANKO("hanko", 16),
        HANAFUDA("hanafuda", 32);

        private final String id;
        private final int targetHeight;

        Category(String id, int targetHeight) {
            this.id = id;
            this.targetHeight = targetHeight;
        }
    }

    /** Result of one PNG conversion: glyph rows and flat palette indices. */
    private record AsciiMatrix(int width, int height, List<String> glyphs, int[] paletteIndices) {
    }

    /** A discovered source sprite together with its derived category and asset id. */
    private record SourceSprite(Path file, String projectRelativePath, Category category, String assetId) {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("PNG -> ASCII conversion failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        Path projectRoot = detectProjectRoot(Paths.get("").toAbsolutePath());
        Path paletteFile = projectRoot.resolve(DEFAULT_PALETTE_FILE);
        Path outputDir = projectRoot.resolve(DEFAULT_OUTPUT_DIR);
        Path paletteTextureFile = projectRoot.resolve(DEFAULT_PALETTE_TEXTURE);
        boolean directionalOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--root" -> projectRoot = Paths.get(requireValue(args, ++i, "--root")).toAbsolutePath();
                case "--palette" -> paletteFile = Paths.get(requireValue(args, ++i, "--palette")).toAbsolutePath();
                case "--out" -> outputDir = Paths.get(requireValue(args, ++i, "--out")).toAbsolutePath();
                case "--palette-texture" ->
                    paletteTextureFile = Paths.get(requireValue(args, ++i, "--palette-texture")).toAbsolutePath();
                case "--directional-player" -> directionalOnly = true;
                case "--help", "-h" -> {
                    printUsage();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }

        if (!Files.isRegularFile(paletteFile)) {
            throw new IOException("Palette file not found: " + paletteFile);
        }

        Palette palette = Palette.load(paletteFile, PALETTE_SIZE);
        palette.writePng(paletteTextureFile);
        Files.createDirectories(outputDir);

        if (directionalOnly) {
            List<Map.Entry<SourceSprite, AsciiMatrix>> directional = new ArrayList<>();
            int generated = generateDirectionalPlayer(projectRoot, outputDir, palette, directional);
            if (generated == 0) {
                throw new IOException("No directional player frames found for "
                    + projectRoot.resolve("assets/entities/player.png"));
            }
            printDirectionalSummary(directional, outputDir);
            return;
        }

        List<Path> sourceRoots = new ArrayList<>();
        for (String name : List.of("assets", "assets_solo", "assets_raw")) {
            Path root = projectRoot.resolve(name);
            if (Files.isDirectory(root)) {
                sourceRoots.add(root);
            }
        }
        if (sourceRoots.isEmpty()) {
            throw new IOException("No source asset directory (assets, assets_solo, assets_raw) below " + projectRoot);
        }

        List<SourceSprite> sprites = discover(projectRoot, sourceRoots);
        if (sprites.isEmpty()) {
            throw new IOException("No sprite PNGs found in " + sourceRoots);
        }

        Files.deleteIfExists(outputDir.resolve("manifest.json"));
        deleteGeneratedJson(outputDir);

        List<Map.Entry<SourceSprite, AsciiMatrix>> converted = new ArrayList<>();
        for (SourceSprite sprite : sprites) {
            BufferedImage image = trimTransparentBorder(firstFrameOf(sprite.file()));
            AsciiMatrix matrix = toAscii(image, sprite.category(), palette);
            writeJson(outputDir.resolve(sprite.assetId() + ".json"), sprite, matrix);
            converted.add(Map.entry(sprite, matrix));
        }

        // Directional player matrices are generated from the Aseprite walk tags
        // and are part of the normal asset regeneration as well.
        generateDirectionalPlayer(projectRoot, outputDir, palette, converted);

        writeManifest(outputDir.resolve("manifest.json"), converted, paletteFile,
            paletteTextureFile);
        printSummary(converted, sourceRoots, outputDir, paletteFile,
            paletteTextureFile);
    }

    /**
     * Generates player_down.json, player_up.json and player_side.json from the
     * first frame of the walk_down / walk_up / walk_left Aseprite tags.
     *
     * @return number of generated matrices (0 when the player sheet is missing)
     */
    private static int generateDirectionalPlayer(Path projectRoot, Path outputDir, Palette palette,
                                                 List<Map.Entry<SourceSprite, AsciiMatrix>> converted)
        throws IOException {
        Path playerPng = projectRoot.resolve("assets/entities/player.png");
        if (!Files.isRegularFile(playerPng)) {
            return 0;
        }
        BufferedImage sheet = ImageIO.read(playerPng.toFile());
        if (sheet == null) {
            throw new IOException("Unsupported image format: " + playerPng);
        }

        final int frameWidth = 64;
        final int frameHeight = 128;
        if (sheet.getWidth() < frameWidth || sheet.getHeight() < frameHeight) {
            return 0;
        }
        int frameCount = Math.min(16, sheet.getWidth() / frameWidth);

        String[] assetIds = {"player_down", "player_up", "player_side"};
        int[] frameIndices = {0, 4, 8}; // walk_down, walk_up, walk_left
        int generated = 0;

        for (int i = 0; i < assetIds.length; i++) {
            int frameIndex = frameIndices[i];
            if (frameIndex >= frameCount) {
                continue;
            }
            BufferedImage frame = sheet.getSubimage(frameIndex * frameWidth, 0,
                frameWidth, frameHeight);
            AsciiMatrix matrix = toAscii(trimTransparentBorder(frame), Category.PLAYER, palette);
            SourceSprite sprite = new SourceSprite(playerPng,
                "assets/entities/player.png#frame" + frameIndex, Category.PLAYER, assetIds[i]);
            writeJson(outputDir.resolve(assetIds[i] + ".json"), sprite, matrix);
            converted.add(Map.entry(sprite, matrix));
            generated++;
        }
        return generated;
    }

    private static void printDirectionalSummary(List<Map.Entry<SourceSprite, AsciiMatrix>> converted,
                                                Path outputDir) {
        System.out.println("Directional player matrices written to " + outputDir + ":");
        for (Map.Entry<SourceSprite, AsciiMatrix> entry : converted) {
            AsciiMatrix matrix = entry.getValue();
            System.out.println("  " + entry.getKey().assetId() + ".json <- "
                + entry.getKey().projectRelativePath() + " (" + matrix.width() + "x"
                + matrix.height() + ")");
        }
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    private static void printUsage() {
        System.out.println("Usage: java tools/PngToAsciiConverter.java"
            + " [--root <dir>] [--palette <file>] [--palette-texture <file>] [--out <dir>]"
            + " [--directional-player]");
        System.out.println("  --directional-player  only regenerate player_down/up/side matrices");
    }

    // ------------------------------------------------------------------
    // Source discovery and categorisation
    // ------------------------------------------------------------------

    /** Walks up from the working directory until the project {@code assets} folder is found. */
    private static Path detectProjectRoot(Path start) {
        Path dir = start;
        while (dir != null && !Files.isDirectory(dir.resolve("assets"))) {
            dir = dir.getParent();
        }
        return dir == null ? start : dir;
    }

    private static List<SourceSprite> discover(Path projectRoot, List<Path> sourceRoots) throws IOException {
        Map<String, SourceSprite> byAssetId = new LinkedHashMap<>();

        for (Path root : sourceRoots) {
            List<Path> pngs;
            try (Stream<Path> walk = Files.walk(root)) {
                pngs = walk.filter(Files::isRegularFile)
                    .filter(PngToAsciiConverter::isPng)
                    .sorted()
                    .collect(Collectors.toList());
            }

            for (Path png : pngs) {
                Path relative = projectRoot.relativize(png);
                if (isInSkippedDirectory(relative)) {
                    continue;
                }
                Category category = classify(relative);
                if (category == null) {
                    continue;
                }
                String assetId = assetIdOf(png, category);
                // Earlier roots win: assets/ before assets_solo/ before assets_raw/.
                byAssetId.putIfAbsent(assetId,
                    new SourceSprite(png, toProjectPath(relative), category, assetId));
            }
        }

        return new ArrayList<>(byAssetId.values());
    }

    private static boolean isPng(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png");
    }

    private static boolean isInSkippedDirectory(Path relative) {
        for (Path part : relative) {
            if (SKIPPED_DIRECTORIES.contains(part.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Categorises a sprite by file name and folder; {@code null} means "not an ASCII sprite". */
    private static Category classify(Path relativePath) {
        String name = relativePath.getFileName().toString().toUpperCase(Locale.ROOT);
        String path = toProjectPath(relativePath).toUpperCase(Locale.ROOT);

        if (name.contains("BACKGROUND") || name.startsWith("BUTTON") || name.startsWith("PANEL")
            || name.startsWith("BOOSTER") || name.contains("TILESET") || name.contains("TREE")
            || name.startsWith("WATER_") || name.contains("GRASS")) {
            return null;
        }
        if (name.contains("OMAMORI")) {
            return Category.OMAMORI;
        }
        if (name.contains("HANKO")) {
            return Category.HANKO;
        }
        if (path.contains("/CARDS/") || path.contains("/CARDS_RAW/")) {
            return Category.HANAFUDA;
        }
        if (name.contains("PLAYER") || name.contains("HERO")) {
            return Category.PLAYER;
        }
        if (name.contains("KITSUNE") || name.contains("ONI") || name.contains("YOKAI")
            || name.contains("ENEMY") || name.contains("SHIKIGAMI")) {
            return Category.YOKAI;
        }
        return null;
    }

    /** Stable, lower-case asset id used as JSON file name and runtime lookup key. */
    private static String assetIdOf(Path png, Category category) {
        String base = stripExtension(png.getFileName().toString())
            .replaceAll("(?i)-dithered$", "")
            .replaceAll("(?i)_raw$", "")
            .replaceAll("(?i)_v\\d+$", "")
            .replaceAll("[^A-Za-z0-9]+", "_")
            .replaceAll("^_+|_+$", "")
            .toLowerCase(Locale.ROOT);

        return switch (category) {
            case PLAYER -> "player";
            case HANAFUDA -> "card_" + base;
            case YOKAI -> base.startsWith("yokai_") ? base : "yokai_" + yokaiBaseName(base);
            case OMAMORI, HANKO -> base;
        };
    }

    private static String yokaiBaseName(String base) {
        return switch (base) {
            case "kitsune_wild" -> "kitsune";
            case "enemy_1" -> "oni_wild";
            case "shikigami_oni" -> "oni";
            case "shikigami_ko_oni" -> "ko_oni";
            case "shikigami_dai_oni" -> "dai_oni";
            default -> base;
        };
    }

    private static String toProjectPath(Path relative) {
        return relative.toString().replace('\\', '/');
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    // ------------------------------------------------------------------
    // Image conversion
    // ------------------------------------------------------------------

    /** Reads the PNG and, when Aseprite metadata exists next to it, crops the first frame. */
    private static BufferedImage firstFrameOf(Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null) {
            throw new IOException("Unsupported image format: " + png);
        }

        Path json = png.resolveSibling(stripExtension(png.getFileName().toString()) + ".json");
        if (!Files.isRegularFile(json)) {
            return image;
        }

        String metadata = Files.readString(json, StandardCharsets.UTF_8);
        Matcher matcher = FIRST_FRAME.matcher(metadata);
        if (!matcher.find()) {
            return image;
        }

        int x = Integer.parseInt(matcher.group(1));
        int y = Integer.parseInt(matcher.group(2));
        int width = Integer.parseInt(matcher.group(3));
        int height = Integer.parseInt(matcher.group(4));
        if (x < 0 || y < 0 || width <= 0 || height <= 0
            || x + width > image.getWidth() || y + height > image.getHeight()) {
            System.err.println("  ! frame metadata outside the image, using the full sheet: " + json);
            return image;
        }
        return image.getSubimage(x, y, width, height);
    }

    /**
     * Crops fully transparent margins so the sprite fills its glyph matrix.
     * Fully opaque artwork (Hanafuda cards) is returned unchanged.
     */
    private static BufferedImage trimTransparentBorder(BufferedImage image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                if (alpha >= ALPHA_TRIM_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            // Completely transparent image: keep as-is so the output still exists.
            return image;
        }
        if (minX == 0 && minY == 0 && maxX == image.getWidth() - 1 && maxY == image.getHeight() - 1) {
            return image;
        }
        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** Box-filters the sprite onto the glyph grid and maps luminance + colour. */
    private static AsciiMatrix toAscii(BufferedImage image, Category category, Palette palette) {
        int sourceWidth = image.getWidth();
        int sourceHeight = image.getHeight();

        int targetHeight = Math.min(category.targetHeight, sourceHeight);
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * (double) targetHeight / sourceHeight));
        if (targetWidth > 64) {
            targetWidth = 64;
            targetHeight = Math.max(1, (int) Math.round(sourceHeight * (double) targetWidth / sourceWidth));
        }

        List<String> glyphRows = new ArrayList<>(targetHeight);
        int[] paletteIndices = new int[targetWidth * targetHeight];

        for (int targetY = 0; targetY < targetHeight; targetY++) {
            int sourceY0 = targetY * sourceHeight / targetHeight;
            int sourceY1 = Math.max(sourceY0 + 1, (targetY + 1) * sourceHeight / targetHeight);
            StringBuilder glyphRow = new StringBuilder(targetWidth);

            for (int targetX = 0; targetX < targetWidth; targetX++) {
                int sourceX0 = targetX * sourceWidth / targetWidth;
                int sourceX1 = Math.max(sourceX0 + 1, (targetX + 1) * sourceWidth / targetWidth);

                long alphaSum = 0;
                long redSum = 0;
                long greenSum = 0;
                long blueSum = 0;
                int samples = 0;

                for (int sourceY = sourceY0; sourceY < sourceY1; sourceY++) {
                    for (int sourceX = sourceX0; sourceX < sourceX1; sourceX++) {
                        int argb = image.getRGB(sourceX, sourceY);
                        int alpha = (argb >>> 24) & 0xFF;
                        alphaSum += alpha;
                        redSum += ((argb >> 16) & 0xFF) * alpha;
                        greenSum += ((argb >> 8) & 0xFF) * alpha;
                        blueSum += (argb & 0xFF) * alpha;
                        samples++;
                    }
                }

                double averageAlpha = samples == 0 ? 0.0 : (double) alphaSum / samples;
                int red = 0;
                int green = 0;
                int blue = 0;
                if (alphaSum > 0) {
                    red = (int) (redSum / alphaSum);
                    green = (int) (greenSum / alphaSum);
                    blue = (int) (blueSum / alphaSum);
                }

                // Composite over the black CRT background: transparency darkens the glyph.
                int composedRed = (int) Math.round(red * averageAlpha / 255.0);
                int composedGreen = (int) Math.round(green * averageAlpha / 255.0);
                int composedBlue = (int) Math.round(blue * averageAlpha / 255.0);

                double luminance = (0.2126 * composedRed + 0.7152 * composedGreen + 0.0722 * composedBlue) / 255.0;
                glyphRow.append(glyphFor(luminance));
                paletteIndices[targetY * targetWidth + targetX] =
                    palette.nearest(composedRed, composedGreen, composedBlue);
            }

            glyphRows.add(glyphRow.toString());
        }

        return new AsciiMatrix(targetWidth, targetHeight, List.copyOf(glyphRows), paletteIndices);
    }

    /** Maps a 0..1 luminosity onto the documented glyph ramp. */
    private static char glyphFor(double luminance) {
        for (int i = 0; i < GLYPH_BOUNDS.length; i++) {
            if (luminance < GLYPH_BOUNDS[i]) {
                return GLYPH_RAMP[i];
            }
        }
        return GLYPH_RAMP[GLYPH_RAMP.length - 1];
    }

    // ------------------------------------------------------------------
    // JSON output
    // ------------------------------------------------------------------

    private static void writeJson(Path target, SourceSprite sprite, AsciiMatrix matrix) throws IOException {
        StringBuilder json = new StringBuilder(8192);
        json.append("{\n");
        json.append("  \"assetId\": \"").append(escapeJson(sprite.assetId())).append("\",\n");
        json.append("  \"category\": \"").append(sprite.category().id).append("\",\n");
        json.append("  \"source\": \"").append(escapeJson(sprite.projectRelativePath())).append("\",\n");
        json.append("  \"width\": ").append(matrix.width()).append(",\n");
        json.append("  \"height\": ").append(matrix.height()).append(",\n");

        json.append("  \"glyphs\": [\n");
        List<String> glyphs = matrix.glyphs();
        for (int row = 0; row < glyphs.size(); row++) {
            json.append("    \"").append(escapeJson(glyphs.get(row))).append('"');
            json.append(row + 1 < glyphs.size() ? ",\n" : "\n");
        }
        json.append("  ],\n");

        json.append("  \"paletteIndices\": [");
        int[] indices = matrix.paletteIndices();
        for (int i = 0; i < indices.length; i++) {
            if (i % 32 == 0) {
                json.append("\n    ");
            }
            json.append(indices[i]);
            if (i + 1 < indices.length) {
                json.append(", ");
            }
        }
        json.append("\n  ]\n}\n");

        Files.writeString(target, json.toString(), StandardCharsets.UTF_8);
    }

    private static void writeManifest(Path target, List<Map.Entry<SourceSprite, AsciiMatrix>> converted,
                                      Path paletteFile, Path paletteTextureFile) throws IOException {
        List<Map.Entry<SourceSprite, AsciiMatrix>> sorted = new ArrayList<>(converted);
        sorted.sort(Comparator
            .comparing((Map.Entry<SourceSprite, AsciiMatrix> entry) -> entry.getKey().category().id)
            .thenComparing(entry -> entry.getKey().assetId()));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            counts.put(category.id, 0);
        }
        for (Map.Entry<SourceSprite, AsciiMatrix> entry : sorted) {
            counts.merge(entry.getKey().category().id, 1, Integer::sum);
        }

        StringBuilder json = new StringBuilder(8192);
        json.append("{\n");
        json.append("  \"paletteSource\": \"").append(escapeJson(paletteFile.getFileName().toString())).append("\",\n");
        json.append("  \"paletteTexture\": \"").append(escapeJson(paletteTextureFile.getFileName().toString())).append("\",\n");
        json.append("  \"paletteSize\": ").append(PALETTE_SIZE).append(",\n");
        json.append("  \"glyphRamp\": \"").append(escapeJson(new String(GLYPH_RAMP))).append("\",\n");
        json.append("  \"assetCount\": ").append(sorted.size()).append(",\n");

        json.append("  \"categories\": {");
        int categoryIndex = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            json.append(categoryIndex == 0 ? "\n" : ",\n");
            json.append("    \"").append(entry.getKey()).append("\": ").append(entry.getValue());
            categoryIndex++;
        }
        json.append("\n  },\n");

        json.append("  \"assets\": [\n");
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<SourceSprite, AsciiMatrix> entry = sorted.get(i);
            SourceSprite sprite = entry.getKey();
            AsciiMatrix matrix = entry.getValue();
            json.append("    {\"assetId\": \"").append(escapeJson(sprite.assetId())).append('"')
                .append(", \"category\": \"").append(sprite.category().id).append('"')
                .append(", \"width\": ").append(matrix.width())
                .append(", \"height\": ").append(matrix.height())
                .append(", \"source\": \"").append(escapeJson(sprite.projectRelativePath())).append("\"}");
            json.append(i + 1 < sorted.size() ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");

        Files.writeString(target, json.toString(), StandardCharsets.UTF_8);
    }

    private static void printSummary(List<Map.Entry<SourceSprite, AsciiMatrix>> converted,
                                     List<Path> sources, Path outputDir, Path paletteFile,
                                     Path paletteTextureFile) {
        System.out.println("PNG -> ASCII conversion");
        System.out.println("  palette: " + paletteFile.getFileName() + " (" + PALETTE_SIZE + " colors)");
        System.out.println("  texture: " + paletteTextureFile);
        System.out.println("  sources: " + sources.stream()
            .map(path -> path.getFileName().toString())
            .collect(Collectors.joining(", ")));
        System.out.println("  output : " + outputDir);
        for (Category category : Category.values()) {
            long count = converted.stream().filter(entry -> entry.getKey().category() == category).count();
            System.out.printf(Locale.ROOT, "  %-9s %3d%n", category.id, count);
        }
        System.out.println("  total    " + converted.size());
    }

    private static void deleteGeneratedJson(Path outputDir) throws IOException {
        List<Path> stale;
        try (Stream<Path> files = Files.list(outputDir)) {
            stale = files
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .collect(Collectors.toList());
        }
        for (Path path : stale) {
            Files.deleteIfExists(path);
        }
    }

    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    /** The project's fixed 128-colour palette, parsed from an Adobe Color Table file. */
    private static final class Palette {

        private final int[][] colors;

        private Palette(int[][] colors) {
            this.colors = colors;
        }

        static Palette load(Path file, int size) throws IOException {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length < size * 3) {
                throw new IOException("Palette file too small for " + size + " colors: " + file);
            }

            int[][] colors = new int[size][3];
            for (int index = 0; index < size; index++) {
                colors[index][0] = bytes[index * 3] & 0xFF;
                colors[index][1] = bytes[index * 3 + 1] & 0xFF;
                colors[index][2] = bytes[index * 3 + 2] & 0xFF;
            }
            return new Palette(colors);
        }

        /** Index of the nearest palette colour (squared RGB distance). */
        int nearest(int red, int green, int blue) {
            int bestIndex = 0;
            long bestDistance = Long.MAX_VALUE;
            for (int index = 0; index < colors.length; index++) {
                long dRed = colors[index][0] - red;
                long dGreen = colors[index][1] - green;
                long dBlue = colors[index][2] - blue;
                long distance = dRed * dRed + dGreen * dGreen + dBlue * dBlue;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestIndex = index;
                }
            }
            return bestIndex;
        }

        /**
         * Writes the palette as a {@code width x 1} RGBA texture. The palette-swap
         * shader samples it with {@code (index + 0.5) / size} on the U axis.
         */
        void writePng(Path target) throws IOException {
            BufferedImage image = new BufferedImage(colors.length, 1, BufferedImage.TYPE_INT_ARGB);
            for (int index = 0; index < colors.length; index++) {
                int argb = 0xFF000000
                    | (colors[index][0] << 16)
                    | (colors[index][1] << 8)
                    | colors[index][2];
                image.setRGB(index, 0, argb);
            }

            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (!ImageIO.write(image, "png", target.toFile())) {
                throw new IOException("No PNG writer available for palette texture: " + target);
            }
        }
    }

}
