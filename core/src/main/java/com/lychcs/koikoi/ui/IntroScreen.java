package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
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
import com.lychcs.koikoi.run.RunSession;

public class IntroScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final KoiKoiGame game;
    private final Stage stage;
    private Skin skin;
    private Texture background;
    private TextButton.TextButtonStyle indieButtonStyle;

    public IntroScreen(KoiKoiGame game) {
        this.game = game;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildUI();
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_STARTING_SCREEN.png"));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        NinePatch buttonPatch = new NinePatch(buttonTex, 15, 15, 15, 15);
        NinePatchDrawable buttonDrawable = new NinePatchDrawable(buttonPatch);

        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = buttonDrawable;
        indieButtonStyle.down = buttonDrawable.tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = skin.getFont("default-font");
        indieButtonStyle.fontColor = Color.WHITE;
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(60);

        Label titleLabel = new Label("DIE KORRUPTION DES VOIDS", skin);
        titleLabel.setFontScale(1.8f);
        titleLabel.setColor(Color.PURPLE);

        String loreText =
            "Die oberste Lichtgöttin wurde vom finsteren Void verschlungen...\n" +
                "Eine Welle schwarzer, lila brennender Flammen überzog das Land und korrumpierte die Kamis.\n\n" +
                "Auch dein Geist wurde berührt. Die Finsternis versuchte, dich zu verschlingen und dir einen Yokai-Fluch einzupflanzen.\n" +
                "Doch du hast widerstanden.\n\n" +
                "Du hast den Willen des Yokai gebrochen und ihn an deine Seite gezwungen.\n" +
                "Jetzt liegt es an dir, durch die verdorbenen Jahreszeiten zu ziehen, die Kamis zu erlösen\n" +
                "und das Licht zurückzubringen.";

        Label loreLabel = new Label(loreText, skin);
        loreLabel.setAlignment(Align.center);
        loreLabel.setFontScale(1.1f);
        loreLabel.setWrap(true);

        TextButton proceedButton = new TextButton("Den Weg antreten", indieButtonStyle);
        proceedButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                RunSession currentRun = new RunSession();
                // Direkt zur Pfadauswahl für das erste Level, Hub wird komplett übersprungen!
                game.setScreen(new ChoiceScreen(currentRun, ChoiceScreen.ChoiceType.BEAST));
            }
        });

        root.add(titleLabel).padBottom(30).row();
        root.add(loreLabel).width(900).expandY().fillY().padBottom(40).row();
        root.add(proceedButton).width(300).height(70);

        stage.addActor(root);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
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
