package com.lychcs.koikoi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Locates the repository-level {@code assets/} folder from any Gradle test
 * working directory and reads files from it, so the tests validate exactly the
 * assets that ship with the game.
 */
public final class TestAssetFiles {

    private TestAssetFiles() {
    }

    /** Absolute path of the repository {@code assets} directory. */
    public static Path assetRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("assets");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
            "assets folder not found above " + Paths.get("").toAbsolutePath().toAbsolutePath());
    }

    /** Reads an asset file as UTF-8 text (path relative to {@code assets/}). */
    public static String read(String relativePath) throws IOException {
        return Files.readString(assetRoot().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /** Sorted files inside an asset directory with the given file-name suffix. */
    public static List<Path> filesIn(String relativeDirectory, String suffix) throws IOException {
        Path directory = assetRoot().resolve(relativeDirectory);
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Missing asset directory: " + directory);
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                .filter(path -> Files.isRegularFile(path))
                .filter(path -> path.getFileName().toString().endsWith(suffix))
                .sorted()
                .collect(Collectors.toList());
        }
    }
}
