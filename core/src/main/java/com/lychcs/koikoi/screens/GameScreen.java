package com.lychcs.koikoi.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.* ;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.scoring.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameScreen extends ScreenAdapter {

    public enum GameState {
        WAITING_FOR_INPUT,
        SCORING_ANIMATION,
        KOI_KOI_DECISION,
        ROUND_END,
        TARGETING_HANKO // Schon mal vorbereitet für später!
    }

    // =========================================================================
    // 1. KONSTANTEN
    // =========================================================================
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int MAX_HAND_SIZE = 8;
    private static final int MAX_SELECTED_CARDS = 5;
    private static final float CARD_WIDTH = 90f;
    private static final float CARD_HEIGHT = 144f;
    private static final float CARD_SELECT_OFFSET_Y = 20f;
    private static final float ANIMATION_SPEED = 0.1f;
    private static final int INITIAL_MAX_DISCARDS = 3;
    private static final int INITIAL_MAX_HANDS = 4;
    private static final int INITIAL_TARGET_SCORE = 2000;

    // =========================================================================
    // 2. SPIEL-ZUSTAND (Model/State)
    // =========================================================================
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> selectedCards = new ArrayList<>();
    private final List<Omamori> activeOmamoris = new ArrayList<>();
    private final List<Card> drawPile = new ArrayList<>();

    private GameState currentState = GameState.WAITING_FOR_INPUT;

    private int currentTargetScore = INITIAL_TARGET_SCORE;
    private int currentRoundScore = 0;

    private int floatingBank = 0;
    private double currentKoiKoiMult = 1.0;

    private int maxDiscards = INITIAL_MAX_DISCARDS;
    private int discardsRemaining = maxDiscards;
    private int maxHands = INITIAL_MAX_HANDS;
    private int handsRemaining = maxHands;

    // =========================================================================
    // 3. UI-ELEMENTE & RESSOURCEN (View)
    // =========================================================================
    private Table infoPopup;
    private final Stage stage;
    private Skin skin;
    private TextureAtlas atlas;
    private final Map<String, TextureRegionDrawable> cardTextures = new HashMap<>();
    private final RunSession runSession;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;

    private Table hankoTable;
    private Table omamoriTable;
    private Table handTable;
    private TextButton playButton;
    private TextButton discardButton;
    private TextButton koiKoiButton;
    private TextButton bankButton;
    private Texture background;

    private Label scoreProgressLabel;
    private Label chipsLabel;
    private Label multLabel;
    private Label floatingBankLabel;
    private Label discardLabel;
    private Label handsLabel;
    private Label yakuNameLabel;
    private Label koiKoiMultLabel;

    public GameScreen(RunSession runSession) {
        this.runSession = runSession;

        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        this.atlas = new TextureAtlas(Gdx.files.internal("packed/game_assets.atlas"));
        for (Texture tex : atlas.getTextures()) {
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest); // GEFIXT: Nearest für knackige Pixel-Art!
        }

        initUiElements();
        startEncounter();
        dealCardsToUI();
        renderOmamoris();
    }

    // =========================================================================
    // HILFSMETHODEN FÜR SPIELLOGIK
    // =========================================================================

    private void startEncounter() {
        currentRoundScore = 0;

        // Werte aus der Session laden (inklusive gekaufter Fukus!)
        discardsRemaining = runSession.getBaseDiscards();
        handsRemaining = runSession.getBaseHands();

        if (scoreProgressLabel != null) scoreProgressLabel.setText("Score: " + currentRoundScore + " / " + currentTargetScore);
        if (discardLabel != null) discardLabel.setText("Discards: " + discardsRemaining);
        if (handsLabel != null) handsLabel.setText("Hands: " + handsRemaining);
        if (koiKoiMultLabel != null) koiKoiMultLabel.setText("Koi-Mult: 1.0x");

        // ==========================================
        // Der Nachziehstapel für diesen Kampf
        // ==========================================
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
        koiKoiButton.getColor().a = 0f;
        bankButton.getColor().a = 0f;
        playButton.getColor().a = 1f;
        discardButton.getColor().a = 1f;

        dealCardsToUI();
        updateLivePreview();
        currentState = GameState.WAITING_FOR_INPUT;
    }

    // =========================================================================
    // FENSTER-SKALIERUNG (Für Vollbildmodus & Resizing)
    // =========================================================================
    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    // =========================================================================
    // UI INITIALISIERUNG
    // =========================================================================

    private void initUiElements() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        loadIndieAssets();

        // ACTION & SORT BUTTONS
        playButton = new TextButton("Play Hand", indieButtonStyle);
        discardButton = new TextButton("Discard", indieButtonStyle);
        koiKoiButton = new TextButton("KOI KOI!", indieButtonStyle);
        bankButton = new TextButton("Bank", indieButtonStyle);
        TextButton sortSeasonButton = new TextButton("Sort: Season", indieButtonStyle);
        TextButton sortRankButton = new TextButton("Sort: Rank", indieButtonStyle);

        koiKoiButton.getColor().a = 0f;
        bankButton.getColor().a = 0f;

        playButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { onPlayHandSubmitted(); } });
        discardButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { onDiscardClicked(); } });
        koiKoiButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { onKoiKoiClicked(); } });
        bankButton.addListener(new ClickListener() { @Override public void clicked(InputEvent e, float x, float y) { onBankClicked(); } });

        sortSeasonButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (currentState != GameState.WAITING_FOR_INPUT || playerHand.isEmpty()) return;
                playerHand.sort(Comparator.comparing(Card::season).thenComparing(Card::rank));
                dealCardsToUI(); updateLivePreview();
            }
        });
        sortRankButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                if (currentState != GameState.WAITING_FOR_INPUT || playerHand.isEmpty()) return;
                playerHand.sort(Comparator.comparing(Card::rank, Comparator.reverseOrder()).thenComparing(Card::season));
                dealCardsToUI(); updateLivePreview();
            }
        });

        // LABELS
        scoreProgressLabel = new Label("0 / " + currentTargetScore, skin);
        chipsLabel = new Label("0", skin);
        multLabel = new Label("0", skin);
        floatingBankLabel = new Label("Pot: 0", skin);
        discardLabel = new Label("Discards: " + discardsRemaining, skin);
        handsLabel = new Label("Hands: " + handsRemaining, skin);
        koiKoiMultLabel = new Label("Koi-Mult: " + currentKoiKoiMult + "x", skin);
        yakuNameLabel = new Label("", skin);

        scoreProgressLabel.setFontScale(1.2f);
        chipsLabel.setFontScale(1.5f);
        multLabel.setFontScale(1.5f);

        // =========================================================
        // DIE LINKE SPALTE (Getrennte Boxen)
        // =========================================================
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

        Table potBox = new Table();
        potBox.setBackground(panelBackground);
        potBox.add(floatingBankLabel).pad(15);
        leftColumn.add(potBox).width(240).padBottom(15).row();

        Table statsBox = new Table();
        statsBox.setBackground(panelBackground);
        statsBox.add(handsLabel).padTop(15).padBottom(10).row();
        statsBox.add(discardLabel).padBottom(10).row();
        statsBox.add(koiKoiMultLabel).padBottom(15).row();
        leftColumn.add(statsBox).width(240).padBottom(15).row();

        // =========================================================
        // DIE OBERE SPALTE (Omamoris und Hankos getrennt)
        // =========================================================
        Table topRow = new Table();
        topRow.left().pad(20);

        omamoriTable = new Table();
        omamoriTable.setBackground(panelBackground);
        omamoriTable.left().pad(15);

        hankoTable = new Table();
        hankoTable.setBackground(panelBackground);
        hankoTable.pad(15);
        hankoTable.add(new Label("Hankos (Empty)", skin));

        topRow.add(omamoriTable).height(150).expandX().fillX().padRight(15);
        topRow.add(hankoTable).width(250).height(150);

        // =========================================================
        // MASTER LAYOUT
        // =========================================================
        Table masterTable = new Table();
        masterTable.setFillParent(true);
        masterTable.add(leftColumn).width(280).expandY().fillY().top();
        masterTable.add(topRow).expandX().fillX().top().row();
        stage.addActor(masterTable);

        // BUTTON ZONE UNTEN
        Table buttonZone = new Table();
        buttonZone.setFillParent(true);
        buttonZone.bottom().padBottom(30);

        Table sortTable = new Table();
        sortTable.add(sortSeasonButton).width(150).height(45).padRight(20);
        sortTable.add(sortRankButton).width(150).height(45);

        Table actionTable = new Table();
        actionTable.add(playButton).width(200).height(70).pad(10);
        actionTable.add(discardButton).width(200).height(70).pad(10);
        actionTable.add(koiKoiButton).width(200).height(70).pad(10);
        actionTable.add(bankButton).width(200).height(70).pad(10);

        buttonZone.add(sortTable).padBottom(15).row();
        buttonZone.add(actionTable);
        stage.addActor(buttonZone);

        handTable = new Table();
        handTable.setFillParent(true);
        handTable.bottom().padBottom(180);
        stage.addActor(handTable);

        // =========================================================
        // DAS UNIVERSELLE INFO-POPUP
        // =========================================================
        infoPopup = new Table();
        infoPopup.setBackground(panelBackground);
        infoPopup.setVisible(false);
        stage.addActor(infoPopup);

        Gdx.input.setInputProcessor(stage);
    }

    // =========================================================================
    // RENDER-LOOP
    // =========================================================================

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        stage.getBatch().begin();
        stage.getBatch().draw(background, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        stage.getBatch().end();

        stage.act(delta);
        stage.draw();
    }
    // =========================================================================
    // ASSET LOADING
    // =========================================================================

    private TextureRegionDrawable getCardImage(Card card) {
        if (card.id() == CardID.UNKNOWN) return null;

        String fileName = card.id().name();

        if (!cardTextures.containsKey(fileName)) {
            TextureRegion region = atlas.findRegion(fileName);

            if (region != null) {
                cardTextures.put(fileName, new TextureRegionDrawable(region));
            } else {
                System.out.println("WARNUNG: Textur nicht im Atlas gefunden: " + fileName);
                return null;
            }
        }
        return cardTextures.get(fileName);
    }

    private void loadIndieAssets() {
        background = new Texture(Gdx.files.internal("backgrounds/BACKGROUND_PLAYING_BOARD.jpg"));

        Texture panelTex = new Texture(Gdx.files.internal("backgrounds/PANEL_PLAYING_BOARD.9.png"));
        NinePatch panelPatch = new NinePatch(panelTex, 40, 40, 40, 40);
        panelBackground = new NinePatchDrawable(panelPatch);

        Texture buttonTex = new Texture(Gdx.files.internal("backgrounds/BUTTONS_PLAYING_BOARD.9.png"));
        NinePatch buttonPatch = new NinePatch(buttonTex, 50, 50, 20, 20);
        NinePatchDrawable buttonDrawable = new NinePatchDrawable(buttonPatch);

        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = buttonDrawable;
        indieButtonStyle.down = buttonDrawable.tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = skin.getFont("default-font");
        indieButtonStyle.fontColor = Color.WHITE;
    }
    // =========================================================================
    // GAMEPLAY LOGIK
    // =========================================================================

    public void onPlayHandSubmitted() {
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty() || handsRemaining <= 0) return;

        handsRemaining--;
        handsLabel.setText("Hands: " + handsRemaining);

        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);

        if (bestYaku == null) {
            floatingBank = 0;
            currentKoiKoiMult = 1.0;

            floatingBankLabel.setText("Pot: 0");
            koiKoiMultLabel.setText("Koi-Mult: 1.0x");

            List<Card> playedCards = new ArrayList<>(selectedCards);

            playerHand.removeAll(selectedCards);
            selectedCards.clear();

            for (Card playedCard : playedCards) {
                if (playedCard.effect() == HankoEffect.STONE_SEAL) {
                    Card strippedCard = new Card(
                        playedCard.id(), playedCard.season(), playedCard.rank(),
                        playedCard.name(), HankoEffect.NONE
                    );
                    playerHand.add(strippedCard);
                    System.out.println("Stone Seal aktiviert: " + playedCard.name() + " kehrt zurück!");

                } else if (playedCard.effect() == HankoEffect.YAMI_SEAL) {
                    // GEFIXT: Das Yami Seal entfernt die Karte jetzt korrekt aus dem Master-Deck der Session!
                    runSession.getPlayerDeck().getCards().remove(playedCard);
                    System.out.println("Yami Seal aktiviert: " + playedCard.name() + " wurde verbrannt!");
                }
            }

            if (handsRemaining <= 0) {
                onBankClicked();
            } else {
                drawCardsToHand(MAX_HAND_SIZE);
                resetUiForNextTurn();
            }
            return;
        }

        currentState = GameState.SCORING_ANIMATION;
        HandContext hand = new HandContext(selectedCards);

        List<Card> unplayed = new ArrayList<>(playerHand);
        unplayed.removeAll(selectedCards);

        ScoreContext context = new ScoreContext(floatingBank, hand, unplayed, bestYaku, activeOmamoris, currentKoiKoiMult);
        CalculationBreakdown breakdown = ScoreCalculator.calculate(context);

        playerHand.removeAll(selectedCards);
        selectedCards.clear();
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

    private void onBankClicked() {
        if (currentState != GameState.KOI_KOI_DECISION && currentState != GameState.WAITING_FOR_INPUT) return;

        currentRoundScore += floatingBank;
        scoreProgressLabel.setText("Score: " + currentRoundScore + " / " + currentTargetScore);

        if (currentRoundScore >= currentTargetScore) {
            currentState = GameState.ROUND_END;
            yakuNameLabel.setText("VICTORY!");

            runSession.addMon(currentRoundScore / 100);
            int interest = runSession.applyEndRoundInterest();
            System.out.println("Zinsen erhalten: " + interest);

            ((com.lychcs.koikoi.KoiKoiGame) Gdx.app.getApplicationListener()).setScreen(new ShopScreen(runSession));
            return;
        }

        if (handsRemaining <= 0) {
            currentState = GameState.ROUND_END;
            yakuNameLabel.setText("GAME OVER! Zu wenig Punkte.");
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;
            koiKoiButton.getColor().a = 0f;
            bankButton.getColor().a = 0f;
            return;
        }

        floatingBank = 0;
        currentKoiKoiMult = 1.0;
        floatingBankLabel.setText("Pot: 0");
        koiKoiMultLabel.setText("Koi-Mult: 1.0x");

        drawCardsToHand(MAX_HAND_SIZE);
        resetUiForNextTurn();
    }

    private void onKoiKoiClicked() {
        if (currentState != GameState.KOI_KOI_DECISION) return;

        currentKoiKoiMult += 1.0;
        koiKoiMultLabel.setText("Koi-Mult: " + currentKoiKoiMult + "x");

        drawCardsToHand(MAX_HAND_SIZE);
        resetUiForNextTurn();
    }

    // =========================================================================
    // UI UPDATES & ANIMATIONEN
    // =========================================================================

    private void dealCardsToUI() {
        handTable.clearChildren();
        selectedCards.clear();

        for (Card card : playerHand) {
            TextureRegionDrawable image = getCardImage(card);
            Button cardView = (image != null) ? new ImageButton(image) : new TextButton(card.season().name() + "\n" + card.rank().name(), skin);

            cardView.addListener(new ClickListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    infoPopup.clearChildren();
                    infoPopup.add(new Label(card.name(), skin)).padTop(10).padBottom(5).row();
                    infoPopup.add(new Label(card.season().name() + " | " + card.rank().name(), skin)).padBottom(10).row();
                    if (card.hasHanko()) {
                        infoPopup.add(new Label("Seal: " + card.effect().name(), skin)).padBottom(10).row();
                    }
                    infoPopup.pack();

                    Vector2 pos = cardView.localToStageCoordinates(new Vector2(x, y));
                    infoPopup.setPosition(pos.x - (infoPopup.getWidth()/2f), pos.y + 40);
                    infoPopup.setVisible(true);

                    return super.touchDown(event, x, y, pointer, button);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    infoPopup.setVisible(false);
                    super.touchUp(event, x, y, pointer, button);
                }

                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (currentState != GameState.WAITING_FOR_INPUT) return;

                    if (selectedCards.contains(card)) {
                        selectedCards.remove(card);
                        cardView.addAction(Actions.moveBy(0, -CARD_SELECT_OFFSET_Y, ANIMATION_SPEED));
                    } else {
                        if (selectedCards.size() < MAX_SELECTED_CARDS) {
                            selectedCards.add(card);
                            cardView.addAction(Actions.moveBy(0, CARD_SELECT_OFFSET_Y, ANIMATION_SPEED));
                        }
                    }
                    updateLivePreview();
                }
            });

            handTable.add(cardView).width(CARD_WIDTH).height(CARD_HEIGHT).pad(5);
        }
    }

    private void renderOmamoris() {
        omamoriTable.clearChildren();

        for (Omamori omamori : activeOmamoris) {
            String className = omamori.getClass().getSimpleName();
            String snakeCaseName = className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
            String regionName = "OMAMORI_" + snakeCaseName;

            TextureRegion region = atlas.findRegion(regionName);

            Actor omamoriView;
            if (region != null) {
                omamoriView = new Image(region);
            } else {
                System.out.println("WARNUNG: Omamori-Textur nicht im Atlas gefunden: " + regionName);
                omamoriView = new TextButton(omamori.getName(), skin);
            }

            TextTooltip tooltip = new TextTooltip(omamori.getName(), skin);
            omamoriView.addListener(tooltip);

            omamoriView.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    triggerPunchAnimation(omamoriView);

                    if (infoPopup.isVisible()) {
                        infoPopup.setVisible(false);
                    } else {
                        infoPopup.clearChildren();
                        infoPopup.add(new Label(omamori.getName(), skin)).padTop(10).padBottom(5).row();
                        infoPopup.add(new Label(omamori.getDescription(), skin)).padBottom(5).row();
                        infoPopup.add(new Label("Rarity: " + omamori.getRarity().name(), skin)).padBottom(10).row();
                        infoPopup.pack();

                        Vector2 pos = omamoriView.localToStageCoordinates(new Vector2(0, 0));
                        infoPopup.setPosition(pos.x, pos.y - infoPopup.getHeight() - 10);
                        infoPopup.setVisible(true);
                    }
                }
            });

            omamoriTable.add(omamoriView).width(96).height(128).pad(10);
        }
    }

    private void updateLivePreview() {
        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);

        List<Card> unplayed = new ArrayList<>(playerHand);
        unplayed.removeAll(selectedCards);

        ScoreContext previewCtx = ScoreContext.preview(floatingBank, hand, unplayed, bestYaku, currentKoiKoiMult);

        chipsLabel.setText(String.valueOf(previewCtx.getYakuBaseChips()));
        multLabel.setText(String.valueOf(previewCtx.getYakuBaseMult()));

        if (selectedCards.isEmpty()) {
            yakuNameLabel.setText("");
        } else if (bestYaku != null) {
            yakuNameLabel.setText(bestYaku.type().getDisplayName());
        } else {
            yakuNameLabel.setText("");
        }

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
        }));
        sequence.addAction(Actions.delay(0.6f));

        for (ScoringEvent event : breakdown.events()) {
            if (event.addedChips() == 0 && event.addedMult() == 0 && event.xMult() == 1.0) continue;

            sequence.addAction(Actions.run(() -> {
                if (event.addedChips() > 0) currentChips[0] += event.addedChips();
                if (event.addedMult() > 0) currentMult[0] += event.addedMult();
                if (event.xMult() > 1.0) currentMult[0] = (int) Math.round(currentMult[0] * event.xMult());

                chipsLabel.setText(String.valueOf(currentChips[0]));
                multLabel.setText(String.valueOf(currentMult[0]));
            }));
            sequence.addAction(Actions.delay(0.35f));
        }

        sequence.addAction(Actions.delay(0.4f));
        sequence.addAction(Actions.run(() -> {
            floatingBank = (int) breakdown.finalPayout();
            floatingBankLabel.setText("Pot: " + floatingBank);
            chipsLabel.setText("0");
            multLabel.setText("0");
        }));
        sequence.addAction(Actions.delay(0.5f));
        sequence.addAction(Actions.run(this::enterKoiKoiDecision));

        stage.addAction(sequence);
    }

    private void enterKoiKoiDecision() {
        currentState = GameState.KOI_KOI_DECISION;
        if (handsRemaining <= 0) {
            onBankClicked();
            return;
        }

        koiKoiButton.getColor().a = 1f;
        bankButton.getColor().a = 1f;
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
    public void dispose() {
        stage.dispose();
        if (skin != null) skin.dispose();

        if (atlas != null) atlas.dispose();
        if (background != null) background.dispose(); // Sicherheitshalber auch hier entsorgen

        cardTextures.clear();
    }
}
