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
import com.lychcs.koikoi.model.yokai.YokaiPool;
import com.lychcs.koikoi.model.yokai.YokaiStage;
import com.lychcs.koikoi.run.RunSession;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class ShrineScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int SUMMON_COST = 30;
    private static final int EVOLVE_COST = 20;

    private final Stage stage;
    private final RunSession runSession;
    private Skin skin;
    private TextureAtlas atlas;
    private Texture background;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Texture darkOverlayTex;
    private Texture purpleOverlayTex;
    private TextureRegionDrawable darkOverlayBackground;
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
        skin.add("default-font", FontManager.getFont(), BitmapFont.class);
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

        // Overlay Texturen sauber erstellen und referenzieren
        Pixmap darkPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        darkPixmap.setColor(new Color(0, 0, 0, 0.85f));
        darkPixmap.fill();
        darkOverlayTex = new Texture(darkPixmap);
        darkOverlayBackground = new TextureRegionDrawable(new TextureRegion(darkOverlayTex));
        darkPixmap.dispose();

        Pixmap purplePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        purplePixmap.setColor(new Color(0.12f, 0.0f, 0.18f, 0.92f));
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

        Table topBar = new Table();
        voidDustLabel = new Label("Void Dust: " + runSession.getVoidDust(), skin);
        voidDustLabel.setFontScale(1.4f);
        voidDustLabel.setColor(Color.valueOf("B388FF"));
        topBar.add(voidDustLabel);
        root.add(topBar).expandX().top().left().padBottom(40).row();

        Table shrineBox = new Table();
        shrineBox.setBackground(panelBackground);
        shrineBox.pad(30);

        Label title = new Label("Schrein der Kamis", skin);
        title.setFontScale(1.5f);
        title.setColor(Color.valueOf("D1C4E9"));
        shrineBox.add(title).padBottom(25).row();

        boolean canSummon = !runSession.isShrineSummonedThisVisit() && runSession.getVoidDust() >= SUMMON_COST
            && runSession.getYokaiBag().size() < runSession.getMaxYokaiBag();

        String summonText = runSession.isShrineSummonedThisVisit()
            ? "Yokai beschworen (0/1)"
            : "Yokai beschwoeren (" + SUMMON_COST + " Dust)";
        TextButton summonButton = new TextButton(summonText, indieButtonStyle);
        if (!canSummon) summonButton.getColor().a = 0.5f;

        summonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!runSession.isShrineSummonedThisVisit() && runSession.getVoidDust() >= SUMMON_COST) {
                    if (runSession.getYokaiBag().size() < runSession.getMaxYokaiBag()) {
                        runSession.addVoidDust(-SUMMON_COST);
                        runSession.setShrineSummonedThisVisit(true);

                        Yokai newYokai = YokaiPool.getRandomYokai();
                        runSession.getYokaiBag().add(newYokai);
                        runSession.getShrineEvolvedThisVisit().add(newYokai);

                        playVoidFlameAnimation(newYokai.getName());
                    }
                }
            }
        });

        TextButton evolveButton = new TextButton("Yokai evolven (" + EVOLVE_COST + " Dust)", indieButtonStyle);
        evolveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.getVoidDust() >= EVOLVE_COST && !runSession.getYokaiBag().isEmpty()) {
                    showSkulEvolutionMenu();
                }
            }
        });

        shrineBox.add(summonButton).width(320).height(60).padBottom(20).row();
        shrineBox.add(evolveButton).width(320).height(60).padBottom(20).row();

        Label bagStatus = new Label("Yokai im Beutel: " + runSession.getYokaiBag().size() + "/" + runSession.getMaxYokaiBag(), skin);
        shrineBox.add(bagStatus);

        root.add(shrineBox).expandY().center().padBottom(40).row();

        TextButton backButton = new TextButton("Zurueck zum Rastplatz", indieButtonStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new OverworldScreen(runSession));
            }
        });
        root.add(backButton).width(320).height(65).bottom();

        stage.addActor(root);
    }

    private String getSeasonalBackgroundPath(String prefix) {
        String seasonName = runSession.getCurrentSeason().name();
        return "backgrounds/" + "BACKGROUND" + "_" + prefix + "_" + seasonName + ".png";
    }

    private void playVoidFlameAnimation(String yokaiName) {
        Table flameOverlay = new Table();
        flameOverlay.setFillParent(true);
        flameOverlay.setBackground(purpleFlameOverlayBackground);

        Label voidText = new Label("~ DER VOID ANTWORTET ~\n\nLila-schwarze Flammen lodern auf...\n[" + yokaiName + "] ist erwacht!", skin);
        voidText.setColor(Color.valueOf("E1BEE7"));
        voidText.setFontScale(1.4f);
        voidText.setAlignment(Align.center);

        flameOverlay.add(voidText);
        stage.addActor(flameOverlay);

        flameOverlay.addAction(Actions.sequence(
            Actions.alpha(0f),
            Actions.fadeIn(0.2f),
            Actions.delay(1.0f),
            Actions.fadeOut(0.3f),
            Actions.run(() -> {
                flameOverlay.remove();
                buildShrineUi();
            })
        ));
    }

    private void showSkulEvolutionMenu() {
        Table evoOverlay = new Table();
        evoOverlay.setFillParent(true);
        evoOverlay.setBackground(darkOverlayBackground);

        Table contentBox = new Table();
        contentBox.setBackground(panelBackground);
        contentBox.pad(30);

        Label title = new Label("Waehle einen Yokai zur Evolution (-" + EVOLVE_COST + " Dust)", skin);
        title.setFontScale(1.3f);
        contentBox.add(title).padBottom(20).row();

        Table listTable = new Table();
        for (Yokai yokai : runSession.getYokaiBag()) {
            boolean alreadyEvolved = runSession.getShrineEvolvedThisVisit().contains(yokai);
            boolean isMaxLevel = (yokai.getLevel() == 3);

            String statusText = alreadyEvolved ? " (Bereits aufgewertet)" : (isMaxLevel ? " (Max Level)" : "");
            TextButton yokaiBtn = new TextButton(yokai.getName() + " [" + yokai.getStage().name() + "]" + statusText, indieButtonStyle);

            if (alreadyEvolved || isMaxLevel || runSession.getVoidDust() < EVOLVE_COST) {
                yokaiBtn.getColor().a = 0.4f;
            } else {
                yokaiBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        runSession.addVoidDust(-EVOLVE_COST);
                        yokai.evolve();
                        runSession.getShrineEvolvedThisVisit().add(yokai);

                        evoOverlay.remove();
                        buildShrineUi();
                    }
                });
            }
            listTable.add(yokaiBtn).width(400).height(50).padBottom(10).row();
        }

        contentBox.add(listTable).padBottom(20).row();

        TextButton closeBtn = new TextButton("Schliessen", indieButtonStyle);
        closeBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                evoOverlay.remove();
            }
        });
        contentBox.add(closeBtn).width(180).height(45);

        evoOverlay.add(contentBox);
        stage.addActor(evoOverlay);
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
        if (darkOverlayTex != null) darkOverlayTex.dispose();
        if (purpleOverlayTex != null) purpleOverlayTex.dispose();
    }
}
