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
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.run.EvolutionMapper;
import com.lychcs.koikoi.run.RunSession;

public class ChoiceScreen extends ScreenAdapter {

    public enum ChoiceType { BEAST, ALIGNMENT }

    private final Stage stage;
    private final RunSession runSession;
    private final ChoiceType choiceType;
    private Skin skin;
    private Texture background;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    public ChoiceScreen(RunSession runSession, ChoiceType choiceType) {
        this.runSession = runSession;
        this.choiceType = choiceType;
        this.stage = new Stage(new FitViewport(1280, 720));

        initAssets();
        buildUI();
        Gdx.input.setInputProcessor(stage);
    }

    private void initAssets() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_STARTING_SCREEN.png"));

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 20, 20, 20, 20));

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = skin.getFont("default-font");
        indieButtonStyle.fontColor = Color.WHITE;
    }

    private void buildUI() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(60);

        Label titleLabel = new Label(choiceType == ChoiceType.BEAST ? "Die Wahl des Pfades" : "Das Schicksal des Geistes", skin);
        titleLabel.setFontScale(1.8f);
        root.add(titleLabel).padBottom(20).row();

        Label descLabel = new Label(choiceType == ChoiceType.BEAST ? "Welches Beast soll dich in dieser Season begleiten?" : "Wähle zwischen dem pfad des Lichts und der Dunkelheit.", skin);
        descLabel.setAlignment(Align.center);
        root.add(descLabel).padBottom(60).row();

        Table choicesTable = new Table();

        if (choiceType == ChoiceType.BEAST) {
            CardID[] choices = EvolutionMapper.getSeasonBeastChoices(runSession.getCurrentSeason());

            for (CardID beast : choices) {
                TextButton btn = new TextButton(beast.name(), indieButtonStyle);
                btn.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent event, float x, float y) {
                        runSession.setBeastChoice(beast);
                        startGame();
                    }
                });
                choicesTable.add(btn).width(350).height(100).pad(20);
            }
        } else {
            TextButton lightBtn = new TextButton("Licht (Hikari)", indieButtonStyle);
            lightBtn.setColor(Color.GOLD);
            lightBtn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    runSession.setAlignmentChoiceAndEvolve(Rank.HIKARI);
                    startGame();
                }
            });

            TextButton darkBtn = new TextButton("Dunkelheit (Yami)", indieButtonStyle);
            darkBtn.setColor(Color.PURPLE);
            darkBtn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    runSession.setAlignmentChoiceAndEvolve(Rank.YAMI);
                    startGame();
                }
            });

            choicesTable.add(lightBtn).width(350).height(100).pad(20);
            choicesTable.add(darkBtn).width(350).height(100).pad(20);
        }

        root.add(choicesTable);
        stage.addActor(root);
    }

    private void startGame() {
        ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new GameScreen(runSession));
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
