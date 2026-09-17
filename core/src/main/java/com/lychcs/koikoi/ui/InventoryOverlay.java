package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.Rank;
import com.lychcs.koikoi.model.Season;
import com.lychcs.koikoi.model.hanko.Hanko;
import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.omamori.Rarity;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.run.YakuProgression;
import com.lychcs.koikoi.scoring.ScoreFormat;
import com.lychcs.koikoi.scoring.YakuType;

import java.util.EnumMap;
import java.util.Map;

public class InventoryOverlay extends Group {

    // Gedaempfte Palette: Tusche/Washi, cremefarbene Schrift, Hanko-Rot, gedämpftes Gold.
    private static final Color COLOR_INK = new Color(0.13f, 0.11f, 0.10f, 0.97f);
    private static final Color COLOR_TILE = new Color(0.22f, 0.18f, 0.15f, 1f);
    private static final Color COLOR_TILE_HEIGHTENED = new Color(0.27f, 0.22f, 0.18f, 1f);
    private static final Color COLOR_ACCENT = new Color(0.62f, 0.14f, 0.14f, 1f);
    private static final Color COLOR_GOLD = new Color(0.82f, 0.68f, 0.34f, 1f);
    private static final Color COLOR_CREAM = new Color(0.93f, 0.89f, 0.80f, 1f);
    private static final Color COLOR_MUTED = new Color(0.68f, 0.63f, 0.55f, 1f);
    private static final Color COLOR_TRACK = new Color(0.30f, 0.25f, 0.21f, 1f);
    private static final Color COLOR_ACCENT_TEXT = new Color(0.86f, 0.44f, 0.38f, 1f);

    private final RunSession runSession;
    private final Skin skin;
    private final TextureAtlas atlas;
    private final TextButton.TextButtonStyle buttonStyle;

    // Gecachte Drawables (einmal erzeugt; global verwaltete Skin-/Atlas-Ressourcen
    // werden hier nie disposed).
    private final Drawable inkDrawable;
    private final Drawable tileDrawable;
    private final Drawable selectedTileDrawable;
    private final Drawable badgeDrawable;
    private final Drawable accentDrawable;
    private final Drawable trackDrawable;

    private final Table root;
    private final Table mainPanel;
    private final Table tabBar;
    private final Table contentArea;
    private final Table detailPanel;
    private final ScrollPane collectionScroll;
    private final ScrollPane detailScroll;
    private final Label currencyLabel;
    private final TextButton deckTab;
    private final TextButton hankoTab;
    private final TextButton omamoriTab;
    private final TextButton shikigamiTab;
    private final TextButton yakuTab;

    private boolean open;
    private HankoEffect selectedHankoToApply;
    /** Zuletzt angeklickte Kachel (nur fuer die Hervorhebung im Raster). */
    private Table selectedTileActor;
    /** Slot-Index, der nach dem Aktivieren kurz pulsieren soll (-1 = keiner). */
    private int pulseSlotIndex = -1;
    /** Omamori, das nach dem Deaktivieren sanft eingeblendet wird. */
    private Omamori fadeInOmamori;
    /** Auswahlzustand je Tab (nur fuer die Hervorhebung im Raster). */
    private Card selectedCard;
    private Omamori selectedOmamori;
    private Shikigami selectedShikigami;
    private YakuType selectedYaku;
    private HankoEffect selectedHanko;
    /** Schutz gegen doppelte Input-Events: es darf immer nur ein Bestaetigungsdialog offen sein. */
    private boolean hankoDialogOpen;

    public InventoryOverlay(
        RunSession runSession,
        Skin skin,
        TextureAtlas atlas,
        NinePatchDrawable panelBackground,
        TextButton.TextButtonStyle buttonStyle
    ) {
        this.runSession = runSession;
        this.skin = skin;
        this.atlas = atlas;
        this.buttonStyle = buttonStyle;

        // Getintete Varianten des vorhandenen Panels (kein neues Asset, kein Leck:
        // die Drawables referenzieren die globale Skin-/Atlas-Textur).
        this.inkDrawable = panelBackground.tint(COLOR_INK);
        this.tileDrawable = panelBackground.tint(COLOR_TILE);
        this.selectedTileDrawable = panelBackground.tint(COLOR_TILE_HEIGHTENED);
        this.badgeDrawable = panelBackground.tint(COLOR_TRACK);
        this.accentDrawable = skin.newDrawable("white", COLOR_ACCENT);
        this.trackDrawable = skin.newDrawable("white", COLOR_TRACK);

        setSize(1280f, 720f);
        setVisible(false);

        Image dimBackground = new Image(skin.getRegion("white"));
        dimBackground.setFillParent(true);
        dimBackground.setColor(0f, 0f, 0f, 0.72f);
        dimBackground.setTouchable(Touchable.enabled);
        dimBackground.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggle();
            }
        });
        addActor(dimBackground);

        // Wurzel: fuellt die sichere Stage-Flaeche mit Aussenabstand.
        root = new Table();
        root.setFillParent(true);
        root.pad(18f);
        addActor(root);

        mainPanel = new Table();
        mainPanel.setBackground(inkDrawable);
        mainPanel.setTouchable(Touchable.enabled);
        mainPanel.pad(18f);
        mainPanel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
            }
        });
        root.add(mainPanel).grow().maxWidth(1240f).maxHeight(700f);

        // Kopfzeile: Titel, Waehrungen und Exit - der Exit bleibt immer sichtbar.
        Label titleLabel = new Label("INVENTAR", skin);
        titleLabel.setFontScale(1.3f);
        titleLabel.setColor(COLOR_GOLD);

        currencyLabel = new Label("", skin);
        currencyLabel.setFontScale(0.85f);
        currencyLabel.setColor(COLOR_MUTED);

        TextButton closeButton = new TextButton("X", buttonStyle);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggle();
            }
        });

        Table header = new Table();
        header.add(titleLabel).left();
        header.add(currencyLabel).expandX().right().padRight(14f);
        header.add(closeButton).width(46f).height(40f).right();
        mainPanel.add(header).growX().row();

        mainPanel.add(separatorLine()).growX().height(2f).padTop(6f).padBottom(10f).row();

        // Tab-Leiste: jeder Tab waechst mit der Breite und ragt nie aus dem Panel.
        deckTab = tabButton("Karten", this::showDeckTab);
        hankoTab = tabButton("Hanko", this::showHankoTab);
        omamoriTab = tabButton("Omamori", this::showOmamoriTab);
        shikigamiTab = tabButton("Shikigami", this::showShikigamiTab);
        yakuTab = tabButton("Yaku", this::showYakuTab);

        tabBar = new Table();
        tabBar.add(deckTab).growX().height(40f).padRight(6f);
        tabBar.add(hankoTab).growX().height(40f).padRight(6f);
        tabBar.add(omamoriTab).growX().height(40f).padRight(6f);
        tabBar.add(shikigamiTab).growX().height(40f).padRight(6f);
        tabBar.add(yakuTab).growX().height(40f);
        mainPanel.add(tabBar).growX().padBottom(10f).row();

        // Sammlung (links, waechst mit der Breite) und Detailpanel (rechts,
        // prozentual mit Mindestbreite). Beide Bereiche sind separat scrollbar.
        contentArea = new Table();
        contentArea.top().left();

        collectionScroll = new ScrollPane(contentArea, skin);
        collectionScroll.setFadeScrollBars(false);
        collectionScroll.setScrollingDisabled(true, false);

        detailPanel = new Table();
        detailPanel.top().left();

        detailScroll = new ScrollPane(detailPanel, skin);
        detailScroll.setFadeScrollBars(false);
        detailScroll.setScrollingDisabled(true, false);

        Table body = new Table();
        body.add(collectionScroll).grow();
        body.add(detailScroll)
            .width(Value.percentWidth(0.35f, mainPanel))
            .minWidth(330f)
            .growY()
            .padLeft(14f);
        mainPanel.add(body).grow().row();

        showDetailPlaceholder();
    }

    /** Dezente Trennlinie aus einem vorhandenen Skin-/Panel-Drawable. */
    private Table separatorLine() {
        Table line = new Table();
        line.setBackground(accentDrawable);
        return line;
    }

    private TextButton tabButton(String text, Runnable action) {
        TextButton button = new TextButton(text, buttonStyle);
        button.addListener(tabListener(action));
        return button;
    }

    private ClickListener tabListener(Runnable action) {
        return new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedHankoToApply = null;
                // Kein Rest des vorherigen Elements im Detailpanel.
                showDetailPlaceholder();
                action.run();
            }
        };
    }

    // ---------------------------------------------------------------------
    // Gemeinsames Detailpanel
    // ---------------------------------------------------------------------

    /** Setzt das Detailpanel auf den Ausgangshinweis zurueck. */
    private void showDetailPlaceholder() {
        Table panel = beginDetail("Details", null);
        addDetailText(panel, "Waehle einen Eintrag in der Sammlung.", COLOR_MUTED, 0.8f);
    }

    /**
     * Leert das Detailpanel, legt Ueberschrift und optionale Typ-/Statuszeile an
     * und laesst den Inhalt dezent einfaden (kein Rest des vorherigen Eintrags).
     */
    private Table beginDetail(String titleText, String typeLine) {
        detailPanel.clearChildren();

        Label title = new Label(titleText == null ? "" : titleText, skin);
        title.setWrap(true);
        title.setFontScale(1.05f);
        title.setColor(COLOR_CREAM);
        detailPanel.add(title).growX().left().padBottom(2f).row();

        if (typeLine != null && !typeLine.isEmpty()) {
            Label type = new Label(typeLine, skin);
            type.setWrap(true);
            type.setFontScale(0.8f);
            type.setColor(COLOR_GOLD);
            detailPanel.add(type).growX().left().padBottom(6f).row();
        }

        detailPanel.add(separatorLine()).growX().height(1f).padBottom(6f).row();

        detailPanel.setColor(1f, 1f, 1f, 0f);
        detailPanel.clearActions();
        detailPanel.addAction(Actions.fadeIn(0.12f));
        return detailPanel;
    }

    /** Grosses Vorschaubild oben im Detailpanel. */
    private void addDetailImage(Table panel, TextureRegion region, float width, float height) {
        Table frame = new Table();
        frame.setBackground(badgeDrawable);
        frame.pad(8f);

        if (region != null) {
            frame.add(new Image(region)).size(width, height);
        } else {
            Label missing = new Label("-", skin);
            missing.setColor(COLOR_MUTED);
            frame.add(missing).size(width, height);
        }
        panel.add(frame).left().padBottom(8f).row();
    }

    /** Zentrale Werte als kleine Badges (zwei pro Zeile). */
    private void addDetailBadges(Table panel, String... badges) {
        Table row = new Table();
        int index = 0;
        for (String badge : badges) {
            if (badge == null || badge.isEmpty()) {
                continue;
            }
            Table chip = new Table();
            chip.setBackground(badgeDrawable);
            chip.pad(5f, 10f, 5f, 10f);
            Label label = new Label(badge, skin);
            label.setFontScale(0.82f);
            label.setColor(COLOR_CREAM);
            chip.add(label);
            row.add(chip).left().padRight(6f).padBottom(4f);
            index++;
            if (index % 2 == 0) {
                row.row();
            }
        }
        panel.add(row).growX().left().padBottom(6f).row();
    }

    /** Abschnittsueberschrift im Detailpanel. */
    private void addDetailSection(Table panel, String headline) {
        Label label = new Label(headline, skin);
        label.setFontScale(0.82f);
        label.setColor(COLOR_GOLD);
        panel.add(label).growX().left().padTop(4f).padBottom(2f).row();
    }

    /** Umgebrochener Detailtext, der das Panel nicht verbreitert. */
    private void addDetailText(Table panel, String text, Color color, float scale) {
        Label label = new Label(text == null ? "-" : text, skin);
        label.setWrap(true);
        label.setFontScale(scale);
        label.setColor(color);
        panel.add(label).growX().left().padBottom(3f).row();
    }

    /** Fortschrittsbalken fuer "current / required" in der verfuegbaren Breite. */
    private void addDetailProgress(Table panel, int current, int required) {
        Table track = new Table();
        track.setBackground(trackDrawable);

        Table fill = new Table();
        fill.setBackground(accentDrawable);

        float ratio = required <= 0 ? 1f : Math.max(0f, Math.min(1f, current / (float) required));
        track.add(fill).width(Value.percentWidth(ratio, track)).height(10f).left();
        track.add().expandX();

        panel.add(track).growX().height(10f).left().padBottom(3f).row();
    }

    /**
     * Hauptaktion ueber die gesamte verfuegbare Breite. {@code growX()} plus
     * {@code minWidth(0)} verhindert, dass lange Texte den Button aus dem Panel
     * schieben (Ursache des zuvor abgeschnittenen Omamori-Buttons).
     */
    private void addDetailPrimaryAction(Table panel, Actor button) {
        panel.add(button).growX().minWidth(0f).height(38f).padTop(6f).row();
    }

    /** Zwei gleich breite Aktionen in einer gemeinsamen Zeile. */
    private void addDetailActionPair(Table panel, Actor first, Actor second) {
        Table row = new Table();
        row.add(first).growX().minWidth(0f).height(36f).padRight(6f);
        row.add(second).growX().minWidth(0f).height(36f);
        panel.add(row).growX().padTop(4f).row();
    }

    /** Anzeigename der aktuellen Entwicklungsstufe eines Shikigami. */
    private static String stageText(Shikigami shikigami) {
        switch (shikigami.getStage()) {
            case EGG: return "Ei";
            case LEVEL_1: return "Stufe 1";
            case LEVEL_2: return "Stufe 2";
            case LEVEL_3: return "Stufe 3 (finale Form)";
            default: return shikigami.getStage().name();
        }
    }

    // ---------------------------------------------------------------------
    // Kleine, gecachte UI-Helfer (keine Allokation pro Frame)
    // ---------------------------------------------------------------------

    /** Dezenter Tabwechsel-Fade: kurz, kein Action-Stapel. */
    private void fadeInCollection() {
        contentArea.setColor(1f, 1f, 1f, 0f);
        contentArea.clearActions();
        contentArea.addAction(Actions.fadeIn(0.12f));
    }

    /** Kleiner Scale-Punch als Auswahl-Feedback (nur transform-faehige Tables). */
    private static void punchActor(Actor actor) {
        if (!(actor instanceof Table table) || !table.isTransform()) {
            return;
        }
        table.clearActions();
        table.addAction(Actions.sequence(
            Actions.scaleTo(1.06f, 1.06f, 0.06f),
            Actions.scaleTo(1f, 1f, 0.1f, Interpolation.swingOut)
        ));
    }

    /** Hebt die angeklickte Kachel hervor und nimmt die vorherige Markierung zurueck. */
    private void highlightTile(Table tile) {
        if (selectedTileActor != null && selectedTileActor != tile) {
            selectedTileActor.setBackground(tileDrawable);
        }
        selectedTileActor = tile;
        tile.setBackground(selectedTileDrawable);
    }

    /** Kurzmarkierung der Jahreszeit fuer das Kartenraster. */
    private static String shortSeason(Season season) {
        switch (season) {
            case SPRING: return "FR";
            case SUMMER: return "SO";
            case AUTUMN: return "HE";
            case WINTER: return "WI";
            default: return season.name();
        }
    }

    /**
     * Kartendetails mit Vorschau, Badges und Hanko-Werten. Die Effektwerte stammen
     * ausschliesslich aus dem zentralen Hanko-Katalog (keine Duplikate im UI).
     */
    private void showCardDetail(Card card) {
        Table panel = beginDetail(card.name(), card.rank().name() + " · " + card.season().name());

        addDetailImage(panel, atlas.findRegion(card.id().name()), 150f, 210f);
        addDetailBadges(panel,
            ScoreFormat.format(card.rank().getBaseValue()) + " Chips",
            card.season().name());

        addDetailSection(panel, "Hanko");
        if (!card.hasHanko()) {
            addDetailText(panel, "Kein Hanko", COLOR_CREAM, 0.84f);
            addDetailText(panel, "Kein zusätzlicher Effekt", COLOR_MUTED, 0.78f);
        } else {
            addDetailText(panel, HankoCatalog.getName(card.effect()), COLOR_CREAM, 0.86f);
            addDetailText(panel, HankoCatalog.getDescription(card.effect()), COLOR_MUTED, 0.78f);
        }

        if (selectedHankoToApply != null) {
            addDetailText(panel,
                "Hanko-Modus: Klicke diese Karte im Raster, um "
                    + HankoCatalog.getName(selectedHankoToApply) + " anzuwenden.",
                COLOR_GOLD, 0.74f);
        }
    }

    public void toggle() {
        open = !open;

        if (open) {
            selectedHankoToApply = null;
            syncSizeToStage();
            updateCurrencies();
            updateHankoTabBadge();
            showDetailPlaceholder();
            showDeckTab();
            setVisible(true);

            // Dezenter Fade (kein Action-Stapel: vorher leeren).
            mainPanel.setColor(1f, 1f, 1f, 0f);
            mainPanel.clearActions();
            mainPanel.addAction(Actions.fadeIn(0.15f));
        } else {
            selectedHankoToApply = null;
            mainPanel.clearActions();
            mainPanel.addAction(Actions.sequence(
                Actions.fadeOut(0.1f, Interpolation.fade),
                Actions.run(() -> setVisible(false))
            ));
        }
    }

    /** Haelt das Overlay deckungsgleich mit der sicheren Stage-Flaeche. */
    private void syncSizeToStage() {
        Group stageRoot = getStage() == null ? null : getStage().getRoot();
        if (stageRoot != null) {
            setSize(stageRoot.getWidth(), stageRoot.getHeight());
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);

        // Responsiv auch nach einem Resize, ohne pro Frame Objekte zu erzeugen.
        if (getStage() != null) {
            Group stageRoot = getStage().getRoot();
            if (stageRoot != null
                && (getWidth() != stageRoot.getWidth() || getHeight() != stageRoot.getHeight())) {
                setSize(stageRoot.getWidth(), stageRoot.getHeight());
            }
        }
    }

    public boolean isOpen() {
        return open;
    }

    private void updateCurrencies() {
        currencyLabel.setText(
            "Mon: " + runSession.getMon()
                + "  |  VOID DUST: " + runSession.getVoidDust()
        );
    }

    private void updateHankoTabBadge() {
        int count = runSession.getPurchasedHankos().size();
        hankoTab.setText(count > 0 ? "Hanko (" + count + ")" : "Hanko");
    }

    // ---------------------------------------------------------------------
    // Deck und Hanko-Anwendung
    // ---------------------------------------------------------------------

    /**
     * Karten-Tab: gleichmaessiges Raster links, vollstaendige Details rechts.
     */
    private void showDeckTab() {
        contentArea.clearChildren();
        selectedTileActor = null;
        updateHankoTabBadge();

        if (!runSession.getPurchasedHankos().isEmpty()) {
            contentArea.add(createHankoShelf()).growX().padBottom(10f).row();
        }

        if (selectedHankoToApply != null) {
            Label hint = new Label(
                "Hanko-Modus: Karte waehlen fuer " + HankoCatalog.getName(selectedHankoToApply)
                    + ". Ein vorhandener Stempel wird ersetzt.", skin);
            hint.setWrap(true);
            hint.setFontScale(0.78f);
            hint.setColor(COLOR_GOLD);
            contentArea.add(hint).growX().padBottom(8f).row();
        }

        Table cardsGrid = new Table();
        int column = 0;
        for (Card card : runSession.getPlayerDeck().getCards()) {
            cardsGrid.add(createCardBox(card)).pad(6f);
            column++;
            if (column % 6 == 0) {
                cardsGrid.row();
            }
        }

        contentArea.add(cardsGrid).growX().row();
        fadeInCollection();
    }

    private Table createHankoShelf() {
        Table shelf = new Table();
        shelf.setBackground(tileDrawable);
        shelf.pad(10f);

        shelf.add(new Label("Hanko anwenden:", skin)).padRight(15f);

        Map<HankoEffect, Integer> counts = new EnumMap<>(HankoEffect.class);
        for (HankoEffect effect : runSession.getPurchasedHankos()) {
            counts.merge(effect, 1, Integer::sum);
        }

        for (Map.Entry<HankoEffect, Integer> entry : counts.entrySet()) {
            HankoEffect effect = entry.getKey();
            Table button = new Table();
            button.setTouchable(Touchable.enabled);
            button.pad(6f);

            boolean selected = effect == selectedHankoToApply;
            if (selected) {
                button.setBackground(selectedTileDrawable);
            }

            TextureRegion region = findHankoRegion(effect);
            if (region != null) {
                button.add(new Image(region)).size(28f).padRight(6f);
            }

            Label label = new Label(
                HankoCatalog.getName(effect) + " x" + entry.getValue(),
                skin
            );
            label.setFontScale(0.72f);
            if (selected) {
                label.setColor(Color.GOLD);
            }
            button.add(label);

            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedHankoToApply = selected ? null : effect;
                    showDeckTab();
                }
            });

            shelf.add(button).padRight(9f);
        }

        if (selectedHankoToApply != null) {
            TextButton cancel = new TextButton("Abbrechen", buttonStyle);
            cancel.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedHankoToApply = null;
                    showDeckTab();
                }
            });
            shelf.add(cancel).width(105f).height(34f).padLeft(8f);
        }

        return shelf;
    }

    /**
     * Kartenkachel im Raster: Bild im korrekten Seitenverhaeltnis, kleine
     * Rank-/Season-Markierung, Hanko-Badge und Hanko-roter Auswahlrahmen.
     * Keine langen Beschreibungen - die stehen rechts im Detailpanel.
     */
    private Table createCardBox(Card card) {
        Table cardBox = new Table();
        cardBox.setTransform(true);
        cardBox.setOrigin(30f, 60f);
        cardBox.setBackground(card.equals(selectedCard) ? selectedTileDrawable : tileDrawable);
        cardBox.pad(5f);

        Hanko selectedHanko = selectedHankoToApply == null
            ? null
            : HankoCatalog.create(selectedHankoToApply);
        boolean canApply = selectedHanko == null || selectedHanko.canTarget(card);

        cardBox.setTouchable(canApply ? Touchable.enabled : Touchable.disabled);

        Stack cardStack = new Stack();
        TextureRegion cardRegion = atlas.findRegion(card.id().name());
        if (cardRegion != null) {
            Image cardImage = new Image(new TextureRegionDrawable(cardRegion));
            cardImage.setTouchable(Touchable.disabled);
            cardImage.setColor(1f, 1f, 1f, canApply ? 1f : 0.38f);
            cardStack.add(cardImage);
        }

        if (card.hasHanko()) {
            TextureRegion hankoRegion = findHankoRegion(card.effect());
            if (hankoRegion != null) {
                Table badgeContainer = new Table();
                badgeContainer.top().right();
                badgeContainer.setTouchable(Touchable.disabled);
                badgeContainer.add(new Image(hankoRegion)).size(18f).pad(2f);
                cardStack.add(badgeContainer);
            }
        }

        cardBox.add(cardStack).size(60f, 106f).padBottom(2f).row();

        Label marker = new Label(card.rank().name().charAt(0) + " · " + shortSeason(card.season()), skin);
        marker.setFontScale(0.6f);
        marker.setColor(selectedHanko != null && !canApply ? Color.GRAY : COLOR_MUTED);
        cardBox.add(marker).row();

        cardBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Hanko-Anwendungsmodus: Klick waehlt die Karte als Hanko-Ziel.
                if (selectedHankoToApply != null) {
                    requestHankoConfirmation(card, selectedHankoToApply);
                    return;
                }

                // Normalmodus: Auswahl markieren und Details rechts zeigen.
                selectedCard = card;
                highlightTile(cardBox);
                punchActor(cardBox);
                showCardDetail(card);
            }
        });

        return cardBox;
    }

    private void requestHankoConfirmation(Card targetCard, HankoEffect effect) {
        // Doppelklicks bzw. doppelte Input-Events duerfen keinen zweiten Dialog
        // (und damit keinen zweiten Verbrauch) ausloesen.
        if (hankoDialogOpen) {
            return;
        }

        Hanko hanko = HankoCatalog.create(effect);
        if (!hanko.canTarget(targetCard)
            || !runSession.getPurchasedHankos().contains(effect)
            || getStage() == null) {
            return;
        }

        hankoDialogOpen = true;

        Dialog dialog = new Dialog("Hanko anwenden", skin) {
            @Override
            protected void result(Object object) {
                hankoDialogOpen = false;
                if (Boolean.TRUE.equals(object)) {
                    applyHankoToCard(targetCard, effect);
                }
            }
        };

        StringBuilder message = new StringBuilder();
        message.append(HankoCatalog.getName(effect))
            .append(" auf\n")
            .append(targetCard.name())
            .append(" anwenden?\n\n");
        if (targetCard.hasHanko()) {
            message.append("Der vorhandene Stempel wird ersetzt.\n\n");
        }
        message.append(HankoCatalog.getDescription(effect));

        Label text = new Label(message.toString(), skin);
        text.setAlignment(Align.center);
        text.setWrap(true);

        dialog.getContentTable().add(text).width(420f).pad(20f);
        dialog.button("Abbrechen", false);
        dialog.button("Anwenden", true);
        dialog.show(getStage());
    }

    private void applyHankoToCard(Card targetCard, HankoEffect effect) {
        Hanko hanko = HankoCatalog.create(effect);
        if (!hanko.canTarget(targetCard)
            || !runSession.getPurchasedHankos().contains(effect)) {
            selectedHankoToApply = null;
            showDeckTab();
            return;
        }

        // Ein zweiter Apply-Vorgang auf dieselbe Karteninstanz kann nicht gelingen:
        // evolveCard() findet die bereits ersetzte Instanz nicht mehr und der Hanko
        // wird in diesem Fall auch nicht verbraucht.
        Card modifiedCard = hanko.applyEffect(targetCard);
        if (runSession.getPlayerDeck().evolveCard(targetCard, modifiedCard)) {
            // remove(Object) entfernt genau ein Exemplar dieses stapelbaren Hankos.
            runSession.getPurchasedHankos().remove(effect);
        }

        selectedHankoToApply = null;
        updateHankoTabBadge();
        showDeckTab();
        // Detailpanel mit dem neuen Hanko aktualisieren.
        showCardDetail(modifiedCard);
    }

    /**
     * Liefert die Atlas-Region des Hanko-Abzeichens. Fehlende Regionen werden mit
     * vollstaendigem Namen geloggt statt eine NullPointerException auszuloesen.
     */
    private TextureRegion findHankoRegion(HankoEffect effect) {
        String regionName = HankoCatalog.getAtlasRegionName(effect);
        TextureRegion region = regionName == null ? null : atlas.findRegion(regionName);
        if (region == null) {
            Gdx.app.error("InventoryOverlay", "Hanko-Atlas-Region fehlt: " + regionName + " (Hanko " + effect + ")");
        }
        return region;
    }

    /** Hanko-Tab: Raster aus Siegeln mit Bestand, Details rechts. */
    private void showHankoTab() {
        contentArea.clearChildren();
        selectedTileActor = null;
        updateHankoTabBadge();

        if (runSession.getPurchasedHankos().isEmpty()) {
            Label empty = new Label("Keine Hankos im Vorrat.\nKaufe sie im Tengu-Markt.", skin);
            empty.setAlignment(Align.center);
            empty.setColor(COLOR_MUTED);
            contentArea.add(empty).growX().pad(20f).row();
            fadeInCollection();
            return;
        }

        Map<HankoEffect, Integer> counts = new EnumMap<>(HankoEffect.class);
        for (HankoEffect effect : runSession.getPurchasedHankos()) {
            counts.merge(effect, 1, Integer::sum);
        }

        Table grid = new Table();
        int column = 0;
        for (Map.Entry<HankoEffect, Integer> entry : counts.entrySet()) {
            grid.add(createHankoTile(entry.getKey(), entry.getValue())).pad(6f);
            column++;
            if (column % 5 == 0) {
                grid.row();
            }
        }
        contentArea.add(grid).growX().row();
        fadeInCollection();
    }

    /** Siegel-Kachel mit Bestand und Auswahlzustand. */
    private Table createHankoTile(HankoEffect effect, int count) {
        Table tile = new Table();
        tile.setTransform(true);
        tile.setBackground(effect == selectedHanko ? selectedTileDrawable : tileDrawable);
        tile.setTouchable(Touchable.enabled);
        tile.pad(8f);

        TextureRegion region = findHankoRegion(effect);
        if (region != null) {
            Image image = new Image(region);
            image.setTouchable(Touchable.disabled);
            tile.add(image).size(44f).padBottom(2f).row();
        }

        Label label = new Label(HankoCatalog.getName(effect), skin);
        label.setFontScale(0.66f);
        label.setColor(COLOR_CREAM);
        tile.add(label).row();

        Label stock = new Label("Bestand: " + count, skin);
        stock.setFontScale(0.62f);
        stock.setColor(COLOR_MUTED);
        tile.add(stock).row();

        tile.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedHanko = effect;
                highlightTile(tile);
                punchActor(tile);
                showHankoDetail(effect, count);
            }
        });
        return tile;
    }

    /** Hanko-Details mit Beschreibung, Anwendungshinweis und Stempel-Aktion. */
    private void showHankoDetail(HankoEffect effect, int count) {
        Table panel = beginDetail(HankoCatalog.getName(effect), "Siegel · Bestand " + count);
        addDetailImage(panel, findHankoRegion(effect), 96f, 96f);
        addDetailText(panel, HankoCatalog.getDescription(effect), COLOR_MUTED, 0.8f);

        addDetailSection(panel, "Anwenden");
        addDetailText(panel,
            "Aktiviere den Stempelmodus und waehle danach eine Karte im Karten-Tab. "
                + "Ein vorhandener Hanko wird ersetzt.", COLOR_MUTED, 0.75f);

        TextButton stamp = new TextButton("Stempeln", buttonStyle);
        stamp.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedHankoToApply = effect;
                showDeckTab();
            }
        });
        addDetailPrimaryAction(panel, stamp);
    }

    // ---------------------------------------------------------------------
    // Omamori-Lager und drei aktive Slots
    // ---------------------------------------------------------------------

    /** Omamori-Tab: drei aktive Plaetze oben, Sammlung darunter, Details rechts. */
    private void showOmamoriTab() {
        contentArea.clearChildren();
        selectedTileActor = null;

        Label activeTitle = new Label(
            "Aktive Plätze (" + runSession.getActiveOmamoris().size()
                + "/" + runSession.getMaxActiveOmamoris() + ")", skin);
        activeTitle.setFontScale(0.95f);
        activeTitle.setColor(COLOR_GOLD);
        contentArea.add(activeTitle).growX().left().padBottom(6f).row();

        Table activeSlots = new Table();
        for (int slot = 0; slot < runSession.getMaxActiveOmamoris(); slot++) {
            Table slotBox = new Table();
            slotBox.setTransform(true);
            slotBox.setBackground(tileDrawable);
            slotBox.pad(8f);

            if (slot < runSession.getActiveOmamoris().size()) {
                Omamori omamori = runSession.getActiveOmamoris().get(slot);
                addActiveOmamoriSlotContent(slotBox, omamori, slot);
            } else {
                Label empty = new Label("Platz " + (slot + 1) + "\nLeer", skin);
                empty.setAlignment(Align.center);
                empty.setFontScale(0.72f);
                empty.setColor(COLOR_MUTED);
                slotBox.add(empty).grow().center();
            }

            activeSlots.add(slotBox).growX().height(140f).pad(5f);

            // Aktivieren eines Omamori: kurzer Puls des neuen Slots.
            if (slot == pulseSlotIndex) {
                punchActor(slotBox);
                pulseSlotIndex = -1;
            }
        }
        contentArea.add(activeSlots).growX().padBottom(14f).row();

        Label storageTitle = new Label("Sammlung", skin);
        storageTitle.setFontScale(0.95f);
        storageTitle.setColor(COLOR_GOLD);
        contentArea.add(storageTitle).growX().left().padBottom(6f).row();

        if (runSession.getOwnedOmamoris().isEmpty()) {
            Label empty = new Label("Noch keine Omamori gekauft.", skin);
            empty.setColor(COLOR_MUTED);
            contentArea.add(empty).growX().pad(16f).row();
            fadeInCollection();
            return;
        }

        for (Omamori omamori : runSession.getOwnedOmamoris()) {
            contentArea.add(createStoredOmamoriRow(omamori)).growX().pad(5f).row();
        }
        fadeInCollection();
    }

    /**
     * Inhalt eines aktiven Omamori-Platzes: anklickbare Kachel (kein Drag). Alle
     * Aktionen (Aktivieren, Deaktivieren, Nach links, Nach rechts) liegen im
     * gemeinsamen Detailpanel.
     */
    private void addActiveOmamoriSlotContent(Table slotBox, Omamori omamori, int slot) {
        if (omamori == selectedOmamori) {
            slotBox.setBackground(selectedTileDrawable);
        }

        Table content = new Table();
        content.setTouchable(Touchable.enabled);

        TextureRegion region = findOmamoriRegion(omamori);
        if (region != null) {
            Image image = new Image(region);
            image.setTouchable(Touchable.disabled);
            content.add(image).size(48f, 64f).padRight(8f);
        }

        Table text = new Table();
        text.left();
        text.setTouchable(Touchable.disabled);

        Label name = new Label((slot + 1) + ". " + omamori.getName(), skin);
        name.setFontScale(0.72f);
        name.setColor(COLOR_CREAM);
        text.add(name).left().row();

        Label state = new Label("Aktiv", skin);
        state.setFontScale(0.62f);
        state.setColor(COLOR_GOLD);
        text.add(state).left().row();

        Label hint = new Label("Klick fuer Details", skin);
        hint.setFontScale(0.58f);
        hint.setColor(COLOR_MUTED);
        text.add(hint).left();

        content.add(text).expand().fill();

        content.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedOmamori = omamori;
                highlightTile(slotBox);
                punchActor(slotBox);
                showOmamoriDetail(omamori);
            }
        });

        slotBox.add(content).expand().fill();
    }

    /**
     * Detailpanel eines Omamori: Vorschau, Badges, Beschreibung und die Aktionen
     * Aktivieren/Deaktivieren sowie Nach links/Nach rechts. Es wird ausschliesslich
     * die RunSession-Reihenfolge veraendert - kein freies Drag-and-drop.
     */
    private void showOmamoriDetail(Omamori omamori) {
        boolean active = runSession.isOmamoriActive(omamori);
        boolean slotsFull = runSession.getActiveOmamoris().size()
            >= runSession.getMaxActiveOmamoris();
        int slotIndex = runSession.getActiveOmamoris().indexOf(omamori);

        Rarity rarity = omamori.getRarity();
        String typeLine = (rarity == null ? "unbekannt" : rarity.name())
            + (active ? " · AKTIV · PLATZ " + (slotIndex + 1) : " · INAKTIV");

        Table panel = beginDetail(omamori.getName(), typeLine);
        addDetailImage(panel, findOmamoriRegion(omamori), 96f, 128f);
        addDetailBadges(panel,
            active ? "Aktiv" : "Inaktiv",
            slotIndex >= 0 ? "Platz " + (slotIndex + 1) + "/" + runSession.getMaxActiveOmamoris() : "nicht belegt");

        addDetailSection(panel, "Wirkung");
        addDetailText(panel, omamori.getDescription(), COLOR_MUTED, 0.8f);

        addDetailSection(panel, "Aktionen");

        TextButton activate = new TextButton("Aktivieren", buttonStyle);
        activate.setDisabled(active || slotsFull);
        activate.getColor().a = activate.isDisabled() ? 0.45f : 1f;
        activate.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!runSession.equipOmamori(omamori)) {
                    return;
                }
                // Kurzer Puls des neuen Slots.
                pulseSlotIndex = runSession.getActiveOmamoris().indexOf(omamori);
                showOmamoriTab();
                showOmamoriDetail(omamori);
            }
        });
        addDetailPrimaryAction(panel, activate);

        if (activate.isDisabled()) {
            addDetailText(panel,
                active ? "Dieses Omamori ist bereits aktiv."
                    : "Alle aktiven Plätze sind belegt.",
                COLOR_ACCENT_TEXT, 0.74f);
        }

        TextButton deactivate = new TextButton("Deaktivieren", buttonStyle);
        deactivate.setDisabled(!active);
        deactivate.getColor().a = deactivate.isDisabled() ? 0.45f : 1f;
        deactivate.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                runSession.unequipOmamori(omamori);
                // Sanfter Fade in den inaktiven Zustand.
                fadeInOmamori = omamori;
                showOmamoriTab();
                showOmamoriDetail(omamori);
            }
        });
        addDetailPrimaryAction(panel, deactivate);

        TextButton left = new TextButton("Nach links", buttonStyle);
        left.setDisabled(slotIndex <= 0);
        left.getColor().a = left.isDisabled() ? 0.45f : 1f;
        left.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                runSession.moveActiveOmamori(omamori, -1);
                showOmamoriTab();
                showOmamoriDetail(omamori);
            }
        });

        TextButton right = new TextButton("Nach rechts", buttonStyle);
        right.setDisabled(slotIndex < 0
            || slotIndex >= runSession.getActiveOmamoris().size() - 1);
        right.getColor().a = right.isDisabled() ? 0.45f : 1f;
        right.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                runSession.moveActiveOmamori(omamori, 1);
                showOmamoriTab();
                showOmamoriDetail(omamori);
            }
        });
        addDetailActionPair(panel, left, right);
    }

    /**
     * Zeile der Sammlung: anklickbare Kachel (kein Drag, keine Aktion direkt in der
     * Liste). Aktive Omamori sind markiert, inaktive gedaempft aber lesbar.
     */
    private Table createStoredOmamoriRow(Omamori omamori) {
        Table row = new Table();
        row.setTransform(true);
        row.setBackground(omamori == selectedOmamori ? selectedTileDrawable : tileDrawable);
        row.setTouchable(Touchable.enabled);
        row.pad(10f);

        boolean active = runSession.isOmamoriActive(omamori);
        boolean slotsFull = runSession.getActiveOmamoris().size()
            >= runSession.getMaxActiveOmamoris();

        TextureRegion region = findOmamoriRegion(omamori);
        if (region != null) {
            Image image = new Image(region);
            image.setTouchable(Touchable.disabled);
            image.setColor(1f, 1f, 1f, active ? 1f : 0.6f);
            row.add(image).size(54f, 72f).padRight(12f);
        }

        Table text = new Table();
        text.left();
        text.setTouchable(Touchable.disabled);

        Label state = new Label(active ? "Aktiv" : "Inaktiv", skin);
        state.setFontScale(0.68f);
        state.setColor(active ? COLOR_GOLD : COLOR_MUTED);

        Label name = new Label(omamori.getName(), skin);
        name.setFontScale(0.82f);
        name.setColor(active ? COLOR_CREAM : COLOR_MUTED);

        Table titleRow = new Table();
        titleRow.left();
        titleRow.add(state).padRight(8f);
        titleRow.add(name).left();

        text.add(titleRow).left().row();

        Label description = new Label(omamori.getDescription(), skin);
        description.setFontScale(0.72f);
        description.setWrap(true);
        description.setColor(COLOR_MUTED);
        text.add(description).growX().left().row();

        if (!active && slotsFull) {
            Label full = new Label("Alle aktiven Plätze sind belegt.", skin);
            full.setFontScale(0.68f);
            full.setColor(COLOR_ACCENT_TEXT);
            text.add(full).growX().left().padTop(2f).row();
        }

        Label hint = new Label("Klick fuer Details und Aktionen", skin);
        hint.setFontScale(0.66f);
        hint.setColor(COLOR_MUTED);
        text.add(hint).left().padTop(2f);

        row.add(text).growX();

        // Deaktivieren: sanfter Fade in den inaktiven Zustand.
        if (omamori == fadeInOmamori) {
            row.setColor(1f, 1f, 1f, 0.3f);
            row.addAction(Actions.fadeIn(0.22f));
            fadeInOmamori = null;
        }

        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedOmamori = omamori;
                highlightTile(row);
                punchActor(row);
                showOmamoriDetail(omamori);
            }
        });
        return row;
    }

    // ---------------------------------------------------------------------
    // Yaku-Tab: Fortschritt direkt aus RunSession.getYakuProgression()
    // ---------------------------------------------------------------------

    private void showYakuTab() {
        contentArea.clearChildren();
        selectedTileActor = null;

        Label title = new Label("Yaku-Fortschritt", skin);
        title.setFontScale(0.95f);
        title.setColor(COLOR_GOLD);
        contentArea.add(title).growX().left().padBottom(6f).row();

        // Stabile Enum-Reihenfolge, keine zusaetzliche Speicherung im UI.
        for (YakuType type : YakuType.values()) {
            contentArea.add(createYakuRow(type)).growX().pad(4f).row();
        }
        fadeInCollection();
    }

    /** Kompakte Listenzeile eines Yaku: Name, Level, XP-Fortschritt, Werte. */
    private Table createYakuRow(YakuType type) {
        YakuProgression progression = runSession.getYakuProgression();
        int required = progression.getXpToNextLevel(type);

        Table row = new Table();
        row.setTransform(true);
        row.setBackground(type == selectedYaku ? selectedTileDrawable : tileDrawable);
        row.setTouchable(Touchable.enabled);
        row.pad(9f);

        Table info = new Table();
        info.left();
        info.setTouchable(Touchable.disabled);

        Label name = new Label(type.getDisplayName(), skin);
        name.setFontScale(0.82f);
        name.setColor(COLOR_CREAM);
        info.add(name).left().row();

        Label level = new Label("Level " + progression.getLevel(type)
            + " · XP " + progression.getXp(type) + " / " + required, skin);
        level.setFontScale(0.7f);
        level.setColor(COLOR_MUTED);
        info.add(level).left().row();

        info.add(createCompactProgress(progression.getXp(type), required)).growX().left().padTop(2f).row();

        Label values = new Label(ScoreFormat.format(progression.getUpgradedChips(type))
            + " Chips × " + ScoreFormat.format(progression.getUpgradedMult(type)) + " Mult", skin);
        values.setFontScale(0.7f);
        values.setColor(COLOR_GOLD);
        info.add(values).left().padTop(2f);

        row.add(info).growX();

        row.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedYaku = type;
                highlightTile(row);
                punchActor(row);
                showYakuDetail(type);
            }
        });
        return row;
    }

    /** Schlanker Fortschrittsbalken fuer die Listenzeile. */
    private Table createCompactProgress(int current, int required) {
        Table track = new Table();
        track.setBackground(trackDrawable);

        Table fill = new Table();
        fill.setBackground(accentDrawable);

        float ratio = required <= 0 ? 1f : Math.max(0f, Math.min(1f, current / (float) required));
        track.add(fill).width(Value.percentWidth(ratio, track)).height(6f).left();
        track.add().expandX();
        return track;
    }

    /** Detailpanel eines Yaku: Level, XP-Schwelle, aktuelle und naechste Werte, Regel. */
    private void showYakuDetail(YakuType type) {
        YakuProgression progression = runSession.getYakuProgression();
        int level = progression.getLevel(type);
        int required = progression.getXpToNextLevel(type);

        Table panel = beginDetail(type.getDisplayName(), "LEVEL " + level);
        addDetailBadges(panel,
            "XP " + progression.getXp(type) + " / " + required,
            "Level " + level);

        addDetailSection(panel, "Fortschritt");
        addDetailProgress(panel, progression.getXp(type), required);

        addDetailSection(panel, "Aktuell");
        addDetailText(panel, ScoreFormat.format(progression.getUpgradedChips(type)) + " Chips × "
            + ScoreFormat.format(progression.getUpgradedMult(type)) + " Mult", COLOR_CREAM, 0.85f);
        addDetailText(panel, "Basis auf Level 1: "
            + ScoreFormat.format(type.getBaseChips()) + " Chips × "
            + ScoreFormat.format(type.getBaseMult()) + " Mult", COLOR_MUTED, 0.76f);

        addDetailSection(panel, "Nächstes Level");
        addDetailText(panel, "+" + ScoreFormat.format(YakuProgression.CHIPS_PER_LEVEL) + " Chips",
            COLOR_CREAM, 0.8f);
        addDetailText(panel, "+" + ScoreFormat.format(YakuProgression.MULT_PER_LEVEL) + " Mult",
            COLOR_CREAM, 0.8f);

        addDetailSection(panel, "Bedingung");
        addDetailText(panel, type.getDescription(), COLOR_MUTED, 0.78f);
    }

    private TextureRegion findOmamoriRegion(Omamori omamori) {
        String className = omamori.getClass().getSimpleName();
        String regionName = "OMAMORI_"
            + className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
        return atlas.findRegion(regionName);
    }

    // ---------------------------------------------------------------------
    // Shikigami-Tab: Portraet-Raster (nicht draggable), Details rechts
    // ---------------------------------------------------------------------

    private void showShikigamiTab() {
        contentArea.clearChildren();
        selectedTileActor = null;

        if (runSession.getShikigamiBag().isEmpty()) {
            Label empty = new Label("Keine Shikigami im Beutel.", skin);
            empty.setColor(COLOR_MUTED);
            contentArea.add(empty).growX().pad(16f).row();
            fadeInCollection();
            return;
        }

        Table grid = new Table();
        int column = 0;
        for (Shikigami shikigami : runSession.getShikigamiBag()) {
            grid.add(createShikigamiTile(shikigami)).pad(6f);
            column++;
            if (column % 3 == 0) {
                grid.row();
            }
        }
        contentArea.add(grid).growX().row();
        fadeInCollection();
    }

    /**
     * Inventarkachel eines Shikigami. Bewusst KEIN {@code JuicyDraggableActor}:
     * Bild, Name und Werte bleiben gemeinsam an ihrer Layoutposition und sind
     * nicht unabhaengig verschiebbar. Ein Klick oeffnet die Details.
     */
    private Table createShikigamiTile(Shikigami shikigami) {
        Table tile = new Table();
        tile.setTransform(true);
        tile.setBackground(shikigami == selectedShikigami ? selectedTileDrawable : tileDrawable);
        tile.setTouchable(Touchable.enabled);
        tile.pad(8f);

        TextureRegion region = atlas.findRegion(shikigami.getAtlasRegionName());
        if (region != null) {
            Image image = new Image(region);
            image.setTouchable(Touchable.disabled);
            // Erschoepfte Shikigami sind sichtbar gedaempft.
            image.setColor(1f, 1f, 1f, shikigami.isExhausted() ? 0.5f : 1f);
            tile.add(image).size(74f, 130f).row();
        }

        Label name = new Label(shikigami.getName(), skin);
        name.setFontScale(0.72f);
        name.setColor(shikigami.isExhausted() ? COLOR_MUTED : COLOR_CREAM);
        tile.add(name).row();

        Label badge = new Label("Lv " + shikigami.getLevel() + " · "
            + (shikigami.isExhausted() ? "Erschöpft" : "Bereit"), skin);
        badge.setFontScale(0.62f);
        badge.setColor(shikigami.isExhausted() ? COLOR_MUTED : COLOR_GOLD);
        tile.add(badge).row();

        tile.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedShikigami = shikigami;
                highlightTile(tile);
                punchActor(tile);
                showShikigamiDetail(shikigami);
            }
        });
        return tile;
    }

    private void showShikigamiDetail(Shikigami shikigami) {
        int required = shikigami.getXpToNextLevel();
        String typeLine = stageText(shikigami) + " · LEVEL " + shikigami.getLevel()
            + " · " + (shikigami.isExhausted() ? "ERSCHÖPFT" : "BEREIT");

        Table panel = beginDetail(shikigami.getName(), typeLine);
        addDetailImage(panel, atlas.findRegion(shikigami.getAtlasRegionName()), 110f, 190f);
        addDetailBadges(panel,
            "Level " + shikigami.getLevel() + "/" + Shikigami.MAX_LEVEL,
            shikigami.isExhausted() ? "Erschöpft" : "Bereit");

        addDetailSection(panel, "Erfahrung");
        if (required > 0) {
            addDetailText(panel, "XP " + shikigami.getCurrentXp() + " / " + required,
                COLOR_CREAM, 0.8f);
            addDetailProgress(panel, shikigami.getCurrentXp(), required);
        } else {
            addDetailText(panel, "XP " + shikigami.getCurrentXp(), COLOR_CREAM, 0.8f);
            addDetailText(panel, "Maximale Entwicklung erreicht", COLOR_GOLD, 0.8f);
        }

        addDetailSection(panel, "Fähigkeit");
        addDetailText(panel, shikigami.getName(), COLOR_CREAM, 0.84f);
        addDetailText(panel, shikigami.getDescription(), COLOR_MUTED, 0.78f);
    }
}
