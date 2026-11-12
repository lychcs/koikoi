package com.lychcs.koikoi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.run.GameSeason;

/**
 * One framed Hanafuda slot of the bottom-centre 3P deck.
 *
 * <p>The border is colour-coded by card archetype (Crimson/Petal,
 * Azure/Ribbon, Gold/Beast). The slot brightens while its card is cycling and
 * switches the key hint to {@code LOCKED} when the deck holds it. The dark
 * translucent backing keeps the name legible over the ASCII world.</p>
 */
public final class HudCardSlot extends Table {

    private static final Color BACKING = new Color(0.020f, 0.028f, 0.050f, 0.88f);
    private static final Color NAME_IDLE = new Color(0.84f, 0.86f, 0.80f, 1f);
    private static final Color NAME_ACTIVE = new Color(1.00f, 0.97f, 0.86f, 1f);
    private static final Color KEY_IDLE = new Color(0.60f, 0.65f, 0.60f, 1f);
    private static final Color KEY_ACTIVE = new Color(1.00f, 0.86f, 0.42f, 1f);

    private final Drawable borderIdle;
    private final Drawable borderActive;
    private final Color typeColor;
    private final Color kindIdle;
    private final Color kindActive;
    private final Label glyphLabel;
    private final Image cardImage;
    private final Label kindLabel;
    private final Label nameLabel;
    private final Label keyLabel;

    public HudCardSlot(Skin skin, TextureRegion pixel, Color typeColor,
                       String glyph, String kind, String keyHint) {
        this(skin, pixel, null, typeColor, glyph, kind, keyHint);
    }

    public HudCardSlot(Skin skin, TextureRegion pixel, TextureRegion cardArt,
                       Color typeColor, String glyph, String kind, String keyHint) {
        if (skin == null) {
            throw new IllegalArgumentException("skin must not be null");
        }
        if (pixel == null) {
            throw new IllegalArgumentException("pixel must not be null");
        }
        if (typeColor == null) {
            throw new IllegalArgumentException("typeColor must not be null");
        }
        if (cardArt != null) {
            // The atlas is already nearest-filtered, but making it explicit here
            // keeps the hand-drawn card pixels sharp at HUD scale.
            cardArt.getTexture().setFilter(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        this.typeColor = new Color(typeColor);
        this.kindIdle = new Color(typeColor).mul(0.82f);
        this.kindActive = new Color(typeColor);
        this.borderIdle = new TextureRegionDrawable(pixel).tint(new Color(
            typeColor.r * 0.62f, typeColor.g * 0.62f, typeColor.b * 0.62f, 0.95f));
        this.borderActive = new TextureRegionDrawable(pixel).tint(new Color(
            Math.min(1f, typeColor.r + 0.22f),
            Math.min(1f, typeColor.g + 0.22f),
            Math.min(1f, typeColor.b + 0.22f), 1f));

        setBackground(borderIdle);
        pad(2f);

        Table inner = new Table();
        inner.setBackground(new TextureRegionDrawable(pixel).tint(BACKING));
        inner.pad(5f, 7f, 5f, 7f);

        Table artFrame = new Table();
        artFrame.setBackground(new TextureRegionDrawable(pixel).tint(new Color(
            typeColor.r * 0.36f, typeColor.g * 0.36f, typeColor.b * 0.36f, 1f)));
        artFrame.pad(2f);
        if (cardArt != null) {
            cardImage = new Image(new TextureRegionDrawable(cardArt));
            cardImage.setScaling(Scaling.fit);
            artFrame.add(cardImage).size(48f, 86f);
            glyphLabel = null;
        } else {
            cardImage = null;
            glyphLabel = new Label(glyph == null ? "?" : glyph, skin);
            glyphLabel.setFontScale(1.15f);
            glyphLabel.setColor(this.typeColor);
            artFrame.add(glyphLabel).size(48f, 86f);
        }

        kindLabel = new Label(kind == null ? "" : kind, skin);
        kindLabel.setFontScale(0.56f);
        kindLabel.setColor(this.typeColor);
        kindLabel.setEllipsis(true);

        nameLabel = new Label("", skin);
        nameLabel.setFontScale(0.68f);
        nameLabel.setColor(NAME_IDLE);
        nameLabel.setWrap(true);
        nameLabel.setAlignment(Align.left);

        keyLabel = new Label("[E] [R] [RMB]", skin);
        keyLabel.setFontScale(0.48f);
        keyLabel.setColor(KEY_IDLE);

        inner.add(artFrame).width(54f).growY().padRight(7f);
        Table textColumn = new Table();
        textColumn.top().left();
        textColumn.add(kindLabel).left().expandX().fillX().row();
        textColumn.add(nameLabel).left().expandX().fillX().padTop(2f).row();
        textColumn.add(keyLabel).left().padTop(2f).row();
        inner.add(textColumn).growX().top();
        add(inner).grow();
    }

    /**
     * Updates the displayed Hanafuda name and selection state.
     *
     * @param name   clean card name of this archetype
     * @param active true while this slot is the cycling highlight
     * @param locked true when the active card has been locked in
     */
    public void setState(String name, boolean active, boolean locked) {
        nameLabel.setText(name == null ? "?" : name);
        boolean highlight = active || locked;
        setBackground(highlight ? borderActive : borderIdle);
        if (glyphLabel != null) {
            glyphLabel.setColor(highlight ? NAME_ACTIVE : typeColor);
        }
        kindLabel.setColor(highlight ? kindActive : kindIdle);
        nameLabel.setColor(highlight ? NAME_ACTIVE : NAME_IDLE);
        keyLabel.setText("[E] [R] [RMB]");
        keyLabel.setColor(highlight ? KEY_ACTIVE : KEY_IDLE);
    }

    /**
     * Existing hand-drawn atlas region for an archetype and season. The HUD
     * uses the same source artwork as the inventory/card battle.
     */
    public static String cardArtRegionName(CardDeckComponent.CardType type,
                                           GameSeason season) {
        String seasonKey;
        if (season == null) {
            seasonKey = "SPRING";
        } else {
            seasonKey = switch (season) {
                case SPRING, FINAL -> "SPRING";
                case SUMMER -> "SUMMER";
                case AUTUMN -> "AUTUMN";
                case WINTER -> "WINTER";
            };
        }
        if (type == null) {
            return seasonKey + "_PETAL";
        }
        return switch (type) {
            case RED -> seasonKey + "_PETAL";
            case BLUE -> seasonKey + "_RIBBON";
            case GOLD -> switch (seasonKey) {
                case "SUMMER" -> "SUMMER_BEAST_MOUNTAIN_MONKEY";
                case "AUTUMN" -> "AUTUMN_BEAST_BOAR";
                case "WINTER" -> "WINTER_BEAST_ARCTIC_FOX";
                default -> "SPRING_BEAST_CRANE";
            };
        };
    }
}
