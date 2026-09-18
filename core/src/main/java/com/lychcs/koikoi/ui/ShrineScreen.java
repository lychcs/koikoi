package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.run.RunSession;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class ShrineScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int PURIFY_COST = 15;

    private final Stage stage;
    private final RunSession runSession;
    /** Geliehene, global verwaltete Assets (Eigentum: GameAssets). */
    private final GameAssets assets;
    private Skin skin;
    private TextureAtlas atlas;
    /** Screen-lokaler, saisonabhaengiger Hintergrund. */
    private Texture background;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    /** Overlay der Reinigungs-Animation: Drawable der gemeinsamen Skin (keine eigene Textur). */
    private Drawable purpleFlameOverlayBackground;

    private Label voidDustLabel;

    /** Schutz gegen Mehrfach-Dispose durch den Screen-Manager. */
    private boolean disposed;

    public ShrineScreen(RunSession runSession, GameAssets assets) {
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }

        this.runSession = runSession;
        this.assets = assets;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildShrineUi();
    }

    @Override
    public void show() {
        // Der InputProcessor wird erst beim Anzeigen gesetzt: ein waehrend eines
        // ausstehenden Screen-Wechsels sofort wieder verworfener Screen darf nie
        // auf eine disposed Stage zeigen.
        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {
        // Geliehene, global verwaltete Assets: kein Laden und kein Dispose hier.
        skin = assets.getSkin();
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        atlas = assets.getAtlas(GameAssets.GAME_ATLAS);

        // Screen-lokal: der Hintergrund haengt von der Jahreszeit ab.
        background = new Texture(Gdx.files.internal(getSeasonalBackgroundPath("SHRINE")));

        // NinePatch-Drawables verweisen nur auf die gemeinsamen Texturen.
        panelBackground = assets.newPanelDrawable();

        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = assets.newButtonDrawable();
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;

        // Das purple Overlay kommt aus dem White-Drawable der gemeinsamen Skin:
        // keine 1x1-Pixmap und damit keine zusaetzliche GPU-Ressource pro Screen.
        purpleFlameOverlayBackground = skin.newDrawable("white", new Color(0.10f, 0.0f, 0.16f, 0.92f));
    }

    private void buildShrineUi() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.pad(40);

        // Header: Währung
        Table topBar = new Table();
        voidDustLabel = new Label("VOID DUST: " + runSession.getVoidDust(), skin);
        voidDustLabel.setFontScale(1.4f);
        voidDustLabel.setColor(Color.valueOf("B388FF"));
        topBar.add(voidDustLabel);
        root.add(topBar).expandX().top().left().padBottom(30).row();

        // Zentrales Schrein-Panel
        Table shrineBox = new Table();
        shrineBox.setBackground(panelBackground);
        shrineBox.pad(35);

        Label title = new Label("Shrine of Rest", skin);
        title.setFontScale(1.5f);
        title.setColor(Color.valueOf("D1C4E9"));
        shrineBox.add(title).padBottom(20).row();

        long exhaustedCount = runSession.getShikigamiBag().stream()
            .filter(Shikigami::isExhausted)
            .count();

        boolean canPurify = (exhaustedCount > 0) && (runSession.getVoidDust() >= PURIFY_COST);

        String purifyText = (exhaustedCount == 0)
            ? "All Shikigami are ready"
            : "Purify Spirits (" + PURIFY_COST + " VOID DUST)";

        TextButton purifyButton = new TextButton(purifyText, indieButtonStyle);
        if (!canPurify) purifyButton.getColor().a = 0.5f;

        purifyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (exhaustedCount > 0 && runSession.getVoidDust() >= PURIFY_COST) {
                    runSession.addVoidDust(-PURIFY_COST);

                    for (Shikigami shikigami : runSession.getShikigamiBag()) {
                        shikigami.setExhausted(false);
                    }

                    playPurifyAnimation((int) exhaustedCount);
                }
            }
        });

        shrineBox.add(purifyButton).width(360).height(65).padBottom(25).row();

        // Übersicht der Begleiter im Beutel
        Table shikigamiListTable = new Table();
        for (Shikigami y : runSession.getShikigamiBag()) {
            String status = y.isExhausted() ? " [Exhausted]" : " [Ready]";
            String xpInfo = (y.getLevel() >= Shikigami.MAX_LEVEL)
                ? "MAX"
                : (y.getCurrentXp() + "/" + y.getXpToNextLevel() + " XP");

            Label yLabel = new Label(y.getName() + " (Lv. " + y.getLevel() + ") - " + xpInfo + status, skin);
            yLabel.setFontScale(0.85f);
            if (y.isExhausted()) yLabel.setColor(Color.GRAY);
            else yLabel.setColor(Color.WHITE);

            shikigamiListTable.add(yLabel).padBottom(6).row();
        }

        shrineBox.add(shikigamiListTable).padBottom(15).row();

        root.add(shrineBox).expandY().center().padBottom(30).row();

        TextButton backButton = new TextButton("Back to Exploration", indieButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).changeScreen(new OverworldScreen(runSession, assets));
            }
        });
        root.add(backButton).width(300).height(60).bottom();

        stage.addActor(root);
    }

    private String getSeasonalBackgroundPath(String prefix) {
        String seasonName = runSession.getCurrentSeason().name();
        return "backgrounds/" + "BACKGROUND" + "_" + prefix + "_" + seasonName + ".png";
    }

    private void playPurifyAnimation(int restoredCount) {
        Table purifyOverlay = new Table();
        purifyOverlay.setFillParent(true);
        purifyOverlay.setBackground(purpleFlameOverlayBackground);

        Label purifyText = new Label("- RITE OF PURIFICATION -\n\nSacred incense awakens your spirits.\n["
            + restoredCount + " Shikigami] are ready for battle again!", skin);
        purifyText.setColor(Color.valueOf("E1BEE7"));
        purifyText.setFontScale(1.3f);
        purifyText.setAlignment(Align.center);

        purifyOverlay.add(purifyText);
        stage.addActor(purifyOverlay);

        purifyOverlay.addAction(Actions.sequence(
            Actions.alpha(0f),
            Actions.fadeIn(0.2f),
            Actions.delay(0.9f),
            Actions.fadeOut(0.3f),
            Actions.run(() -> {
                purifyOverlay.remove();
                buildShrineUi();
            })
        ));
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        stage.getBatch().begin();
        stage.getBatch().draw(background, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        stage.getBatch().end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        // Nur screen-eigene Ressourcen freigeben.
        stage.dispose();
        if (background != null) background.dispose();
        // Skin, Atlas, Panel-/Button-Textur und das Overlay-Drawable sind
        // geliehen (Eigentum: GameAssets/Skin) und werden hier bewusst NICHT
        // disposet.
    }
}
