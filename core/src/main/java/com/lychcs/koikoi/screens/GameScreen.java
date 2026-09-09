package com.lychcs.koikoi.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Deck;
import com.lychcs.koikoi.model.Omamori;
import com.lychcs.koikoi.scoring.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameScreen extends ScreenAdapter {

    public enum GameState {
        WAITING_FOR_INPUT,
        SCORING_ANIMATION,
        KOI_KOI_DECISION,
        ROUND_END
    }

    // =========================================================================
    // 1. KONSTANTEN
    // =========================================================================
    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int MAX_HAND_SIZE = 8;
    private static final int MAX_SELECTED_CARDS = 5;
    private static final float CARD_WIDTH = 120f;
    private static final float CARD_HEIGHT = 192f;
    private static final float CARD_SELECT_OFFSET_Y = 20f;
    private static final float ANIMATION_SPEED = 0.1f;

    private static final int INITIAL_MAX_DISCARDS = 3;
    private static final int INITIAL_MAX_HANDS = 4;
    private static final int INITIAL_TARGET_SCORE = 25000; // NEU: Der Ziel-Score!

    // =========================================================================
    // 2. SPIEL-ZUSTAND (Model/State)
    // =========================================================================
    private final Deck deck;
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> selectedCards = new ArrayList<>();
    private final List<Omamori> activeOmamoris = new ArrayList<>();

    private GameState currentState = GameState.WAITING_FOR_INPUT;

    // NEU: Trennung in Ziel-Score und aktuellen Run-Score
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
    private final Stage stage;
    private Skin skin;
    private final Map<String, TextureRegionDrawable> cardTextures = new HashMap<>();
    private final Map<String, Actor> actorRegistry = new HashMap<>();

    private Table omamoriTable;
    private Table handTable;
    private TextButton playButton;
    private TextButton discardButton;
    private TextButton koiKoiButton;
    private TextButton bankButton;

    private Label scoreProgressLabel; // NEU: Zeigt den Fortschritt zum Boss-Kill
    private Label chipsLabel;
    private Label multLabel;
    private Label floatingBankLabel;
    private Label discardLabel;
    private Label handsLabel;
    private Label yakuNameLabel;
    private Label koiKoiMultLabel;

    public GameScreen() {
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.deck = new Deck();

        initUiElements();
        startEncounter(); // Zuvor startNewRound
        dealCardsToUI();
        renderOmamoris();
    }

    // =========================================================================
    // HILFSMETHODEN FÜR SPIELLOGIK
    // =========================================================================

    private void startEncounter() {
        currentRoundScore = 0;
        discardsRemaining = maxDiscards;
        handsRemaining = maxHands;

        if (scoreProgressLabel != null) scoreProgressLabel.setText("Score: " + currentRoundScore + " / " + currentTargetScore);
        if (discardLabel != null) discardLabel.setText("Discards: " + discardsRemaining);
        if (handsLabel != null) handsLabel.setText("Hands: " + handsRemaining);
        if (koiKoiMultLabel != null) koiKoiMultLabel.setText("Koi-Mult: 1.0x");

        resetBoardAfterBank();
    }

    private void resetBoardAfterBank() {
        deck.initializeDeck();
        Collections.shuffle(deck.getCards());
        playerHand.clear();
        selectedCards.clear();
        drawCardsToHand(MAX_HAND_SIZE);
    }

    private void drawCardsToHand(int targetSize) {
        while (playerHand.size() < targetSize && !deck.getCards().isEmpty()) {
            playerHand.add(deck.getCards().remove(0));
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
    // UI INITIALISIERUNG
    // =========================================================================

    private void initUiElements() {
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        playButton = new TextButton("Play Hand", skin);
        discardButton = new TextButton("Discard", skin);
        koiKoiButton = new TextButton("KOI KOI!", skin);
        bankButton = new TextButton("Bank", skin);

        koiKoiButton.getColor().a = 0f;
        bankButton.getColor().a = 0f;

        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onPlayHandSubmitted();
            }
        });

        discardButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onDiscardClicked();
            }
        });

        koiKoiButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onKoiKoiClicked();
            }
        });

        bankButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onBankClicked();
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.bottom().padBottom(50);

        table.add(playButton).width(200).height(60).pad(10);
        table.add(discardButton).width(200).height(60).pad(10);
        table.add(koiKoiButton).width(200).height(60).pad(10);
        table.add(bankButton).width(200).height(60).pad(10);

        // --- NEU: Große Score Anzeige ---
        scoreProgressLabel = new Label("Score: 0 / " + currentTargetScore, skin);
        scoreProgressLabel.setFontScale(2.5f);

        chipsLabel = new Label("0", skin);
        multLabel = new Label("0", skin);
        floatingBankLabel = new Label("Pot: 0", skin);
        discardLabel = new Label("Discards: " + discardsRemaining, skin);
        handsLabel = new Label("Hands: " + handsRemaining, skin);
        koiKoiMultLabel = new Label("Koi-Mult: " + currentKoiKoiMult + "x", skin);

        chipsLabel.setFontScale(2f);
        multLabel.setFontScale(2f);
        floatingBankLabel.setFontScale(1.5f);
        discardLabel.setFontScale(1.5f);
        handsLabel.setFontScale(1.5f);
        koiKoiMultLabel.setFontScale(1.5f);

        yakuNameLabel = new Label("", skin);
        yakuNameLabel.setFontScale(1.5f);

        Table topTable = new Table();
        topTable.setFillParent(true);
        topTable.top().padTop(20);

        // Reihe 0: Der große Ziel-Score
        topTable.add(scoreProgressLabel).colspan(4).center().padBottom(20);
        topTable.row();

        // Reihe 1: Chips und Mult
        topTable.add(chipsLabel).padRight(20).right();
        topTable.add(new Label(" X ", skin)).center();
        topTable.add(multLabel).padLeft(20).left();

        topTable.row().padTop(5);
        topTable.add(yakuNameLabel).colspan(3).center();
        topTable.row().padTop(20);

        // Reihe 2: HUD Anzeige
        topTable.add(handsLabel).padRight(20);
        topTable.add(discardLabel).padRight(20);
        topTable.add(floatingBankLabel).padRight(20);
        topTable.add(koiKoiMultLabel);

        stage.addActor(topTable);
        stage.addActor(table);

        omamoriTable = new Table();
        omamoriTable.setFillParent(true);
        omamoriTable.top().padTop(170); // Etwas weiter nach unten wegen des großen Scores
        stage.addActor(omamoriTable);

        handTable = new Table();
        handTable.setFillParent(true);
        handTable.center().padTop(100);
        stage.addActor(handTable);

        Gdx.input.setInputProcessor(stage);
    }

    // =========================================================================
    // RENDER-LOOP
    // =========================================================================

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.15f, 0.17f, 0.21f, 1f);
        stage.act(delta);
        stage.draw();
    }

    // =========================================================================
    // FENSTER-SKALIERUNG (Für Vollbildmodus & Resizing)
    // =========================================================================

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    // =========================================================================
    // ASSET LOADING
    // =========================================================================

    private TextureRegionDrawable getCardImage(Card card) {
        if (card.id() == CardID.UNKNOWN) return null;

        String fileName = card.id().name();

        if (!cardTextures.containsKey(fileName)) {
            FileHandle file = Gdx.files.internal("cards/" + fileName + ".png");
            if (!file.exists()) {
                file = Gdx.files.internal("assets/cards/" + fileName + ".png");
            }

            if (file.exists()) {
                Texture tex = new Texture(file);
                tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                cardTextures.put(fileName, new TextureRegionDrawable(tex));
            } else {
                return null;
            }
        }
        return cardTextures.get(fileName);
    }

    // =========================================================================
    // GAMEPLAY LOGIK
    // =========================================================================

    public void onPlayHandSubmitted() {
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty() || handsRemaining <= 0) return;

        handsRemaining--;
        handsLabel.setText("Hands: " + handsRemaining);

        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);

        // BUST-LOGIK
        if (bestYaku == null) {
            floatingBank = 0;
            currentKoiKoiMult = 1.0;

            floatingBankLabel.setText("Pot: 0");
            koiKoiMultLabel.setText("Koi-Mult: 1.0x");

            playerHand.removeAll(selectedCards);
            selectedCards.clear();

            if (handsRemaining <= 0) {
                onBankClicked();
            } else {
                drawCardsToHand(MAX_HAND_SIZE);
                resetUiForNextTurn();
            }
            return;
        }

        // NORMAL ABRECHNEN
        currentState = GameState.SCORING_ANIMATION;
        HandContext hand = new HandContext(selectedCards);
        ScoreContext context = new ScoreContext(floatingBank, hand, bestYaku, activeOmamoris, currentKoiKoiMult);
        CalculationBreakdown breakdown = ScoreCalculator.calculate(context);

        // --- NEU: Wir entfernen die Karten, ABER WIR ZIEHEN NOCH NICHT NACH! ---
        playerHand.removeAll(selectedCards);
        selectedCards.clear();
        dealCardsToUI(); // Rendert die Lücken in deiner Hand
        updateLivePreview();
        // ------------------------------------------------------------------------

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

    // --- NEU: WIN & LOSS MECHANIKEN ---
    private void onBankClicked() {
        if (currentState != GameState.KOI_KOI_DECISION && currentState != GameState.WAITING_FOR_INPUT) return;

        // 1. Score verrechnen
        currentRoundScore += floatingBank;
        scoreProgressLabel.setText("Score: " + currentRoundScore + " / " + currentTargetScore);

        // 2. CHECK WIN CONDITION
        if (currentRoundScore >= currentTargetScore) {
            currentState = GameState.ROUND_END;
            yakuNameLabel.setText("VICTORY! Shop kommt bald...");

            // UI sperren
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;
            koiKoiButton.getColor().a = 0f;
            bankButton.getColor().a = 0f;
            return;
        }

        // 3. CHECK LOSS CONDITION
        if (handsRemaining <= 0) {
            currentState = GameState.ROUND_END;
            yakuNameLabel.setText("GAME OVER! Zu wenig Punkte.");

            // UI sperren
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;
            koiKoiButton.getColor().a = 0f;
            bankButton.getColor().a = 0f;
            return;
        }

        // 4. Kampf geht weiter (Weder gewonnen noch verloren)
        floatingBank = 0;
        currentKoiKoiMult = 1.0;
        floatingBankLabel.setText("Pot: 0");
        koiKoiMultLabel.setText("Koi-Mult: 1.0x");

        // Lädt neues Deck für den nächsten Klick, aber Hände und Discards bleiben!
        resetBoardAfterBank();
        resetUiForNextTurn();
    }

    private void onKoiKoiClicked() {
        if (currentState != GameState.KOI_KOI_DECISION) return;

        currentKoiKoiMult += 1.0;
        koiKoiMultLabel.setText("Koi-Mult: " + currentKoiKoiMult + "x");

        // --- NEU: Erst hier, wenn man das Risiko eingeht, wird nachgezogen! ---
        drawCardsToHand(MAX_HAND_SIZE);
        // ----------------------------------------------------------------------

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
            TextButton omamoriView = new TextButton(omamori.getName(), skin);
            omamoriView.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    triggerPunchAnimation(omamoriView);
                }
            });
            omamoriTable.add(omamoriView).width(100).height(150).pad(10);
        }
    }

    private void updateLivePreview() {
        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);
        ScoreContext previewCtx = ScoreContext.preview(floatingBank, hand, bestYaku, currentKoiKoiMult);

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

    // =========================================================================
    // MEMORY MANAGEMENT
    // =========================================================================

    @Override
    public void dispose() {
        stage.dispose();
        if (skin != null) skin.dispose();
        for (TextureRegionDrawable drawable : cardTextures.values()) {
            if (drawable.getRegion() != null && drawable.getRegion().getTexture() != null) {
                drawable.getRegion().getTexture().dispose();
            }
        }
        cardTextures.clear();
    }
}
