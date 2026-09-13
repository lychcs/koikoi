package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.run.RunSession;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class HubScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final Stage stage;
    private final RunSession runSession;
    private Skin skin;
    private Texture background;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Label monLabel;
    private Label voidDustLabel;

    public HubScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildHubUi();

        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        skin.add("default-font", FontManager.getFont(), BitmapFont.class);
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        // Lädt automatisch HUB_SPRING.jpg, HUB_SUMMER.jpg etc.
        background = new Texture(Gdx.files.internal(getSeasonalBackgroundPath("HUB")));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;
    }

    private void buildHubUi() {
        stage.clear();

        Table root = new Table();
        root.setFillParent(true);
        root.pad(40);

        Table topBar = new Table();
        monLabel = new Label("Mon: " + runSession.getMon(), skin);
        monLabel.setFontScale(1.4f);

        voidDustLabel = new Label("Void Dust: " + runSession.getVoidDust(), skin);
        voidDustLabel.setFontScale(1.4f);
        voidDustLabel.setColor(Color.valueOf("B388FF"));

        topBar.add(monLabel).padRight(60);
        topBar.add(voidDustLabel);
        root.add(topBar).expandX().top().left().padBottom(80).row();

        Table centerTable = new Table();

        TextButton shrineButton = new TextButton("Schrein betreten", indieButtonStyle);
        shrineButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShrineScreen(runSession));
            }
        });

        TextButton shopButton = new TextButton("Shop betreten", indieButtonStyle);
        shopButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShopScreen(runSession));
            }
        });

        centerTable.add(shrineButton).width(320).height(90).padRight(80);
        centerTable.add(shopButton).width(320).height(90);
        root.add(centerTable).expandY().center().padBottom(80).row();

        // Dynamischer Weiterziehen-Button je nach Level
        String btnText = "Level " + runSession.getSeasonEncounterStage() + " Starten";
        if (runSession.getSeasonEncounterStage() == 3) btnText = "Kami Bosskampf";

        TextButton proceedButton = new TextButton(btnText, indieButtonStyle);
        proceedButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                KoiKoiGame game = (KoiKoiGame) Gdx.app.getApplicationListener();

                if (runSession.getSeasonEncounterStage() == 1 && runSession.needsBeastChoice()) {
                    game.setScreen(new ChoiceScreen(runSession, ChoiceScreen.ChoiceType.BEAST));
                } else if (runSession.getSeasonEncounterStage() == 2 && runSession.needsAlignmentChoice()) {
                    game.setScreen(new ChoiceScreen(runSession, ChoiceScreen.ChoiceType.ALIGNMENT));
                } else {
                    game.setScreen(new GameScreen(runSession));
                }
            }
        });
        root.add(proceedButton).width(320).height(70).bottom();

        stage.addActor(root);
    }

    private String getSeasonalBackgroundPath(String prefix) {
        String seasonName = runSession.getCurrentSeason().name(); // SPRING, SUMMER, AUTUMN, WINTER
        return "backgrounds/" + "BACKGROUND" + "_" + prefix + "_" + seasonName + ".png";
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
        skin.dispose();
    }
}
