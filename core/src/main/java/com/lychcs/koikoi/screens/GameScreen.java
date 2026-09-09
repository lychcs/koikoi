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

    // =========================================================================
    // 2. SPIEL-ZUSTAND (Model/State)
    // =========================================================================
    private final Deck deck;
    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> selectedCards = new ArrayList<>();
    private final List<Omamori> activeOmamoris = new ArrayList<>();

    private GameState currentState = GameState.WAITING_FOR_INPUT;
    private int totalRunScore = 0;
    private int floatingBank = 0;
    private double currentKoiKoiMult = 1.0;

    //Discard-Tracking Variablen
    private int maxDiscards = INITIAL_MAX_DISCARDS;
    private int discardsRemaining = maxDiscards;

    // =========================================================================
    // 3. UI-ELEMENTE & RESSOURCEN (View)
    // =========================================================================
    private final Stage stage;
    private Skin skin;
    private final Map<String, TextureRegionDrawable> cardTextures = new HashMap<>();
    private final Map<String, Actor> actorRegistry = new HashMap<>();

    private Table handTable;
    private TextButton playButton;
    private TextButton discardButton;
    private TextButton koiKoiButton;
    private TextButton bankButton;
    private Label chipsLabel;
    private Label multLabel;
    private Label floatingBankLabel;
    private Label discardLabel;

    public GameScreen() {
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));
        this.deck = new Deck();

        initUiElements();
        startNewRound();
        dealCardsToUI();
    }

    // =========================================================================
    // HILFSMETHODEN FÜR SPIELLOGIK
    // =========================================================================

    private void startNewRound() {
        deck.initializeDeck();
        Collections.shuffle(deck.getCards());

        playerHand.clear();
        selectedCards.clear();

        // refill discards
        discardsRemaining = maxDiscards;
        if (discardLabel != null) discardLabel.setText("Discards: " + discardsRemaining);

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

        chipsLabel = new Label("0", skin);
        multLabel = new Label("0", skin);
        floatingBankLabel = new Label("Pot: 0", skin);
        discardLabel = new Label("Discards: " + discardsRemaining, skin);

        chipsLabel.setFontScale(2f);
        multLabel.setFontScale(2f);
        floatingBankLabel.setFontScale(1.5f);
        discardLabel.setFontScale(1.5f);

        Table topTable = new Table();
        topTable.setFillParent(true);
        topTable.top().padTop(50);

        topTable.add(chipsLabel).padRight(20);
        topTable.add(new Label(" X ", skin)).padRight(20);
        topTable.add(multLabel);
        topTable.row().padTop(20);
        topTable.add(floatingBankLabel).colspan(2).padRight(20);
        topTable.add(discardLabel).colspan(1);

        stage.addActor(topTable);
        stage.addActor(table);

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
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty()) return;

        currentState = GameState.SCORING_ANIMATION;

        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);
        ScoreContext context = new ScoreContext(floatingBank, hand, bestYaku, activeOmamoris, currentKoiKoiMult);
        CalculationBreakdown breakdown = ScoreCalculator.calculate(context);

        playScoringSequence(breakdown);
    }

    private void onDiscardClicked() {
        // Nichts tun, wenn gerade abgerechnet wird, nichts ausgewählt ist, oder keine Discards mehr übrig sind
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty() || discardsRemaining <= 0) {
            return;
        }

        // 1. Zähler verringern & UI aktualisieren
        discardsRemaining--;
        discardLabel.setText("Discards: " + discardsRemaining);

        // 2. Karten aus der Hand entfernen
        playerHand.removeAll(selectedCards);
        selectedCards.clear();

        // 3. Hand wieder auffüllen, Karten neu rendern und Preview auf resetten
        drawCardsToHand(MAX_HAND_SIZE);
        dealCardsToUI();
        updateLivePreview();
    }

    private void onBankClicked() {
        if (currentState != GameState.KOI_KOI_DECISION) return;

        totalRunScore += floatingBank;
        System.out.println("Neuer Gesamt-Score: " + totalRunScore);

        floatingBank = 0;
        currentKoiKoiMult = 1.0;
        floatingBankLabel.setText("Pot: 0");

        startNewRound(); // Füllt jetzt auch die Discards wieder auf
        resetUiForNextTurn();
    }

    private void onKoiKoiClicked() {
        if (currentState != GameState.KOI_KOI_DECISION) return;

        currentKoiKoiMult += 1.0;
        playerHand.removeAll(selectedCards);
        selectedCards.clear();

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

    private void updateLivePreview() {
        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);
        ScoreContext previewCtx = ScoreContext.preview(floatingBank, hand, bestYaku, currentKoiKoiMult);

        chipsLabel.setText(String.valueOf(previewCtx.getYakuBaseChips()));
        multLabel.setText(String.valueOf(previewCtx.getYakuBaseMult()));

        // Optisches Feedback: Den Discard-Knopf ausgrauen, wenn man nicht abwerfen kann
        if (selectedCards.isEmpty() || discardsRemaining <= 0) {
            discardButton.getColor().a = 0.5f; // Halb transparent
        } else {
            discardButton.getColor().a = 1.0f; // Voll sichtbar
        }
    }

    private void playScoringSequence(CalculationBreakdown breakdown) {
        var sequence = Actions.sequence();
        final int[] currentChips = { breakdown.yakuChips() };
        final int[] currentMult = { breakdown.yakuBaseMult() };

        sequence.addAction(Actions.run(() -> {
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f; // NEU: Discard Knopf in der Animation verstecken
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
        koiKoiButton.getColor().a = 1f;
        bankButton.getColor().a = 1f;
    }

    // =========================================================================
    // MEMORY MANAGEMENT
    // =========================================================================

    @Override
    public void dispose() {
        stage.dispose();

        if (skin != null) {
            skin.dispose();
        }

        for (TextureRegionDrawable drawable : cardTextures.values()) {
            if (drawable.getRegion() != null && drawable.getRegion().getTexture() != null) {
                drawable.getRegion().getTexture().dispose();
            }
        }
        cardTextures.clear();
    }
}
