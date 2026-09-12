package com.lychcs.koikoi.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.OmamoriPool;

public class HubScreen extends ScreenAdapter {
    private final Stage stage;
    private final RunSession runSession;
    private Skin skin;
    private TextureAtlas atlas;
    private Texture background;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Label monLabel;
    private Label voidDustLabel;

    // Schrein-Zustand (1x Beschwören, 1x Upgraden pro Besuch)
    private boolean hasSummonedThisVisit = false;
    private boolean hasUpgradedThisVisit = false;
    private String lastSummonedYokaiName = null; // Schutz: Kann nicht das frisch beschworene upgraden

    public HubScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(1280, 720));

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
    }

    private void buildHubUi() {
        stage.clear();
        Table root = new Table();
        root.setFillParent(true);
        root.pad(40);

        // OBERE LEISTE: Währungen
        Table topBar = new Table();
        monLabel = new Label("Mon: " + runSession.getMon(), skin);
        monLabel.setFontScale(1.3f);
        voidDustLabel = new Label("Void Dust: " + runSession.getVoidDust(), skin);
        voidDustLabel.setFontScale(1.3f);
        voidDustLabel.setColor(Color.PURPLE);

        topBar.add(monLabel).padRight(50);
        topBar.add(voidDustLabel);
        root.add(topBar).padBottom(30).row();

        // HAUPTBEREICH: Links Schrein, Rechts Shop
        Table splitTable = new Table();

        // --- LINKER BEREICH: DER SCHREIN (Yokai) ---
        Table shrineBox = new Table();
        shrineBox.setBackground(panelBackground);
        shrineBox.pad(20);

        Label shrineTitle = new Label("Schrein der Kamis", skin);
        shrineTitle.setFontScale(1.2f);
        shrineBox.add(shrineTitle).padBottom(15).row();

        TextButton summonButton = new TextButton(hasSummonedThisVisit ? "Beschworen (0/1)" : "Yokai beschwören (30 Dust)", indieButtonStyle);
        summonButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!hasSummonedThisVisit && runSession.getVoidDust() >= 30) {
                    if (runSession.getYokaiBag().size() < runSession.getMaxYokaiBag()) {
                        runSession.addVoidDust(-30);
                        hasSummonedThisVisit = true;

                        // Hier generierst du einen neuen Yokai (Beispiel-Dummy oder aus Pool)
                        // lastSummonedYokaiName = ...

                        updateLabels();
                        buildHubUi(); // UI aktualisieren
                    }
                }
            }
        });

        TextButton upgradeButton = new TextButton(hasUpgradedThisVisit ? "Aufgewertet (0/1)" : "Yokai upgraden (20 Dust)", indieButtonStyle);
        upgradeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!hasUpgradedThisVisit && runSession.getVoidDust() >= 20) {
                    runSession.addVoidDust(-20);
                    hasUpgradedThisVisit = true;
                    updateLabels();
                    buildHubUi();
                }
            }
        });

        shrineBox.add(summonButton).width(240).height(50).padBottom(15).row();
        shrineBox.add(upgradeButton).width(240).height(50).padBottom(10).row();


        // --- RECHTER BEREICH: DER SHOP (Omamoris & Items) ---
        Table shopBox = new Table();
        shopBox.setBackground(panelBackground);
        shopBox.pad(20);

        Label shopTitle = new Label("Markt der Omamori", skin);
        shopTitle.setFontScale(1.2f);
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

        shopBox.add(buyOmaButton).width(280).height(50).padBottom(15).row();


        splitTable.add(shrineBox).width(350).height(400).padRight(50).top();
        splitTable.add(shopBox).width(380).height(400).top();
        root.add(splitTable).expandY().fillY().padBottom(30).row();

        // UNTERE LEISTE: Weiterziehen
        TextButton proceedButton = new TextButton("Weiterziehen", indieButtonStyle);
        proceedButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
            }
        });
        root.add(proceedButton).width(300).height(65);

        stage.addActor(root);
    }

    private void updateLabels() {
        monLabel.setText("Mon: " + runSession.getMon());
        voidDustLabel.setText("Void Dust: " + runSession.getVoidDust());
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        stage.getBatch().begin();
        stage.getBatch().draw(background, 0, 0, 1280, 720);
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
    }
}
