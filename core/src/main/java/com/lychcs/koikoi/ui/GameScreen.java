package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.actions.SequenceAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import com.lychcs.koikoi.KoiKoiGame;
import com.lychcs.koikoi.graphics.CorruptionEngine;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.CardID;
import com.lychcs.koikoi.model.DeckTemplates;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.*;
import com.lychcs.koikoi.model.shikigami.*;
import com.lychcs.koikoi.model.shikigami.ability.CardTransformation;
import com.lychcs.koikoi.model.shikigami.ability.ShikigamiAbility;
import com.lychcs.koikoi.model.shikigami.ability.ShikigamiAbilityContext;
import com.lychcs.koikoi.model.shikigami.ability.ShikigamiAbilityResult;
import com.lychcs.koikoi.model.shikigami.ability.ShikigamiActivationTiming;
import com.lychcs.koikoi.model.shikigami.ability.ShikigamiSelectionMode;
import com.lychcs.koikoi.run.GameSeason;
import com.lychcs.koikoi.run.HandSorting;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.run.ShikigamiReward;
import com.lychcs.koikoi.run.YokaiEncounter;
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

    /**
     * Stabile Bindung waehrend einer Scoresequenz: die sichtbare Originalkarte der
     * Hand, die fuer die Wertung verwendete (ggf. mutierte) Karte, ihr Actor auf
     * der Score-Buehne sowie Beitragstext und optionaler Hinweis.
     *
     * <p>Die Animation ordnet Events ausschliesslich ueber diese Bindung zu:
     * kein Textvergleich, keine Suche anhand von sourceName, keine globale
     * Actor-Map und keine UI-Referenzen im Scoring-Model.</p>
     */
    private static final class ScoringCardBinding {
        final Card originalCard;
        final Card evaluatedCard;
        final JuicyCardActor actor;
        final Label contribution;
        final Label note;
        final boolean scored;
        final int index;
        final float x;
        final float y;

        ScoringCardBinding(Card originalCard, Card evaluatedCard, JuicyCardActor actor,
                           Label contribution, Label note, boolean scored,
                           int index, float x, float y) {
            this.originalCard = originalCard;
            this.evaluatedCard = evaluatedCard;
            this.actor = actor;
            this.contribution = contribution;
            this.note = note;
            this.scored = scored;
            this.index = index;
            this.x = x;
            this.y = y;
        }
    }

    private static final float WORLD_WIDTH = 1280f;
    private static final float WORLD_HEIGHT = 720f;
    private static final int MAX_HAND_SIZE = 8;
    private static final int MAX_SELECTED_CARDS = 5;

    private static final float CARD_WIDTH = 72f;
    private static final float CARD_HEIGHT = 128f;

    // Handbogen-Geometrie (eine Quelle fuer Zielslots und Einflug neuer Karten).
    private static final float CARD_SPACING = 64f;
    private static final float CARD_DROP_PER_CARD = 1.8f;
    private static final float CARD_ROTATION_PER_CARD = 2.5f;

    /** Dauer eines einzelnen Score-Schritts der Animation. */
    private static final float SCORE_STEP_DELAY = 0.18f;

    // Animationsdetails der Wertung.
    private static final float PULSE_SCALE = 1.18f;
    private static final float PULSE_TIME = 0.10f;
    private static final float FLOATING_TEXT_TIME = 0.55f;

    // Farben der Score-Rueckmeldungen (Flussfarben, kein neues Asset).
    private static final Color SCORE_CHIP_COLOR = new Color(0.98f, 0.83f, 0.35f, 1f);
    private static final Color SCORE_MULT_COLOR = new Color(0.55f, 0.85f, 1f, 1f);
    private static final Color SCORE_HANKO_COLOR = new Color(1f, 0.58f, 0.76f, 1f);
    private static final Color SCORE_OMAMORI_COLOR = new Color(0.76f, 1f, 0.70f, 1f);
    private static final Color SCORE_SHIKIGAMI_COLOR = new Color(0.85f, 0.72f, 1f, 1f);
    /** Farbe fuer Karten, die nicht zum Yaku gehoeren. */
    private static final Color SCORE_UNMATCHED_TINT = new Color(0.55f, 0.55f, 0.60f, 0.92f);
    private static final Color SCORE_IDLE_TEXT_COLOR = new Color(0.72f, 0.72f, 0.72f, 1f);

    // ---------------------------------------------------------------------
    // Score-Buehne: freie Flaeche zwischen Omamori-Anzeige und Kartenhand.
    // Alle Werte sind relativ zur Welt-/Stage-Groesse und damit aufloesungsunabhaengig.
    // ---------------------------------------------------------------------
    /** Y-Position der Kartenunterkante in der Buehne (Stage-Koordinaten). */
    private static final float SCORE_AREA_Y = WORLD_HEIGHT * 0.53f;
    /** Horizontaler Bereich, in dem die Kartenreihe zentriert wird. */
    private static final float SCORE_AREA_MIN_CENTER_X = WORLD_WIDTH * 0.28f;
    private static final float SCORE_AREA_MAX_CENTER_X = WORLD_WIDTH * 0.96f;
    /** Abstand zwischen zwei gespielten Karten (wird bei vielen Karten enger). */
    private static final float SCORE_CARD_MAX_SPACING = 84f;
    private static final float SCORE_CARD_MIN_SPACING = 58f;
    /** Abstand zwischen Kartenoberkante und Beitragstext. */
    private static final float SCORE_CONTRIBUTION_GAP = 6f;
    /** Abstand zwischen Kartenunterkante und Hinweis "No Yaku contribution". */
    private static final float SCORE_NOTE_GAP = 20f;
    /** Zeit, bis die Physik das Reparenting als neue Position uebernommen hat. */
    private static final float SCORE_STAGE_SETTLE_TIME = 0.08f;
    /** Zeit fuer den Flug in die Kartenreihe der Buehne. */
    private static final float SCORE_STAGE_ALIGN_TIME = 0.32f;
    /** Kurzes Wackeln der gewerteten Karte. */
    private static final float CARD_WOBBLE_X = 6f;
    private static final float CARD_WOBBLE_Y = 12f;
    private static final float CARD_WOBBLE_STEP = 0.06f;
    private static final float CARD_WOBBLE_SCALE = 1.12f;
    /** Kraeftigerer Puls des Begleiters (Shikigami) ganz am Ende. */
    private static final float SHIKIGAMI_PULSE_SCALE = 1.26f;

    // Bis zu drei aktive Omamori: jeder Slot hat eine eigene Zellgroesse, Ausrichtung und Padding.
    private static final float[] OMAMORI_SLOT_CELL_WIDTHS = {80f, 84f, 80f};
    private static final float[] OMAMORI_SLOT_CELL_HEIGHTS = {100f, 104f, 100f};
    private static final float[] OMAMORI_SLOT_CELL_PADS = {2f, 4f, 2f};
    private static final float OMAMORI_ICON_WIDTH = 72f;
    private static final float OMAMORI_ICON_HEIGHT = 96f;

    private static final int INITIAL_MAX_DISCARDS = 3;
    private static final int INITIAL_MAX_HANDS = 4;

    // ---------------------------------------------------------------------
    // Faehigkeitsdialog (Altar-Aktivierung): Groessen in Stage-Einheiten.
    // Die Stage ist ein FitViewport(1280x720); damit bleibt das Modal bei
    // 1280x720 und 1920x1080 vollstaendig sichtbar und bedienbar.
    // ---------------------------------------------------------------------
    /** Breite des Dialogpanels (Inhalt bleibt innerhalb der Stage). */
    private static final float ABILITY_PANEL_WIDTH = 620f;
    /** Sicherer Abstand des Panels zum Viewport-Rand. */
    private static final float ABILITY_PANEL_MARGIN = 12f;
    /** Sichtbare Hoehe der Zielgalerie (Level 3) vor dem Scrollen. */
    private static final float ABILITY_GALLERY_HEIGHT = 296f;
    /** Zellbreite eines Zielmotivs in der Galerie. */
    private static final float ABILITY_GALLERY_ENTRY_WIDTH = 112f;
    /** Zielmotive je Galeriezeile. */
    private static final int ABILITY_GALLERY_COLUMNS = 5;
    /** Abdunklung des Hintergrunds (Drawable der gemeinsamen Skin, keine neue Textur). */
    private static final float ABILITY_DIM_ALPHA = 0.72f;

    // Ablaufzeiten der kurzen Transformationsanimation (nach erfolgreichem Commit).
    private static final float ABILITY_SOURCE_PULSE_SCALE = 1.18f;
    private static final float ABILITY_SOURCE_PULSE_TIME = 0.16f;
    private static final float ABILITY_TRANSFORM_TIME = 0.22f;
    private static final float ABILITY_REPLACEMENT_START_SCALE = 0.7f;
    /** Dauer des Altar-/Kitsune-Pulses, bis die Eingabe wieder frei ist. */
    private static final float ABILITY_ALTAR_PULSE_TIME = 0.45f;

    /** Hervorhebung der gewaehlten Quellkarte. */
    private static final Color ABILITY_SELECTED_TINT = new Color(1f, 0.93f, 0.62f, 1f);
    /** Sichtbar gedaempfte, nicht auswaehlbare Zielmotive (gleiche CardID wie die Quelle). */
    private static final Color ABILITY_DIMMED_TINT = new Color(1f, 1f, 1f, 0.38f);

    /**
     * Zustandsautomat der Faehigkeitsaktivierung. Jeder Zustand ausser
     * {@link #IDLE} sperrt die normale Hand-, Sortier-, Omamori- und
     * Shikigami-Bedienung; der Screen rendert und animiert dabei weiter.
     *
     * <p>{@link #RESOLVED} und {@link #CANCELLED} sind die dokumentierten
     * Endzustaende eines Vorgangs (Commit bzw. Abbruch) und gehen unmittelbar in
     * {@link #IDLE} ueber, sobald die kurze Animation beziehungsweise das
     * Aufraeumen abgeschlossen ist.</p>
     */
    private enum AbilityActivationState {
        /** Keine Aktivierung: normale Bedienung. */
        IDLE,
        /** Bestaetigung/Fehlermeldung sichtbar, noch kein Commit. */
        PENDING_CONFIRMATION,
        /** Der Spieler waehlt die Quellkarte aus der Hand. */
        SELECTING_SOURCE_CARD,
        /** Der Spieler waehlt das Zielmotiv (Level 3). */
        SELECTING_TARGET_CARD,
        /** Ergebnis wird angewendet und animiert: Eingabe bleibt gesperrt. */
        RESOLVING,
        /** Faehigkeit erfolgreich angewendet. */
        RESOLVED,
        /** Vorgang abgebrochen: nichts wurde veraendert. */
        CANCELLED
    }

    /**
     * Kurze Anzeigedauer der Victory-/Belohnungsmeldung vor der Rueckkehr in die
     * Overworld (dasselbe sichere Muster wie im Niederlage-Zweig).
     */
    private static final float VICTORY_RETURN_DELAY = 0.9f;

    /** Maximale Breite des Shikigami-Infopanels, damit lange Faehigkeitstexte umbrechen. */
    private static final float INFO_POPUP_MAX_WIDTH = 330f;

    /** Sicherer Abstand des Infopanels zum Viewport-Rand. */
    private static final float INFO_POPUP_MARGIN = 6f;

    private final List<Card> playerHand = new ArrayList<>();
    private final List<Card> selectedCards = new ArrayList<>();
    private final List<Omamori> activeOmamoris = new ArrayList<>();
    private final List<Card> drawPile = new ArrayList<>();

    private Shikigami activeAltarShikigami = null;
    private GameState currentState = GameState.WAITING_FOR_INPUT;
    /** true, solange die Wertungssequenz laeuft (verhindert Doppelstarts). */
    private boolean scoringSequenceRunning = false;
    /** true, sobald Sieg oder Niederlage committet wurden (Duplikatschutz). */
    private boolean encounterResolved = false;
    /** true, wenn das Shikigami-Infopanel per Klick fixiert wurde. */
    private boolean infoPinned = false;
    /** Shikigami, dessen Info aktuell im Panel steht (null = keins). */
    private Shikigami infoPopupOwner = null;
    /** Wiederverwendeter Vektor fuer die Panelpositionierung (keine Allokation pro Event). */
    private final Vector2 infoAnchorPosition = new Vector2();

    // ---------------------------------------------------------------------
    // Pending-Faehigkeitsaktivierung (Altar-Drop als Transaktion)
    // ---------------------------------------------------------------------
    /** Aktueller Zustand des Aktivierungsvorgangs. */
    private AbilityActivationState activationState = AbilityActivationState.IDLE;
    /** Vorgemerkter Begleiter: noch NICHT im Altar und noch nicht erschoepft. */
    private Shikigami pendingActivationShikigami;
    /** Deklarative Faehigkeit des vorgemerkten Begleiters (aus getAbility()). */
    private ShikigamiAbility pendingActivationAbility;
    /** Exakte Referenz der gewaehlten Quellkarte (Identitaetsvergleich, kein equals). */
    private Card pendingSourceCard;
    /** Handindex der Quellkarte zum Auswahlzeitpunkt (nur fuer die Anzeige). */
    private int pendingSourceIndex = -1;
    /** Gewaehltes Zielmotiv (Level 3). */
    private Card pendingTargetTemplate;
    /** Modal (Abdunklung + Panel) der Aktivierung. */
    private Table abilityOverlay;
    /** Panelinhalt des aktuellen Schritts. */
    private Table abilityPanel;
    /** Zielgalerie (Level 3) fuer den Scrollzustand. */
    private ScrollPane abilityTargetScroll;
    /** Zielmotiv-Kacheln der Galerie (Auswahlmarkierung ohne Namensvergleich). */
    private final List<Table> abilityTargetTiles = new ArrayList<>();
    /** Quellkarten-Kacheln des aktuellen Auswahlschritts. */
    private final List<Table> abilitySourceTiles = new ArrayList<>();
    /** Aktuell markierte Zielkachel. */
    private Table abilitySelectedTargetTile;
    /** Aktuell markierte Quellkachel. */
    private Table abilitySelectedSourceTile;
    /** Infozeile des aktuellen Schritts (gewaehlte Quelle/Ziel). */
    private Label abilityStepInfoLabel;
    /** Weiter-/Apply-Button des aktuellen Schritts (Alpha je nach Auswahl). */
    private TextButton abilityForwardButton;
    /** Pulsierende Handkarten waehrend der Quellauswahl. */
    private final List<Actor> abilityHighlightedCards = new ArrayList<>();
    /** Begleiter, deren Faehigkeit in diesem Kampf bereits angewendet wurde (Identitaet). */
    private final Set<Shikigami> abilityResolvedShikigami =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private double currentTargetScore;
    private double currentRoundScore = 0.0;
    private int maxDiscards = INITIAL_MAX_DISCARDS;
    private int discardsRemaining = maxDiscards;
    private int maxHands = INITIAL_MAX_HANDS;
    private int handsRemaining = maxHands;
    private HandSorting.Mode currentHandSortMode = HandSorting.Mode.NONE;
    private Table infoPopup;
    private final Stage stage;
    private Skin skin;
    private TextureAtlas atlas;
    private final Map<String, TextureRegionDrawable> cardTextures = new HashMap<>();
    /** Cache fuer die Hanko-Abzeichen auf den Kampfkarten (fehlende Regionen inklusive). */
    private final Map<HankoEffect, TextureRegion> hankoBadgeRegions = new EnumMap<>(HankoEffect.class);
    private final RunSession runSession;
    /** Begegnung dieses Kampfes (stabile Definition aus dem Encounter-Katalog). */
    private final YokaiEncounter encounter;
    private NinePatchDrawable panelBackground;
    private TextButton.TextButtonStyle indieButtonStyle;
    private AltarFlameActor altarFlames;
    private Table hankoTable;
    private Table omamoriTable;
    private Table altarTable;
    private Table ShikigamiBagTable;
    private final Set<Shikigami> usedShikigamiInBattle = new HashSet<>();
    private Group handGroup;

    // Score-Buehne und Beitragsanzeigen der laufenden Wertung.
    private Group scoreArea;
    private Group omamoriContributionLayer;
    /** Bindungen Original/Evaluated/Actor der aktuellen Scoresequenz. */
    private final List<ScoringCardBinding> scoreBindings = new ArrayList<>();
    /** Beitragstext je aktivem Omamori (leer = kein Effekt). */
    private final Map<Omamori, Label> omamoriContributions = new HashMap<>();

    private TextButton playButton;
    private TextButton discardButton;
    /** Screen-lokaler Kampfhintergrund. */
    private Texture background;
    /** Geliehene, global verwaltete Assets (Eigentum: GameAssets). */
    private GameAssets assets;

    private Label scoreProgressLabel;
    private Label chipsLabel;
    private Label multLabel;
    private Label discardLabel;
    private Label handsLabel;
    private Label yakuNameLabel;

    // Post-Processing

    private FrameBuffer fbo;
    private SpriteBatch screenBatch;
    private TextureRegion fboRegion;
    private ShaderProgram edgeShader;

    private CorruptionEngine corruptionEngine;

    /** Schutz gegen Mehrfach-Dispose durch den Screen-Manager. */
    private boolean disposed;

    public GameScreen(RunSession runSession, GameAssets assets, YokaiEncounter encounter) {
        if (assets == null) {
            throw new IllegalArgumentException("assets must not be null");
        }
        if (encounter == null) {
            throw new IllegalArgumentException("encounter must not be null");
        }

        this.runSession = runSession;
        this.assets = assets;
        this.encounter = encounter;
        this.stage = new Stage(new FitViewport(WORLD_WIDTH, WORLD_HEIGHT));

        // Zielscore stammt aus der Begegnungsdefinition (Wert unveraendert zum bisherigen
        // festen Kampfziel) und steht vor dem Aufbau der UI fest.
        this.currentTargetScore = encounter.getTargetScore();

        // Geliehener, global geladener Atlas: kein Laden, kein Dispose, keine Filterarbeit.
        this.atlas = assets.getAtlas(GameAssets.GAME_ATLAS);

        // Wilde Form nur aufloesen/pruefen, wenn sie aus dem Atlas stammt; animierte
        // Aseprite-Formen laedt die Overworld-Entity ueber GameAssets. Fehlt sogar die
        // Fallback-Region, ist der Atlas fuer diese Spezies unbrauchbar.
        if (!encounter.getSpecies().hasWildSheet()
            && encounter.getSpecies().resolveWildRegion(atlas) == null) {
            Gdx.app.error("GameScreen", "Missing wild form region '"
                + encounter.getSpecies().getWildAtlasRegionName() + "' and fallback region '"
                + encounter.getSpecies().getFirstPurifiedAtlasRegionName() + "' in "
                + GameAssets.GAME_ATLAS + " (species " + encounter.getSpeciesId() + ").");
        }

        screenBatch = new SpriteBatch();
        edgeShader = new ShaderProgram(
            Gdx.files.internal("shaders/edge_detection.vert"),
            Gdx.files.internal("shaders/edge_detection.frag")
        );

        if (!edgeShader.isCompiled()) {
            throw new GdxRuntimeException(
                "edge_detection shader could not be compiled:\n"
                    + edgeShader.getLog()
            );
        }

        initUiElements();
        startEncounter();
        dealCardsToUI();
    }

    private void startEncounter() {
        currentRoundScore = 0.0;
        // Reste einer vorherigen Wertung entfernen (Buehne und Beitragsanzeigen).
        clearScoreStage();
        clearOmamoriContributions();
        // Einen offenen Faehigkeitsvorgang eines vorherigen Kampfes restlos
        // entfernen: kein liegenbleibendes Modal, kein halb gesetzter Zustand.
        activationState = AbilityActivationState.IDLE;
        closeAbilityModal();
        clearPendingActivation();
        // Ein neuer Kampf startet ohne Sortiermodus.
        currentHandSortMode = HandSorting.Mode.NONE;
        discardsRemaining = runSession.getBaseDiscards();
        handsRemaining = runSession.getBaseHands();

        activeOmamoris.clear();
        activeOmamoris.addAll(runSession.getActiveOmamoris());
        renderOmamoris();

        activeAltarShikigami = null;
        renderShikigamiUI();

        if (scoreProgressLabel != null) scoreProgressLabel.setText("Score: " + ScoreFormat.format(currentRoundScore) + " / " + ScoreFormat.format(currentTargetScore));
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
        // Neue Karten werden einzeln ueber den gespeicherten Einfuegemodus platziert.
        // Bereits vorhandene Karten werden dabei nicht erneut sortiert; ihre relative
        // Reihenfolge bleibt auch nach manuellem Verschieben erhalten.
        while (
            playerHand.size() < targetSize &&
                !drawPile.isEmpty()
        ) {
            HandSorting.insertCard(playerHand, drawPile.remove(0), currentHandSortMode);
        }
    }

    /**
     * Sortiert die aktuell vorhandene Hand einmalig gemaess dem gespeicherten
     * Sortiermodus. Der Modus bleibt danach als Einfuegemodus fuer neu gezogene
     * Karten erhalten und schraenkt manuelles Verschieben nicht ein.
     */
    private void applyCurrentHandSort() {
        HandSorting.sortOnce(playerHand, currentHandSortMode);
    }

    private void resetUiForNextTurn() {
        playButton.getColor().a = 1f;
        discardButton.getColor().a = 1f;

        dealCardsToUI();
        renderShikigamiUI();
        updateLivePreview();
        currentState = GameState.WAITING_FOR_INPUT;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);

        // Kein neuer FBO ohne echte Groessenaenderung und nie mit Groesse 0.
        if (width <= 0 || height <= 0) {
            return;
        }
        if (fbo != null && fbo.getWidth() == width && fbo.getHeight() == height) {
            return;
        }

        if (fbo != null) {
            fbo.dispose();
        }
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        // Frische Region pro FBO: der Y-Flip wird dadurch nie mehrfach angewendet.
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);

        if (screenBatch != null) {
            screenBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        }
    }

    private void initUiElements() {
        // Geliehene, globale Skin: kein Dispose in diesem Screen.
        skin = assets.getSkin();
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
            @Override
            public boolean touchDown(
                InputEvent event,
                float x,
                float y,
                int pointer,
                int button
            ) {
                if (
                    !isHandInteractionAllowed() ||
                        playerHand.isEmpty()
                ) {
                    return true;
                }

                currentHandSortMode = HandSorting.Mode.SEASON;
                applyCurrentHandSort();
                dealCardsToUI();
                updateLivePreview();

                return true;
            }
        });

        sortRankButton.addListener(new ClickListener() {
            @Override
            public boolean touchDown(
                InputEvent event,
                float x,
                float y,
                int pointer,
                int button
            ) {
                if (
                    !isHandInteractionAllowed() ||
                        playerHand.isEmpty()
                ) {
                    return true;
                }

                currentHandSortMode = HandSorting.Mode.RANK;
                applyCurrentHandSort();
                dealCardsToUI();
                updateLivePreview();

                return true;
            }
        });

        scoreProgressLabel = new Label(ScoreFormat.format(0.0) + " / " + ScoreFormat.format(currentTargetScore), skin);
        chipsLabel = new Label("0", skin);
        multLabel = new Label("0", skin);
        discardLabel = new Label("Discards: " + discardsRemaining, skin);
        handsLabel = new Label("Hands: " + handsRemaining, skin);
        yakuNameLabel = new Label("", skin);
        // Sieg- und Belohnungsmeldungen sind zweizeilig: Wrap plus feste Breite verhindern,
        // dass der Text aus dem Panel laeuft.
        yakuNameLabel.setWrap(true);
        yakuNameLabel.setAlignment(Align.center);

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
        yakuBox.add(yakuNameLabel).width(240f).padBottom(10).padTop(15).row();
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

        ShikigamiBagTable = new Table();
        ShikigamiBagTable.setBackground(panelBackground);
        ShikigamiBagTable.left().pad(15);

        hankoTable = new Table();
        hankoTable.setBackground(panelBackground);
        hankoTable.pad(15);
        hankoTable.add(new Label("Hanko", skin));

        topRow.add(omamoriTable).height(120).expandX().fillX().padRight(15);
        topRow.add(altarStack).width(150).height(120).padRight(15);
        topRow.add(ShikigamiBagTable).height(120).expandX().fillX().padRight(15);
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

        // Score-Buehne in der freien Flaeche zwischen Omamori-Anzeige und Hand.
        // Eigene Gruppe mit relativer Position (keine feste Bildschirmaufloesung).
        scoreArea = new Group();
        scoreArea.setPosition(0f, SCORE_AREA_Y);
        stage.addActor(scoreArea);

        // Beitragsanzeigen der Omamori (liegen unter den Slot-Icons der Omamori-Zeile).
        omamoriContributionLayer = new Group();
        omamoriContributionLayer.setPosition(0f, 0f);
        stage.addActor(omamoriContributionLayer);

        infoPopup = new Table();
        infoPopup.setBackground(panelBackground);
        infoPopup.setVisible(false);
        stage.addActor(infoPopup);

        // ESC bricht einen laufenden Faehigkeitsvorgang ab. Es gibt genau ein
        // Eingabesystem (diese Stage); der Listener ergaenzt nur diese eine Taste
        // und greift ausschliesslich bei aktivem Vorgang ein.
        stage.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ESCAPE || !isAbilityFlowActive()) {
                    return false;
                }
                if (activationState == AbilityActivationState.PENDING_CONFIRMATION
                    && pendingActivationShikigami == null) {
                    // Reines Hinweisfenster (Ablehnung/leere Hand/bereits verwendet).
                    dismissAbilityMessage();
                } else {
                    cancelAbilityActivation();
                }
                return true;
            }
        });

        corruptionEngine = new CorruptionEngine();
    }

    @Override
    public void render(float delta) {
        HankoShaderManager.update(delta);

        // NEU: Gray Scott Engine im Hintergrund rechnen lassen
        corruptionEngine.update(delta);

        // 1. PHASE: Spiel in FBO rendern (Bleibt gleich)
        fbo.begin();
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        float activeDelta = com.lychcs.koikoi.graphics.JuiceManager.update(delta, stage.getCamera());
        stage.getBatch().begin();
        stage.getBatch().draw(background, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
        stage.getBatch().end();
        stage.act(activeDelta);
        stage.draw();
        fbo.end();

        // 2. PHASE: Post-Processing
        ScreenUtils.clear(0, 0, 0, 1);
        screenBatch.setShader(edgeShader);
        screenBatch.begin();

        // Standard Edge Uniforms
        edgeShader.setUniformf("u_pixelSize", 1f / Gdx.graphics.getWidth(), 1f / Gdx.graphics.getHeight());
        edgeShader.setUniformf("u_threshold", 0.52f);
        edgeShader.setUniformf("u_lineColor", 0.12f, 0.10f, 0.14f, 1.0f);
        edgeShader.setUniformf("u_backgroundColor", 0.94f, 0.91f, 0.83f, 1.0f);
        edgeShader.setUniformf("u_time", HankoShaderManager.getTotalTime());

        // NEU: Binde die Gray-Scott Textur auf Slot 1, das FBO ist auf Slot 0
        corruptionEngine.getCorruptionMap().bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0); // Wieder auf Slot 0 zurück!
        edgeShader.setUniformi("u_corruptionMap", 1);

        screenBatch.draw(fboRegion, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        screenBatch.end();
        screenBatch.setShader(null);

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

    /**
     * Liefert die Atlas-Region fuer das Hanko-Abzeichen einer Kampfkarte.
     * Fehlt die Region, wird der vollstaendige erwartete Regionsname geloggt und
     * {@code null} zurueckgegeben (keine NullPointerException).
     */
    private TextureRegion getHankoBadgeRegion(Card card) {
        if (card == null || !card.hasHanko()) return null;

        HankoEffect effect = card.effect();
        if (hankoBadgeRegions.containsKey(effect)) {
            return hankoBadgeRegions.get(effect);
        }

        String regionName = HankoCatalog.getAtlasRegionName(effect);
        TextureRegion region = regionName == null ? null : atlas.findRegion(regionName);
        if (region == null) {
            Gdx.app.error("GameScreen", "Hanko atlas region missing: " + regionName + " (Hanko " + effect + ")");
        }
        // Auch null cachen, damit der Fehler nur einmal geloggt wird.
        hankoBadgeRegions.put(effect, region);
        return region;
    }

    private void loadIndieAssets() {
        // Screen-lokaler Kampfhintergrund.
        background = new Texture(Gdx.files.internal(GameAssets.PLAYING_BOARD_BACKGROUND));

        // NinePatch-Drawables verweisen nur auf die gemeinsamen Texturen und
        // besitzen selbst keine GPU-Ressource.
        panelBackground = assets.newPanelDrawable();

        indieButtonStyle = new TextButton.TextButtonStyle();
        indieButtonStyle.up = assets.newButtonDrawable();
        indieButtonStyle.down = ((NinePatchDrawable) indieButtonStyle.up).tint(Color.LIGHT_GRAY);
        indieButtonStyle.font = FontManager.getFont();
        indieButtonStyle.fontColor = COLOR_TEXT_MAIN;
    }

    public void onPlayHandSubmitted() {
        if (!isHandInteractionAllowed()
            || selectedCards.isEmpty() || handsRemaining <= 0) return;

        handsRemaining--;
        handsLabel.setText("Hands: " + handsRemaining);

        List<Card> playedCards = new ArrayList<>(selectedCards);

        // Sichtbare Links-nach-rechts-Reihenfolge sichern, BEVOR Karten aus der Hand
        // entfernt oder Actors geloescht werden.
        List<Card> visualOrder = visualOrderOf(playedCards);

        List<Card> evaluatedCards = playedCards;
        if (activeAltarShikigami instanceof Oni oni) {
            evaluatedCards = oni.mutateHand(playedCards, runSession.getCurrentSeason());
        }
        List<Card> evaluatedVisualOrder = mapToEvaluatedOrder(visualOrder, playedCards, evaluatedCards);

        // Die sichtbare Links-nach-rechts-Reihenfolge ist die autoritative
        // Auswahlreihenfolge: bei gleich starken Karten gewinnt immer die linke.
        HandContext hand = new HandContext(evaluatedVisualOrder);
        List<Card> unplayed = new ArrayList<>(playerHand);
        unplayed.removeAll(selectedCards);

        // Bestes Yaku nach realer Auszahlung: jeder Kandidat wird side-effect-frei
        // ueber denselben Score-Kern bewertet (inkl. Level, Kartenbasis, Hankos,
        // Shikigami und Omamori). Kein Zufall, keine Rewards.
        YakuResult bestYaku = YakuSelector.findBestByRealScore(
            evaluatedVisualOrder,
            unplayed,
            activeOmamoris,
            activeAltarShikigami,
            runSession.getYakuProgression(),
            evaluatedVisualOrder
        ).orElse(null);

        if (bestYaku == null) {
            playerHand.removeAll(selectedCards);
            selectedCards.clear();

            if (activeAltarShikigami != null) {
                activeAltarShikigami.setExhausted(true);
                activeAltarShikigami = null;
            }

            checkRoundEndCondition();
            return;
        }

        currentState = GameState.SCORING_ANIMATION;

        // 1. Sichtbare Actors der ausgespielten Karten finden, Bindings Original/
        //    Evaluated/Actor aufbauen und die Actors in die Score-Buehne reparenten -
        //    BEVOR Karten aus playerHand entfernt oder Hand-Actors synchronisiert werden.
        buildScoreStage(visualOrder, playedCards, evaluatedCards, bestYaku);

        // 2. Erst jetzt die verbrauchten Karten aus der Hand nehmen und die verbleibende
        //    Hand synchronisieren. Die Buehnen-Actors liegen nicht mehr in handGroup und
        //    koennen von dealCardsToUI() nicht als veraltete Hand-Actors entfernt werden.
        playerHand.removeAll(selectedCards);
        selectedCards.clear();

        // 3. Echte Wertung: genau einmal fuer das gewaehlte Yaku, in derselben
        //    Reihenfolge, die anschliessend animiert wird.
        List<Card> scoringOrder = YakuSelector.orderMatchedCards(bestYaku.matchedCards(), evaluatedVisualOrder);
        ScoreContext context = new ScoreContext(
            hand, unplayed, bestYaku, activeOmamoris, activeAltarShikigami,
            runSession.getYakuProgression(), scoringOrder);
        CalculationBreakdown breakdown = ScoreCalculator.calculate(context);

        dealCardsToUI();
        updateLivePreview();

        playScoringSequence(breakdown, bestYaku);
    }

    /**
     * Sichtbare Karten-Actors in exakt der Reihenfolge von {@code playerHand}.
     * Jede physische Kopie wird genau einmal zugeordnet (kein Vertauschen bei
     * gleichen CardIDs); Karten ohne Actor liefern {@code null} an ihrer Position.
     */
    private List<JuicyCardActor> handActorsInHandOrder() {
        List<JuicyCardActor> available = new ArrayList<>();
        for (Actor child : handGroup.getChildren()) {
            if (child instanceof JuicyCardActor cardActor) {
                available.add(cardActor);
            }
        }

        List<JuicyCardActor> ordered = new ArrayList<>(playerHand.size());
        for (Card card : playerHand) {
            JuicyCardActor match = null;
            for (int i = 0; i < available.size(); i++) {
                JuicyCardActor candidate = available.get(i);
                if (candidate.card.equals(card)) {
                    match = candidate;
                    available.remove(i);
                    break;
                }
            }
            ordered.add(match);
        }
        return ordered;
    }

    /**
     * Sichtbare Links-nach-rechts-Reihenfolge der ausgespielten Karten
     * (Reihenfolge der Hand, gefiltert auf die Auswahl).
     */
    private List<Card> visualOrderOf(List<Card> playedCards) {
        List<Card> order = new ArrayList<>(playedCards.size());
        for (Card card : playerHand) {
            if (playedCards.contains(card)) {
                order.add(card);
            }
        }
        for (Card card : playedCards) {
            if (!order.contains(card)) {
                order.add(card);
            }
        }
        return order;
    }

    /**
     * Uebertraegt die Sichtreihenfolge auf die tatsaechlich ausgespielten Karten.
     * Ein Oni erzeugt neue Card-Records in indexgleicher Reihenfolge, daher wird
     * ueber den Index abgebildet.
     */
    private static List<Card> mapToEvaluatedOrder(List<Card> visualOrder, List<Card> playedCards, List<Card> evaluatedCards) {
        List<Card> mapped = new ArrayList<>(visualOrder.size());
        for (Card original : visualOrder) {
            int index = playedCards.indexOf(original);
            if (index >= 0 && index < evaluatedCards.size()) {
                mapped.add(evaluatedCards.get(index));
            }
        }
        if (mapped.isEmpty()) {
            mapped.addAll(evaluatedCards);
        }
        return mapped;
    }

    // ---------------------------------------------------------------------
    // Score-Buehne
    // ---------------------------------------------------------------------

    /**
     * Baut die Score-Buehne auf: findet die sichtbaren Actors der ausgespielten
     * Karten, erstellt die Bindings Original/Evaluated/Actor und verschiebt die
     * Actors mit erhaltener Position in die Buehne.
     *
     * <p>Laeuft VOR dem Entfernen der Karten aus {@code playerHand} und vor dem
     * Hand-Sync, damit kein Actor vorzeitig verschwindet.</p>
     */
    private void buildScoreStage(List<Card> playedInVisualOrder, List<Card> playedCards,
                                 List<Card> evaluatedCards, YakuResult bestYaku) {
        clearScoreStage();

        int count = playedInVisualOrder.size();
        if (count == 0) return;

        // Sichtbare Actors in playerHand-Reihenfolge: jede physische Kopie genau einmal.
        List<JuicyCardActor> handActors = handActorsInHandOrder();

        float spacing = scoreStageSpacing(count);
        float centerX = (SCORE_AREA_MIN_CENTER_X + SCORE_AREA_MAX_CENTER_X) / 2f;
        float startX = centerX - ((spacing * (count - 1)) / 2f);

        for (int i = 0; i < count; i++) {
            Card original = playedInVisualOrder.get(i);

            // Ausgewertete Karte ueber den Index der ausgespielten Karte (ein Oni
            // ersetzt Records indexgleich, die sichtbare Karte bleibt unveraendert).
            int playedIndex = playedCards.indexOf(original);
            Card evaluated = (playedIndex >= 0 && playedIndex < evaluatedCards.size())
                ? evaluatedCards.get(playedIndex)
                : original;

            int handIndex = playerHand.indexOf(original);
            JuicyCardActor visible = (handIndex >= 0 && handIndex < handActors.size())
                ? handActors.get(handIndex)
                : null;

            JuicyCardActor actor = takeCardActorForScoreStage(visible);
            if (actor == null) {
                Gdx.app.error("GameScreen", "No visible card actor for '" + original.name()
                    + "': scoring continues, the animation for this card is skipped.");
                continue;
            }

            boolean scored = bestYaku != null && bestYaku.matchedCards().contains(evaluated);

            Label note = null;
            if (scored) {
                actor.setColor(Color.WHITE);
            } else {
                // Karten ausserhalb des Yakus bleiben sichtbar, aber abgedunkelt.
                actor.setColor(SCORE_UNMATCHED_TINT);
                note = new Label("No Yaku contribution", skin);
                note.setAlignment(Align.center);
                note.setFontScale(0.62f);
                note.setColor(SCORE_IDLE_TEXT_COLOR);
            }

            Label contribution = new Label("", skin);
            contribution.setAlignment(Align.center);
            contribution.setFontScale(0.75f);
            contribution.setColor(SCORE_CHIP_COLOR);

            scoreBindings.add(new ScoringCardBinding(original, evaluated, actor, contribution, note,
                scored, i, startX + (i * spacing), 0f));
        }

        renderScoreStageDecorations();
    }

    /**
     * Verschiebt einen Actor aus der Hand in die Score-Buehne. Die sichtbare Position
     * wird vorher in Stage-Koordinaten gesichert und nach dem Reparenting in lokale
     * Koordinaten der Buehne umgerechnet - dadurch springt die Karte nicht und
     * behaelt Scale, Rotation, Farbe und Hanko-Badge.
     */
    private JuicyCardActor takeCardActorForScoreStage(JuicyCardActor actor) {
        if (actor == null) {
            return null;
        }

        Vector2 stagePos = actor.localToStageCoordinates(new Vector2(0f, 0f));
        actor.clearActions();
        actor.remove();
        scoreArea.addActor(actor);

        Vector2 local = scoreArea.stageToLocalCoordinates(stagePos);
        actor.setPosition(local.x, local.y);

        // Waehrend der Scoresequenz sind die gespielten Karten nicht interaktiv.
        actor.setTouchable(Touchable.disabled);
        return actor;
    }

    /** Abstand der Kartenreihe: bei vielen Karten enger, aber nie unlesbar. */
    private static float scoreStageSpacing(int count) {
        if (count <= 1) {
            return SCORE_CARD_MAX_SPACING;
        }
        float usable = SCORE_AREA_MAX_CENTER_X - SCORE_AREA_MIN_CENTER_X;
        float spacing = usable / (count - 1);
        return Math.max(SCORE_CARD_MIN_SPACING, Math.min(SCORE_CARD_MAX_SPACING, spacing));
    }

    /** Beitragstexte ueber und Hinweise unter den Karten der Buehne. */
    private void renderScoreStageDecorations() {
        for (ScoringCardBinding binding : scoreBindings) {
            scoreArea.addActor(binding.contribution);
            positionCardContribution(binding);

            if (binding.note != null) {
                binding.note.pack();
                binding.note.setPosition(
                    binding.x + (CARD_WIDTH / 2f) - (binding.note.getWidth() / 2f),
                    binding.y - SCORE_NOTE_GAP);
                scoreArea.addActor(binding.note);
            }
        }
    }

    /** Bringt alle Karten der Buehne in ihre vorgegebenen Slots. */
    private void alignScoreStage() {
        for (ScoringCardBinding binding : scoreBindings) {
            binding.actor.clearActions();
            binding.actor.targetX = binding.x;
            binding.actor.targetY = binding.y;
            binding.actor.targetRot = 0f;
            binding.actor.targetScale = 1f;
        }
    }

    /**
     * Leert die Buehne vollstaendig: Karten-Actors (inklusive laufender Actions),
     * Beitragstexte, Hinweise und alle Bindings. Diese Methode ist der einzige
     * Cleanup-Pfad und wird nach der Sequenz, bei Kampfstart und beim Dispose
     * aufgerufen; die Karten selbst sind zu diesem Zeitpunkt bereits verbraucht.
     */
    private void clearScoreStage() {
        scoreBindings.clear();

        if (scoreArea != null) {
            for (Actor child : scoreArea.getChildren()) {
                child.clearActions();
            }
            scoreArea.clearChildren();
        }
    }

    /** Textbaustein eines Karteneintrags ueber der Karte positionieren. */
    private void positionCardContribution(ScoringCardBinding binding) {
        binding.contribution.pack();
        binding.contribution.setPosition(
            binding.x + (CARD_WIDTH / 2f) - (binding.contribution.getWidth() / 2f),
            binding.y + CARD_HEIGHT + SCORE_CONTRIBUTION_GAP);
    }

    /**
     * Bindung zu einem Scoring-Event: zuerst ueber Objektidentitaet (mutierte
     * Karten eines Oni), danach ueber Wertgleichheit. Es wird nie ein Text
     * ausgewertet und nie ein gleichartiger Actor "geraten".
     */
    private ScoringCardBinding bindingForEvent(Card eventCard) {
        if (eventCard == null) {
            return null;
        }

        for (ScoringCardBinding binding : scoreBindings) {
            if (binding.evaluatedCard == eventCard || binding.originalCard == eventCard) {
                return binding;
            }
        }
        for (ScoringCardBinding binding : scoreBindings) {
            if (eventCard.equals(binding.evaluatedCard) || eventCard.equals(binding.originalCard)) {
                return binding;
            }
        }
        return null;
    }

    /**
     * Haengt einen Beitragstext ueber der Karte an (Kartenbasis, danach Hanko).
     * Mehrere Beitraege derselben Karte stehen untereinander; ganze Zahlen ohne
     * Nachkommastelle, sonst maximal eine (ScoreFormat).
     */
    private void appendCardContribution(ScoringCardBinding binding, String line, Color color) {
        if (binding == null || line == null || line.isEmpty()) return;

        String current = binding.contribution.getText().toString();
        binding.contribution.setColor(color);
        binding.contribution.setText(current.isEmpty() ? line : current + "\n" + line);
        positionCardContribution(binding);
    }

    /**
     * Kurzes kontrolliertes Wackeln der gewerteten Karte (links-rechts, leichter
     * vertikaler Bounce, Scale-Punch). Danach werden Position, Rotation und Scale
     * des Slots zuverlaessig wiederhergestellt.
     */
    private void wobbleScoreCard(ScoringCardBinding binding) {
        JuicyCardActor actor = binding.actor;
        actor.clearActions();
        actor.addAction(Actions.sequence(
            Actions.run(() -> {
                actor.targetX = binding.x - CARD_WOBBLE_X;
                actor.targetY = binding.y + CARD_WOBBLE_Y;
                actor.targetScale = CARD_WOBBLE_SCALE;
            }),
            Actions.delay(CARD_WOBBLE_STEP),
            Actions.run(() -> actor.targetX = binding.x + CARD_WOBBLE_X),
            Actions.delay(CARD_WOBBLE_STEP),
            Actions.run(() -> {
                actor.targetX = binding.x;
                actor.targetY = binding.y;
                actor.targetRot = 0f;
                actor.targetScale = 1f;
            })
        ));
    }

    // ---------------------------------------------------------------------
    // Beitragsanzeigen der Omamori
    // ---------------------------------------------------------------------

    /**
     * Erzeugt fuer jeden aktiven Omamori ein Beitragslabel unter dem Slot.
     * Inaktive Omamori und leere Slots erhalten keine Anzeige.
     */
    private void prepareOmamoriContributions() {
        clearOmamoriContributions();

        for (Omamori omamori : activeOmamoris) {
            JuicyOmamoriActor actor = findOmamoriActor(omamori);
            if (actor == null) {
                Gdx.app.error("GameScreen", "No Omamori actor for the contribution display: " + omamori.getName());
                continue;
            }

            Label label = new Label("", skin);
            label.setAlignment(Align.center);
            label.setFontScale(0.62f);
            label.setColor(SCORE_IDLE_TEXT_COLOR);
            omamoriContributionLayer.addActor(label);
            omamoriContributions.put(omamori, label);
            positionOmamoriContribution(actor, label);
        }
    }

    private void positionOmamoriContribution(JuicyOmamoriActor actor, Label label) {
        label.pack();
        Vector2 stagePos = actor.localToStageCoordinates(new Vector2(0f, 0f));
        label.setPosition(
            stagePos.x + (actor.getWidth() / 2f) - (label.getWidth() / 2f),
            stagePos.y - label.getHeight() - 2f);
    }

    /** Beitrag eines Omamori unter dessen Slot anhaengen. */
    private void appendOmamoriContribution(ScoringEvent event, String line) {
        if (line == null || line.isEmpty()) return;

        Label label = omamoriContributions.get(event.omamori());
        if (label == null) {
            Gdx.app.error("GameScreen", "No contribution display for Omamori event '" + event.sourceName() + "'.");
            return;
        }

        String current = label.getText().toString();
        label.setColor(SCORE_OMAMORI_COLOR);
        label.setText(current.isEmpty() ? line : current + "\n" + line);

        JuicyOmamoriActor actor = findOmamoriActor(event.omamori());
        if (actor != null) {
            positionOmamoriContribution(actor, label);
        } else {
            label.pack();
        }
    }

    /** Aktive Omamori ohne Effekt dezent als "No effect" kennzeichnen (ohne Pause). */
    private void markOmamoriWithoutEffect() {
        for (Map.Entry<Omamori, Label> entry : omamoriContributions.entrySet()) {
            Label label = entry.getValue();
            if (label.getText().length() == 0) {
                label.setColor(SCORE_IDLE_TEXT_COLOR);
                label.setText("No effect");
                JuicyOmamoriActor actor = findOmamoriActor(entry.getKey());
                if (actor != null) {
                    positionOmamoriContribution(actor, label);
                } else {
                    label.pack();
                }
            }
        }
    }

    private void clearOmamoriContributions() {
        omamoriContributions.clear();
        if (omamoriContributionLayer != null) {
            omamoriContributionLayer.clearChildren();
        }
    }

    private boolean isCardDiscardable(Card card) {
        return card != null && card.effect() != HankoEffect.STONE_SEAL;
    }

    private List<Card> getDiscardableSelectedCards() {
        List<Card> discardable = new ArrayList<>();
        for (Card c : selectedCards) {
            if (isCardDiscardable(c)) {
                discardable.add(c);
            }
        }
        return discardable;
    }

    private void updateLivePreview() {
        List<Card> previewHand = selectedCards;
        if (activeAltarShikigami instanceof Oni oni) {
            previewHand = oni.mutateHand(selectedCards, runSession.getCurrentSeason());
        }

        List<Card> unplayed = new ArrayList<>(playerHand);
        unplayed.removeAll(selectedCards);

        // Die Vorschau zeigt dasselbe Yaku an, das die echte Wertung waehlen wuerde.
        List<Card> selectedInHandOrder = new ArrayList<>();
        for (Card card : playerHand) {
            if (selectedCards.contains(card)) {
                selectedInHandOrder.add(card);
            }
        }
        List<Card> previewVisualOrder = mapToEvaluatedOrder(selectedInHandOrder, selectedCards, previewHand);

        // Sichtreihenfolge ist auch in der Vorschau die autoritative Auswahlreihenfolge.
        HandContext hand = new HandContext(previewVisualOrder);

        YakuResult bestYaku = YakuSelector.findBestByRealScore(
            previewVisualOrder,
            unplayed,
            activeOmamoris,
            activeAltarShikigami,
            runSession.getYakuProgression(),
            previewVisualOrder
        ).orElse(null);

        // Preview-Kontext mit übergebener YakuProgression für exakte Level-Werte
        ScoreContext previewCtx = ScoreContext.preview(hand, unplayed, bestYaku, activeAltarShikigami, runSession.getYakuProgression());

        chipsLabel.setText(ScoreFormat.format(previewCtx.getYakuBaseChips()));
        multLabel.setText(ScoreFormat.format(previewCtx.getYakuBaseMult()));

        if (selectedCards.isEmpty()) yakuNameLabel.setText("");
        else if (bestYaku != null) yakuNameLabel.setText(bestYaku.getDisplayName());
        else yakuNameLabel.setText("");

        boolean canDiscard = discardsRemaining > 0 && !getDiscardableSelectedCards().isEmpty();
        if (!canDiscard) discardButton.getColor().a = 0.5f;
        else discardButton.getColor().a = 1.0f;

        if (selectedCards.isEmpty() || handsRemaining <= 0) playButton.getColor().a = 0.5f;
        else playButton.getColor().a = 1.0f;
    }

    private void onDiscardClicked() {
        if (!isHandInteractionAllowed() || discardsRemaining <= 0) return;
        List<Card> cardsToDiscard = getDiscardableSelectedCards();
        if (cardsToDiscard.isEmpty()) return;

        discardsRemaining--;
        discardLabel.setText("Discards: " + discardsRemaining);
        playerHand.removeAll(cardsToDiscard);
        selectedCards.clear();
        drawCardsToHand(MAX_HAND_SIZE);
        dealCardsToUI();
        updateLivePreview();
    }

    private void checkRoundEndCondition() {
        // Anzeige und Zielvergleich nutzen dieselbe Rundungsregel (ScoreFormat).
        if (ScoreFormat.reachesTarget(currentRoundScore, currentTargetScore)) {
            currentState = GameState.ROUND_END;

            int totalXpReward = 50;
            if (!usedShikigamiInBattle.isEmpty()) {
                int xpPerShikigami = totalXpReward / usedShikigamiInBattle.size();
                for (Shikigami y : usedShikigamiInBattle) {
                    y.addXp(xpPerShikigami);
                }
            }

            int voidDustEarned = 10 + (handsRemaining * 5) + (discardsRemaining * 2);
            runSession.addVoidDust(voidDustEarned);

            // Mon bleibt eine Ganzzahl-Waehrung: die Abrundung passiert ausschliesslich
            // an dieser Systemgrenze, nie innerhalb der Scoreberechnung.
            int earnedMon = (int) Math.min((double) Integer.MAX_VALUE, Math.floor(currentRoundScore / 100.0));
            runSession.addMon(earnedMon);

            int completedStage = runSession.getLocationEncounterStage();
            runSession.advanceEncounterStage();

            // Begegnung genau einmal abschliessen und die Rekrutierungsbelohnung genau
            // einmal vergeben. Der Guard deckt doppelte Victory-Callbacks ab; zusaetzlich
            // schuetzt RunSession.grantShikigamiReward gegen Duplikate derselben Spezies.
            String rewardLine = null;
            if (!encounterResolved) {
                encounterResolved = true;
                runSession.markEncounterDefeated(encounter.getId());
                rewardLine = rewardMessage(runSession.grantShikigamiReward(encounter));
            }

            String headline = (completedStage == RunSession.ENCOUNTERS_PER_LOCATION)
                ? "This location has been purified."
                : "VICTORY!";
            yakuNameLabel.setText(rewardLine == null ? headline : headline + "\n" + rewardLine);

            // Siegreicher Kampf: kein Zwischenscreen, sondern sichere, verzoegerte Rueckkehr
            // in die Overworld. Die kurze Pause macht Sieg und Belohnung lesbar; danach wird
            // dieser Screen vom Screen-Manager genau einmal freigegeben.
            stage.addAction(Actions.sequence(
                Actions.delay(VICTORY_RETURN_DELAY),
                Actions.run(() -> ((KoiKoiGame) Gdx.app.getApplicationListener())
                    .changeScreen(new OverworldScreen(runSession, assets)))
            ));

        } else if (handsRemaining <= 0) {
            currentState = GameState.ROUND_END;
            // Niederlage: kein Encounter-Abschluss, keine Rekrutierung, keine Belohnung.
            encounterResolved = true;
            yakuNameLabel.setText("DEFEAT...");
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;

            stage.addAction(Actions.sequence(
                Actions.delay(1.5f),
                Actions.run(() -> {
                    ((KoiKoiGame) Gdx.app.getApplicationListener())
                        .changeScreen(new OverworldScreen(runSession, assets));
                })
            ));
        } else {
            drawCardsToHand(MAX_HAND_SIZE);
            resetUiForNextTurn();
        }
    }

    /**
     * Kurze englische Ergebniszeile der Rekrutierung.
     *
     * <p>Sie zeigt auch den Ersatzpfad: bereits besessene Spezies (Beutel oder Reserve)
     * erzeugen bewusst kein Duplikat - der Encounter gilt trotzdem als abgeschlossen und
     * der Spieler bekommt eine klare Meldung statt einer stillen Nichtvergabe.</p>
     */
    private String rewardMessage(ShikigamiReward reward) {
        if (reward == null) {
            return null;
        }

        String speciesName = encounter.getSpecies().getDisplayName();
        if (reward.outcome() == ShikigamiReward.Outcome.ALREADY_RECRUITED) {
            return speciesName + " already recruited.";
        }
        if (reward.outcome() == ShikigamiReward.Outcome.REWARD_UNAVAILABLE) {
            return speciesName + " reward is not implemented yet.";
        }

        Shikigami gained = reward.shikigami();
        if (reward.outcome() == ShikigamiReward.Outcome.ADDED_TO_RESERVE) {
            return gained.getName() + " sent to reserve (bag full).";
        }
        return gained.getName() + " joined your bag.";
    }

    private void dealCardsToUI() {
        // Bestehende Actor-Instanzen bleiben erhalten (Auswahlzustand, Hanko-Badges,
        // Drag-Zustand); nur neue Karten erhalten einen neuen Actor.
        Map<Card, JuicyCardActor> actorsByCard = new HashMap<>();
        for (Actor child : handGroup.getChildren()) {
            if (child instanceof JuicyCardActor existing) {
                actorsByCard.put(existing.card, existing);
            }
        }

        for (JuicyCardActor existing : new ArrayList<>(actorsByCard.values())) {
            if (!playerHand.contains(existing.card)) {
                existing.remove();
                actorsByCard.remove(existing.card);
            }
        }

        for (Card card : playerHand) {
            if (!actorsByCard.containsKey(card)) {
                JuicyCardActor actor = createCardActor(card);
                if (actor != null) {
                    handGroup.addActor(actor);
                    actorsByCard.put(card, actor);
                }
            }
        }

        // Setzt die Zielslots anhand der (ggf. geaenderten) Handreihenfolge; neue
        // Karten gleiten so direkt in ihren vorgesehenen Slot.
        updateCardArcTargets();
    }

    /**
     * Erzeugt den Actor einer neu gezogenen Karte. Die Startposition liegt am
     * spaeteren Slot (leicht darunter), damit die Karte direkt dorthin gleitet
     * und nicht erst rechts angehaengt und anschliessend versetzt wird.
     */
    private JuicyCardActor createCardActor(Card card) {
        TextureRegionDrawable cardImage = getCardImage(card);
        if (cardImage == null) {
            return null;
        }

        JuicyCardActor juicyCard = new JuicyCardActor(card, cardImage.getRegion(), getHankoBadgeRegion(card), skin.getRegion("white"), new JuicyCardActor.CardListener() {
            @Override
            public void onTap(JuicyCardActor actor) {
                // Waehrend der Quellauswahl waehlt ein Klick genau diese Karteninstanz.
                if (activationState == AbilityActivationState.SELECTING_SOURCE_CARD) {
                    selectSourceCardForAbility(actor, actor.card);
                    return;
                }
                if (!isHandInteractionAllowed()) return;

                if (selectedCards.contains(card)) {
                    selectedCards.remove(card);
                } else {
                    if (selectedCards.size() < MAX_SELECTED_CARDS) {
                        selectedCards.add(card);
                    }
                }
                updateLivePreview();
                updateCardArcTargets();
            }

            @Override
            public void onDrag(JuicyCardActor actor, Vector2 stagePos) {
                if (!isHandInteractionAllowed()) return;

                // Manuelles Verschieben bleibt jederzeit moeglich - auch nach der
                // Wahl eines Sortiermodus. Die Reihenfolge der Hand folgt der
                // aktuellen Zieh-Position; der gespeicherte Einfuegemodus fuer
                // neu gezogene Karten bleibt davon unberuehrt.
                List<Card> newOrder = new ArrayList<>(playerHand);
                newOrder.sort((c1, c2) -> {
                    JuicyCardActor a1 = findActorForCard(c1);
                    JuicyCardActor a2 = findActorForCard(c2);
                    float x1 = (a1 != null && a1.isDragging()) ? a1.targetX : (a1 != null ? a1.baseX : 0);
                    float x2 = (a2 != null && a2.isDragging()) ? a2.targetX : (a2 != null ? a2.baseX : 0);
                    return Float.compare(x1, x2);
                });

                if (!playerHand.equals(newOrder)) {
                    playerHand.clear();
                    playerHand.addAll(newOrder);
                    updateCardArcTargets();
                }
            }

            @Override
            public void onDrop(JuicyCardActor actor, Vector2 stagePos) {
                if (!isHandInteractionAllowed()) return;

                // Die manuell gewaehlte Position bleibt bestehen: kein erneutes
                // Sortieren der bereits vorhandenen Karten.
                updateLivePreview();
                updateCardArcTargets();
            }
        });

        int index = playerHand.indexOf(card);
        int count = Math.max(1, playerHand.size());
        juicyCard.setX(handArcX(index, count));
        juicyCard.setY(handArcY(index, count) - 70f);
        juicyCard.setRotation(handArcRotation(index, count) * 0.5f);

        return juicyCard;
    }

    /**
     * Sucht den Actor einer Karte in der Hand. Wird ausschliesslich fuer
     * Handkarten (Drag/Arc) verwendet; ausgespielte Karten werden waehrend der
     * Scoresequenz ueber ihre {@link ScoringCardBinding} zugeordnet.
     */
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

        for (int i = 0; i < cardCount; i++) {
            Card card = playerHand.get(i);
            JuicyCardActor actor = findActorForCard(card);

            if (actor != null) {
                boolean isSelected = selectedCards.contains(card);
                actor.updateArc(handArcX(i, cardCount), handArcY(i, cardCount), handArcRotation(i, cardCount), isSelected);
                actor.setZIndex(i);

                if (actor.isDragging()) {
                    actor.toFront();
                }
            }
        }
    }

    /** X-Basisposition des Slots {@code index} im Handbogen (Parent-Koordinaten). */
    private static float handArcX(int index, int cardCount) {
        return ((index - ((cardCount - 1) / 2f)) * CARD_SPACING) - (CARD_WIDTH / 2f);
    }

    /** Y-Basisposition des Slots {@code index} im Handbogen (Parent-Koordinaten). */
    private static float handArcY(int index, int cardCount) {
        float distFromCenter = index - ((cardCount - 1) / 2f);
        return -(Math.abs(distFromCenter) * Math.abs(distFromCenter) * CARD_DROP_PER_CARD);
    }

    /** Basisrotation des Slots {@code index} im Handbogen (Grad). */
    private static float handArcRotation(int index, int cardCount) {
        return -(index - ((cardCount - 1) / 2f)) * CARD_ROTATION_PER_CARD;
    }

    private void renderShikigamiUI() {
        altarTable.clearChildren();
        ShikigamiBagTable.clearChildren();

        if (activeAltarShikigami != null) {
            final Shikigami altarShikigami = activeAltarShikigami;
            TextureRegion ShikigamiRegion = atlas.findRegion(altarShikigami.getAtlasRegionName());
            if (ShikigamiRegion != null) {
                JuicyShikigamiActor actor = new JuicyShikigamiActor(altarShikigami, ShikigamiRegion, true, skin, new JuicyShikigamiActor.ShikigamiListener() {
                    @Override public void onTap(JuicyShikigamiActor a) {
                        // Waehrend der Wertung bleibt der Altar unveraendert.
                        if (!isHandInteractionAllowed()) return;

                        // Klick ist der zugaengliche Fallback fuer das Infopanel.
                        toggleShikigamiInfo(altarShikigami, a);

                        activeAltarShikigami = null;
                        renderShikigamiUI();
                        updateLivePreview();
                    }
                    @Override public void onDrop(JuicyShikigamiActor a, Vector2 stagePos) {
                        if (!isHandInteractionAllowed()) return;

                        Vector2 bagPos = ShikigamiBagTable.localToStageCoordinates(new Vector2(0, 0));
                        if (stagePos.x >= bagPos.x) {
                            activeAltarShikigami = null;
                            renderShikigamiUI();
                            updateLivePreview();
                        }
                    }
                });
                addShikigamiHoverInfo(actor, altarShikigami);
                altarTable.add(actor).width(108).height(192);
            } else {
                Gdx.app.error("GameScreen", "Shikigami atlas region missing: "
                    + altarShikigami.getAtlasRegionName() + " in " + GameAssets.GAME_ATLAS
                    + " (using text fallback).");
                TextButton fallback = new TextButton(altarShikigami.getName() + "\n(In Altar)", skin);
                addShikigamiHoverInfo(fallback, altarShikigami);
                addShikigamiClickInfo(fallback, altarShikigami);
                altarTable.add(fallback).width(108).height(192);
            }
        } else {
            Label emptyLabel = new Label("Altar\n(Empty)", skin);
            emptyLabel.setAlignment(Align.center);
            altarTable.add(emptyLabel).width(108).height(192);
        }

        if (runSession.getShikigamiBag() != null) {
            for (Shikigami shikigami : runSession.getShikigamiBag()) {
                if (shikigami == activeAltarShikigami) continue;

                TextureRegion ShikigamiRegion = atlas.findRegion(shikigami.getAtlasRegionName());
                if (ShikigamiRegion != null) {
                    JuicyShikigamiActor actor = new JuicyShikigamiActor(shikigami, ShikigamiRegion, false, skin, new JuicyShikigamiActor.ShikigamiListener() {
                        @Override public void onTap(JuicyShikigamiActor a) {
                            // Waehrend der Wertung bleibt die Begleiterauswahl gesperrt;
                            // waehrend eines laufenden Faehigkeitsvorgangs ebenfalls.
                            if (!isHandInteractionAllowed()) return;

                            // Klick ist der zugaengliche Fallback fuer das Infopanel; das
                            // bestehende Bag-zu-Altar-Verhalten bleibt unveraendert.
                            toggleShikigamiInfo(shikigami, a);

                            if (!shikigami.isExhausted() && activeAltarShikigami == null) {
                                placeShikigamiFromBag(shikigami);
                            }
                        }
                        @Override public void onDrop(JuicyShikigamiActor a, Vector2 stagePos) {
                            if (!isHandInteractionAllowed()) return;

                            Vector2 altarPos = altarTable.localToStageCoordinates(new Vector2(0, 0));
                            boolean droppedOnAltar = stagePos.x < altarPos.x + altarTable.getWidth();

                            if (droppedOnAltar) {
                                // Altar-Drop als Transaktion: der Begleiter wird nur vorgemerkt.
                                // In den Altar uebernommen wird er erst nach erfolgreichem
                                // Ability-Commit; die Bag-Reihenfolge bleibt bis dahin unveraendert.
                                placeShikigamiFromBag(shikigami);
                                return;
                            }

                            // Ablegen im Beutel: die bestehende Reihenfolge-Regel greift.
                            syncBagOrderWithAltar();
                            renderShikigamiUI();
                            updateLivePreview();
                        }
                    });
                    addShikigamiHoverInfo(actor, shikigami);
                    ShikigamiBagTable.add(actor).width(108).height(192).pad(4);
                } else {
                    Gdx.app.error("GameScreen", "Shikigami atlas region missing: "
                        + shikigami.getAtlasRegionName() + " in " + GameAssets.GAME_ATLAS
                        + " (using text fallback).");
                    String text = shikigami.getName() + (shikigami.isExhausted() ? "\n(Exhausted)" : "");
                    TextButton ShikigamiBtn = new TextButton(text, skin);
                    if (shikigami.isExhausted()) ShikigamiBtn.getColor().a = 0.5f;
                    addShikigamiHoverInfo(ShikigamiBtn, shikigami);
                    addShikigamiClickInfo(ShikigamiBtn, shikigami);
                    ShikigamiBagTable.add(ShikigamiBtn).width(108).height(192).pad(4);
                }
            }
        }
    }

    /**
     * Maus-Hover zeigt das kompakte Shikigami-Infopanel; der Verlassen-Event schliesst es
     * wieder, solange es nicht per Klick fixiert wurde. Waehrend der Wertungsanimation
     * ist das Panel komplett gesperrt.
     */
    private void addShikigamiHoverInfo(Actor actor, Shikigami shikigami) {
        if (actor == null || shikigami == null) {
            return;
        }
        actor.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                // pointer == -1 ist echte Mausbewegung; kein Doppel-Handling pro Beruehrung.
                if (pointer != -1 || !canShowInfoPopup()) {
                    return;
                }
                showShikigamiInfo(shikigami, actor, false);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                if (pointer != -1 || infoPinned) {
                    return;
                }
                if (infoPopupOwner == shikigami) {
                    hideInfoPopup();
                }
            }
        });
    }

    /** Klick-Fallback fuer Aktoren ohne eigenen Tap-Callback (z. B. Text-Fallbacks). */
    private void addShikigamiClickInfo(Actor actor, Shikigami shikigami) {
        if (actor == null || shikigami == null) {
            return;
        }
        actor.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!canShowInfoPopup()) {
                    return;
                }
                toggleShikigamiInfo(shikigami, actor);
            }
        });
    }

    /**
     * true, solange ein Faehigkeitsvorgang laeuft (Dialog, Kartenauswahl, Commit
     * oder dessen kurze Animation). Der Screen rendert und animiert dabei weiter;
     * alle normalen Eingaben (Play Hand, Discard, Sortieren, Karten-/Omamori-/
     * Shikigami-Drag, Infopanel) sind gesperrt.
     */
    private boolean isAbilityFlowActive() {
        return activationState != AbilityActivationState.IDLE;
    }

    /**
     * Einzige Freigaberegel der normalen Kampfbedienung: erwarteter Screen-Zustand,
     * kein laufendes Scoring und kein offener Faehigkeitsvorgang. Damit gibt es
     * keine zweite, konkurrierende Wahrheit fuer die Eingabesperre.
     */
    private boolean isHandInteractionAllowed() {
        return currentState == GameState.WAITING_FOR_INPUT
            && !scoringSequenceRunning
            && !isAbilityFlowActive();
    }

    /**
     * true, solange das Infopanel erlaubt ist (nie waehrend der Wertungsanimation
     * und nie waehrend eines Faehigkeitsvorgangs).
     */
    private boolean canShowInfoPopup() {
        return isHandInteractionAllowed();
    }

    /** Klick: fixiert das Panel fuer dieses Shikigami oder schliesst es wieder. */
    private void toggleShikigamiInfo(Shikigami shikigami, Actor anchor) {
        if (shikigami == null || anchor == null) {
            return;
        }
        if (infoPinned && infoPopupOwner == shikigami) {
            hideInfoPopup();
            return;
        }
        showShikigamiInfo(shikigami, anchor, true);
    }

    /**
     * Befuellt das vorhandene Infopanel (Panel-Drawable aus GameAssets) mit den Werten
     * aus {@link ShikigamiInfo} - dieselben Texte wie im Inventar, nur kompakter. Es
     * entstehen ausschliesslich Labels, keine Texturen; lange Faehigkeitstexte brechen
     * innerhalb der Panelbreite um.
     */
    private void showShikigamiInfo(Shikigami shikigami, Actor anchor, boolean pinned) {
        if (shikigami == null || anchor == null || infoPopup == null) {
            return;
        }

        infoPopup.clearChildren();

        Label title = new Label(ShikigamiInfo.battleTitle(shikigami), skin);
        title.setFontScale(0.8f);
        title.setColor(SCORE_SHIKIGAMI_COLOR);
        infoPopup.add(title).left().pad(6f, 8f, 2f, 8f).row();

        Label summary = new Label(ShikigamiInfo.battleSummary(shikigami), skin);
        summary.setFontScale(0.68f);
        summary.setColor(SCORE_IDLE_TEXT_COLOR);
        infoPopup.add(summary).left().pad(0f, 8f, 2f, 8f).row();

        Label ability = new Label(ShikigamiInfo.abilityLine(shikigami), skin);
        ability.setWrap(true);
        ability.setFontScale(0.66f);
        ability.setColor(Color.WHITE);
        infoPopup.add(ability).width(INFO_POPUP_MAX_WIDTH).left().pad(0f, 8f, 4f, 8f).row();

        String reason = ShikigamiInfo.unusableReason(shikigami, false);
        if (reason != null) {
            Label why = new Label(reason, skin);
            why.setWrap(true);
            why.setFontScale(0.64f);
            why.setColor(SCORE_UNMATCHED_TINT);
            infoPopup.add(why).width(INFO_POPUP_MAX_WIDTH).left().pad(0f, 8f, 6f, 8f).row();
        }

        infoPopup.pack();
        positionInfoPopupAbove(anchor);
        infoPopup.setVisible(true);

        infoPinned = pinned;
        infoPopupOwner = shikigami;
    }

    /**
     * Positioniert das Infopanel oberhalb seines Actors und haelt es vollstaendig
     * innerhalb des Viewports.
     */
    private void positionInfoPopupAbove(Actor anchor) {
        infoAnchorPosition.set(0f, 0f);
        anchor.localToStageCoordinates(infoAnchorPosition);

        float x = infoAnchorPosition.x + (anchor.getWidth() / 2f) - (infoPopup.getWidth() / 2f);
        float y = infoAnchorPosition.y - infoPopup.getHeight() - INFO_POPUP_MARGIN;

        float maxX = Math.max(INFO_POPUP_MARGIN, WORLD_WIDTH - infoPopup.getWidth() - INFO_POPUP_MARGIN);
        float maxY = Math.max(INFO_POPUP_MARGIN, WORLD_HEIGHT - infoPopup.getHeight() - INFO_POPUP_MARGIN);

        infoPopup.setPosition(
            MathUtils.clamp(x, INFO_POPUP_MARGIN, maxX),
            MathUtils.clamp(y, INFO_POPUP_MARGIN, maxY));
    }

    /** Schliesst das Infopanel, leert es und gibt eine Fixierung frei. */
    private void hideInfoPopup() {
        if (infoPopup != null) {
            infoPopup.setVisible(false);
            infoPopup.clearChildren();
        }
        infoPinned = false;
        infoPopupOwner = null;
    }

    // =====================================================================
    // Faehigkeitsaktivierung am Altar (Kitsune "Fox Trick" & Co.)
    //
    // Der Screen ist hier ausschliesslich fuer UI-Zustand, Auswahl, Aufruf der
    // Model-Ability und den sicheren Commit zustaendig. Die Regeln selbst
    // (Timing, Auswahlart, Zufall, Kartentransformation) liegen im Modell
    // (model.shikigami.ability.*) und werden hier nicht nachgebaut.
    // =====================================================================

    /**
     * Ablegen eines Beutel-Begleiters auf dem Altar.
     *
     * <p>Begleiter ohne Altar-Faehigkeit (z. B. Oni) wandern wie bisher sofort in
     * den Altar. Ein Begleiter mit
     * {@link ShikigamiActivationTiming#ON_ALTAR_PLACED} wird dagegen nur
     * vorgemerkt: kein Altar-Eintrag, keine Erschoepfung, keine Animation und kein
     * Bag-Austrag, bevor die Faehigkeit erfolgreich angewendet wurde.</p>
     */
    private void placeShikigamiFromBag(Shikigami shikigami) {
        if (shikigami == null || shikigami.isExhausted() || activeAltarShikigami != null) {
            return;
        }

        // Kein instanceof und kein Namensvergleich: die Faehigkeit beschreibt sich selbst.
        ShikigamiAbility ability = shikigami.getAbility();
        if (ability == null || ability.getTiming() != ShikigamiActivationTiming.ON_ALTAR_PLACED) {
            activeAltarShikigami = shikigami;
            usedShikigamiInBattle.add(shikigami);
            syncBagOrderWithAltar();
            renderShikigamiUI();
            updateLivePreview();
            return;
        }

        if (abilityResolvedShikigami.contains(shikigami)) {
            showAbilityDialogMessage(ability.getAbilityName(),
                ability.getAbilityName() + " was already used in this battle.");
            return;
        }

        beginAbilityActivation(shikigami, ability);
    }

    /**
     * Uebernimmt die bestehende Bag-Reihenfolge-Regel: der Altar-Begleiter steht
     * zuerst, danach folgen die Beutel-Actors in ihrer sichtbaren Reihenfolge und
     * zuletzt alles, was noch in keinem Actor sichtbar ist.
     */
    private void syncBagOrderWithAltar() {
        List<JuicyShikigamiActor> actors = new ArrayList<>();
        for (Actor child : ShikigamiBagTable.getChildren()) {
            if (child instanceof JuicyShikigamiActor) actors.add((JuicyShikigamiActor) child);
        }
        actors.sort(Comparator.comparing(act -> act.localToStageCoordinates(new Vector2(0, 0)).x));

        List<Shikigami> oldBag = new ArrayList<>(runSession.getShikigamiBag());
        runSession.getShikigamiBag().clear();
        if (activeAltarShikigami != null) runSession.getShikigamiBag().add(activeAltarShikigami);

        for (JuicyShikigamiActor act : actors) {
            if (!runSession.getShikigamiBag().contains(act.shikigami)) runSession.getShikigamiBag().add(act.shikigami);
        }
        for (Shikigami i : oldBag) {
            if (!runSession.getShikigamiBag().contains(i)) runSession.getShikigamiBag().add(i);
        }
    }

    /**
     * Startet den abbrechbaren Aktivierungsvorgang und oeffnet den zum
     * {@link ShikigamiSelectionMode} passenden Schritt.
     */
    private void beginAbilityActivation(Shikigami shikigami, ShikigamiAbility ability) {
        if (isAbilityFlowActive()) {
            return; // Es laeuft bereits genau ein Dialog.
        }

        pendingActivationShikigami = shikigami;
        pendingActivationAbility = ability;
        pendingSourceCard = null;
        pendingSourceIndex = -1;
        pendingTargetTemplate = null;
        hideInfoPopup();

        if (playerHand.isEmpty()) {
            // Leere Hand: der Vorgang endet sofort ohne jede Aenderung.
            clearPendingActivation();
            showAbilityDialogMessage(ability.getAbilityName(),
                ShikigamiAbilityResult.emptyHand(ability.getAbilityName()).message());
            return;
        }

        buildAbilityModal();
        if (ability.getSelectionMode().requiresHandCard()) {
            showAbilitySourceStep();
        } else {
            showAbilityConfirmationStep();
        }
    }

    /** Baut (oder ersetzt) das einzige Modal der Aktivierung. */
    private void buildAbilityModal() {
        closeAbilityModal();

        abilityOverlay = new Table();
        abilityOverlay.setFillParent(true);
        // Abdunklung ohne neue Textur: White-Drawable der gemeinsamen Skin.
        abilityOverlay.setBackground(skin.newDrawable("white", new Color(0f, 0f, 0f, ABILITY_DIM_ALPHA)));
        // Das Overlay liegt ueber der Kampf-UI und blockiert deren Eingaben.
        abilityOverlay.setTouchable(Touchable.enabled);

        abilityPanel = new Table();
        abilityPanel.setBackground(panelBackground);
        abilityPanel.pad(22f);

        abilityOverlay.add(abilityPanel).width(ABILITY_PANEL_WIDTH).pad(ABILITY_PANEL_MARGIN);
        stage.addActor(abilityOverlay);
    }

    /** Entfernt das Modal samt Inhalt restlos (keine liegenbleibenden Listener). */
    private void closeAbilityModal() {
        if (abilityOverlay != null) {
            abilityOverlay.clearChildren();
            abilityOverlay.remove();
            abilityOverlay = null;
        }
        abilityPanel = null;
        abilityTargetScroll = null;
        abilityTargetTiles.clear();
        abilitySelectedTargetTile = null;
        clearHandHighlights();
    }

    /** Loescht den Pending-Zustand vollstaendig (ohne den Zustandsautomaten zu setzen). */
    private void clearPendingActivation() {
        pendingActivationShikigami = null;
        pendingActivationAbility = null;
        pendingSourceCard = null;
        pendingSourceIndex = -1;
        pendingTargetTemplate = null;
    }

    /**
     * Bestaetigungsschritt: Level 1 (reine Rueckfrage) sowie die Endbestaetigung
     * nach der Kartenauswahl (Level 2/3).
     */
    private void showAbilityConfirmationStep() {
        if (abilityPanel == null || pendingActivationAbility == null) {
            return;
        }
        activationState = AbilityActivationState.PENDING_CONFIRMATION;
        abilityPanel.clearChildren();

        addAbilityTitle();
        addAbilityText(confirmationPromptText());

        Table buttons = new Table();
        if (pendingActivationAbility.getSelectionMode().requiresTargetCard()) {
            // Endbestaetigung von Level 3: zurueck zur Zielgalerie moeglich.
            buttons.add(abilityButton("Back", this::showAbilityTargetStep))
                .width(140f).height(46f).padRight(10f);
        }
        buttons.add(abilityButton("Cancel", this::onAbilityCancelClicked))
            .width(140f).height(46f).padRight(10f);
        buttons.add(abilityButton("Apply", this::onAbilityForwardClicked))
            .width(140f).height(46f);
        abilityPanel.add(buttons);
    }

    /**
     * Schritt 1: Quellkarte aus der aktuellen Hand (Level 2 und 3). Die Kacheln
     * stehen in der momentanen Handreihenfolge; ein Klick waehlt exakt die
     * dahinterliegende Karteninstanz (Identitaet, kein Namensvergleich).
     */
    private void showAbilitySourceStep() {
        if (abilityPanel == null || pendingActivationAbility == null) {
            return;
        }
        activationState = AbilityActivationState.SELECTING_SOURCE_CARD;
        pendingTargetTemplate = null;
        abilityPanel.clearChildren();

        addAbilityTitle();
        addAbilityText("Choose a card to transform.");

        Table grid = new Table();
        abilitySourceTiles.clear();
        abilitySelectedSourceTile = null;
        int column = 0;
        for (int i = 0; i < playerHand.size(); i++) {
            Card card = playerHand.get(i);
            Table tile = createAbilityCardTile(card);
            abilitySourceTiles.add(tile);
            if (card == pendingSourceCard) {
                abilitySelectedSourceTile = tile;
            }
            grid.add(tile).width(ABILITY_GALLERY_ENTRY_WIDTH).pad(4f);
            if (++column % ABILITY_GALLERY_COLUMNS == 0) {
                grid.row();
            }
        }
        if (abilitySelectedSourceTile != null) {
            abilitySelectedSourceTile.setColor(ABILITY_SELECTED_TINT);
        }
        abilityPanel.add(grid).padBottom(12f).row();

        abilityStepInfoLabel = new Label("", skin);
        abilityStepInfoLabel.setWrap(true);
        abilityStepInfoLabel.setAlignment(Align.center);
        abilityStepInfoLabel.setFontScale(0.62f);
        abilityStepInfoLabel.setColor(SCORE_IDLE_TEXT_COLOR);
        abilityPanel.add(abilityStepInfoLabel).width(ABILITY_PANEL_WIDTH - 60f).padBottom(12f).row();

        Table buttons = new Table();
        buttons.add(abilityButton("Cancel", this::onAbilityCancelClicked))
            .width(140f).height(46f).padRight(10f);
        abilityForwardButton = abilityButton(
            pendingActivationAbility.getSelectionMode().requiresTargetCard() ? "Next" : "Apply",
            this::onAbilityForwardClicked);
        buttons.add(abilityForwardButton).width(140f).height(46f);
        abilityPanel.add(buttons);

        refreshAbilityStepInfo();
        refreshAbilityForwardButton();
    }

    /** Titelzeile des Modals: der Name kommt aus der Ability, nicht aus einer Klasse. */
    private void addAbilityTitle() {
        Label title = new Label(pendingActivationAbility.getAbilityName(), skin);
        title.setFontScale(1.1f);
        abilityPanel.add(title).padBottom(8f).row();
    }

    /** Zentrierter, umbrechender englischer Text des Modals. */
    private void addAbilityText(String text) {
        Label label = new Label(text, skin);
        label.setWrap(true);
        label.setAlignment(Align.center);
        abilityPanel.add(label).width(ABILITY_PANEL_WIDTH - 60f).padBottom(12f).row();
    }

    /** Text des Bestaetigungsschritts (Modelltext oder gewaehltes Kartenpaar). */
    private String confirmationPromptText() {
        if (pendingActivationAbility == null) {
            return "";
        }
        if (pendingActivationAbility.getSelectionMode().requiresTargetCard()
            && pendingSourceCard != null && pendingTargetTemplate != null) {
            return "'" + pendingSourceCard.name() + "' becomes '"
                + DeckTemplates.baseNameOf(pendingTargetTemplate) + "'.";
        }
        if (pendingActivationAbility.getSelectionMode().requiresHandCard() && pendingSourceCard != null) {
            return "'" + pendingSourceCard.name()
                + "' transforms into a random different card.";
        }
        return pendingActivationAbility.getPromptText();
    }

    /** Button in der gemeinsamen Skin-Optik (keine neue Textur, kein neuer Stil). */
    private TextButton abilityButton(String label, Runnable action) {
        TextButton button = new TextButton(label, indieButtonStyle);
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                action.run();
            }
        });
        return button;
    }

    /**
     * Schritt 2 (Level 3): Zielmotiv aus dem vollstaendigen Deck waehlen.
     *
     * <p>Genau eine Kachel je {@link CardID} ({@link DeckTemplates#distinctByCardId()}):
     * 20 unterschiedliche Motive, ohne Kopiennummern. Das Motiv der Quellkarte ist
     * sichtbar gedaempft und nicht auswaehlbar. Ein Klick auf ein zulaessiges Motiv
     * fuehrt zur Endbestaetigung; damit ist "Apply erst bei gueltiger
     * Quellen-/Zielauswahl" strukturell garantiert.</p>
     */
    private void showAbilityTargetStep() {
        if (abilityPanel == null || pendingActivationAbility == null || pendingSourceCard == null) {
            return;
        }
        activationState = AbilityActivationState.SELECTING_TARGET_CARD;
        abilityPanel.clearChildren();

        addAbilityTitle();
        addAbilityText("Choose its new form.");

        Table gallery = new Table();
        gallery.top();
        abilityTargetTiles.clear();
        abilitySelectedTargetTile = null;
        int column = 0;
        for (Card template : DeckTemplates.distinctByCardId()) {
            boolean selectable = !CardTransformation.isSameCardId(pendingSourceCard, template);
            Table tile = createAbilityTargetTile(template, selectable);
            gallery.add(tile).width(ABILITY_GALLERY_ENTRY_WIDTH).pad(4f);
            if (++column % ABILITY_GALLERY_COLUMNS == 0) {
                gallery.row();
            }
        }

        // Vorhandene ScrollPane der Scene2D-Toolbox: keine neue Textur, keine
        // zusaetzliche Scrollbar-Grafik.
        abilityTargetScroll = new ScrollPane(gallery, new ScrollPane.ScrollPaneStyle());
        abilityTargetScroll.setFadeScrollBars(false);
        abilityTargetScroll.setScrollingDisabled(true, false);
        abilityTargetScroll.setOverscroll(false, false);
        abilityPanel.add(abilityTargetScroll)
            .width(ABILITY_PANEL_WIDTH - 60f).height(ABILITY_GALLERY_HEIGHT).padBottom(12f).row();

        abilityStepInfoLabel = new Label("", skin);
        abilityStepInfoLabel.setWrap(true);
        abilityStepInfoLabel.setAlignment(Align.center);
        abilityStepInfoLabel.setFontScale(0.62f);
        abilityStepInfoLabel.setColor(SCORE_IDLE_TEXT_COLOR);
        abilityPanel.add(abilityStepInfoLabel).width(ABILITY_PANEL_WIDTH - 60f).padBottom(12f).row();

        Table buttons = new Table();
        buttons.add(abilityButton("Back", this::onAbilityBackClicked))
            .width(140f).height(46f).padRight(10f);
        buttons.add(abilityButton("Cancel", this::onAbilityCancelClicked))
            .width(140f).height(46f);
        abilityPanel.add(buttons);

        refreshAbilityStepInfo();
    }

    /** Kachel einer Handkarte (Quellauswahl) inklusive Hanko-Abzeichen. */
    private Table createAbilityCardTile(Card card) {
        Table tile = new Table();
        tile.setBackground(panelBackground);

        TextureRegionDrawable cardImage = getCardImage(card);
        if (cardImage != null) {
            tile.add(new Image(cardImage)).size(54f, 96f).padTop(4f).row();
        } else {
            Gdx.app.error("GameScreen", "Card atlas region missing for '" + card.name()
                + "' (CardID " + card.id() + "): using a text placeholder in the ability dialog.");
            Label placeholder = new Label(card.id().name(), skin);
            placeholder.setWrap(true);
            placeholder.setAlignment(Align.center);
            placeholder.setFontScale(0.5f);
            tile.add(placeholder).size(54f, 96f).padTop(4f).row();
        }

        Label name = new Label(card.name(), skin);
        name.setWrap(true);
        name.setAlignment(Align.center);
        name.setFontScale(0.55f);
        tile.add(name).width(ABILITY_GALLERY_ENTRY_WIDTH - 8f).pad(2f).row();

        TextureRegion badge = getHankoBadgeRegion(card);
        if (badge != null) {
            tile.add(new Image(badge)).size(16f).padBottom(4f).row();
        }

        tile.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectSourceCardForAbility(tile, card);
            }
        });
        return tile;
    }

    /** Kachel eines Zielmotivs (Level 3) mit Season, Rang und Chips-Basiswert. */
    private Table createAbilityTargetTile(Card template, boolean selectable) {
        Table tile = new Table();
        tile.setBackground(panelBackground);

        TextureRegionDrawable cardImage = getCardImage(template);
        if (cardImage != null) {
            tile.add(new Image(cardImage)).size(44f, 78f).padTop(3f).row();
        } else {
            Gdx.app.error("GameScreen", "Card atlas region missing for '" + template.id().name()
                + "': using a text placeholder in the target gallery.");
            Label placeholder = new Label(DeckTemplates.baseNameOf(template), skin);
            placeholder.setWrap(true);
            placeholder.setAlignment(Align.center);
            placeholder.setFontScale(0.5f);
            tile.add(placeholder).size(44f, 78f).padTop(3f).row();
        }

        Label name = new Label(DeckTemplates.baseNameOf(template), skin);
        name.setWrap(true);
        name.setAlignment(Align.center);
        name.setFontScale(0.5f);
        tile.add(name).width(ABILITY_GALLERY_ENTRY_WIDTH - 8f).pad(2f).row();

        Label meta = new Label(template.season() + " | " + template.rank() + " | "
            + ScoreFormat.format(template.rank().getBaseValue()) + " chips", skin);
        meta.setFontScale(0.46f);
        meta.setColor(SCORE_IDLE_TEXT_COLOR);
        tile.add(meta).padBottom(3f).row();

        if (!selectable) {
            // Gleiche CardID wie die Quelle: sichtbar, aber nicht auswaehlbar.
            tile.setColor(ABILITY_DIMMED_TINT);
            return tile;
        }

        tile.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectTargetTemplateForAbility(tile, template);
            }
        });
        return tile;
    }

    /**
     * Quellkartenauswahl: speichert die <b>exakte Referenz</b> der Karteninstanz
     * und markiert Kachel und Handkarte. Nur im Auswahlschritt und nur fuer
     * Karten, die wirklich in der aktuellen Hand liegen (Identitaetsvergleich).
     */
    private void selectSourceCardForAbility(Actor tile, Card card) {
        if (activationState != AbilityActivationState.SELECTING_SOURCE_CARD || card == null) {
            return;
        }
        int index = identityIndexOf(playerHand, card);
        if (index < 0) {
            return; // Veraltete Auswahl: keine Aenderung, kein Commit.
        }

        pendingSourceCard = playerHand.get(index);
        pendingSourceIndex = index;

        for (Table candidate : abilitySourceTiles) {
            candidate.setColor(Color.WHITE);
        }
        if (tile instanceof Table table) {
            table.setColor(ABILITY_SELECTED_TINT);
            abilitySelectedSourceTile = table;
        }
        markSourceCardInHand(pendingSourceCard);
        refreshAbilityStepInfo();
        refreshAbilityForwardButton();
    }

    /** Zielmotivauswahl (Level 3); das Quellmotiv bleibt gesperrt. */
    private void selectTargetTemplateForAbility(Table tile, Card template) {
        if (activationState != AbilityActivationState.SELECTING_TARGET_CARD || template == null) {
            return;
        }
        if (CardTransformation.isSameCardId(pendingSourceCard, template)) {
            return;
        }

        pendingTargetTemplate = template;
        for (Table candidate : abilityTargetTiles) {
            candidate.setColor(Color.WHITE);
        }
        tile.setColor(ABILITY_SELECTED_TINT);
        abilitySelectedTargetTile = tile;
        refreshAbilityStepInfo();

        // Quell- und Zielkarte stehen fest: erst jetzt die Endbestaetigung zeigen.
        showAbilityConfirmationStep();
    }

    /** Hebt die zur Quelle gehoerende Handkarte hervor (Actor ueber Identitaet). */
    private void markSourceCardInHand(Card sourceCard) {
        clearHandHighlights();
        JuicyCardActor actor = findActorForCardIdentity(sourceCard);
        if (actor != null) {
            actor.setColor(ABILITY_SELECTED_TINT);
            abilityHighlightedCards.add(actor);
        }
    }

    /** Gibt alle Handkarten-Hervorhebungen wieder frei. */
    private void clearHandHighlights() {
        for (Actor actor : abilityHighlightedCards) {
            actor.setColor(Color.WHITE);
        }
        abilityHighlightedCards.clear();
    }

    /** Infozeile des aktuellen Schritts (englisch). */
    private void refreshAbilityStepInfo() {
        if (abilityStepInfoLabel == null) {
            return;
        }
        if (pendingSourceCard == null) {
            abilityStepInfoLabel.setText("No card selected yet.");
            return;
        }
        if (pendingActivationAbility != null
            && pendingActivationAbility.getSelectionMode().requiresTargetCard()) {
            String target = pendingTargetTemplate == null
                ? "no new form selected yet"
                : "'" + DeckTemplates.baseNameOf(pendingTargetTemplate) + "'";
            abilityStepInfoLabel.setText("Selected: '" + pendingSourceCard.name() + "' -> " + target);
            return;
        }
        abilityStepInfoLabel.setText("Selected: '" + pendingSourceCard.name() + "'");
    }

    /** Weiter-/Apply-Button bleibt sichtbar gesperrt, solange die Auswahl fehlt. */
    private void refreshAbilityForwardButton() {
        if (abilityForwardButton == null) {
            return;
        }
        boolean ready = canApplyAbility();
        abilityForwardButton.setDisabled(!ready);
        abilityForwardButton.getColor().a = ready ? 1f : 0.5f;
    }

    /** true, wenn die Auswahl den Auswahlbedarf der Faehigkeit erfuellt. */
    private boolean canApplyAbility() {
        ShikigamiAbility ability = pendingActivationAbility;
        if (ability == null || pendingActivationShikigami == null) {
            return false;
        }
        ShikigamiSelectionMode mode = ability.getSelectionMode();
        if (mode.requiresHandCard() && pendingSourceCard == null) {
            return false;
        }
        if (mode.requiresTargetCard() && pendingTargetTemplate == null) {
            return false;
        }
        return true;
    }

    /**
     * Weiter/Apply: bei Level 3 fuehrt der Quellschritt zunaechst zur Zielgalerie,
     * sonst wird die Faehigkeit genau einmal ausgewertet.
     */
    private void onAbilityForwardClicked() {
        if (pendingActivationAbility == null) {
            return;
        }
        if (activationState == AbilityActivationState.SELECTING_SOURCE_CARD
            && pendingActivationAbility.getSelectionMode().requiresTargetCard()) {
            if (pendingSourceCard == null) {
                return;
            }
            showAbilityTargetStep();
            return;
        }
        if (!canApplyAbility()) {
            return;
        }
        resolveAbilityOnce();
    }

    /** Back in Galerie/Endbestaetigung: zurueck zu Schritt 1 (ohne jede Mutation). */
    private void onAbilityBackClicked() {
        if (activationState == AbilityActivationState.SELECTING_TARGET_CARD
            || activationState == AbilityActivationState.PENDING_CONFIRMATION) {
            showAbilitySourceStep();
        }
    }

    /** Cancel/ESC: bricht den gesamten Vorgang ab, ohne irgendetwas zu veraendern. */
    private void onAbilityCancelClicked() {
        cancelAbilityActivation();
    }

    /**
     * Reine Namensabbildung der Kampfsaison auf die Karten-Season des Modells
     * ({@code GameSeason} und {@code Season} sind getrennte Enums).
     * {@code GameSeason.FINAL} hat kein Gegenstueck und liefert {@code null};
     * Fox Trick benoetigt die Saison nicht, sie ist nur Kontextinformation.
     */
    private Season currentModelSeason() {
        GameSeason season = runSession.getCurrentSeason();
        if (season == null) {
            return null;
        }
        for (Season candidate : Season.values()) {
            if (candidate.name().equals(season.name())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Wertet die Faehigkeit <b>genau einmal</b> aus und committet nur ein
     * {@code APPLIED}-Ergebnis.
     *
     * <p>{@link AbilityActivationState#RESOLVING} sperrt Doppelklicks: ein zweiter
     * Aufruf kehrt sofort zurueck, ebenso ein Aufruf nach dem Commit.</p>
     */
    private void resolveAbilityOnce() {
        if (pendingActivationAbility == null || pendingActivationShikigami == null) {
            return;
        }
        if (activationState == AbilityActivationState.RESOLVING
            || activationState == AbilityActivationState.RESOLVED) {
            return;
        }

        activationState = AbilityActivationState.RESOLVING;

        Shikigami shikigami = pendingActivationShikigami;
        ShikigamiAbility ability = pendingActivationAbility;
        String abilityName = ability.getAbilityName();

        ShikigamiAbilityContext context = ShikigamiAbilityContext.forAltarPlaced(
            playerHand, pendingSourceCard, pendingTargetTemplate, currentModelSeason());
        ShikigamiAbilityResult result = ability.resolve(context);

        if (!result.isApplied()) {
            // Ablehnung: keine Mutation, keine Erschoepfung, keine Animation.
            closeAbilityModal();
            clearPendingActivation();
            updateLivePreview();
            activationState = AbilityActivationState.IDLE;
            showAbilityDialogMessage(abilityName, result.message());
            return;
        }

        Card sourceCard = result.sourceCard();
        Card replacementCard = result.replacementCard();
        int index = identityIndexOf(playerHand, sourceCard);
        if (index < 0 || replacementCard == null) {
            Gdx.app.error("GameScreen", "Ability commit aborted: the source card reference is no "
                + "longer part of the hand. No card was changed.");
            closeAbilityModal();
            clearPendingActivation();
            updateLivePreview();
            activationState = AbilityActivationState.IDLE;
            showAbilityDialogMessage(abilityName,
                "The selected card is no longer in your hand. No card was changed.");
            return;
        }

        JuicyCardActor sourceActor = findActorForCardIdentity(sourceCard);

        // 1. Atomarer Modellersatz: exakt dieser Listeneintrag, kein removeAll(),
        //    keine Neusortierung und kein Eingriff in das persistente Deck.
        playerHand.set(index, replacementCard);
        // Die Quellkarte verlaesst eine bestehende Auswahl; die Ersatzkarte wird
        // bewusst NICHT automatisch ausgewaehlt.
        removeByIdentity(selectedCards, sourceCard);

        // 2. Vorschau sofort mit der neuen Kartenidentitaet (Yaku/Basis/Hanko).
        updateLivePreview();

        clearHandHighlights();
        closeAbilityModal();

        // 3. Erst nach der Mutation: kurze Transformationsanimation, danach
        //    Altar-Uebernahme, Erschoepfung und Aktivierungsanimation.
        startAbilityTransformation(sourceActor, replacementCard, index, shikigami);
    }

    /**
     * Kurze, rein sichtbare Transformationsanimation: die Quellkarte pulsiert,
     * der Ersatz-Actor erscheint am gleichen Slot und blendet ein. Der
     * Modellzustand ist zu diesem Zeitpunkt bereits korrekt.
     */
    private void startAbilityTransformation(JuicyCardActor sourceActor, Card replacementCard,
                                            int index, Shikigami shikigami) {
        if (sourceActor == null) {
            Gdx.app.error("GameScreen", "No visible card actor for the transformed card '"
                + replacementCard.name() + "': syncing the hand without the transition animation.");
            replaceCardActor(null, replacementCard, index);
            finishAbilityCommit(shikigami);
            endAbilityActivationFlow();
            return;
        }

        sourceActor.setTouchable(Touchable.disabled);
        pulseActor(sourceActor, ABILITY_SOURCE_PULSE_SCALE);
        sourceActor.addAction(Actions.sequence(
            Actions.delay(ABILITY_SOURCE_PULSE_TIME),
            Actions.run(() -> replaceCardActor(sourceActor, replacementCard, index)),
            Actions.delay(ABILITY_TRANSFORM_TIME),
            Actions.run(() -> finishAbilityCommit(shikigami)),
            Actions.delay(ABILITY_ALTAR_PULSE_TIME),
            Actions.run(this::endAbilityActivationFlow)));
    }

    /**
     * Ersetzt den sichtbaren Actor der Quellkarte durch einen neuen Actor der
     * Ersatzkarte - am gleichen Handslot, mit Kartenbild, Hanko-Abzeichen und
     * kurzer Einblendung. Es entsteht nie ein doppelter Actor.
     */
    private void replaceCardActor(JuicyCardActor sourceActor, Card replacementCard, int index) {
        if (sourceActor != null) {
            sourceActor.clearActions();
            sourceActor.remove();
        }

        JuicyCardActor replacementActor = createCardActor(replacementCard);
        if (replacementActor == null) {
            Gdx.app.error("GameScreen", "No card actor created for '" + replacementCard.name()
                + "': the hand model stays correct, only the visual is missing.");
            updateCardArcTargets();
            updateLivePreview();
            return;
        }

        handGroup.addActor(replacementActor);
        int count = Math.max(1, playerHand.size());
        // Ersatzkarte bleibt zunaechst am gleichen visuellen Slot.
        replacementActor.setPosition(handArcX(index, count), handArcY(index, count));
        replacementActor.setRotation(handArcRotation(index, count) * 0.5f);
        // Scale laeuft ueber die Lerp-Physik des Actors (Actions werden dort jeden
        // Frame ueberschrieben); Alpha ist physikfrei und darf als Fade dienen.
        replacementActor.targetScale = ABILITY_REPLACEMENT_START_SCALE;
        replacementActor.setColor(1f, 1f, 1f, 0f);
        replacementActor.addAction(Actions.sequence(
            Actions.fadeIn(ABILITY_TRANSFORM_TIME, Interpolation.fade),
            Actions.run(() -> replacementActor.targetScale = 1f)));

        updateCardArcTargets();
        updateLivePreview();
    }

    /**
     * Uebernimmt den Begleiter nach erfolgreichem Commit in den Altar und
     * erschöpft ihn genau einmal. Erst hier beginnen Altarflammen und Puls -
     * also nie bei Abbruch oder Ablehnung.
     *
     * <p>Die Erschoepfung haengt am Timing {@code ON_ALTAR_PLACED}: scorebasierte
     * Begleiter (Oni) werden weiterhin ausschliesslich im bestehenden
     * Scoring-/Commit-Pfad erschoepft.</p>
     */
    private void finishAbilityCommit(Shikigami shikigami) {
        if (shikigami == null) {
            endAbilityActivationFlow();
            return;
        }

        activeAltarShikigami = shikigami;
        abilityResolvedShikigami.add(shikigami);
        usedShikigamiInBattle.add(shikigami);
        shikigami.setExhausted(true);

        syncBagOrderWithAltar();
        renderShikigamiUI();
        updateLivePreview();

        // Aktivierungsanimation/Altarflammen starten erst jetzt.
        if (altarFlames != null) {
            altarFlames.ignite();
        }
        com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.35f);
        JuicyShikigamiActor altarActor = findAltarShikigamiActor(shikigami);
        if (altarActor != null) {
            triggerPunchAnimation(altarActor);
        }
    }

    /**
     * Beendet den Vorgang nach der kurzen Animation: dokumentierter Endzustand
     * {@link AbilityActivationState#RESOLVED}, danach wieder normale Bedienung.
     */
    private void endAbilityActivationFlow() {
        clearPendingActivation();
        activationState = AbilityActivationState.RESOLVED;
        activationState = AbilityActivationState.IDLE;
        updateLivePreview();
    }

    /**
     * Abbruch des gesamten Vorgangs (Cancel-Button oder ESC).
     *
     * <p>Es wird nichts veraendert: keine Karte, keine Zufallsaufloesung, keine
     * Erschoepfung, kein Altar-Eintrag, keine Animation und kein Score-/Reward-/
     * XP-Effekt. Der Begleiter liegt unveraendert in
     * {@code RunSession.getShikigamiBag()} und wird von den bestehenden
     * Bag-Actors an seiner vorherigen Position weiter angezeigt; es entsteht keine
     * neue Instanz und es wird keine kopiert.</p>
     */
    private void cancelAbilityActivation() {
        if (!isAbilityFlowActive()) {
            return;
        }
        if (activationState == AbilityActivationState.RESOLVING
            || activationState == AbilityActivationState.RESOLVED) {
            // Der Commit laeuft oder ist abgeschlossen: ein Abbruch wuerde die
            // angewendete Faehigkeit halb zuruecknehmen. Deshalb kein Eingriff.
            return;
        }

        activationState = AbilityActivationState.CANCELLED;

        clearHandHighlights();
        closeAbilityModal();
        clearPendingActivation();
        updateLivePreview();

        activationState = AbilityActivationState.IDLE;
    }

    /**
     * Kleines Hinweisfenster mit englischer Meldung (Ablehnung, leere Hand, bereits
     * verwendet). Es veraendert keinen Spielzustand und laesst sich nur schliessen.
     */
    private void showAbilityDialogMessage(String title, String message) {
        clearPendingActivation();
        buildAbilityModal();
        activationState = AbilityActivationState.PENDING_CONFIRMATION;

        Label titleLabel = new Label(title == null || title.isEmpty() ? "Ability" : title, skin);
        titleLabel.setFontScale(1.05f);
        abilityPanel.add(titleLabel).padBottom(8f).row();

        Label messageLabel = new Label(message == null ? "" : message, skin);
        messageLabel.setWrap(true);
        messageLabel.setAlignment(Align.center);
        abilityPanel.add(messageLabel).width(ABILITY_PANEL_WIDTH - 60f).padBottom(14f).row();

        Table buttons = new Table();
        buttons.add(abilityButton("Close", this::dismissAbilityMessage))
            .width(160f).height(46f);
        abilityPanel.add(buttons);
    }

    /** Schliesst das reine Hinweisfenster; es wurde nichts veraendert. */
    private void dismissAbilityMessage() {
        if (activationState != AbilityActivationState.PENDING_CONFIRMATION
            || pendingActivationShikigami != null) {
            return; // Ein echter Auswahlvorgang wird nicht ueber "Close" beendet.
        }
        closeAbilityModal();
        clearPendingActivation();
        activationState = AbilityActivationState.IDLE;
        updateLivePreview();
    }

    /**
     * Handindex ueber Referenzvergleich. {@link Card} ist ein Record: {@code equals}
     * oder {@code indexOf()} koennten bei gleichem Inhalt die falsche physische
     * Karte treffen.
     */
    private static int identityIndexOf(List<Card> cards, Card card) {
        if (cards == null || card == null) {
            return -1;
        }
        for (int i = 0; i < cards.size(); i++) {
            if (cards.get(i) == card) {
                return i;
            }
        }
        return -1;
    }

    /** Entfernt genau diese Referenz (kein removeAll, keine gleichartige Karte). */
    private static boolean removeByIdentity(List<Card> cards, Card card) {
        int index = identityIndexOf(cards, card);
        if (index < 0) {
            return false;
        }
        cards.remove(index);
        return true;
    }

    /** Karten-Actor ueber Referenzvergleich (nie ueber Namen oder {@code equals}). */
    private JuicyCardActor findActorForCardIdentity(Card card) {
        if (card == null) {
            return null;
        }
        for (Actor child : handGroup.getChildren()) {
            if (child instanceof JuicyCardActor cardActor && cardActor.card == card) {
                return cardActor;
            }
        }
        return null;
    }

    private void renderOmamoris() {
        omamoriTable.clearChildren();

        // Drei feste Slots in exakt der Reihenfolge aus RunSession (wichtig fuer Yata Mirror).
        Table slotRow = new Table();
        slotRow.left();

        for (int slot = 0; slot < OMAMORI_SLOT_CELL_WIDTHS.length; slot++) {
            Table slotBox = new Table();
            slotBox.setBackground(panelBackground);

            if (slot < activeOmamoris.size()) {
                slotBox.add(createOmamoriSlotContent(activeOmamoris.get(slot)))
                    .size(OMAMORI_ICON_WIDTH, OMAMORI_ICON_HEIGHT);
            } else {
                Label emptySlot = new Label("Slot " + (slot + 1) + "\nEmpty", skin);
                emptySlot.setFontScale(0.7f);
                emptySlot.setAlignment(Align.center);
                emptySlot.setColor(Color.GRAY);
                slotBox.add(emptySlot).expand().center();
            }

            slotRow.add(slotBox)
                .width(OMAMORI_SLOT_CELL_WIDTHS[slot])
                .height(OMAMORI_SLOT_CELL_HEIGHTS[slot])
                .pad(OMAMORI_SLOT_CELL_PADS[slot])
                .top();
        }

        // Zentrierte Ausrichtung wie bisher, damit der Inhalt im Panel bleibt.
        omamoriTable.add(slotRow).expand().fill();
    }

    private Actor createOmamoriSlotContent(Omamori omamori) {
        String className = omamori.getClass().getSimpleName();
        String snakeCaseName = className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
        String regionName = "OMAMORI_" + snakeCaseName;

        TextureRegion region = atlas.findRegion(regionName);
        if (region == null) {
            Gdx.app.error("GameScreen", "Omamori atlas region missing: " + regionName);
            return new TextButton(omamori.getName(), skin);
        }

        return new JuicyOmamoriActor(omamori, region, new JuicyOmamoriActor.OmamoriListener() {
            @Override
            public void onTap(JuicyOmamoriActor actor) {
                // Waehrend der Wertung und waehrend eines Faehigkeitsvorgangs
                // sind Omamori nicht bedienbar.
                if (!isHandInteractionAllowed()) return;

                // Ein fixiertes Shikigami-Infopanel wird durch die Omamori-Info abgeloest.
                hideInfoPopup();
                infoPopup.clearChildren();
                infoPopup.add(new Label(omamori.getName(), skin)).padTop(10).padBottom(5).row();
                infoPopup.add(new Label(omamori.getDescription(), skin)).padBottom(5).row();
                infoPopup.pack();

                Vector2 pos = actor.localToStageCoordinates(new Vector2(0, 0));
                infoPopup.setPosition(pos.x, pos.y - infoPopup.getHeight() - 10);
                infoPopup.setVisible(true);
            }

            @Override
            public void onDrop(JuicyOmamoriActor actor, Vector2 stagePos) {
                // Waehrend der Wertung und waehrend eines Faehigkeitsvorgangs
                // werden Omamori weder verschoben noch neu aufgebaut.
                if (!isHandInteractionAllowed()) return;

                // Die Slot-Reihenfolge kommt ausschliesslich aus RunSession und wird nicht
                // umsortiert; der Neuaufbau setzt die Omamori wieder in ihre Slots.
                renderOmamoris();
            }
        });
    }

    /**
     * Spielt die Wertung in exakt der Reihenfolge ab, in der sie berechnet wurde:
     * Yaku-Basis -> gespielte Karten in die Score-Buehne -> gewertete Karten links
     * nach rechts (Kartenbasis, danach Hanko derselben Karte) -> aktive Omamori in
     * Slot-Reihenfolge -> Begleiterbonus (Shikigami) ganz am Ende -> Endscore.
     *
     * <p>Die Mathematik ist bereits abgeschlossen; die Animation liest nur die
     * ScoringEvents in ihrer Reihenfolge und veraendert keinen Spielzustand.
     * Fehlt ein Actor, wird das Event verstaendlich geloggt und die Animation
     * uebersprungen – der Score bleibt korrekt.</p>
     */
    private void playScoringSequence(CalculationBreakdown breakdown, YakuResult bestYaku) {
        scoringSequenceRunning = true;

        var sequence = Actions.sequence();
        final double[] currentChips = { breakdown.yakuChips() };
        final double[] currentMult = { breakdown.yakuBaseMult() };

        sequence.addAction(Actions.run(() -> {
            playButton.getColor().a = 0f;
            discardButton.getColor().a = 0f;
            hideInfoPopup();
            yakuNameLabel.setText(bestYaku.getDisplayName());
            updateScoreLabels(currentChips[0], currentMult[0]);
            prepareOmamoriContributions();

            if (activeAltarShikigami != null && altarFlames != null) {
                // Injiziert Essenz mittig ins Gitter
                corruptionEngine.injectDisturbance(0.5f, 0.5f, 0.15f);

                altarFlames.ignite();
                com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.7f);
            } else {
                // Kleine Dosis beim normalen Legen
                corruptionEngine.injectDisturbance(0.5f, 0.3f, 0.05f);
                com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.3f);
            }
        }));
        sequence.addAction(Actions.delay(0.25f));

        // Die gespielten Karten richten sich auf der Buehne aus. Die Physik muss das
        // Reparenting zuerst als neue Position uebernehmen, danach fliegen die
        // Karten in ihre Slots.
        sequence.addAction(Actions.delay(SCORE_STAGE_SETTLE_TIME));
        sequence.addAction(Actions.run(this::alignScoreStage));
        sequence.addAction(Actions.delay(SCORE_STAGE_ALIGN_TIME));

        // 1. Karten- und Omamori-Events in Berechnungsreihenfolge.
        for (ScoringEvent event : breakdown.events()) {
            if (event.sourceType() == ScoringSourceType.SHIKIGAMI) {
                continue; // Begleiterbonus folgt ganz am Ende
            }
            switch (event.sourceType()) {
                case CARD_BASE, HANKO -> addCardScoreStep(sequence, event, currentChips, currentMult);
                case OMAMORI -> addOmamoriScoreStep(sequence, event, currentChips, currentMult);
                case UNATTRIBUTED -> addUnattributedScoreStep(sequence, event, currentChips, currentMult);
                case SHIKIGAMI, REWARD -> {
                    // Begleiter folgt unten, Belohnungen erst im Commit.
                }
            }
        }

        // Aktive Omamori ohne Effekt dezent kennzeichnen (ohne Animationspause).
        sequence.addAction(Actions.run(this::markOmamoriWithoutEffect));

        // 2. Begleiterbonus (Shikigami) ganz am Ende.
        for (ScoringEvent event : breakdown.events()) {
            if (event.sourceType() == ScoringSourceType.SHIKIGAMI) {
                addShikigamiScoreStep(sequence, event, currentChips, currentMult);
            }
        }

        sequence.addAction(Actions.delay(0.4f));
        sequence.addAction(Actions.run(() -> finishScoringSequence(breakdown, bestYaku)));

        stage.addAction(sequence);
    }

    // ---- Hilfsmethoden der Wertungssequenz (eventgetrieben, ohne Zustandsaenderung) ----


    /** Kartenbasis- beziehungsweise Hanko-Schritt einer gewerteten Karte. */
    private void addCardScoreStep(SequenceAction sequence, ScoringEvent event, double[] chips, double[] mult) {
        if (!event.affectsScore()) return;

        sequence.addAction(Actions.run(() -> {
            applyEventTotals(event, chips, mult);
            updateScoreLabels(chips[0], mult[0]);

            ScoringCardBinding binding = bindingForEvent(event.card());
            if (binding == null) {
                Gdx.app.error("GameScreen", "No card binding for score event '" + event.sourceName()
                    + "': the effect was calculated, the animation was skipped.");
                return;
            }

            if (event.sourceType() == ScoringSourceType.HANKO) {
                // Hanko wirkt auf derselben Karte: erst Kartenbasis, danach dieser
                // zweite Impuls; der Beitrag erscheint als weitere Zeile.
                pulseActor(binding.actor);
                appendCardContribution(binding, event.sourceName() + ": " + describeDelta(event), SCORE_HANKO_COLOR);
            } else {
                wobbleScoreCard(binding);
                appendCardContribution(binding, describeDelta(event), SCORE_CHIP_COLOR);
            }

            com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.12f);
            com.lychcs.koikoi.graphics.JuiceManager.hitstop(0.02f);
        }));
        sequence.addAction(Actions.delay(SCORE_STEP_DELAY));
    }

    /** Begleiterbonus (Shikigami) ganz am Ende: nur bei echtem Chips-/Mult-Effekt. */
    private void addShikigamiScoreStep(SequenceAction sequence, ScoringEvent event, double[] chips, double[] mult) {
        if (!event.affectsScore()) return;

        sequence.addAction(Actions.run(() -> {
            applyEventTotals(event, chips, mult);
            updateScoreLabels(chips[0], mult[0]);

            JuicyShikigamiActor actor = findAltarShikigamiActor(event.shikigami());
            if (actor == null) {
                Gdx.app.error("GameScreen", "No Shikigami actor for score event '" + event.sourceName()
                    + "': the effect was calculated, the animation was skipped.");
            } else {
                pulseActor(actor, SHIKIGAMI_PULSE_SCALE);
                spawnFloatingText(event.sourceName() + "\n" + describeDelta(event), actor, SCORE_SHIKIGAMI_COLOR);
            }

            com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.28f);
            com.lychcs.koikoi.graphics.JuiceManager.hitstop(0.04f);
        }));
        sequence.addAction(Actions.delay(SCORE_STEP_DELAY));
    }

    /** Omamori-Schritt in exakter Slot-Reihenfolge (wichtig fuer Yata Mirror). */
    private void addOmamoriScoreStep(SequenceAction sequence, ScoringEvent event, double[] chips, double[] mult) {
        if (!event.affectsScore()) return;

        sequence.addAction(Actions.run(() -> {
            applyEventTotals(event, chips, mult);
            updateScoreLabels(chips[0], mult[0]);

            JuicyOmamoriActor actor = findOmamoriActor(event.omamori());
            if (actor == null) {
                Gdx.app.error("GameScreen", "No Omamori actor for score event '" + event.sourceName()
                    + "': the effect was calculated, the animation was skipped.");
            } else {
                pulseActor(actor);
            }

            // Der Beitrag erscheint unter dem Omamori, das den Effekt verursacht hat
            // (bei Yata Mirror also unter dem Spiegel).
            appendOmamoriContribution(event, describeDelta(event));

            com.lychcs.koikoi.graphics.JuiceManager.addTrauma(0.14f);
            com.lychcs.koikoi.graphics.JuiceManager.hitstop(0.02f);
        }));
        sequence.addAction(Actions.delay(SCORE_STEP_DELAY));
    }

    /** Schritt ohne zuordenbaren Actor (z. B. direkte Effekte): nur Zaehler und Text. */
    private void addUnattributedScoreStep(SequenceAction sequence, ScoringEvent event, double[] chips, double[] mult) {
        if (!event.affectsScore()) return;

        sequence.addAction(Actions.run(() -> {
            applyEventTotals(event, chips, mult);
            updateScoreLabels(chips[0], mult[0]);
            spawnFloatingText(event.sourceName() + "\n" + describeDelta(event), chipsLabel, eventColor(event));
        }));
        sequence.addAction(Actions.delay(SCORE_STEP_DELAY));
    }

    /** Chips-/Mult-Anzeige auf den aktuellen Zwischenstand setzen. */
    private void updateScoreLabels(double chips, double mult) {
        chipsLabel.setText(ScoreFormat.format(chips));
        multLabel.setText(ScoreFormat.format(mult));
    }

    /** Rechnet ein Event auf die angezeigten Zwischensummen (identisch zur Berechnung). */
    private static void applyEventTotals(ScoringEvent event, double[] chips, double[] mult) {
        if (event.addedChips() != 0.0) chips[0] += event.addedChips();
        if (event.addedMult() != 0.0) mult[0] += event.addedMult();
        if (event.xMult() != 1.0) mult[0] *= event.xMult();
    }

    /**
     * Abschluss der Sequenz: Endscore, XP fuer das gewaehlte Yaku und Belohnungen
     * werden GENAU EINMAL gutgeschrieben. Anschliessend werden Shikigami-Zustand,
     * Score-Buehne, Beitragsanzeigen, Handziele und Eingabesperre zurueckgesetzt.
     */
    private void finishScoringSequence(CalculationBreakdown breakdown, YakuResult bestYaku) {
        currentRoundScore += breakdown.finalScore();

        if (bestYaku != null) {
            runSession.getYakuProgression().addXp(bestYaku.type(), 1);
        }

        for (ScoringEvent event : breakdown.events()) {
            if (event.addedMon() > 0) runSession.addMon(event.addedMon());
            if (event.addedVoidDust() > 0) runSession.addVoidDust(event.addedVoidDust());
        }

        scoreProgressLabel.setText("Score: " + ScoreFormat.format(currentRoundScore) + " / " + ScoreFormat.format(currentTargetScore));
        updateScoreLabels(breakdown.totalChips(), breakdown.totalMult());
        spawnFloatingText("Score " + ScoreFormat.format(breakdown.finalScore()), chipsLabel, COLOR_TEXT_MAIN);

        if (activeAltarShikigami != null) {
            activeAltarShikigami.setExhausted(true);
            activeAltarShikigami = null;
        }
        if (altarFlames != null) {
            altarFlames.extinguish();
        }

        // Position, Rotation, Scale und Z-Reihenfolge der Hand wiederherstellen.
        updateCardArcTargets();

        // Score-Buehne und Beitragsanzeigen vollstaendig aufraeumen: die gespielten
        // Karten sind verbraucht, es bleiben keine Actors oder Labels zurueck.
        clearScoreStage();
        clearOmamoriContributions();

        scoringSequenceRunning = false;
        checkRoundEndCondition();
    }

    /**
     * Kurzer Scale-Punch ueber die Lerp-Ziele: direkte Scale-Actions werden von
     * der Physik des Actors jeden Frame ueberschrieben und waeren unsichtbar.
     * Actors ohne Physik nutzen die vorhandene Punch-Hilfe.
     */
    private void pulseActor(Actor actor) {
        pulseActor(actor, PULSE_SCALE);
    }

    private void pulseActor(Actor actor, float scale) {
        if (!(actor instanceof JuicyDraggableActor juicy)) {
            triggerPunchAnimation(actor);
            return;
        }

        juicy.targetScale = scale;
        juicy.addAction(Actions.sequence(
            Actions.delay(PULSE_TIME),
            Actions.run(() -> juicy.targetScale = 1f)
        ));
    }

    /**
     * Kurzlebiger Floating Text ueber einem Actor. Es entstehen nur Objekte pro
     * Score-Event (nicht pro Renderframe); nach dem Aufsteigen entfernt sich der
     * Text selbst.
     */
    private void spawnFloatingText(String text, Actor anchor, Color color) {
        if (anchor == null || text == null || text.isEmpty()) return;

        Label label = new Label(text, skin);
        label.setAlignment(Align.center);
        label.setFontScale(0.8f);
        label.setColor(color);
        label.pack();

        Vector2 stagePos = anchor.localToStageCoordinates(new Vector2(anchor.getWidth() / 2f, anchor.getHeight()));
        label.setPosition(stagePos.x - (label.getWidth() / 2f), stagePos.y + 6f);
        stage.addActor(label);

        label.addAction(Actions.sequence(
            Actions.parallel(
                Actions.moveBy(0f, 42f, FLOATING_TEXT_TIME, Interpolation.pow2Out),
                Actions.fadeOut(FLOATING_TEXT_TIME, Interpolation.fade)
            ),
            Actions.removeActor()
        ));
    }

    /** Aufsteigender Textinhalt (z. B. "+5 Chips", "+10 Mult", "x1.5 Mult"). */
    private static String describeDelta(ScoringEvent event) {
        StringBuilder text = new StringBuilder();
        if (event.addedChips() != 0.0) {
            text.append("+").append(ScoreFormat.format(event.addedChips())).append(" Chips");
        }
        if (event.addedMult() != 0.0) {
            if (text.length() > 0) text.append('\n');
            text.append("+").append(ScoreFormat.format(event.addedMult())).append(" Mult");
        }
        if (event.xMult() != 1.0) {
            if (text.length() > 0) text.append('\n');
            text.append("x").append(ScoreFormat.format(event.xMult())).append(" Mult");
        }
        return text.toString();
    }

    private static Color eventColor(ScoringEvent event) {
        return switch (event.sourceType()) {
            case HANKO -> SCORE_HANKO_COLOR;
            case SHIKIGAMI -> SCORE_SHIKIGAMI_COLOR;
            case OMAMORI -> SCORE_OMAMORI_COLOR;
            default -> event.addedChips() != 0.0 ? SCORE_CHIP_COLOR : SCORE_MULT_COLOR;
        };
    }

    /** Altar-Actor wird zur Animationszeit im Szenengraph gesucht (resize-sicher). */
    private JuicyShikigamiActor findAltarShikigamiActor(Shikigami shikigami) {
        if (shikigami == null || altarTable == null) return null;
        for (Actor child : altarTable.getChildren()) {
            if (child instanceof JuicyShikigamiActor actor && actor.shikigami == shikigami) {
                return actor;
            }
        }
        return null;
    }

    /** Omamori-Actors liegen in den Slot-Tabellen verschachtelt und werden rekursiv gesucht. */
    private JuicyOmamoriActor findOmamoriActor(Omamori omamori) {
        if (omamori == null || omamoriTable == null) return null;
        return findOmamoriActor(omamoriTable, omamori);
    }

    private static JuicyOmamoriActor findOmamoriActor(Group group, Omamori omamori) {
        for (Actor child : group.getChildren()) {
            if (child instanceof JuicyOmamoriActor actor && actor.omamori == omamori) {
                return actor;
            }
            if (child instanceof Group nested) {
                JuicyOmamoriActor found = findOmamoriActor(nested, omamori);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        if (disposed) {
            return;
        }
        disposed = true;

        // Keine temporaeren Actors oder Referenzen aus einer laufenden bzw.
        // abgebrochenen Wertung zuruecklassen (Screen-Wechsel, Exception, Dispose).
        scoringSequenceRunning = false;
        // Offenen Faehigkeitsdialog restlos entfernen: keine Listener oder Actions
        // auf einem disposed Screen und kein halb gesetzter Pending-Zustand.
        activationState = AbilityActivationState.IDLE;
        closeAbilityModal();
        clearPendingActivation();
        clearScoreStage();
        clearOmamoriContributions();
        hideInfoPopup();

        // Nur screen-eigene Ressourcen freigeben.
        if (stage != null) stage.dispose();
        if (fbo != null) {
            fbo.dispose();
            fbo = null;
        }
        if (screenBatch != null) {
            screenBatch.dispose();
            screenBatch = null;
        }
        if (edgeShader != null) {
            edgeShader.dispose();
            edgeShader = null;
        }
        if (corruptionEngine != null) {
            corruptionEngine.dispose();
            corruptionEngine = null;
        }
        if (background != null) {
            background.dispose();
            background = null;
        }
        cardTextures.clear();
        // Skin, Atlas, Panel-/Button-Textur sind geliehen (Eigentum: GameAssets)
        // und werden hier bewusst NICHT disposet.
    }
}
