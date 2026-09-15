package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.yokai.Yokai;
import com.lychcs.koikoi.run.RunSession;

public class InventoryOverlay extends Group {

    private final RunSession runSession;
    private final Skin skin;
    private final TextureAtlas atlas;
    private final NinePatchDrawable panelBackground;
    private final TextButton.TextButtonStyle buttonStyle;

    private final Table mainPanel;
    private final Table contentArea;
    private final Label currencyLabel;
    private final TextButton hankoTab;
    private boolean isOpen = false;

    // Stempel-Modus Zustand
    private HankoEffect selectedHankoToApply = null;

    public InventoryOverlay(RunSession session, Skin skin, TextureAtlas atlas,
                            NinePatchDrawable panelBg, TextButton.TextButtonStyle btnStyle) {
        this.runSession = session;
        this.skin = skin;
        this.atlas = atlas;
        this.panelBackground = panelBg;
        this.buttonStyle = btnStyle;

        setSize(1280, 720);
        setVisible(false);

        // Halbdunkler Hintergrund-Schleier
        Image dimBackground = new Image(skin.getRegion("white"));
        dimBackground.setSize(1280, 720);
        dimBackground.setColor(0f, 0f, 0f, 0.65f);
        addActor(dimBackground);

        // Zentrales Hauptfenster
        mainPanel = new Table();
        mainPanel.setSize(1000, 580);
        mainPanel.setPosition(140, 70);
        mainPanel.setOrigin(500, 290);
        mainPanel.setBackground(panelBackground);
        mainPanel.pad(20);

        // Header: Tabs & Währungen
        Table header = new Table();
        TextButton deckTab = new TextButton("Deck", buttonStyle);
        TextButton omamoriTab = new TextButton("Omamori", buttonStyle);
        TextButton yokaiTab = new TextButton("Yokai", buttonStyle);
        hankoTab = new TextButton("Hankos", buttonStyle);
        TextButton closeBtn = new TextButton("X", buttonStyle);

        currencyLabel = new Label("", skin);
        currencyLabel.setFontScale(0.9f);

        deckTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                selectedHankoToApply = null;
                showDeckTab();
            }
        });
        omamoriTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                selectedHankoToApply = null;
                showOmamoriTab();
            }
        });
        yokaiTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                selectedHankoToApply = null;
                showYokaiTab();
            }
        });
        hankoTab.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                showHankoTab();
            }
        });
        closeBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) {
                toggle();
            }
        });

        header.add(deckTab).width(115).height(45).padRight(8);
        header.add(omamoriTab).width(115).height(45).padRight(8);
        header.add(yokaiTab).width(115).height(45).padRight(8);
        header.add(hankoTab).width(135).height(45).padRight(20);
        header.add(currencyLabel).expandX().left();
        header.add(closeBtn).width(45).height(45).right();

        mainPanel.add(header).expandX().fillX().padBottom(15).row();

        // Inhaltsbereich (Scrollbar)
        contentArea = new Table();
        contentArea.top().left();

        ScrollPane scrollPane = new ScrollPane(contentArea, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        mainPanel.add(scrollPane).expand().fill().row();
        addActor(mainPanel);
    }

    public void toggle() {
        isOpen = !isOpen;

        if (isOpen) {
            selectedHankoToApply = null;
            updateCurrencies();
            updateHankoTabBadge();
            showDeckTab();
            setVisible(true);
            mainPanel.setScale(0.85f);
            mainPanel.setColor(1f, 1f, 1f, 0f);
            mainPanel.clearActions();
            mainPanel.addAction(Actions.parallel(
                Actions.scaleTo(1.0f, 1.0f, 0.18f, Interpolation.swingOut),
                Actions.fadeIn(0.12f)
            ));
        } else {
            selectedHankoToApply = null;
            mainPanel.clearActions();
            mainPanel.addAction(Actions.sequence(
                Actions.parallel(
                    Actions.scaleTo(0.9f, 0.9f, 0.12f, Interpolation.fade),
                    Actions.fadeOut(0.1f)
                ),
                Actions.run(() -> setVisible(false))
            ));
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    private void updateCurrencies() {
        currencyLabel.setText("Mon: " + runSession.getMon() + "  |  Void Dust: " + runSession.getVoidDust());
    }

    private void updateHankoTabBadge() {
        int count = runSession.getPurchasedHankos().size();
        hankoTab.setText(count > 0 ? "Hankos (" + count + ")" : "Hankos");
    }

    // =========================================================================
    // 1. DECK-ANSICHT (Inklusive Stempel-Leiste und Stempeln auf Klick)
    // =========================================================================
    private void showDeckTab() {
        contentArea.clearChildren();
        updateHankoTabBadge();

        // --- STEMPEL-LEISTE AM KOPF DER KARTEN (falls Siegel im Besitz sind) ---
        if (!runSession.getPurchasedHankos().isEmpty()) {
            Table shelf = new Table();
            shelf.setBackground(panelBackground);
            shelf.pad(10);

            Label shelfLabel = new Label("Hanko anwenden:", skin);
            shelfLabel.setFontScale(0.85f);
            shelf.add(shelfLabel).padRight(15);

            for (HankoEffect effect : runSession.getPurchasedHankos()) {
                TextureRegion hRegion = atlas.findRegion("HANKO_" + effect.name());
                Table hBtn = new Table();
                hBtn.pad(4);

                boolean isSelected = (selectedHankoToApply == effect);
                if (isSelected) {
                    hBtn.setBackground(panelBackground);
                }

                if (hRegion != null) {
                    Image icon = new Image(hRegion);
                    hBtn.add(icon).size(28, 28).padRight(6);
                }
                Label nameLbl = new Label(getHankoShortName(effect), skin);
                nameLbl.setFontScale(0.75f);
                if (isSelected) nameLbl.setColor(Color.GOLD);
                hBtn.add(nameLbl);

                hBtn.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent e, float x, float y) {
                        if (selectedHankoToApply == effect) {
                            selectedHankoToApply = null; // Klick deselektiert
                        } else {
                            selectedHankoToApply = effect;
                        }
                        showDeckTab();
                    }
                });

                shelf.add(hBtn).padRight(12);
            }

            // Abbrechen-Button bei aktivem Stempel
            if (selectedHankoToApply != null) {
                TextButton cancelBtn = new TextButton("Abbrechen", buttonStyle);
                cancelBtn.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent e, float x, float y) {
                        selectedHankoToApply = null;
                        showDeckTab();
                    }
                });
                shelf.add(cancelBtn).width(110).height(35).padLeft(10);
            }

            contentArea.add(shelf).expandX().fillX().padBottom(15).row();

            // Status-Hinweis
            if (selectedHankoToApply != null) {
                Label hint = new Label("Waehle eine Karte! (Bestehendes Siegel wird ueberschrieben)", skin);
                hint.setColor(Color.GOLD);
                hint.setFontScale(0.85f);
                hint.setAlignment(Align.center);
                contentArea.add(hint).padBottom(10).row();
            }
        }

        // --- KARTEN GRID ---
        Table cardsGrid = new Table();
        int col = 0;

        for (Card card : runSession.getPlayerDeck().getCards()) {
            TextureRegion region = atlas.findRegion(card.id().name());
            Table cardBox = new Table();

            Stack cardStack = new Stack();

            if (region != null) {
                Image cardImg = new Image(new TextureRegionDrawable(region));
                cardImg.setSize(60, 106);
                cardStack.add(cardImg);
            }

            // Falls die Karte bereits ein Siegel hat: Icon oben rechts anzeigen!
            if (card.hasHanko()) {
                TextureRegion hRegion = atlas.findRegion("HANKO_" + card.effect().name());
                if (hRegion != null) {
                    Image badge = new Image(hRegion);
                    Table badgeContainer = new Table();
                    badgeContainer.top().right();
                    badgeContainer.add(badge).size(20, 20).pad(2);
                    cardStack.add(badgeContainer);
                }
            }

            cardBox.add(cardStack).size(60, 106).padBottom(4).row();

            Label nameLbl = new Label(card.name(), skin);
            nameLbl.setFontScale(0.65f);
            cardBox.add(nameLbl).row();

            // Interaktion: Bei Klick Karte stempeln
            cardBox.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    if (selectedHankoToApply != null) {
                        applyHankoToCard(card, selectedHankoToApply);
                    } else {
                        // Kleiner Wackler beim normalen Anklicken
                        cardBox.clearActions();
                        cardBox.addAction(Actions.sequence(
                            Actions.scaleTo(1.15f, 1.15f, 0.08f),
                            Actions.scaleTo(1.0f, 1.0f, 0.1f)
                        ));
                    }
                }
            });

            cardsGrid.add(cardBox).pad(8);
            col++;
            if (col % 9 == 0) cardsGrid.row();
        }

        contentArea.add(cardsGrid).expand().fill().row();
    }

    /**
     * Tauscht das Siegel auf der Karte aus (überschreibt bestehende)
     * und entfernt das Siegel aus dem Vorrat.
     */
    private void applyHankoToCard(Card targetCard, HankoEffect newEffect) {
        // Neue Karte mit dem neuen Effekt erzeugen (altes Siegel wird verworfen)
        Card modifiedCard = new Card(
            targetCard.id(),
            targetCard.season(),
            targetCard.rank(),
            targetCard.name(),
            newEffect
        );

        // Im Deck austauschen
        runSession.getPlayerDeck().evolveCard(targetCard, modifiedCard);

        // Verbrauchen
        runSession.getPurchasedHankos().remove(newEffect);
        selectedHankoToApply = null;

        System.out.println("Hanko " + newEffect + " auf " + targetCard.name() + " aufgetragen!");

        // Ansicht aktualisieren
        showDeckTab();
    }

    // =========================================================================
    // 2. HANKOS TAB (Detaillierte Vorrats-Übersicht)
    // =========================================================================
    private void showHankoTab() {
        contentArea.clearChildren();
        updateHankoTabBadge();

        if (runSession.getPurchasedHankos().isEmpty()) {
            Table emptyBox = new Table();
            emptyBox.pad(40);
            Label emptyLbl = new Label("Keine Hankos im Vorrat.\nKaufe Booster-Packs im Markt, um neue Siegel zu erhalten!", skin);
            emptyLbl.setAlignment(Align.center);
            emptyLbl.setFontScale(1.0f);
            emptyBox.add(emptyLbl);
            contentArea.add(emptyBox).expand().center();
            return;
        }

        for (HankoEffect effect : runSession.getPurchasedHankos()) {
            Table itemBox = new Table();
            itemBox.setBackground(panelBackground);
            itemBox.pad(15);

            TextureRegion hRegion = atlas.findRegion("HANKO_" + effect.name());
            if (hRegion != null) {
                itemBox.add(new Image(hRegion)).size(48, 48).padRight(20);
            }

            Table textTable = new Table();
            textTable.left();
            Label title = new Label(getHankoShortName(effect), skin);
            title.setFontScale(1.0f);
            textTable.add(title).left().row();

            Label desc = new Label(getHankoDescription(effect), skin);
            desc.setFontScale(0.8f);
            desc.setColor(Color.LIGHT_GRAY);
            textTable.add(desc).left();

            itemBox.add(textTable).expandX().left();

            TextButton useBtn = new TextButton("Stempeln", buttonStyle);
            useBtn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent e, float x, float y) {
                    selectedHankoToApply = effect;
                    showDeckTab(); // Wechselt ins Deck mit aktiviertem Stempel
                }
            });

            itemBox.add(useBtn).width(130).height(45).padLeft(15);
            contentArea.add(itemBox).expandX().fillX().pad(8).row();
        }
    }

    // =========================================================================
    // 3. OMAMORI TAB
    // =========================================================================
    private void showOmamoriTab() {
        contentArea.clearChildren();

        for (Omamori omamori : runSession.getActiveOmamoris()) {
            String className = omamori.getClass().getSimpleName();
            String regionName = "OMAMORI_" + className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
            TextureRegion region = atlas.findRegion(regionName);

            if (region != null) {
                JuicyOmamoriActor actor = new JuicyOmamoriActor(omamori, region, new JuicyOmamoriActor.OmamoriListener() {
                    @Override public void onTap(JuicyOmamoriActor a) {}
                    @Override public void onDrop(JuicyOmamoriActor a, Vector2 p) {}
                });

                Table box = new Table();
                box.add(actor).size(72, 96).padRight(15);

                Table textTable = new Table();
                textTable.left();
                textTable.add(new Label(omamori.getName(), skin)).left().row();
                Label desc = new Label(omamori.getDescription(), skin);
                desc.setFontScale(0.8f);
                desc.setWrap(true);
                textTable.add(desc).width(500).left();

                box.add(textTable).expandX().fillX();
                contentArea.add(box).expandX().fillX().pad(10).row();
            }
        }
    }

    // =========================================================================
    // 4. YOKAI TAB
    // =========================================================================
    private void showYokaiTab() {
        contentArea.clearChildren();

        if (runSession.getYokaiBag() != null) {
            for (Yokai yokai : runSession.getYokaiBag()) {
                TextureRegion region = atlas.findRegion(yokai.getAtlasRegionName());
                if (region != null) {
                    JuicyYokaiActor actor = new JuicyYokaiActor(yokai, region, false, skin, new JuicyYokaiActor.YokaiListener() {
                        @Override public void onTap(JuicyYokaiActor a) {}
                        @Override public void onDrop(JuicyYokaiActor a, Vector2 p) {}
                    });

                    Table box = new Table();
                    box.add(actor).size(80, 142).padRight(15);

                    Table infoTable = new Table();
                    infoTable.left();
                    infoTable.add(new Label(yokai.getName(), skin)).left().row();
                    Label xpLbl = new Label("XP: " + yokai.getCurrentXp() + " / " + yokai.getXpToNextLevel(), skin);
                    xpLbl.setFontScale(0.8f);
                    infoTable.add(xpLbl).left().row();

                    box.add(infoTable).expandX().fillX();
                    contentArea.add(box).expandX().fillX().pad(8).row();
                }
            }
        }
    }

    // --- TEXT-HELPER FÜR HANKOS ---
    private String getHankoShortName(HankoEffect effect) {
        return switch (effect) {
            case WHITE_SEAL -> "White Seal";
            case BLACK_SEAL -> "Black Seal";
            case GOLDEN_SEAL -> "Golden Seal";
            case VOID_SEAL -> "Void Seal";
            case STONE_SEAL -> "Stone Seal";
            case POLYCHROME_SEAL -> "Polychrome Seal";
            case BLOOD_SEAL -> "Blood Seal";
            default -> effect.name();
        };
    }

    private String getHankoDescription(HankoEffect effect) {
        return switch (effect) {
            case WHITE_SEAL -> "+30 Basis-Chips beim Ausspielen.";
            case BLACK_SEAL -> "+4 Mult beim Ausspielen.";
            case GOLDEN_SEAL -> "Chance auf +3 oder +10 Mon bei Wertung.";
            case VOID_SEAL -> "Chance auf +5 oder +10 Void Dust bei Wertung.";
            case STONE_SEAL -> "Kehrt nach dem Ausspielen 1x auf die Hand zurueck.";
            case POLYCHROME_SEAL -> "Zaehlt gleichzeitig fuer alle 4 Jahreszeiten.";
            case BLOOD_SEAL -> "+50 Chips & +10 Mult, aber 25% Chance auf Verbannung.";
            default -> "Kein spezieller Effekt.";
        };
    }
}
