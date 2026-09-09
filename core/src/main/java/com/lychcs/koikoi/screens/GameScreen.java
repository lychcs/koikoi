package com.lychcs.koikoi.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.Omamori;
import com.lychcs.koikoi.scoring.*;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.files.FileHandle;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
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

    private final Map<String, TextureRegionDrawable> cardTextures = new HashMap<>();
    private final List<Card> playerHand = new ArrayList<>();
    private Table handTable;
    private com.lychcs.koikoi.model.Deck deck;
    private Skin skin;
    private int totalRunScore = 0;
    private TextButton playButton;
    private final Stage stage;
    private Label chipsLabel;
    private Label multLabel;
    private Label floatingBankLabel;
    private GameState currentState = GameState.WAITING_FOR_INPUT;

    private int floatingBank = 0;
    private double currentKoiKoiMult = 1.0;

    private final List<Card> selectedCards = new ArrayList<>();
    private final List<Omamori> activeOmamoris = new ArrayList<>();

    private final Map<String, Actor> actorRegistry = new HashMap<>();

    private TextButton koiKoiButton;
    private TextButton bankButton;

    public GameScreen() {
        this.stage = new Stage(new FitViewport(1280, 720));

        initUiElements();

        // Deck initialisieren
        deck = new com.lychcs.koikoi.model.Deck();
        deck.initializeDeck();
        java.util.Collections.shuffle(deck.getCards());

        // 8 Karten ziehen
        for (int i = 0; i < 8; i++) {
            playerHand.add(deck.getCards().remove(0));
        }

        dealCardsToUI();
    }

    private void initUiElements() {
        // Lade die Standard-Skin (uiskin.json muss im assets Ordner liegen!)
        skin = new Skin(Gdx.files.internal("uiskin.json"));

        // Buttons erstellen
        playButton = new TextButton("Play Hand", skin);
        koiKoiButton = new TextButton("KOI KOI!", skin);
        bankButton = new TextButton("Bank (Shobu)", skin);

        // Koi-Koi und Bank Button sind am Anfang unsichtbar
        koiKoiButton.getColor().a = 0f;
        bankButton.getColor().a = 0f;

        // Klick-Event für den Play Button
        // Klick-Event für den Play Button
        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onPlayHandSubmitted();
            }
        });

        // Klick-Event für den KOI KOI! Button
        koiKoiButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onKoiKoiClicked(); // Ruft deine neue Zocker-Methode auf!
            }
        });

        // Klick-Event für den Bank Button
        bankButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                onBankClicked(); // Ruft deine neue Sicherungs-Methode auf!
            }
        });

        // UI-Layout (Tabelle) erstellen
        Table table = new Table();
        table.setFillParent(true);
        table.bottom().padBottom(50); // Ganz nach unten packen mit 50px Abstand

        // Buttons in die Tabelle einfügen
        table.add(playButton).width(200).height(60).pad(10);
        table.add(koiKoiButton).width(200).height(60).pad(10);
        table.add(bankButton).width(200).height(60).pad(10);

        // --- Score HUD (Oben) ---
        chipsLabel = new Label("0", skin);
        multLabel = new Label("0", skin);
        floatingBankLabel = new Label("Pot: 0", skin);

        chipsLabel.setFontScale(2f);
        multLabel.setFontScale(2f);
        floatingBankLabel.setFontScale(1.5f);

        Table topTable = new Table();
        topTable.setFillParent(true);
        topTable.top().padTop(50); // Ganz oben andocken


        //[Chips] X [Mult]
        topTable.add(chipsLabel).padRight(20);
        topTable.add(new Label(" X ", skin)).padRight(20);
        topTable.add(multLabel);
        topTable.row().padTop(20);
        topTable.add(floatingBankLabel).colspan(3); // Nimmt die ganze Breite ein

        stage.addActor(topTable);

        // Tabelle zur Stage hinzufügen
        stage.addActor(table);

        handTable = new Table();
        handTable.setFillParent(true);
        handTable.center().padTop(100);
        stage.addActor(handTable);

        // click detection
        Gdx.input.setInputProcessor(stage);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.15f, 0.17f, 0.21f, 1f);
        //libGDX game loop: delta time verarbeiten
        stage.act(delta);
        stage.draw();
    }

    private TextureRegionDrawable getCardImage(com.lychcs.koikoi.model.Card card) {
        if (card.id() == CardID.UNKNOWN) {
            return null;
        }

        String fileName = card.id().name();

        if (!cardTextures.containsKey(fileName)) {
            // Erst der Standardpfad für LibGDX
            FileHandle file = Gdx.files.internal("cards/" + fileName + ".png");

            // Fallback: Falls das Working Directory "assets/" als Präfix verlangt
            if (!file.exists()) {
                file = Gdx.files.internal("assets/cards/" + fileName + ".png");
            }

            if (file.exists()) {
                Texture tex = new Texture(file);
                tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
                cardTextures.put(fileName, new TextureRegionDrawable(tex));
            } else {
                System.out.println("Konnte Textur nicht finden: " + fileName + ".png (geprüfte Pfade: cards/ und assets/cards/)");
                return null;
            }
        }
        return cardTextures.get(fileName);
    }
    public void onPlayHandSubmitted() {
        if (currentState != GameState.WAITING_FOR_INPUT || selectedCards.isEmpty()) return;

        currentState = GameState.SCORING_ANIMATION;

        // 1. Scoring-Engine füttern
        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);

        ScoreContext context = new ScoreContext(floatingBank, hand, bestYaku, activeOmamoris, currentKoiKoiMult);

        // 2. Berechnung ausführen (Erzeugt das Logbuch "Breakdown")
        CalculationBreakdown breakdown = ScoreCalculator.calculate(context);

        // 3. UI-Animation starten
        playScoringSequence(breakdown);
    }
    private void playScoringSequence(CalculationBreakdown breakdown) {
        var sequence = Actions.sequence();

        // Lokale Variablen, um den sichtbaren Stand während der Animation hochzuzählen
        final int[] currentChips = { breakdown.yakuChips() };
        final int[] currentMult = { breakdown.yakuBaseMult() };

        // 1. Start: Knöpfe verstecken & Basis-Yaku anzeigen
        sequence.addAction(Actions.run(() -> {
            playButton.getColor().a = 0f; // Play-Button unsichtbar machen
            chipsLabel.setText(String.valueOf(currentChips[0]));
            multLabel.setText(String.valueOf(currentMult[0]));
        }));
        sequence.addAction(Actions.delay(0.6f)); // Kurze Pause für Dramatik

        // 2. Iteration über alle Events (Karten & Omamoris)
        for (ScoringEvent event : breakdown.events()) {
            // Leere Events überspringen
            if (event.addedChips() == 0 && event.addedMult() == 0 && event.xMult() == 1.0) {
                continue;
            }

            sequence.addAction(Actions.run(() -> {
                // Werte aufaddieren/multiplizieren
                if (event.addedChips() > 0) currentChips[0] += event.addedChips();
                if (event.addedMult() > 0) currentMult[0] += event.addedMult();
                if (event.xMult() > 1.0) {
                    currentMult[0] = (int) Math.round(currentMult[0] * event.xMult());
                }

                // UI updaten
                chipsLabel.setText(String.valueOf(currentChips[0]));
                multLabel.setText(String.valueOf(currentMult[0]));

                // TODO später: Hier könnten wir das jeweilige Omamori wackeln lassen!
            }));

            sequence.addAction(Actions.delay(0.35f)); // Pacing zwischen den xMults
        }

        // 3. Abrechnung in den Pot (Floating Bank)
        sequence.addAction(Actions.delay(0.4f));
        sequence.addAction(Actions.run(() -> {
            floatingBank = (int) breakdown.finalPayout();
            floatingBankLabel.setText("Pot: " + floatingBank);

            // Chips und Mult wieder auf 0 setzen für die nächste Hand
            chipsLabel.setText("0");
            multLabel.setText("0");
        }));
        sequence.addAction(Actions.delay(0.5f));

        // 4. Ende der Animation: Die Koi-Koi Entscheidung aufrufen!
        sequence.addAction(Actions.run(this::enterKoiKoiDecision));

        // Action an die Stage hängen, damit sie ausgeführt wird
        stage.addAction(sequence);
    }

    private void triggerPunchAnimation(Actor actor) {
        actor.setOrigin(actor.getWidth() / 2f, actor.getHeight() / 2f);
        actor.clearActions();
        actor.addAction(Actions.sequence(
            Actions.parallel(
                Actions.scaleTo(1.25f, 1.25f, 0.08f, Interpolation.fastSlow),
                Actions.rotateBy(4f, 0.08f)
            ),
            Actions.parallel(
                Actions.scaleTo(1.0f, 1.0f, 0.15f, Interpolation.bounceOut),
                Actions.rotateTo(0f, 0.15f)
            )
        ));
    }

    private void enterKoiKoiDecision() {
        currentState = GameState.KOI_KOI_DECISION;

        // "Push Your Luck"-Knöpfe einblenden
        koiKoiButton.getColor().a = 1f;
        bankButton.getColor().a = 1f;
    }

    private void onBankClicked() {
        if (currentState != GameState.KOI_KOI_DECISION) return;

        // 1. Pot sichern!
        totalRunScore += floatingBank;
        System.out.println("Neuer Gesamt-Score: " + totalRunScore);

        floatingBank = 0;
        currentKoiKoiMult = 1.0;
        floatingBankLabel.setText("Pot: 0");

        // 2. Komplett neue Runde starten (Neues Deck)
        deck.initializeDeck();
        java.util.Collections.shuffle(deck.getCards());

        playerHand.clear();
        selectedCards.clear();
        for (int i = 0; i < 8; i++) {
            playerHand.add(deck.getCards().remove(0));
        }

        // 3. UI aufräumen
        koiKoiButton.getColor().a = 0f;
        bankButton.getColor().a = 0f;
        playButton.getColor().a = 1f;

        dealCardsToUI();
        updateLivePreview();

        currentState = GameState.WAITING_FOR_INPUT;
    }

    private void onKoiKoiClicked() {
        if (currentState != GameState.KOI_KOI_DECISION) return;

        // 1. Risiko erhöhen
        currentKoiKoiMult += 1.0;

        // 2. Die gespielten Karten aus der Hand entfernen und vom Deck abziehen
        playerHand.removeAll(selectedCards);
        selectedCards.clear();

        // 3. Hand wieder auf 8 Karten auffüllen
        while (playerHand.size() < 8 && !deck.getCards().isEmpty()) {
            playerHand.add(deck.getCards().remove(0));
        }

        // 4. UI aufräumen und State zurücksetzen
        koiKoiButton.getColor().a = 0f;
        bankButton.getColor().a = 0f;
        playButton.getColor().a = 1f; // Play Button wieder einblenden!

        dealCardsToUI(); // Rendert die neuen Karten!
        updateLivePreview(); // Setzt die Labels oben wieder auf 0

        currentState = GameState.WAITING_FOR_INPUT;
    }

    private void dealCardsToUI() {
        handTable.clearChildren();
        selectedCards.clear();

        for (com.lychcs.koikoi.model.Card card : playerHand) {

            TextureRegionDrawable image = getCardImage(card);
            Button cardView;

            if (image != null) {
                // Wir haben ein Bild -> Nutze einen ImageButton
                cardView = new ImageButton(image);
            } else {
                // Wir haben noch kein Bild -> Nutze den alten grauen TextButton als Fallback
                cardView = new TextButton(card.season().name() + "\n" + card.rank().name(), skin);
            }

            // --- DER KLICK-LISTENER BLEIBT EXAKT GLEICH ---
            cardView.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (currentState != GameState.WAITING_FOR_INPUT) return;

                    if (selectedCards.contains(card)) {
                        selectedCards.remove(card);
                        cardView.addAction(Actions.moveBy(0, -20f, 0.1f));
                    } else {
                        if (selectedCards.size() < 5) {
                            selectedCards.add(card);
                            cardView.addAction(Actions.moveBy(0, 20f, 0.1f));
                        }
                    }
                    updateLivePreview();
                }
            });

            // Tabelle updaten: 120 Breite und 192 Höhe entspricht exakt deinem 300x480 (1:1.6) Verhältnis!
            handTable.add(cardView).width(120).height(192).pad(5);
        }
    }
    private void updateLivePreview() {
        HandContext hand = new HandContext(selectedCards);
        YakuResult bestYaku = YakuDetector.findBestYaku(selectedCards).orElse(null);

        ScoreContext previewCtx = ScoreContext.preview(floatingBank, hand, bestYaku, currentKoiKoiMult);

        // Labels aktualisieren
        chipsLabel.setText(String.valueOf(previewCtx.getYakuBaseChips()));
        multLabel.setText(String.valueOf(previewCtx.getYakuBaseMult()));
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
