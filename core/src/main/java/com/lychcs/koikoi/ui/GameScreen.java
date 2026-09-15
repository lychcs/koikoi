package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.*;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.scoring.*;

import java.util.*;
import java.util.List;

import static com.lychcs.koikoi.graphics.FontManager.COLOR_TEXT_MAIN;

public class GameScreen extends ScreenAdapter {

    public enum GameState {
        WAITING_FOR_INPUT,
        SCORING_ANIMATION,
        ROUND_END
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int MAX_HAND_SIZE = 8;
    private static final int MAX_SELECTED_CARDS = 5;

    private static final float CARD_WIDTH = 72f;
    private static final float CARD_HEIGHT = 128f;

    private static final float CARD_SELECT_OFFSET_Y = 25f;

    private static final int INITIAL_MAX_DISCARDS = 3;
    private static final int INITIAL_MAX_HANDS = 4;
    private static final int INITIAL_TARGET_SCORE = 200;

    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> selectedCards = new ArrayList<>();
    private final List<Omamori> activeOmamoris = new ArrayList<>();
    private final List<Card> drawPile = new ArrayList<>();

    private Yokai activeAltarYokai = null;
    private GameState currentState = GameState.WAITING_FOR_INPUT;

    private int currentTargetScore = INITIAL_TARGET_SCORE;
    private int currentRoundScore = 0;
    private int maxDiscards = INITIAL_MAX_DISCARDS;
    private int discardsRemaining = maxDiscards;
    private int maxHands = INITIAL_MAX_HANDS;
    private int handsRemaining = maxHands;

    private Table infoPopup;
    private final Stage stage;
    private Skin skin;
    private TextureAtlas atlas;
    private final Map<String, TextureRegionDrawable> cardTextures = new HashMap<>();
    private final RunSession runSession;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;
    private AltarFlameActor altarFlames;
    private Table hankoTable;
    private Table omamoriTable;
    private Table altarTable;
    private Table yokaiBagTable;
    private final Set<Yokai> usedYokaiInBattle = new HashSet<>();
    private Group handGroup;

    private TextButton playButton;
    private TextButton discardButton;
    private Texture background;

    private Label scoreProgressLabel;
    private Label chipsLabel;
    private Label multLabel;
    private Label discardLabel;
    private Label handsLabel;
    private Label yakuNameLabel;

    public GameScreen(RunSession runSession) {
        this.runSession = runSession;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        this.atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture tex : atlas.getTextures()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }

        initUiElements();
        startEncounter();
        dealCardsToUI();
    }

    private void startEncounter() {
        currentRoundScore = 0;
        discardsRemaining = runSession.getBaseDiscards();
        handsRemaining = runSession.getBaseHands();

        activeOmamoris.clear();
        activeOmamoris.addAll(runSession.getActiveOmamoris());
        renderOmamoris();

        if(runSession.getYokaiBag() != null) {
            runSession.getYokaiBag().forEach(y -> y.setExhausted(false));
        }
        activeAltarYokai = null;
        renderYokaiUI();

        if (scoreProgressLabel != null) scoreProgressLabel.setText("Score: " + currentRoundScore + " / " + currentTargetScore);
        if (discardLabel != null) discardLabel.setText("Discards: " + discardsRemaining);
        if (handsLabel != null) handsLabel.setText("Hands: " + handsRemaining);

        drawPile.clear();
        drawPile.addAll(runSession.getPlayerDeck().getCards());
        Collections.shuffle(drawPile);

        playerHand.clear();
        selectedCards.clear();
        drawCardsToHand(MAX_HAND_SIZE);
    }

    private void drawCardsToHand(int targetSize) {
        while (playerHand.size() < targetSize && !drawPile.isEmpty()) {
            playerHand.add(drawPile.remove(0));
        }
    }

    private void resetUiForNextTurn() {
        playButton.getColor().a = 1f;
        discardButton.getColor().a = 1f;

        dealCardsToUI();
        renderYokaiUI();
        updateLivePreview();
        currentState = GameState.WAITING_FOR_INPUT;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    private void initUiElements() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        skin.add("default-font", FontManager.getFont(), BitmapFont.class);
        skin.get(Label.LabelStyle.class).font = FontManager.getFont();

        loadIndieAssets();

        playButton = new TextButton("Play Hand", indieButtonStyle);
        discardButton = new TextButton("Discard", indieButtonStyle);
        TextButton sortSeasonButton = new TextButton("Sort: Season", indieButtonStyle);
        TextButton sortRankButton = new TextButton("Sort: Rank", indieButtonStyle);

        playButton.addListener(new ClickListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                super.touchDown(e, x, y, pointer, button);
                onPlayHandSubmitted();
                return true;
            }
        });
        discardButton.addListener(new ClickListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                super.touchDown(e, x, y, pointer, button);
                onDiscardClicked();
                return true;
            }
        });

        sortSeasonButton.addListener(new ClickListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                if (currentState != GameState.WAITING_FOR_INPUT || playerHand.isEmpty()) return true;
                playerHand.sort(Comparator.comparing(Card::season).thenComparing(Card::rank));
                dealCardsToUI();
                updateLivePreview();
                return true;
            }
        });
        sortRankButton.addListener(new ClickListener() {
            @Override public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                if (currentState != GameState.WAITING_FOR_INPUT || playerHand.isEmpty()) return true;
                playerHand.sort(Comparator.comparing(Card::rank, Comparator.reverseOrder()).thenComparing(Card::season));
                dealCardsToUI();
                updateLivePreview();
                return true;
            }
        });

        scoreProgressLabel = new Label("0 / " + currentTargetScore, skin);
        chipsLabel = new Label("0", skin);
        multLabel = new Label("0", skin);
        discardLabel = new Label("Discards: " + discardsRemaining, skin);
        handsLabel = new Label("Hands: " + handsRemaining, skin);
        yakuNameLabel = new Label("", skin);

        scoreProgressLabel.setFontScale(1.2f);
        chipsLabel.setFontScale(1.5f);
        multLabel.setFontScale(1.5f);

        Table leftColumn = new Table();
        leftColumn.top().pad(20);

        Table scoreBox = new Table();
        scoreBox.setBackground(panelBackground);
        scoreBox.add(new Label("Target Score", skin)).padBottom(5).row();
        scoreBox.add(scoreProgressLabel).pad(15);
        leftColumn.add(scoreBox).width(240).padBottom(15).row();

        Table yakuBox = new Table();
        yakuBox.setBackground(panelBackground);
        yakuBox.add(yakuNameLabel).padBottom(10).padTop(15).row();
        Table multTable = new Table();
        multTable.add(chipsLabel).padRight(10);
        multTable.add(new Label(" X ", skin));
        multTable.add(multLabel).padLeft(10);
        yakuBox.add(multTable).padBottom(15).row();
        leftColumn.add(yakuBox).width(240).padBottom(15).row();

        Table statsBox = new Table();
        statsBox.setBackground(panelBackground);
        statsBox.add(handsLabel).padTop(15).padBottom(10).row();
        statsBox.add(discardLabel).padBottom(15).row();
        leftColumn.add(statsBox).width(240).padBottom(15).row();

        Table topRow = new Table();
        topRow.left().pad(20);

        omamoriTable = new Table();
        omamoriTable.setBackground(panelBackground);
        omamoriTable.left().pad(15);

        altarTable = new Table();
        altarTable.setBackground(panelBackground);
        altarTable.pad(15);

        TextureRegion flameRegion = atlas.findRegion("FLAME_SHAPE");
        if (flameRegion == null) {
            flameRegion = skin.getRegion("white");
        }
        altarFlames = new AltarFlameActor(flameRegion);

        Stack altarStack = new Stack();
        altarStack.add(altarFlames);
        altarStack.add(altarTable);

        yokaiBagTable = new Table();
        yokaiBagTable.setBackground(panelBackground);
        yokaiBagTable.left().pad(15);

        hankoTable = new Table();
        hankoTable.setBackground(panelBackground);
        hankoTable.pad(15);
        hankoTable.add(new Label("Hankos", skin));

        topRow.add(omamoriTable).height(120).expandX().fillX().padRight(15);
        topRow.add(altarStack).width(150).height(120).padRight(15);
        topRow.add(yokaiBagTable).height(120).expandX().fillX().padRight(15);
        topRow.add(hankoTable).width(150).height(120);

        Table masterTable = new Table();
        masterTable.setFillParent(true);
        masterTable.add(leftColumn).width(280).expandY().fillY().top();
        masterTable.add(topRow).expandX().fillX().top().row();
        stage.addActor(masterTable);

        Table buttonZone = new Table();
        buttonZone.setFillParent(true);
        buttonZone.bottom().padBottom(30);

        Table sortTable = new Table();
        sortTable.add(sortSeasonButton).width(150).height(45).padRight(20);
        sortTable.add(sortRankButton).width(150).height(45);

        Table actionTable = new Table();
        actionTable.add(playButton).width(200).height(70).pad(10);
        actionTable.add(discardButton).width(200).height(70).pad(10);

        buttonZone.add(sortTable).padBottom(15).row();
        buttonZone.add(actionTable);
        stage.addActor(buttonZone);

        handGroup = new Group();
        handGroup.setPosition(640f, 220f);
        stage.addActor(handGroup);

        infoPopup = new Table();
        infoPopup.setBackground(panelBackground);
        infoPopup.setVisible(false);
        stage.addActor(infoPopup);
    }

    @Override
    public void render(float delta) {
        HankoShaderManager.update(delta);
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        float activeDelta = com.lychcs.koikoi.graphics.JuiceManager.update(delta, stage.getCamera());

        stage.getBatch().begin();
        stage.getBatch().draw(background, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        stage.getBatch().end();

        stage.act(activeDelta);
        stage.draw();

        float flash = com.lychcs.koikoi.graphics.JuiceManager.getFlashAlpha();
        if (flash > 0f) {
            stage.getBatch().begin();
            stage.getBatch().setColor(1f, 1f, 1f, flash);
            stage.getBatch().draw(skin.getRegion("white"), 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
            stage.getBatch().setColor(Color.WHITE);
            stage.getBatch().end();
        }
    }

    private TextureRegionDrawable getCardImage(Card card) {
        if (card.id() == CardID.UNKNOWN) return null;
        String fileName = card.id().name();
        if (!cardTextures.containsKey(fileName)) {
            TextureRegion region = atlas.findRegion(fileName);
            if (region != null) cardTextures.put(fileName, new TextureRegionDrawable(region));
            else return null;
        }
        return cardTextures.get(fileName);
    }

    private void loadIndieAssets() {
        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_PLAYING_BOARD.jpg"));
        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        panelBackground = new NinePatchDrawable(new NinePatch(panelTex, 20, 20, 20, 20));
        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = new NinePatchDrawable(new NinePatch(buttonTex, 15, 15, 15, 15));
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;
    }

    public void onPlayHandSubmitted() {
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty() || handsRemaining <= 0) return;

        handsRemaining--;
        handsLabel.setText("Hands: " + handsRemaining);

        List<Card> playedCards = new ArrayList<>(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);

        if (bestYaku == null) {
            playerHand.removeAll(selectedCards);
            selectedCards.clear();

            for (Card playedCard : playedCards) {
                if (playedCard.effect() == HankoEffect.STONE_SEAL) {
                    Card strippedCard = new Card(
                        playedCard.id(), playedCard.season(), playedCard.rank(),
                        playedCard.name(), HankoEffect.NONE
                    );
                    playerHand.add(strippedCard);
                }
            }

            if (activeAltarYokai != null) {
                activeAltarYokai.setExhausted(true);
                activeAltarYokai = null;
            }

            checkRoundEndCondition();
            return;
        }

        currentState = GameState.SCORING_ANIMATION;
        HandContext hand = new HandContext(selectedCards);
        List<Card> unplayed = new ArrayList<>(playerHand);
        unplayed.removeAll(selectedCards);

        ScoreContext context = new ScoreContext(0, hand, unplayed, bestYaku, activeOmamoris, activeAltarYokai);
        CalculationBreakdown breakdown = ScoreCalculator.calculate(context);

        playerHand.removeAll(selectedCards);
        selectedCards.clear();

        for (Card playedCard : playedCards) {
            if (playedCard.effect() == HankoEffect.STONE_SEAL) {
                Card strippedCard = new Card(
                    playedCard.id(), playedCard.season(), playedCard.rank(),
                    playedCard.name(), HankoEffect.NONE
                );
                playerHand.add(strippedCard);

            } else if (playedCard.effect() == HankoEffect.BLOOD_SEAL) {
                if (com.badlogic.gdx.math.MathUtils.random(1, 4) == 1) {
                    runSession.banishCardForSeason(playedCard);
                }
            }
        }

        dealCardsToUI();
        updateLivePreview();

        playScoringSequence(breakdown);
    }

    private void onDiscardClicked() {
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty() || discardsRemaining <= 0) return;
        discardsRemaining--;
        discardLabel.setText("Discards: " + discardsRemaining);
        playerHand.removeAll(selectedCards);
        selectedCards.clear();
        drawCardsToHand(MAX_HAND_SIZE);
        dealCardsToUI();
        updateLivePreview();
    }

    private void checkRoundEndCondition() {
        if (currentRoundScore >= currentTargetScore) {
            currentState = GameState.ROUND_END;
            yakuNameLabel.setText("VICTORY!");

            int totalXpReward = 50; // Basis-EP für den gewonnenen Kampf
            if (!usedYokaiInBattle.isEmpty()) {
                int xpPerYokai = totalXpReward / usedYokaiInBattle.size(); // EP-Teiler durch Anzahl der genutzten Yokai
                for (Yokai y : usedYokaiInBattle) {
                    y.addXp(xpPerYokai);
                }
            }

            int voidDustEarned = 10 + (handsRemaining * 5) + (discardsRemaining * 2);
            runSession.addVoidDust(voidDustEarned);
            runSession.addMon(currentRoundScore / 100);
            runSession.applyEndRoundInterest();
            runSession.resetShrineVisit();

            GameSeason defeatedSeason = runSession.getCurrentSeason();
            int completedStage = runSession.getSeasonEncounterStage();

            runSession.advanceEncounterStage();

            if (completedStage == 3) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new CutsceneScreen(runSession, defeatedSeason));
            } else if (defeatedSeason == GameSeason.FINAL) {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new CutsceneScreen(runSession, GameSeason.FINAL));
            } else {
                ((KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new OverworldScreen(runSession));
            }

        } else if (handsRemaining <= 0) {
            currentState = GameState.ROUND_END;
            yakuNameLabel.setText("GAME OVER!");
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;
        } else {
            drawCardsToHand(MAX_HAND_SIZE);
            resetUiForNextTurn();
        }
    }

    private void dealCardsToUI() {
        handGroup.clearChildren();

        for (Card card : playerHand) {
            TextureRegionDrawable cardImage = getCardImage(card);
            if (cardImage != null) {
                JuicyCardActor juicyCard = new JuicyCardActor(card, cardImage);
                handGroup.addActor(juicyCard);

                juicyCard.setX(MathUtils.random(-30f, 30f));
                juicyCard.setY(MathUtils.random(-40f, -80f));
                juicyCard.setRotation(MathUtils.random(-25f, 25f));
            }
        }
        updateCardArcTargets();
    }

    private JuicyCardActor findActorForCard(Card card) {
        for (Actor a : handGroup.getChildren()) {
            if (a instanceof JuicyCardActor && ((JuicyCardActor) a).card.equals(card)) {
                return (JuicyCardActor) a;
            }
        }
        return null;
    }

    private void updateCardArcTargets() {
        int cardCount = playerHand.size();
        if (cardCount == 0) return;

        float spacing = 64f;
        float dropPerCard = 1.8f;
        float rotationPerCard = 2.5f;
        float centerIndex = (cardCount - 1) / 2f;

        for (int i = 0; i < cardCount; i++) {
            Card card = playerHand.get(i);
            JuicyCardActor actor = findActorForCard(card);

            if (actor != null) {
                float distFromCenter = i - centerIndex;
                float bx = (distFromCenter * spacing) - (CARD_WIDTH / 2f);
                float by = - (Math.abs(distFromCenter) * Math.abs(distFromCenter) * dropPerCard);
                float brot = -distFromCenter * rotationPerCard;

                actor.updateArc(bx, by, brot);
                actor.setZIndex(i);

                if (actor.isDragging) {
                    actor.toFront();
                }
            }
        }
    }

    private class JuicyCardActor extends Group {
        private final Card card;
        protected float baseX, baseY, baseRot;
        private final Image shadowImg;

        protected float targetX, targetY, targetRot;
        private float targetScale = 1f;
        private float targetShadow = 0f;
        private float currentShadow = 0f;

        protected boolean isDragging = false;
        private float dragOffsetX, dragOffsetY;

        public JuicyCardActor(Card card, TextureRegionDrawable tex) {
            this.card = card;
            setSize(CARD_WIDTH, CARD_HEIGHT);
            setOrigin(CARD_WIDTH / 2f, CARD_HEIGHT / 2f);

            shadowImg = new Image(tex);
            shadowImg.setSize(CARD_WIDTH, CARD_HEIGHT);
            shadowImg.setColor(0f, 0f, 0f, 0f);
            shadowImg.setPosition(-4, -6);
            addActor(shadowImg);

            Image mainImg = new Image(tex);
            mainImg.setSize(CARD_WIDTH, CARD_HEIGHT);
            mainImg.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            addActor(mainImg);

            if (card.hasHanko()) {
                String regionName = "HANKO_" + card.effect().name();
                TextureRegion hankoRegion = atlas.findRegion(regionName);
                if (hankoRegion == null) hankoRegion = atlas.findRegion("hankos/" + regionName);

                if (hankoRegion != null) {
                    HankoActor hankoActor = new HankoActor(card.effect(), hankoRegion);
                    hankoActor.setSize(22, 22);
                    hankoActor.setPosition(CARD_WIDTH - 28, CARD_HEIGHT - 28);
                    addActor(hankoActor);
                }
            }

            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    if (currentState != GameState.WAITING_FOR_INPUT) return false;

                    isDragging = false;
                    dragOffsetX = x;
                    dragOffsetY = y;

                    infoPopup.clearChildren();
                    infoPopup.add(new Label(card.name(), skin)).padTop(10).padBottom(5).row();
                    infoPopup.add(new Label(card.season().name() + " | " + card.rank().name(), skin)).padBottom(10).row();
                    if (card.hasHanko()) infoPopup.add(new Label("Seal: " + card.effect().name(), skin)).padBottom(10).row();
                    infoPopup.pack();
                    Vector2 pos = localToStageCoordinates(new Vector2(x, y));
                    infoPopup.setPosition(pos.x - (infoPopup.getWidth() / 2f), pos.y + 40);
                    infoPopup.setVisible(true);

                    targetScale = 1.0f;
                    targetShadow = 0.3f;

                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (currentState != GameState.WAITING_FOR_INPUT) return;

                    isDragging = true;
                    infoPopup.setVisible(false);

                    targetScale = 1.0f;
                    targetShadow = 0.6f;
                    targetRot = 0f;
                    toFront();

                    Vector2 stagePos = localToStageCoordinates(new Vector2(x, y));
                    Vector2 parentPos = getParent().stageToLocalCoordinates(stagePos);

                    targetX = parentPos.x - dragOffsetX;
                    targetY = parentPos.y - dragOffsetY;

                    List<Card> newOrder = new ArrayList<>(playerHand);
                    newOrder.sort((c1, c2) -> {
                        JuicyCardActor a1 = findActorForCard(c1);
                        JuicyCardActor a2 = findActorForCard(c2);
                        float x1 = (a1 != null && a1.isDragging) ? a1.targetX : (a1 != null ? a1.baseX : 0);
                        float x2 = (a2 != null && a2.isDragging) ? a2.targetX : (a2 != null ? a2.baseX : 0);
                        return Float.compare(x1, x2);
                    });

                    if (!playerHand.equals(newOrder)) {
                        playerHand.clear();
                        playerHand.addAll(newOrder);
                        updateCardArcTargets();
                    }
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    infoPopup.setVisible(false);
                    if (currentState != GameState.WAITING_FOR_INPUT) return;

                    targetScale = 1f;

                    if (isDragging) {
                        isDragging = false;
                        if (selectedCards.contains(card)) {
                            targetY = baseY + CARD_SELECT_OFFSET_Y;
                            targetRot = 0f;
                            targetShadow = 0.1f;
                        } else {
                            targetY = baseY;
                            targetRot = baseRot;
                            targetShadow = 0f;
                        }
                        targetX = baseX;
                        updateLivePreview();
                        updateCardArcTargets();

                    } else {
                        if (selectedCards.contains(card)) {
                            selectedCards.remove(card);
                            targetY = baseY;
                            targetRot = baseRot;
                            targetShadow = 0f;
                        } else {
                            if (selectedCards.size() < MAX_SELECTED_CARDS) {
                                selectedCards.add(card);
                                targetY = baseY + CARD_SELECT_OFFSET_Y;
                                targetRot = 0f;
                                targetShadow = 0.1f;
                            } else {
                                targetY = baseY;
                                targetRot = baseRot;
                                targetShadow = 0f;
                            }
                        }
                        targetX = baseX;
                        updateLivePreview();
                    }
                }
            });
        }

        public void updateArc(float bx, float by, float brot) {
            this.baseX = bx;
            this.baseY = by;
            this.baseRot = brot;

            if (!isDragging) {
                targetX = bx;
                targetY = selectedCards.contains(card) ? by + CARD_SELECT_OFFSET_Y : by;
                targetRot = selectedCards.contains(card) ? 0f : brot;
            }
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            setX(MathUtils.lerp(getX(), targetX, 22f * delta));
            setY(MathUtils.lerp(getY(), targetY, 22f * delta));
            setRotation(MathUtils.lerp(getRotation(), targetRot, 18f * delta));

            float newScale = MathUtils.lerp(getScaleX(), targetScale, 35f * delta);
            setScale(newScale, newScale);

            currentShadow = MathUtils.lerp(currentShadow, targetShadow, 35f * delta);
            shadowImg.setColor(0f, 0f, 0f, currentShadow);
        }
    }

    private void renderYokaiUI() {
        altarTable.clearChildren();
        yokaiBagTable.clearChildren();

        if (activeAltarYokai != null) {
            TextureRegion yokaiRegion = atlas.findRegion(activeAltarYokai.getAtlasRegionName());
            if (yokaiRegion != null) {
                JuicyYokaiActor actor = new JuicyYokaiActor(activeAltarYokai, yokaiRegion, true);
                altarTable.add(actor).width(108).height(192);
            } else {
                TextButton fallback = new TextButton(activeAltarYokai.getName() + "\n(In Altar)", skin);
                altarTable.add(fallback).width(108).height(192);
            }
        } else {
            Label emptyLabel = new Label("Altar\n(Leer)", skin);
            emptyLabel.setAlignment(Align.center);
            altarTable.add(emptyLabel).width(108).height(192);
        }

        if (runSession.getYokaiBag() != null) {
            for (Yokai yokai : runSession.getYokaiBag()) {
                if (yokai == activeAltarYokai) continue;

                TextureRegion yokaiRegion = atlas.findRegion(yokai.getAtlasRegionName());
                if (yokaiRegion != null) {
                    JuicyYokaiActor actor = new JuicyYokaiActor(yokai, yokaiRegion, false);
                    yokaiBagTable.add(actor).width(108).height(192).pad(4);
                } else {
                    String text = yokai.getName() + (yokai.isExhausted() ? "\n(Rastet)" : "");
                    TextButton yokaiBtn = new TextButton(text, skin);
                    if (yokai.isExhausted()) yokaiBtn.getColor().a = 0.5f;
                    yokaiBagTable.add(yokaiBtn).width(108).height(192).pad(4);
                }
            }
        }
    }

    private class JuicyYokaiActor extends Group {
        private final Yokai yokai;
        private final boolean isAltar;
        private final Image shadowImg;
        private float currentShadow = 0f;
        private float targetShadow = 0f;
        private float targetScale = 1f;
        private boolean isDragging = false;
        private float dragOffsetX, dragOffsetY;

        public JuicyYokaiActor(Yokai yokai, TextureRegion tex, boolean isAltar) {
            this.yokai = yokai;
            this.isAltar = isAltar;

            setSize(108, 192);
            setOrigin(54, 96);

            shadowImg = new Image(tex);
            shadowImg.setSize(108, 192);
            shadowImg.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            shadowImg.setColor(0, 0, 0, 0f);
            shadowImg.setPosition(-6, -8);
            addActor(shadowImg);

            Stack stack = new Stack();
            stack.setSize(108, 192);

            Image mainImg = new Image(tex);
            mainImg.setScaling(com.badlogic.gdx.utils.Scaling.fit);
            stack.add(mainImg);

            if (yokai.isExhausted()) {
                mainImg.setColor(0.35f, 0.35f, 0.35f, 0.6f);
                Label exLabel = new Label("Rastet", skin);
                exLabel.setFontScale(0.75f);
                Table t = new Table();
                t.center().add(exLabel);
                stack.add(t);
            } else {
                Label stageLabel = new Label(yokai.getName(), skin);
                stageLabel.setFontScale(0.8f);
                Table labelTable = new Table();
                labelTable.bottom().padBottom(4);
                labelTable.add(stageLabel);
                stack.add(labelTable);
            }

            addActor(stack);

            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    if (currentState != GameState.WAITING_FOR_INPUT) return false;
                    isDragging = false;
                    dragOffsetX = x; dragOffsetY = y;
                    toFront();
                    targetScale = 1.0f;
                    targetShadow = 0.3f;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    if (currentState != GameState.WAITING_FOR_INPUT) return;
                    isDragging = true;
                    infoPopup.setVisible(false);
                    targetScale = 1.0f;
                    targetShadow = 0.6f;

                    Vector2 stagePos = localToStageCoordinates(new Vector2(x, y));
                    Vector2 parentPos = getParent().stageToLocalCoordinates(stagePos);
                    setX(parentPos.x - dragOffsetX);
                    setY(parentPos.y - dragOffsetY);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    targetShadow = 0f;
                    targetScale = 1f;

                    if (isDragging) {
                        isDragging = false;

                        Vector2 stagePos = localToStageCoordinates(new Vector2(x, y));
                        Vector2 bagPos = yokaiBagTable.localToStageCoordinates(new Vector2(0, 0));

                        // Wenn die X-Koordinate links von der Yokai-Tasche ist, werten wir es als Drop auf dem Altar
                        boolean droppedOnAltar = stagePos.x < bagPos.x;

                        if (droppedOnAltar && !isAltar && !yokai.isExhausted() && activeAltarYokai == null) {
                            activeAltarYokai = yokai; // Ins Altar-Feld gezogen
                            usedYokaiInBattle.add(activeAltarYokai); // <--- HIER MERKEN WIR IHN FÜR DIE EP VOR!
                        } else if (!droppedOnAltar && isAltar) {
                            activeAltarYokai = null;  // Aus dem Altar-Feld gezogen
                        }

                        // Tasche nach X-Koordinaten neu sortieren (Drag & Drop im Inventar)
                        if (!isAltar && !droppedOnAltar) {
                            List<JuicyYokaiActor> actors = new ArrayList<>();
                            for (Actor a : yokaiBagTable.getChildren()) {
                                if (a instanceof JuicyYokaiActor) actors.add((JuicyYokaiActor) a);
                            }
                            actors.sort(Comparator.comparing(a -> a.localToStageCoordinates(new Vector2(0, 0)).x));

                            List<Yokai> oldBag = new ArrayList<>(runSession.getYokaiBag());
                            runSession.getYokaiBag().clear();

                            // Aktiven Altar-Yokai nicht vergessen
                            if (activeAltarYokai != null) runSession.getYokaiBag().add(activeAltarYokai);

                            for (JuicyYokaiActor a : actors) {
                                if (!runSession.getYokaiBag().contains(a.yokai)) runSession.getYokaiBag().add(a.yokai);
                            }
                            for (Yokai i : oldBag) {
                                if (!runSession.getYokaiBag().contains(i)) runSession.getYokaiBag().add(i);
                            }
                        }

                        renderYokaiUI();
                        updateLivePreview();

                    } else {
                        // Regulärer Klick (weiterhin als komfortable Alternative nutzbar)
                        triggerPunchAnimation(JuicyYokaiActor.this);
                        infoPopup.clearChildren();
                        infoPopup.add(new Label(yokai.getName(), skin)).padTop(10).padBottom(5).row();
                        infoPopup.add(new Label(yokai.isExhausted() ? "(Rastet)" : "(Bereit)", skin)).padBottom(5).row();
                        infoPopup.pack();
                        Vector2 pos = localToStageCoordinates(new Vector2(0, 0));
                        infoPopup.setPosition(pos.x, pos.y - infoPopup.getHeight() - 10);
                        infoPopup.setVisible(true);

                        if (isAltar) {
                            activeAltarYokai = null;
                            renderYokaiUI();
                            updateLivePreview();
                        } else if (!yokai.isExhausted() && activeAltarYokai == null) {
                            activeAltarYokai = yokai;
                            usedYokaiInBattle.add(activeAltarYokai); // <--- HIER AUCH BEIM KLICKEN MERKEN!
                            renderYokaiUI();
                            updateLivePreview();
                        }
                    }
                }
            });
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            currentShadow = MathUtils.lerp(currentShadow, targetShadow, 25f * delta);
            shadowImg.setColor(0f, 0f, 0f, currentShadow);
            float newScale = MathUtils.lerp(getScaleX(), targetScale, 35f * delta);
            setScale(newScale, newScale);
        }
    }

    private void renderOmamoris() {
        omamoriTable.clearChildren();
        for (Omamori omamori : activeOmamoris) {
            String className = omamori.getClass().getSimpleName();
            String snakeCaseName = className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
            String regionName = "OMAMORI_" + snakeCaseName;

            TextureRegion region = atlas.findRegion(regionName);
            if (region != null) {
                JuicyOmamoriActor omamoriActor = new JuicyOmamoriActor(omamori, region);
                omamoriTable.add(omamoriActor).width(72).height(96).pad(5);
            } else {
                TextButton fallback = new TextButton(omamori.getName(), skin);
                omamoriTable.add(fallback).width(72).height(96).pad(5);
            }
        }
    }

    private class JuicyOmamoriActor extends Group {
        private final Omamori omamori;
        private final Image shadowImg;
        private float currentShadow = 0f;
        private float targetShadow = 0f;
        private float targetScale = 1f;
        private boolean isDragging = false;
        private float dragOffsetX, dragOffsetY;

        public JuicyOmamoriActor(Omamori omamori, TextureRegion tex) {
            this.omamori = omamori;
            setSize(72, 96);
            setOrigin(36, 48);

            shadowImg = new Image(tex);
            shadowImg.setSize(72, 96);
            shadowImg.setColor(0, 0, 0, 0f);
            shadowImg.setPosition(-4, -6);
            addActor(shadowImg);

            Image mainImg = new Image(tex);
            mainImg.setSize(72, 96);
            addActor(mainImg);

            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    isDragging = false;
                    dragOffsetX = x; dragOffsetY = y;
                    toFront();

                    targetScale = 1.0f;
                    targetShadow = 0.3f;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    isDragging = true;
                    infoPopup.setVisible(false);

                    targetScale = 1.0f;
                    targetShadow = 0.6f;

                    Vector2 stagePos = localToStageCoordinates(new Vector2(x, y));
                    Vector2 parentPos = getParent().stageToLocalCoordinates(stagePos);
                    setX(parentPos.x - dragOffsetX);
                    setY(parentPos.y - dragOffsetY);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    targetShadow = 0f;
                    targetScale = 1f;

                    if (isDragging) {
                        isDragging = false;

                        List<JuicyOmamoriActor> actors = new ArrayList<>();
                        for (Actor a : omamoriTable.getChildren()) {
                            if (a instanceof JuicyOmamoriActor) actors.add((JuicyOmamoriActor) a);
                        }
                        actors.sort(Comparator.comparing(a -> a.localToStageCoordinates(new Vector2(0, 0)).x));

                        activeOmamoris.clear();
                        for(JuicyOmamoriActor a : actors) activeOmamoris.add(a.omamori);

                        renderOmamoris();
                    } else {
                        triggerPunchAnimation(JuicyOmamoriActor.this);
                        infoPopup.clearChildren();
                        infoPopup.add(new Label(omamori.getName(), skin)).padTop(10).padBottom(5).row();
                        infoPopup.add(new Label(omamori.getDescription(), skin)).padBottom(5).row();
                        infoPopup.pack();
                        Vector2 pos = localToStageCoordinates(new Vector2(0, 0));
                        infoPopup.setPosition(pos.x, pos.y - infoPopup.getHeight() - 10);
                        infoPopup.setVisible(true);
                    }
                }
            });
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            currentShadow = MathUtils.lerp(currentShadow, targetShadow, 25f * delta);
            shadowImg.setColor(0f, 0f, 0f, currentShadow);

            float newScale = MathUtils.lerp(getScaleX(), targetScale, 35f * delta);
            setScale(newScale, newScale);
        }
    }

    private void updateLivePreview() {
        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);
        List<Card> unplayed = new ArrayList<>(playerHand);
        unplayed.removeAll(selectedCards);

        ScoreContext previewCtx = ScoreContext.preview(hand, unplayed, bestYaku, activeAltarYokai);

        chipsLabel.setText(String.valueOf(previewCtx.getYakuBaseChips()));
        multLabel.setText(String.valueOf(previewCtx.getYakuBaseMult()));

        if (selectedCards.isEmpty()) yakuNameLabel.setText("");
        else if (bestYaku != null) yakuNameLabel.setText(bestYaku.type().getDisplayName());
        else yakuNameLabel.setText("");

        if (selectedCards.isEmpty() || discardsRemaining <= 0) discardButton.getColor().a = 0.5f;
        else discardButton.getColor().a = 1.0f;

        if (selectedCards.isEmpty() || handsRemaining <= 0) playButton.getColor().a = 0.5f;
        else playButton.getColor().a = 1.0f;
    }

    private void playScoringSequence(CalculationBreakdown breakdown) {
        var sequence = Actions.sequence();
        final int[] currentChips = { breakdown.yakuChips() };
        final int[] currentMult = { breakdown.yakuBaseMult() };

        sequence.addAction(Actions.run(() -> {
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;
            chipsLabel.setText(String.valueOf(currentChips[0]));
            multLabel.setText(String.valueOf(currentMult[0]));

            if (activeAltarYokai != null && altarFlames != null) {
                altarFlames.ignite();
                com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.7f);
                com.lychcs.koikoi.graphics.JuiceManager.hitstop(0.08f);
                com.lychcs.koikoi.graphics.JuiceManager.flashScreen(0.85f);
            } else {
                com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.3f);
            }
        }));
        sequence.addAction(Actions.delay(0.2f));

        for (ScoringEvent event : breakdown.events()) {
            if (event.addedChips() == 0 && event.addedMult() == 0 && event.xMult() == 1.0) continue;

            sequence.addAction(Actions.run(() -> {
                if (event.addedChips() > 0) currentChips[0] += event.addedChips();
                if (event.addedMult() > 0) currentMult[0] += event.addedMult();
                if (event.xMult() > 1.0) currentMult[0] = (int) Math.round(currentMult[0] * event.xMult());

                chipsLabel.setText(String.valueOf(currentChips[0]));
                multLabel.setText(String.valueOf(currentMult[0]));

                com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.15f);
                com.lychcs.koikoi.graphics.JuiceManager.hitstop(0.02f);
            }));
            sequence.addAction(Actions.delay(0.15f));
        }

        sequence.addAction(Actions.delay(0.4f));

        sequence.addAction(Actions.run(() -> {
                currentRoundScore += breakdown.finalPayout();

                for (ScoringEvent event : breakdown.events()) {
                    if (event.addedMon() > 0) runSession.addMon(event.addedMon());
                    if (event.addedVoidDust() > 0) runSession.addVoidDust(event.addedVoidDust());
                }

                scoreProgressLabel.setText("Score: " + currentRoundScore + " / " + currentTargetScore);
                chipsLabel.setText("0");
                multLabel.setText("0");

                if (activeAltarYokai != null) {
                    activeAltarYokai.setExhausted(true);
                    activeAltarYokai = null;
                }
                if (altarFlames != null) {
                    altarFlames.extinguish();
                }

                checkRoundEndCondition();
            }
        ));

        stage.addAction(sequence);
    }

    private void triggerPunchAnimation(Actor actor) {
        actor.setOrigin(actor.getWidth() / 2f, actor.getHeight() / 2f);
        actor.clearActions();
        actor.addAction(Actions.sequence(
            Actions.parallel(Actions.scaleTo(1.25f, 1.25f, 0.08f, Interpolation.fastSlow), Actions.rotateBy(4f, 0.08f)),
            Actions.parallel(Actions.scaleTo(1.0f, 1.0f, 0.15f, Interpolation.bounceOut), Actions.rotateTo(0f, 0.15f))
        ));
    }

    @Override
    public void show() { Gdx.input.setInputProcessor(stage); }

    @Override
    public void dispose() {
        stage.dispose();
        if (skin != null) skin.dispose();
        if (atlas != null) atlas.dispose();
        if (background != null) background.dispose();
        cardTextures.clear();
    }
}
