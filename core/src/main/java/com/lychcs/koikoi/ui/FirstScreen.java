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

public class FirstScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final KoiKoiGame game;
    private final Stage stage;

    private Texture background;
    private Texture logoTexture;
    private Skin skin;
    private Texture buttonTex;

    public FirstScreen(KoiKoiGame game) {
        this.game = game;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildUI();
    }

    private void initAssets() {
        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_STARTING_SCREEN.png"));
        logoTexture = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_LOGO.png"));
        logoTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
    }

    private void buildUI() {
        NinePatch buttonPatch = new NinePatch(buttonTex, 15, 15, 15, 15);
        NinePatchDrawable buttonDrawable = new NinePatchDrawable(buttonPatch);

        TextButton.TextButtonStyle buttonStyle = new TextButton.TextButtonStyle();
        buttonStyle.up = buttonDrawable;
        buttonStyle.down = buttonDrawable.tint(Color.LIGHT_GRAY);
        buttonStyle.font = FontManager.getFont();
        buttonStyle.fontColor = COLOR_TEXT_MAIN;

        TextButton startButton = new TextButton("Start Journey", buttonStyle);

        startButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // 1. Neue Spieldaten-Session erstellen
                RunSession currentRun = new RunSession();

                // 2. Direkt auf die Overworld-Map springen!
                game.changeScreen(new OverworldScreen(currentRun));
            }
        });

        Image logoImage = new Image(logoTexture);

        Table rootTable = new Table();
        rootTable.setFillParent(true);

        rootTable.add(logoImage).width(840).height(280).expandY().top().padTop(280).row();
        rootTable.add(startButton).width(300).height(80).padBottom(60);

        stage.addActor(rootTable);
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
        logoTexture.dispose();
        skin.dispose();
        buttonTex.dispose();
    }
}
