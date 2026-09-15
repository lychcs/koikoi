package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class CutsceneScreen extends ScreenAdapter {

    private final Stage stage;
    private final RunSession runSession;
    private final GameSeason defeatedKami;
    private Skin skin;
    private Texture background;
    private TextButton.TextButtonStyle indieButtonStyle;

    public CutsceneScreen(RunSession runSession, GameSeason defeatedKami) {
        this.runSession = runSession;
        this.defeatedKami = defeatedKami;
        this.stage = new Stage(new FitViewport(1280, 720));

        initAssets();
        buildUI();
        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {

        skin = new Skin(Gdx.files.internal("uiskin.json"));
        skin.add("default-font", FontManager.getFont(), BitmapFont.class);
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_STARTING_SCREEN.png"));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(60);

        boolean isFinal = (defeatedKami == GameSeason.FINAL);

        String titleStr = isFinal ? "DIE UR-GÖTTIN ERWACHT" : "EIN KAMI WURDE ERLÖST";
        Label titleLabel = new Label(titleStr, skin);
        titleLabel.setFontScale(1.8f);
        titleLabel.setColor(Color.GOLD);
        root.add(titleLabel).padBottom(30).row();

        String loreText;
        if (isFinal) {
            loreText = "Das reine Licht durchbricht den kosmischen Void.\nDie Urmutter ist befreit und das Gleichgewicht kehrt zurück.\n\nDu hast die Dunkelheit überwunden.\nDANKE FÜRS SPIELEN!";
        } else {
            loreText = "Der korrumpierende Einfluss des Yami schwindet.\n" + defeatedKami.getKamiName() + ", " + defeatedKami.getKamiTitle() + ",\natmet auf und lässt strahlendes Licht auf das Land regnen.\n\nDie nächste Jahreszeit bricht an.";
        }

        Label loreLabel = new Label(loreText, skin);
        loreLabel.setAlignment(Align.center);
        loreLabel.setFontScale(1.2f);
        root.add(loreLabel).expandY().fillY().padBottom(40).row();

        TextButton proceedButton = new TextButton(isFinal ? "Zum Hauptmenü" : "Weiterziehen", indieButtonStyle);
        proceedButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (isFinal) {
                    ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new FirstScreen((KoiKoiGame) Gdx.app.getApplicationListener()));
                } else {
                    ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new OverworldScreen(runSession));
                }
            }
        });

        root.add(proceedButton).width(300).height(70);
        stage.addActor(root);
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
        skin.dispose();
    }
}
