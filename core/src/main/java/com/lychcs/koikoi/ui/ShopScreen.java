package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.OmamoriPool;
import com.lychcs.koikoi.run.RunSession;

import java.util.ArrayList;
import java.util.List;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class ShopScreen extends ScreenAdapter {

    private static final int REFRESH_COST = 10;
    private static final int OMAMORI_OFFER_COUNT = 2;
    private static final int HANKO_OFFER_COUNT = 3;

    private record PurchaseButton(TextButton button, int price) {}

    private final Stage stage;
    private final RunSession runSession;
    private final List<PurchaseButton> purchaseButtons = new ArrayList<>();

    private Skin skin;
    private TextureAtlas atlas;
    private Texture background;
    private Texture panelTex;
    private Texture buttonTex;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Label monLabel;
    private TextButton refreshButton;

    public ShopScreen(RunSession runSession) {
        if (runSession == null) {
            throw new IllegalArgumentException("runSession darf nicht null sein");
        }

        this.runSession = runSession;
        stage = new Stage(new FitViewport(1280, 720));

        initAssets();
        buildShopUi();
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture texture : atlas.getTextures()) {
            texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }

        background = new Texture(Gdx.files.internal(getSeasonalBackgroundPath("SHOP")));

        panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 20, 20, 20, 20));

        buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;
    }

    private void buildShopUi() {
        purchaseButtons.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.pad(28f, 70f, 24f, 70f);

        Label title = new Label("Tengu Market", skin);
        title.setFontScale(1.45f);
        title.setAlignment(Align.center);
        root.add(title).expandX().fillX().padBottom(16f).row();

        Table offers = new Table();
        offers.defaults().top();

        Table omamoriColumn = new Table();
        omamoriColumn.top();
        Label omamoriTitle = new Label("Omamori", skin);
        omamoriTitle.setFontScale(1.1f);
        omamoriColumn.add(omamoriTitle).padBottom(10f).row();

        List<Omamori> omamoriOffers = OmamoriPool.getRandomDistinctOmamoris(
            OMAMORI_OFFER_COUNT,
            runSession.getOwnedOmamoris()
        );

        for (Omamori omamori : omamoriOffers) {
            omamoriColumn.add(createOmamoriCard(omamori))
                .width(430f)
                .height(205f)
                .padBottom(12f)
                .row();
        }

        for (int i = omamoriOffers.size(); i < OMAMORI_OFFER_COUNT; i++) {
            omamoriColumn.add(createPlaceholderCard("Alle verfuegbaren Omamori wurden gesammelt."))
                .width(430f)
                .height(205f)
                .padBottom(12f)
                .row();
        }

        Table hankoColumn = new Table();
        hankoColumn.top();
        Label hankoTitle = new Label("Hankos", skin);
        hankoTitle.setFontScale(1.1f);
        hankoColumn.add(hankoTitle).padBottom(10f).row();

        for (HankoEffect effect : HankoCatalog.drawDistinctOffers(HANKO_OFFER_COUNT)) {
            hankoColumn.add(createHankoCard(effect))
                .width(430f)
                .height(132f)
                .padBottom(12f)
                .row();
        }

        offers.add(omamoriColumn).expand().top().padRight(24f);
        offers.add(hankoColumn).expand().top().padLeft(24f);
        root.add(offers).expand().fill().row();

        Table bottomBar = new Table();

        refreshButton = new TextButton("Refresh (-" + REFRESH_COST + ")", indieButtonStyle);
        refreshButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!runSession.spendMon(REFRESH_COST)) {
                    refreshAffordability();
                    return;
                }

                stage.clear();
                buildShopUi();
            }
        });

        monLabel = new Label("Mon: " + runSession.getMon(), skin);
        monLabel.setFontScale(1.25f);
        monLabel.setColor(Color.WHITE);
        monLabel.setAlignment(Align.center);

        TextButton exitButton = new TextButton("Leave Market", indieButtonStyle);
        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener())
                    .changeScreen(new OverworldScreen(runSession));
            }
        });

        bottomBar.add(refreshButton).width(210f).height(58f);
        bottomBar.add(monLabel).expandX().center();
        bottomBar.add(exitButton).width(210f).height(58f);

        root.add(bottomBar).expandX().fillX().padTop(12f);
        stage.addActor(root);

        refreshAffordability();
    }

    private Table createOmamoriCard(Omamori omamori) {
        Table card = createBaseCard();

        String className = omamori.getClass().getSimpleName();
        String snakeCaseName = className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
        TextureRegion region = atlas.findRegion("OMAMORI_" + snakeCaseName);

        Actor icon = region != null ? new Image(region) : createMissingImageLabel();

        Label nameLabel = new Label(omamori.getName(), skin);
        nameLabel.setFontScale(0.95f);

        Label rarityLabel = new Label(omamori.getRarity().name(), skin);
        rarityLabel.setFontScale(0.65f);
        rarityLabel.setColor(Color.LIGHT_GRAY);

        Label description = new Label(omamori.getDescription(), skin);
        description.setWrap(true);
        description.setAlignment(Align.center);
        description.setFontScale(0.72f);

        int price = OmamoriPool.getCost(omamori.getRarity());
        TextButton buyButton = new TextButton(price + " Mon", indieButtonStyle);
        registerPurchaseButton(buyButton, price);

        buyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.ownsOmamori(omamori.getClass())) {
                    markSold(card, "BEREITS IM LAGER");
                    return;
                }

                if (!runSession.spendMon(price)) {
                    refreshAffordability();
                    return;
                }

                if (!runSession.addOwnedOmamori(omamori)) {
                    runSession.addMon(price);
                    refreshAffordability();
                    return;
                }

                updateMonDisplay();
                markSold(card, "GEKAUFT - IM LAGER");
            }
        });

        Table heading = new Table();
        heading.add(nameLabel).expandX().left();
        heading.add(rarityLabel).right();

        card.add(heading).expandX().fillX().pad(8f, 12f, 3f, 12f).row();
        card.add(icon).width(58f).height(76f).padBottom(3f).row();
        card.add(description).width(380f).expandX().fillX().pad(2f, 10f, 4f, 10f).row();
        card.add(buyButton).width(155f).height(38f).padBottom(8f);
        return card;
    }

    private Table createHankoCard(HankoEffect effect) {
        Table card = createBaseCard();

        TextureRegion region = atlas.findRegion("HANKO_" + effect.name());
        Actor icon = region != null ? new Image(region) : createMissingImageLabel();

        Label name = new Label(HankoCatalog.getName(effect), skin);
        name.setFontScale(0.9f);

        Label description = new Label(HankoCatalog.getDescription(effect), skin);
        description.setWrap(true);
        description.setFontScale(0.68f);

        int price = HankoCatalog.getPrice(effect);
        TextButton buyButton = new TextButton(price + " Mon", indieButtonStyle);
        registerPurchaseButton(buyButton, price);

        buyButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!runSession.spendMon(price)) {
                    refreshAffordability();
                    return;
                }

                runSession.getPurchasedHankos().add(effect);
                updateMonDisplay();
                markSold(card, "GEKAUFT - IM HANKO-VORRAT");
            }
        });

        Table text = new Table();
        text.left();
        text.add(name).expandX().left().row();
        text.add(description).width(255f).expandX().fillX().left();

        card.add(icon).size(62f).pad(10f);
        card.add(text).expand().fill().pad(6f);
        card.add(buyButton).width(120f).height(40f).pad(10f);
        return card;
    }

    private Table createBaseCard() {
        Table card = new Table();
        card.setBackground(panelBackground);
        return card;
    }

    private Table createPlaceholderCard(String text) {
        Table card = createBaseCard();
        Label label = new Label(text, skin);
        label.setWrap(true);
        label.setAlignment(Align.center);
        label.setColor(Color.LIGHT_GRAY);
        card.add(label).width(350f).expand().center();
        return card;
    }

    private Label createMissingImageLabel() {
        Label label = new Label("No Img", skin);
        label.setAlignment(Align.center);
        label.setColor(Color.GRAY);
        return label;
    }

    private void registerPurchaseButton(TextButton button, int price) {
        purchaseButtons.add(new PurchaseButton(button, price));
    }

    private void markSold(Table card, String message) {
        card.clearChildren();
        Label sold = new Label(message, skin);
        sold.setWrap(true);
        sold.setAlignment(Align.center);
        sold.setColor(Color.GOLD);
        card.add(sold).width(300f).expand().center();
        refreshAffordability();
    }

    private void updateMonDisplay() {
        if (monLabel != null) {
            monLabel.setText("Mon: " + runSession.getMon());
        }
        refreshAffordability();
    }

    private void refreshAffordability() {
        int mon = runSession.getMon();

        for (PurchaseButton purchase : purchaseButtons) {
            TextButton button = purchase.button();
            boolean disabled = mon < purchase.price();
            button.setDisabled(disabled);
            button.getColor().a = disabled ? 0.45f : 1f;
        }

        if (refreshButton != null) {
            boolean disabled = mon < REFRESH_COST;
            refreshButton.setDisabled(disabled);
            refreshButton.getColor().a = disabled ? 0.45f : 1f;
        }
    }

    private String getSeasonalBackgroundPath(String prefix) {
        return "backgrounds/BACKGROUND_" + prefix + "_"
            + runSession.getCurrentSeason().name() + ".jpg";
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        stage.getBatch().begin();
        stage.getBatch().draw(background, 0f, 0f, 1280f, 720f);
        stage.getBatch().end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
        if (skin != null) skin.dispose();
        if (background != null) background.dispose();
        if (atlas != null) atlas.dispose();
        if (panelTex != null) panelTex.dispose();
        if (buttonTex != null) buttonTex.dispose();
    }
}
