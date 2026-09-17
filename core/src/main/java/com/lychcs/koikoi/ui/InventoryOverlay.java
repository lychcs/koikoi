package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
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
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.lychcs.koikoi.model.Card;
import com.lychcs.koikoi.model.hanko.Hanko;
import com.lychcs.koikoi.model.hanko.HankoCatalog;
import com.lychcs.koikoi.model.hanko.HankoEffect;
import com.lychcs.koikoi.model.omamori.Omamori;
import com.lychcs.koikoi.model.shikigami.Shikigami;
import com.lychcs.koikoi.run.RunSession;

import java.util.EnumMap;
import java.util.Map;

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

    private boolean open;
    private HankoEffect selectedHankoToApply;
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
        this.panelBackground = panelBackground;
        this.buttonStyle = buttonStyle;

        setSize(1280f, 720f);
        setVisible(false);

        Image dimBackground = new Image(skin.getRegion("white"));
        dimBackground.setSize(1280f, 720f);
        dimBackground.setColor(0f, 0f, 0f, 0.65f);
        dimBackground.setTouchable(Touchable.enabled);
        dimBackground.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggle();
            }
        });
        addActor(dimBackground);

        mainPanel = new Table();
        mainPanel.setSize(1000f, 580f);
        mainPanel.setPosition(140f, 70f);
        mainPanel.setOrigin(500f, 290f);
        mainPanel.setBackground(panelBackground);
        mainPanel.setTouchable(Touchable.enabled);
        mainPanel.pad(20f);
        mainPanel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
            }
        });

        Table header = new Table();
        TextButton deckTab = new TextButton("Deck", buttonStyle);
        TextButton omamoriTab = new TextButton("Omamori", buttonStyle);
        TextButton shikigamiTab = new TextButton("Shikigami", buttonStyle);
        hankoTab = new TextButton("Hankos", buttonStyle);
        TextButton closeButton = new TextButton("X", buttonStyle);

        currencyLabel = new Label("", skin);
        currencyLabel.setFontScale(0.9f);

        deckTab.addListener(tabListener(this::showDeckTab));
        omamoriTab.addListener(tabListener(this::showOmamoriTab));
        shikigamiTab.addListener(tabListener(this::showShikigamiTab));
        hankoTab.addListener(tabListener(this::showHankoTab));
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                toggle();
            }
        });

        header.add(deckTab).width(115f).height(45f).padRight(8f);
        header.add(omamoriTab).width(115f).height(45f).padRight(8f);
        header.add(shikigamiTab).width(115f).height(45f).padRight(8f);
        header.add(hankoTab).width(135f).height(45f).padRight(20f);
        header.add(currencyLabel).expandX().left();
        header.add(closeButton).width(45f).height(45f).right();
        mainPanel.add(header).expandX().fillX().padBottom(15f).row();

        contentArea = new Table();
        contentArea.top().left();

        ScrollPane scrollPane = new ScrollPane(contentArea, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);
        mainPanel.add(scrollPane).expand().fill().row();

        addActor(mainPanel);
    }

    private ClickListener tabListener(Runnable action) {
        return new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                selectedHankoToApply = null;
                action.run();
            }
        };
    }

    public void toggle() {
        open = !open;

        if (open) {
            selectedHankoToApply = null;
            updateCurrencies();
            updateHankoTabBadge();
            showDeckTab();
            setVisible(true);

            mainPanel.setScale(0.85f);
            mainPanel.setColor(1f, 1f, 1f, 0f);
            mainPanel.clearActions();
            mainPanel.addAction(Actions.parallel(
                Actions.scaleTo(1f, 1f, 0.18f, Interpolation.swingOut),
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
        hankoTab.setText(count > 0 ? "Hankos (" + count + ")" : "Hankos");
    }

    // ---------------------------------------------------------------------
    // Deck und Hanko-Anwendung
    // ---------------------------------------------------------------------

    private void showDeckTab() {
        contentArea.clearChildren();
        updateHankoTabBadge();

        if (!runSession.getPurchasedHankos().isEmpty()) {
            contentArea.add(createHankoShelf()).expandX().fillX().padBottom(12f).row();
        }

        if (selectedHankoToApply != null) {
            Label hint = new Label(
                "Waehle eine Karte fuer "
                    + HankoCatalog.getName(selectedHankoToApply)
                    + ".\nEin bereits vorhandener Stempel wird ersetzt.",
                skin
            );
            hint.setColor(Color.GOLD);
            hint.setAlignment(Align.center);
            contentArea.add(hint).expandX().fillX().padBottom(10f).row();
        }

        Table cardsGrid = new Table();
        int column = 0;

        for (Card card : runSession.getPlayerDeck().getCards()) {
            cardsGrid.add(createCardBox(card)).pad(8f);
            column++;
            if (column % 9 == 0) {
                cardsGrid.row();
            }
        }

        contentArea.add(cardsGrid).expand().fill().row();
    }

    private Table createHankoShelf() {
        Table shelf = new Table();
        shelf.setBackground(panelBackground);
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
                button.setBackground(panelBackground);
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

    private Table createCardBox(Card card) {
        Table cardBox = new Table();
        cardBox.setTransform(true);
        cardBox.setOrigin(30f, 60f);

        Hanko selectedHanko = selectedHankoToApply == null
            ? null
            : HankoCatalog.create(selectedHankoToApply);
        boolean canApply = selectedHanko == null || selectedHanko.canTarget(card);

        cardBox.setTouchable(canApply ? Touchable.enabled : Touchable.disabled);

        Stack cardStack = new Stack();
        TextureRegion cardRegion = atlas.findRegion(card.id().name());
        if (cardRegion != null) {
            Image cardImage = new Image(new TextureRegionDrawable(cardRegion));
            cardImage.setColor(1f, 1f, 1f, canApply ? 1f : 0.38f);
            cardStack.add(cardImage);
        }

        if (card.hasHanko()) {
            TextureRegion hankoRegion = findHankoRegion(card.effect());
            if (hankoRegion != null) {
                Table badgeContainer = new Table();
                badgeContainer.top().right();
                badgeContainer.add(new Image(hankoRegion)).size(20f).pad(2f);
                cardStack.add(badgeContainer);
            }
        }

        cardBox.add(cardStack).size(60f, 106f).padBottom(4f).row();

        Label name = new Label(card.name(), skin);
        name.setFontScale(0.62f);
        if (selectedHanko != null) {
            name.setColor(canApply ? Color.GOLD : Color.GRAY);
        }
        cardBox.add(name).width(82f).row();

        if (selectedHanko != null) {
            if (!canApply) {
                Label blocked = new Label("Nicht moeglich", skin);
                blocked.setFontScale(0.55f);
                blocked.setColor(Color.GRAY);
                cardBox.add(blocked);
            } else if (card.hasHanko()) {
                Label replace = new Label("Wird ersetzt", skin);
                replace.setFontScale(0.55f);
                replace.setColor(Color.GOLD);
                cardBox.add(replace);
            }
        }

        cardBox.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (selectedHankoToApply != null) {
                    requestHankoConfirmation(card, selectedHankoToApply);
                    return;
                }

                cardBox.clearActions();
                cardBox.addAction(Actions.sequence(
                    Actions.scaleTo(1.15f, 1.15f, 0.08f),
                    Actions.scaleTo(1f, 1f, 0.1f)
                ));
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

    private void showHankoTab() {
        contentArea.clearChildren();
        updateHankoTabBadge();

        if (runSession.getPurchasedHankos().isEmpty()) {
            Label empty = new Label(
                "Keine Hankos im Vorrat.\nKaufe sie im Tengu-Markt.",
                skin
            );
            empty.setAlignment(Align.center);
            contentArea.add(empty).expand().center();
            return;
        }

        Map<HankoEffect, Integer> counts = new EnumMap<>(HankoEffect.class);
        for (HankoEffect effect : runSession.getPurchasedHankos()) {
            counts.merge(effect, 1, Integer::sum);
        }

        for (Map.Entry<HankoEffect, Integer> entry : counts.entrySet()) {
            HankoEffect effect = entry.getKey();
            Table item = new Table();
            item.setBackground(panelBackground);
            item.pad(15f);

            TextureRegion region = findHankoRegion(effect);
            if (region != null) {
                item.add(new Image(region)).size(48f).padRight(20f);
            }

            Table text = new Table();
            text.left();
            Label title = new Label(
                HankoCatalog.getName(effect) + " x" + entry.getValue(),
                skin
            );
            Label description = new Label(HankoCatalog.getDescription(effect), skin);
            description.setFontScale(0.8f);
            description.setColor(Color.LIGHT_GRAY);
            description.setWrap(true);
            text.add(title).left().row();
            text.add(description).width(500f).left();
            item.add(text).expandX().fillX();

            TextButton use = new TextButton("Stempeln", buttonStyle);
            use.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    selectedHankoToApply = effect;
                    showDeckTab();
                }
            });
            item.add(use).width(130f).height(45f).padLeft(15f);

            contentArea.add(item).expandX().fillX().pad(8f).row();
        }
    }

    // ---------------------------------------------------------------------
    // Omamori-Lager und drei aktive Slots
    // ---------------------------------------------------------------------

    private void showOmamoriTab() {
        contentArea.clearChildren();

        Label activeTitle = new Label(
            "Aktive Omamori (" + runSession.getActiveOmamoris().size()
                + "/" + runSession.getMaxActiveOmamoris() + ")",
            skin
        );
        activeTitle.setFontScale(1.05f);
        contentArea.add(activeTitle).left().padBottom(8f).row();

        Table activeSlots = new Table();
        for (int slot = 0; slot < runSession.getMaxActiveOmamoris(); slot++) {
            Table slotBox = new Table();
            slotBox.setBackground(panelBackground);
            slotBox.pad(8f);

            if (slot < runSession.getActiveOmamoris().size()) {
                Omamori omamori = runSession.getActiveOmamoris().get(slot);
                addActiveOmamoriSlotContent(slotBox, omamori, slot);
            } else {
                Label empty = new Label("Slot " + (slot + 1) + "\nLeer", skin);
                empty.setAlignment(Align.center);
                empty.setColor(Color.GRAY);
                slotBox.add(empty).expand().center();
            }

            activeSlots.add(slotBox).width(285f).height(155f).pad(6f);
        }
        contentArea.add(activeSlots).expandX().fillX().padBottom(18f).row();

        Label storageTitle = new Label("Omamori-Lager", skin);
        storageTitle.setFontScale(1.05f);
        contentArea.add(storageTitle).left().padBottom(8f).row();

        if (runSession.getOwnedOmamoris().isEmpty()) {
            Label empty = new Label("Noch keine Omamori gekauft.", skin);
            empty.setColor(Color.LIGHT_GRAY);
            contentArea.add(empty).left().pad(20f).row();
            return;
        }

        for (Omamori omamori : runSession.getOwnedOmamoris()) {
            contentArea.add(createStoredOmamoriRow(omamori))
                .expandX()
                .fillX()
                .pad(7f)
                .row();
        }
    }

    private void addActiveOmamoriSlotContent(Table slotBox, Omamori omamori, int slot) {
        TextureRegion region = findOmamoriRegion(omamori);
        if (region != null) {
            slotBox.add(new Image(region)).size(48f, 64f).padRight(8f);
        }

        Table text = new Table();
        text.left();
        Label name = new Label((slot + 1) + ". " + omamori.getName(), skin);
        name.setFontScale(0.75f);
        text.add(name).left().colspan(3).row();

        TextButton left = new TextButton("<", buttonStyle);
        left.setDisabled(slot == 0);
        left.getColor().a = left.isDisabled() ? 0.4f : 1f;
        left.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.moveActiveOmamori(omamori, -1)) {
                    showOmamoriTab();
                }
            }
        });

        TextButton remove = new TextButton("Ablegen", buttonStyle);
        remove.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                runSession.unequipOmamori(omamori);
                showOmamoriTab();
            }
        });

        TextButton right = new TextButton(">", buttonStyle);
        right.setDisabled(slot >= runSession.getActiveOmamoris().size() - 1);
        right.getColor().a = right.isDisabled() ? 0.4f : 1f;
        right.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.moveActiveOmamori(omamori, 1)) {
                    showOmamoriTab();
                }
            }
        });

        text.add(left).size(35f, 30f).padTop(8f);
        text.add(remove).width(90f).height(30f).pad(8f, 4f, 0f, 4f);
        text.add(right).size(35f, 30f).padTop(8f);
        slotBox.add(text).expand().fill();
    }

    private Table createStoredOmamoriRow(Omamori omamori) {
        Table row = new Table();
        row.setBackground(panelBackground);
        row.pad(12f);

        TextureRegion region = findOmamoriRegion(omamori);
        if (region != null) {
            row.add(new Image(region)).size(60f, 80f).padRight(15f);
        }

        Table text = new Table();
        text.left();
        Label name = new Label(omamori.getName(), skin);
        Label description = new Label(omamori.getDescription(), skin);
        description.setFontScale(0.78f);
        description.setWrap(true);
        description.setColor(Color.LIGHT_GRAY);
        text.add(name).left().row();
        text.add(description).width(570f).left();
        row.add(text).expandX().fillX();

        boolean active = runSession.isOmamoriActive(omamori);
        boolean slotsFull = runSession.getActiveOmamoris().size()
            >= runSession.getMaxActiveOmamoris();

        TextButton equip = new TextButton(active ? "Aktiv" : "Ausruesten", buttonStyle);
        equip.setDisabled(active || slotsFull);
        equip.getColor().a = equip.isDisabled() ? 0.45f : 1f;
        equip.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (runSession.equipOmamori(omamori)) {
                    showOmamoriTab();
                }
            }
        });
        row.add(equip).width(135f).height(42f).padLeft(12f);
        return row;
    }

    private TextureRegion findOmamoriRegion(Omamori omamori) {
        String className = omamori.getClass().getSimpleName();
        String regionName = "OMAMORI_"
            + className.replaceAll("([a-z])([A-Z]+)", "$1_$2").toUpperCase();
        return atlas.findRegion(regionName);
    }

    // ---------------------------------------------------------------------
    // Shikigami-Ansicht (bestehendes Verhalten)
    // ---------------------------------------------------------------------

    private void showShikigamiTab() {
        contentArea.clearChildren();

        for (Shikigami shikigami : runSession.getShikigamiBag()) {
            TextureRegion region = atlas.findRegion(shikigami.getAtlasRegionName());
            if (region == null) {
                continue;
            }

            JuicyShikigamiActor actor = new JuicyShikigamiActor(
                shikigami,
                region,
                false,
                skin,
                new JuicyShikigamiActor.ShikigamiListener() {
                    @Override
                    public void onTap(JuicyShikigamiActor actor) {}

                    @Override
                    public void onDrop(JuicyShikigamiActor actor, Vector2 position) {}
                }
            );

            Table box = new Table();
            box.add(actor).size(80f, 142f).padRight(15f);

            Table info = new Table();
            info.left();
            info.add(new Label(shikigami.getName(), skin)).left().row();
            Label xp = new Label(
                "XP: " + shikigami.getCurrentXp() + " / " + shikigami.getXpToNextLevel(),
                skin
            );
            xp.setFontScale(0.8f);
            info.add(xp).left();

            box.add(info).expandX().fillX();
            contentArea.add(box).expandX().fillX().pad(8f).row();
        }
    }
}
