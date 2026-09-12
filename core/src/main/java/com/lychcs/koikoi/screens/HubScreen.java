package com.lychcs.koikoi.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
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
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.OmamoriPool;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.model.yokai.YokaiPool;
import com.lychcs.koikoi.model.yokai.YokaiStage;
import com.lychcs.koikoi.run.RunSession;

import java.util.HashSet;
import java.util.Set;

public class HubScreen extends ScreenAdapter {

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
    private TextureRegionDrawable darkOverlayBackground;
    private TextureRegionDrawable purpleFlameOverlayBackground;

    private Label monLabel;
    private Label voidDustLabel;

    // Schrein-Restriktionen für diesen Besuch
    private boolean hasSummonedThisVisit = false;
    // Trackt alle Yokai, die bei diesem Schrein-Besuch bereits 1x geupgradet wurden
    private final Set<Yokai> evolvedThisVisit = new HashSet<>();

    public HubScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildHubUi();

        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture tex : atlas.getTextures()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }

        background = new Texture(Gdx.files.internal("backgrounds/SHOP_AUTUMN.jpg"));

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 20, 20, 20, 20));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = skin.getFont("default-font");
        indieButtonStyle.fontColor = Color.WHITE;

        // Dunkles Overlay für Menüs
        Pixmap darkPixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        darkPixmap.setColor(new Color(0, 0, 0, 0.85f));
        darkPixmap.fill();
        darkOverlayBackground = new TextureRegionDrawable(new TextureRegion(new Texture(darkPixmap)));
        darkPixmap.dispose();

        // Lila-Schwarzes Overlay für Void-Flammen
        Pixmap purplePixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        purplePixmap.setColor(new Color(0.12f, 0.0f, 0.18f, 0.92f));
        purplePixmap.fill();
        purpleFlameOverlayBackground = new TextureRegionDrawable(new TextureRegion(new Texture(purplePixmap)));
        purplePixmap.dispose();
    }

    private void buildHubUi() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.pad(30);

        // --- TOP BAR (Währungen) ---
        Table topBar = new Table();
        monLabel = new Label("Mon: " + runSession.getMon(), skin);
        monLabel.setFontScale(1.3f);

        voidDustLabel = new Label("Void Dust: " + runSession.getVoidDust(), skin);
        voidDustLabel.setFontScale(1.3f);
        voidDustLabel.setColor(Color.valueOf("B388FF"));

        topBar.add(monLabel).padRight(60);
        topBar.add(voidDustLabel);
        root.add(topBar).padBottom(25).row();

        // --- HAUPTBEREICH (Split: Links Schrein, Rechts Shop) ---
        Table splitTable = new Table();

        // 1. LINKER BEREICH: SCHREIN
        Table shrineBox = new Table();
        shrineBox.setBackground(panelBackground);
        shrineBox.pad(20);

        Label shrineTitle = new Label("Schrein der Kamis", skin);
        shrineTitle.setFontScale(1.3f);
        shrineTitle.setColor(Color.valueOf("D1C4E9"));
        shrineBox.add(shrineTitle).padBottom(15).row();

        // Beschwörungs-Button (1x pro Besuch)
        boolean canSummon = !hasSummonedThisVisit && runSession.getVoidDust() >= SUMMON_COST
            && (runSession.getYokaiBag() != null && runSession.getYokaiBag().size() < runSession.getMaxYokaiBag());

        String summonLabelText = hasSummonedThisVisit
            ? "Yokai beschwören (0/1)"
            : "Yokai beschwören (" + SUMMON_COST + " Dust)";
        TextButton summonButton = new TextButton(summonLabelText, indieButtonStyle);
        if (!canSummon) summonButton.getColor().a = 0.5f;

        summonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!hasSummonedThisVisit && runSession.getVoidDust() >= SUMMON_COST) {
                    if (runSession.getYokaiBag() != null && runSession.getYokaiBag().size() < runSession.getMaxYokaiBag()) {
                        runSession.addVoidDust(-SUMMON_COST);
                        hasSummonedThisVisit = true;

                        Yokai newYokai = YokaiPool.getRandomYokai();
                        runSession.getYokaiBag().add(newYokai);
                        // Neu beschworene Yokai dürfen in diesem Schrein noch nicht evolvt werden:
                        evolvedThisVisit.add(newYokai);

                        playVoidFlameAnimation(newYokai.getName());
                    }
                }
            }
        });

        // Evolve-Button (Beliebig oft, aber jeder Yokai nur 1x pro Besuch)
        TextButton evolveButton = new TextButton("Yokai evolven (" + EVOLVE_COST + " Dust)", indieButtonStyle);
        boolean canEvolveAny = runSession.getVoidDust() >= EVOLVE_COST && hasEvolvableYokai();
        if (!canEvolveAny) evolveButton.getColor().a = 0.5f;

        evolveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.getVoidDust() >= EVOLVE_COST && runSession.getYokaiBag() != null && !runSession.getYokaiBag().isEmpty()) {
                    showSkulEvolutionMenu();
                }
            }
        });

        shrineBox.add(summonButton).width(280).height(55).padBottom(15).row();
        shrineBox.add(evolveButton).width(280).height(55).padBottom(20).row();

        Label bagStatus = new Label("Yokai im Beutel: " +
            (runSession.getYokaiBag() != null ? runSession.getYokaiBag().size() : 0) + "/" + runSession.getMaxYokaiBag(), skin);
        bagStatus.setFontScale(0.9f);
        shrineBox.add(bagStatus);

        // 2. RECHTER BEREICH: SHOP (Omamori & Items)
        Table shopBox = new Table();
        shopBox.setBackground(panelBackground);
        shopBox.pad(20);

        Label shopTitle = new Label("Markt der Omamori", skin);
        shopTitle.setFontScale(1.3f);
        shopBox.add(shopTitle).padBottom(15).row();

        Omamori randomOma = OmamoriPool.getRandomOmamori();
        int price = OmamoriPool.getCost(randomOma.getRarity());
        TextButton buyOmaButton = new TextButton(randomOma.getName() + " (" + price + " Mon)", indieButtonStyle);
        buyOmaButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.getMon() >= price) {
                    runSession.addMon(-price);
                    runSession.getActiveOmamoris().add(randomOma);
                    updateLabels();
                    buildHubUi();
                }
            }
        });

        shopBox.add(buyOmaButton).width(300).height(55).padBottom(15).row();

        splitTable.add(shrineBox).width(400).height(420).padRight(40).top();
        splitTable.add(shopBox).width(400).height(420).top();
        root.add(splitTable).expandY().fillY().padBottom(25).row();

        // --- BOTTOM BAR (Weiterziehen) ---
        TextButton proceedButton = new TextButton("Weiterziehen", indieButtonStyle);
        proceedButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
            }
        });
        root.add(proceedButton).width(320).height(65);

        stage.addActor(root);
    }

    private boolean hasEvolvableYokai() {
        if (runSession.getYokaiBag() == null) return false;
        for (Yokai y : runSession.getYokaiBag()) {
            if (!evolvedThisVisit.contains(y) && y.getStage() != YokaiStage.LEVEL_3 && y.getStage() != YokaiStage.YAMI) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spielt die Void-Flammen Animation ab, wenn ein Yokai beschworen wird.
     */
    private void playVoidFlameAnimation(String yokaiName) {
        Table flameOverlay = new Table();
        flameOverlay.setFillParent(true);
        flameOverlay.setBackground(purpleFlameOverlayBackground);

        Label voidText = new Label("~ DER VOID ANTWORTET ~\n\nLila-schwarze Flammen verdichten sich...\n[" + yokaiName + "] ist erwacht!", skin);
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
                updateLabels();
                buildHubUi();
            })
        ));
    }

    /**
     * Skul-Style Evolution Overlay:
     * Man kann beliebig viele verschiedene Yokai upgraden, aber jeden pro Besuch nur 1x.
     */
    private void showSkulEvolutionMenu() {
        Table evoOverlay = new Table();
        evoOverlay.setFillParent(true);
        evoOverlay.setBackground(darkOverlayBackground);

        Table contentBox = new Table();
        contentBox.setBackground(panelBackground);
        contentBox.pad(30);

        Label title = new Label("Wähle einen Yokai zur Evolution (-" + EVOLVE_COST + " Dust)", skin);
        title.setFontScale(1.3f);
        contentBox.add(title).padBottom(20).row();

        Table listTable = new Table();
        for (Yokai yokai : runSession.getYokaiBag()) {
            boolean alreadyEvolved = evolvedThisVisit.contains(yokai);
            boolean isMaxLevel = (yokai.getStage() == YokaiStage.LEVEL_3 || yokai.getStage() == YokaiStage.YAMI);

            String statusText = "";
            if (alreadyEvolved) statusText = " (Bereits aufgewertet)";
            else if (isMaxLevel) statusText = " (Max Level)";

            TextButton yokaiBtn = new TextButton(yokai.getName() + " [" + yokai.getStage().name() + "]" + statusText, indieButtonStyle);

            if (alreadyEvolved || isMaxLevel || runSession.getVoidDust() < EVOLVE_COST) {
                yokaiBtn.getColor().a = 0.4f;
            } else {
                yokaiBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        runSession.addVoidDust(-EVOLVE_COST);
                        yokai.evolve();
                        evolvedThisVisit.add(yokai); // Verhindert eine 2. Evolution desselben Yokais bei diesem Besuch

                        evoOverlay.remove();
                        updateLabels();
                        buildHubUi();
                    }
                });
            }
            listTable.add(yokaiBtn).width(380).height(50).padBottom(10).row();
        }

        contentBox.add(listTable).padBottom(20).row();

        TextButton closeBtn = new TextButton("Schließen", indieButtonStyle);
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

    private void updateLabels() {
        monLabel.setText("Mon: " + runSession.getMon());
        voidDustLabel.setText("Void Dust: " + runSession.getVoidDust());
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
        background.dispose();
        atlas.dispose();
        skin.dispose();
    }
}
