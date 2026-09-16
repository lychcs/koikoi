package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.run.RunSession;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class ShrineScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int PURIFY_COST = 15;

    private final Stage stage;
    private final RunSession runSession;
    private Skin skin;
    private TextureAtlas atlas;
    private Texture background;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Texture purpleOverlayTex;
    private TextureRegionDrawable purpleFlameOverlayBackground;

    private Label voidDustLabel;

    public ShrineScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildShrineUi();

        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture tex : atlas.getTextures()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }

        background = new Texture(Gdx.files.internal(getSeasonalBackgroundPath("SHRINE")));

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 20, 20, 20, 20));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;

        Pixmap purplePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        purplePixmap.setColor(new Color(0.10f, 0.0f, 0.16f, 0.92f));
        purplePixmap.fill();
        purpleOverlayTex = new Texture(purplePixmap);
        purpleFlameOverlayBackground = new TextureRegionDrawable(new TextureRegion(purpleOverlayTex));
        purplePixmap.dispose();
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

        Label title = new Label("Schrein der Rast", skin);
        title.setFontScale(1.5f);
        title.setColor(Color.valueOf("D1C4E9"));
        shrineBox.add(title).padBottom(20).row();

        long exhaustedCount = runSession.getYokaiBag().stream()
            .filter(Yokai::isExhausted)
            .count();

        boolean canPurify = (exhaustedCount > 0) && (runSession.getVoidDust() >= PURIFY_COST);

        String purifyText = (exhaustedCount == 0)
            ? "Alle Yokai sind ausgeruht"
            : "Geister wecken (" + PURIFY_COST + " VOID DUST)";

        TextButton purifyButton = new TextButton(purifyText, indieButtonStyle);
        if (!canPurify) purifyButton.getColor().a = 0.5f;

        purifyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (exhaustedCount > 0 && runSession.getVoidDust() >= PURIFY_COST) {
                    runSession.addVoidDust(-PURIFY_COST);

                    for (Yokai yokai : runSession.getYokaiBag()) {
                        yokai.setExhausted(false);
                    }

                    playPurifyAnimation((int) exhaustedCount);
                }
            }
        });

        shrineBox.add(purifyButton).width(360).height(65).padBottom(25).row();

        // Übersicht der Begleiter im Beutel
        Table yokaiListTable = new Table();
        for (Yokai y : runSession.getYokaiBag()) {
            String status = y.isExhausted() ? " [Rastet]" : " [Bereit]";
            String xpInfo = (y.getLevel() >= Yokai.MAX_LEVEL)
                ? "MAX"
                : (y.getCurrentXp() + "/" + y.getXpToNextLevel() + " XP");

            Label yLabel = new Label(y.getName() + " (Lv. " + y.getLevel() + ") - " + xpInfo + status, skin);
            yLabel.setFontScale(0.85f);
            if (y.isExhausted()) yLabel.setColor(Color.GRAY);
            else yLabel.setColor(Color.WHITE);

            yokaiListTable.add(yLabel).padBottom(6).row();
        }

        shrineBox.add(yokaiListTable).padBottom(15).row();

        root.add(shrineBox).expandY().center().padBottom(30).row();

        TextButton backButton = new TextButton("Zurueck zur Erkundung", indieButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).changeScreen(new OverworldScreen(runSession));
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

        Label purifyText = new Label("~ RITUS DER REINIGUNG ~\n\nHeilige Raeucherstaebchen wecken deine Geister.\n["
            + restoredCount + " Yokai] sind wieder einsatzbereit!", skin);
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
        stage.dispose();
        if (background != null) background.dispose();
        if (atlas != null) atlas.dispose();
        if (skin != null) skin.dispose();
        if (purpleOverlayTex != null) purpleOverlayTex.dispose();
    }
}
