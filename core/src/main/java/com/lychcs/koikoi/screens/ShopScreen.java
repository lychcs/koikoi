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
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.model.*;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.run.RunSession;

import java.util.Random;

public class ShopScreen extends ScreenAdapter {
    private final Stage stage;
    private final RunSession runSession;
    private Skin skin;
    private TextureAtlas atlas;

    // UI Assets
    private Texture background;
    private Texture boosterPackTexture;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Label monLabel;

    // NEU: Das Overlay für das Booster Pack!
    private Table boosterOverlay;
    private TextureRegionDrawable darkOverlayBackground;

    public ShopScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(1280, 720));

        initAssets();
        buildShopUi();
        buildBoosterOverlay();

        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        // TextureAtlas für die Spielkarten laden
        atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture tex : atlas.getTextures()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // WICHTIG FÜR PIXEL ART!
        }

        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_PLAYING_BOARD.jpg"));

        // DEIN NEUES BOOSTER BILD (dithered)
        boosterPackTexture = new Texture(Gdx.files.internal("backgrounds/BOOSTER_PACK.png"));
        boosterPackTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 40, 40, 40, 40));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        NinePatchDrawable buttonDrawable = new NinePatchDrawable(new NinePatch(buttonTex, 50, 50, 20, 20));

        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = buttonDrawable;
        indieButtonStyle.down = buttonDrawable.tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = skin.getFont("default-font");
        indieButtonStyle.fontColor = Color.WHITE;

        // Generiert programmtechnisch ein halbtransparentes schwarzes Bild für das Overlay
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(new Color(0, 0, 0, 0.85f));
        pixmap.fill();
        darkOverlayBackground = new TextureRegionDrawable(new TextureRegion(new Texture(pixmap)));
        pixmap.dispose();
    }

    private void buildShopUi() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(30);

        // --- TOP ZONE ---
        Table headerTable = new Table();
        headerTable.setBackground(panelBackground);

        Label title = new Label("--- YOKAI BLACK MARKET ---", skin);
        title.setFontScale(1.5f);
        monLabel = new Label("Mon: " + runSession.getMon(), skin);
        monLabel.setFontScale(1.5f);
        monLabel.setColor(Color.GOLD);

        headerTable.add(title).pad(20).expandX().left();
        headerTable.add(monLabel).pad(20).right();
        root.add(headerTable).expandX().fillX().padBottom(30).row();

        // --- MIDDLE ZONE (REGALE) ---
        Table shelfTable = new Table();

        Table fukuShelf = createItemCard("Fuku: Senba Zuru", "+1 Hand, +1 Discard permanent.", 50, () -> {
            if (runSession.getMon() >= 50) {
                runSession.addMon(-50);
                runSession.addMaxHands(1);
                runSession.addMaxDiscards(1);
                updateMonDisplay();
            }
        });

        Table hankoShelf = createItemCard("Hanko: Golden Seal", "Gain Mon on play.", 30, () -> {
            if (runSession.getMon() >= 30) {
                runSession.addMon(-30);
                runSession.getPurchasedHankos().add(HankoEffect.GOLDEN_SEAL);
                updateMonDisplay();
            }
        });

        Table boosterShelf = createItemCard("Mythic Pack", "Open to choose 1 of 3 rare Cards.", 80, () -> {
            if (runSession.getMon() >= 80) {
                runSession.addMon(-80);
                updateMonDisplay();
                startBoosterSequence(); // STARTET DAS OVERLAY!
            }
        });

        shelfTable.add(fukuShelf).width(350).height(350).pad(15);
        shelfTable.add(hankoShelf).width(350).height(350).pad(15);
        shelfTable.add(boosterShelf).width(350).height(350).pad(15);
        root.add(shelfTable).expand().fill().row();

        // --- BOTTOM ZONE ---
        TextButton leaveButton = new TextButton("Next Round", indieButtonStyle);
        leaveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
            }
        });
        root.add(leaveButton).width(300).height(80).padTop(20);

        stage.addActor(root);
    }

    private void buildBoosterOverlay() {
        boosterOverlay = new Table();
        boosterOverlay.setFillParent(true);
        boosterOverlay.setBackground(darkOverlayBackground);
        boosterOverlay.setVisible(false); // Ist am Anfang unsichtbar
        stage.addActor(boosterOverlay); // Liegt über der normalen Shop-Stage
    }

    private void startBoosterSequence() {
        boosterOverlay.clearChildren();
        boosterOverlay.setVisible(true); // Mach es dunkel!

        // 1. Das Booster Pack als Button in die Mitte legen
        ImageButton packButton = new ImageButton(new TextureRegionDrawable(boosterPackTexture));
        packButton.setTransform(true);
        packButton.setOrigin(150, 250); // Mitte für die Wackel-Animation (ca. anpassen)

        // Wackel-Animation (Es pulsiert leicht)
        packButton.addAction(Actions.forever(Actions.sequence(
            Actions.rotateTo(3f, 0.1f), Actions.rotateTo(-3f, 0.1f)
        )));

        // 2. Klick-Logik auf das Pack
        packButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                packButton.clearActions();
                // Explosion/Verschwinden
                packButton.addAction(Actions.sequence(
                    Actions.parallel(Actions.scaleTo(1.5f, 1.5f, 0.2f), Actions.fadeOut(0.2f)),
                    Actions.run(() -> showCardChoices()) // Nach der Animation: Karten zeigen!
                ));
            }
        });

        Label instruction = new Label("Click to rip open!", skin);
        instruction.setFontScale(2f);
        instruction.setColor(Color.WHITE);

        boosterOverlay.add(instruction).padBottom(50).row();
        boosterOverlay.add(packButton).width(300).height(500); // Maße deines Packs anpassen
    }

    private void showCardChoices() {
        boosterOverlay.clearChildren();

        Label instruction = new Label("Choose 1 Card for your Deck!", skin);
        instruction.setFontScale(1.5f);
        boosterOverlay.add(instruction).colspan(3).padBottom(50).row();

        // ==============================================================
        // DER FIX: Wir holen uns 3 echte, zufällige Karten aus dem Pool!
        // ==============================================================
        Deck tempDeck = new Deck();
        tempDeck.initializeDeck(); // Generiert alle Standard-Karten
        java.util.Collections.shuffle(tempDeck.getCards()); // Mischen

        Card[] choices = new Card[]{
            tempDeck.getCards().get(0),
            tempDeck.getCards().get(1),
            tempDeck.getCards().get(2)
        };

        for (Card card : choices) {
            TextureRegion region = atlas.findRegion(card.id().name());
            Button cardBtn = (region != null) ? new ImageButton(new TextureRegionDrawable(region))
                : new TextButton(card.name(), skin);

            // Pop-In Animation der Karten
            cardBtn.setTransform(true);
            cardBtn.setScale(0f);
            cardBtn.addAction(Actions.scaleTo(1f, 1f, 0.5f, Interpolation.elasticOut));

            cardBtn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    // 1. Karte zum Deck hinzufügen!
                    runSession.getPlayerDeck().getCards().add(card);
                    System.out.println(card.name() + " zum Deck hinzugefügt!");

                    // 2. Overlay schließen
                    boosterOverlay.setVisible(false);
                }
            });

            boosterOverlay.add(cardBtn).width(120).height(192).pad(20);
        }
    }

    private Table createItemCard(String name, String desc, int price, Runnable onBuy) {
        Table card = new Table();
        card.setBackground(panelBackground);

        Label nameLabel = new Label(name, skin);
        Label descLabel = new Label(desc, skin);
        descLabel.setWrap(true);

        TextButton buyButton = new TextButton("Buy (" + price + " Mon)", indieButtonStyle);
        buyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onBuy.run();
            }
        });

        card.add(nameLabel).padTop(20).padBottom(10).row();
        card.add(descLabel).expand().fill().pad(10).row();
        card.add(buyButton).width(200).height(60).padBottom(20);

        return card;
    }

    private void updateMonDisplay() {
        monLabel.setText("Mon: " + runSession.getMon());
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
