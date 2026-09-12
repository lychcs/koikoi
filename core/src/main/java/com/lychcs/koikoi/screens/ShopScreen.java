package com.lychcs.koikoi.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
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
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.OmamoriPool;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;

public class ShopScreen extends ScreenAdapter {
    private final Stage stage;
    private final RunSession runSession;
    private Skin skin;
    private TextureAtlas atlas;

    private Texture background;
    private Texture boosterPackTexture;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Label monLabel;
    private Table boosterOverlay;
    private TextureRegionDrawable darkOverlayBackground;

    public ShopScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(1280, 720));

        initAssets();

        // Initialer Aufbau
        buildShopUi();
        buildBoosterOverlay();

        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture tex : atlas.getTextures()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }

        String bgPath = "backgrounds/SHOP_AUTUMN.jpg";
        if (runSession.getCurrentSeason() == GameSeason.SPRING) bgPath = "backgrounds/SHOP_SPRING.jpg";
        if (runSession.getCurrentSeason() == GameSeason.SUMMER) bgPath = "backgrounds/SHOP_SUMMER.jpg";
        if (runSession.getCurrentSeason() == GameSeason.AUTUMN) bgPath = "backgrounds/SHOP_AUTUMN.jpg";
        if (runSession.getCurrentSeason() == GameSeason.WINTER) bgPath = "backgrounds/SHOP_WINTER.jpg";

        background = new Texture(Gdx.files.internal(bgPath));

        boosterPackTexture = new Texture(Gdx.files.internal("backgrounds/BOOSTER_PACK.png"));
        boosterPackTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 40, 40, 40, 40));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 50, 50, 20, 20));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = skin.getFont("default-font");
        indieButtonStyle.fontColor = Color.WHITE;

        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0, 0, 0, 0.85f));
        pixmap.fill();
        darkOverlayBackground = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
        pixmap.dispose();
    }

    private void buildShopUi() {
        Table root = new Table();
        root.setFillParent(true);

        Table shelves = new Table();
        shelves.padTop(80).padBottom(120).padLeft(120).padRight(120);
        shelves.setFillParent(true);

        Table column1 = new Table(); // Omamoris
        Table column2 = new Table(); // Booster Packs
        Table column3 = new Table(); // Fukus

        // ==========================================
        // SPALTE 1: RANDOM OMAMORIS AUS DEM POOL
        // ==========================================
        Omamori oma1 = OmamoriPool.getRandomOmamori();
        Omamori oma2 = OmamoriPool.getRandomOmamori();

        // Verhindert, dass 2x exakt das gleiche Omamori im selben Shop liegt
        while (oma2.getClass().equals(oma1.getClass())) {
            oma2 = OmamoriPool.getRandomOmamori();
        }

        column1.add(createOmamoriCard(oma1)).padBottom(20).row();
        column1.add(createOmamoriCard(oma2)).row();

        // ==========================================
        // SPALTE 2: BOOSTER PACKS
        // ==========================================
        column2.add(createItemCard("Ema Booster Pack", "Get 1 of 3 rare Cards.", 80, () -> {
            if (runSession.getMon() >= 80) {
                runSession.addMon(-80);
                updateMonDisplay();
                startBoosterSequence("EMA");
            }
        })).padBottom(20).row();

        column2.add(createItemCard("Hanko Booster Pack", "Get a random Seal for your run.", 60, () -> {
            if (runSession.getMon() >= 60) {
                runSession.addMon(-60);
                updateMonDisplay();
                runSession.getPurchasedHankos().add(HankoEffect.GOLDEN_SEAL);
                System.out.println("Hanko gezogen!");
            }
        }));

        // ==========================================
        // SPALTE 3: FUKUS
        // ==========================================
        column3.add(createItemCard("Fuku: Senba Zuru", "+1 Hand, +1 Discard.", 50, () -> {
            if (runSession.getMon() >= 50) {
                runSession.addMon(-50);
                runSession.addMaxHands(1);
                runSession.addMaxDiscards(1);
                updateMonDisplay();
            }
        }));

        shelves.add(column1).expandX().fillX().top().padRight(40);
        shelves.add(column2).expandX().fillX().top().padRight(40);
        shelves.add(column3).expandX().fillX().top();
        stage.addActor(shelves);

        // ==========================================
        // BOTTOM BAR & REFRESH LOGIK
        // ==========================================
        Table bottomBar = new Table();
        bottomBar.setFillParent(true);
        bottomBar.bottom().padBottom(35);

        TextButton refreshButton = new TextButton("Refresh (-10)", indieButtonStyle);
        refreshButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.getMon() >= 10) {
                    runSession.addMon(-10);
                    // Den kompletten Screen fegen und neu aufbauen!
                    stage.clear();
                    buildShopUi();
                    buildBoosterOverlay();
                }
            }
        });

        monLabel = new Label(String.valueOf(runSession.getMon()), skin);
        monLabel.setFontScale(2f);
        monLabel.setColor(Color.WHITE);
        monLabel.setAlignment(Align.center);

        TextButton exitButton = new TextButton("Leave Market", indieButtonStyle);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
            }
        });

        bottomBar.add(refreshButton).width(200).height(60).padLeft(80);
        bottomBar.add(monLabel).expandX().center();
        bottomBar.add(exitButton).width(200).height(60).padRight(80);

        stage.addActor(bottomBar);
    }

    /**
     * Neue Methode speziell für Omamoris, um deren Bild mitzuladen!
     */
    private Table createOmamoriCard(Omamori omamori) {
        Table card = new Table();
        card.setBackground(panelBackground);

        // 1. Textur-Namen generieren (Genau wie im GameScreen!)
        String className = omamori.getClass().getSimpleName();
        String snakeCaseName = className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
        String regionName = "OMAMORI_" + snakeCaseName;

        TextureRegion region = atlas.findRegion(regionName);
        Actor icon = (region != null) ? new Image(region) : new Label("No Img", skin);

        // 2. Texte vorbereiten
        Label nameLabel = new Label(omamori.getName(), skin);
        nameLabel.setFontScale(0.9f);
        Label descLabel = new Label(omamori.getDescription(), skin);
        descLabel.setWrap(true);
        descLabel.setAlignment(Align.center);
        descLabel.setFontScale(0.8f);

        // 3. Kauf-Logik
        int price = OmamoriPool.getCost(omamori.getRarity());
        TextButton buyButton = new TextButton(price + " Mon", indieButtonStyle);

        buyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.getMon() >= price) {
                    runSession.addMon(-price);
                    runSession.getActiveOmamoris().add(omamori);
                    updateMonDisplay();

                    // Visuelles Feedback: Kasten leeren und "SOLD OUT" anzeigen
                    card.clearChildren();
                    Label soldOut = new Label("SOLD OUT", skin);
                    soldOut.setColor(Color.FIREBRICK);
                    card.add(soldOut).expand().center();
                }
            }
        });

        // 4. Layout in der Box (Bild ist jetzt in der Mitte!)
        card.add(nameLabel).padTop(10).padBottom(5).row();
        card.add(icon).width(72).height(96).row(); // Maße anpassen falls nötig
        card.add(descLabel).expand().fill().pad(5).row();
        card.add(buyButton).width(160).height(45).padBottom(10);

        return card;
    }

    private Table createItemCard(String name, String desc, int price, Runnable onBuy) {
        Table card = new Table();
        card.setBackground(panelBackground);

        Label nameLabel = new Label(name, skin);
        Label descLabel = new Label(desc, skin);
        descLabel.setWrap(true);
        descLabel.setAlignment(Align.center);

        TextButton buyButton = new TextButton(price + " Mon", indieButtonStyle);
        buyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onBuy.run();
            }
        });

        card.add(nameLabel).padTop(15).padBottom(5).row();
        card.add(descLabel).expand().fill().pad(5).row();
        card.add(buyButton).width(160).height(50).padBottom(15);

        return card;
    }

    private void buildBoosterOverlay() {
        boosterOverlay = new Table();
        boosterOverlay.setFillParent(true);
        boosterOverlay.setBackground(darkOverlayBackground);
        boosterOverlay.setVisible(false);
        stage.addActor(boosterOverlay);
    }

    private void startBoosterSequence(String type) {
        boosterOverlay.clearChildren();
        boosterOverlay.setVisible(true);

        ImageButton packButton = new ImageButton(new TextureRegionDrawable(boosterPackTexture));
        packButton.setTransform(true);
        packButton.setOrigin(150, 250);

        packButton.addAction(Actions.forever(Actions.sequence(
            Actions.rotateTo(3f, 0.1f), Actions.rotateTo(-3f, 0.1f)
        )));

        packButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                packButton.clearActions();
                packButton.addAction(Actions.sequence(
                    Actions.parallel(Actions.scaleTo(1.5f, 1.5f, 0.2f), Actions.fadeOut(0.2f)),
                    Actions.run(() -> showCardChoices())
                ));
            }
        });

        Label instruction = new Label("Click to rip open!", skin);
        instruction.setFontScale(2f);
        instruction.setColor(Color.WHITE);

        boosterOverlay.add(instruction).padBottom(50).row();
        boosterOverlay.add(packButton).width(300).height(500);
    }

    private void showCardChoices() {
        boosterOverlay.clearChildren();

        Label instruction = new Label("Choose 1 Card for your Deck!", skin);
        instruction.setFontScale(1.5f);
        boosterOverlay.add(instruction).colspan(3).padBottom(50).row();

        Deck tempDeck = new Deck();
        tempDeck.initializeDeck();
        java.util.Collections.shuffle(tempDeck.getCards());

        Card[] choices = new Card[]{
            tempDeck.getCards().get(0),
            tempDeck.getCards().get(1),
            tempDeck.getCards().get(2)
        };

        for (Card card : choices) {
            TextureRegion region = atlas.findRegion(card.id().name());
            Button cardBtn = (region != null) ? new ImageButton(new TextureRegionDrawable(region))
                : new TextButton(card.name(), skin);

            cardBtn.setTransform(true);
            cardBtn.setScale(0f);
            cardBtn.addAction(Actions.scaleTo(1f, 1f, 0.5f, Interpolation.elasticOut));

            cardBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    runSession.getPlayerDeck().getCards().add(card);
                    boosterOverlay.setVisible(false);
                }
            });

            boosterOverlay.add(cardBtn).width(108).height(192).pad(20);
        }
    }

    private void updateMonDisplay() {
        monLabel.setText(String.valueOf(runSession.getMon()));
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
    public void dispose() {
        stage.dispose();
        background.dispose();
        boosterPackTexture.dispose();
        atlas.dispose();
    }
}
