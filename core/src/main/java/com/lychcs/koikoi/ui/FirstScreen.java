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
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.run.RunSession;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class FirstScreen extends ScreenAdapter {

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;

    private final KoiKoiGame game;
    private final GameAssets assets;
    private final Stage stage;

    private Texture background;
    private Texture logoTexture;
    private Skin skin;

    /** Schutz gegen Mehrfach-Dispose durch den Screen-Manager. */
    private boolean disposed;

    public FirstScreen(KoiKoiGame game, GameAssets assets) {
        this.game = game;
        this.assets = assets;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        initAssets();
        buildUI();
    }

    private void initAssets() {
        // Screen-lokale Texturen: nur das Hauptmenue verwendet sie.
        background = new Texture(Gdx.files.internal(GameAssets.STARTING_SCREEN_BACKGROUND));
        logoTexture = new Texture(Gdx.files.internal(GameAssets.LOGO_TEXTURE));
        logoTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);

        // Geliehene, global verwaltete Skin (wird hier nicht disposet).
        skin = assets.getSkin();
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();
    }

    private void buildUI() {
        // NeunPatch auf der gemeinsamen Button-Textur: keine eigene GPU-Ressource.
        NinePatchDrawable buttonDrawable = assets.newButtonDrawable();

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
                game.changeScreen(new OverworldScreen(currentRun, assets));
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
        if (disposed) {
            return;
        }
        disposed = true;

        // Screen-lokale Ressourcen genau einmal freigeben.
        // Skin, Button-Textur und Assets bleiben Eigentum von KoiKoiGame/GameAssets
        // und werden hier bewusst NICHT disposet.
        stage.dispose();
        background.dispose();
        logoTexture.dispose();
    }
}
