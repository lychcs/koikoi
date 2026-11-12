package com.lychcs.koikoi.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.lychcs.koikoi.graphics.FontManager;

/**
 * Washi-parchment NPC conversation overlay.
 *
 * <p>Left: a monospaced Shift-JIS/ASCII portrait with procedural blink and
 * mouth characters. Right: typewriter dialogue and keyboard/mouse-selectable
 * merchant choices. The owning screen treats this overlay as a combat pause.</p>
 */
public final class DialogueOverlay extends Group implements Disposable {

    private static final Color COLOR_SABLE = new Color(0.035f, 0.030f, 0.028f, 1f);
    private static final Color COLOR_WASHI = new Color(0.16f, 0.135f, 0.11f, 0.98f);
    private static final Color COLOR_INK = new Color(0.08f, 0.075f, 0.06f, 1f);
    private static final Color COLOR_CREAM = new Color(0.94f, 0.89f, 0.78f, 1f);
    private static final Color COLOR_MUTED = new Color(0.68f, 0.62f, 0.52f, 1f);
    private static final Color COLOR_GOLD = new Color(0.88f, 0.70f, 0.32f, 1f);
    private static final Color COLOR_VERMILLION = new Color(0.74f, 0.18f, 0.15f, 1f);

    private static final String INITIAL_TEXT =
        "Welcome, wanderer. The lanterns are quiet and the temple bell has not "
            + "yet rung. How may this humble shrine serve you?";
    private static final String HEAL_TEXT =
        "The shrine's blessing settles over you. May your spirit burn steady "
            + "through the next yokai encounter.";
    private static final String[] CHOICE_LABELS = {
        "Heal spirit / HP", "Inspect Hanafuda deck", "Close"
    };

    private final Runnable healAction;
    private final Runnable inspectAction;
    private final Skin skin;
    private final Drawable panelBackground;
    private final BitmapFont portraitFont;
    private final Image dimBackground;
    private final Table root;
    private final Table mainPanel;
    private final Table choicesTable;
    private final Label speakerLabel;
    private final Label portraitLabel;
    private final Label dialogueLabel;
    private final Label hintLabel;
    private final TextButton[] choiceButtons = new TextButton[CHOICE_LABELS.length];

    private boolean open;
    private float revealedCharacters;
    private float portraitClock;
    private float lastPortraitUpdate = -1f;
    private int selectedChoice;
    private String fullText = "";

    public DialogueOverlay(Skin skin, NinePatchDrawable panelBackground,
                           TextButton.TextButtonStyle buttonStyle,
                           Runnable healAction, Runnable inspectAction) {
        if (skin == null) {
            throw new IllegalArgumentException("skin must not be null");
        }
        if (panelBackground == null) {
            throw new IllegalArgumentException("panelBackground must not be null");
        }
        if (buttonStyle == null) {
            throw new IllegalArgumentException("buttonStyle must not be null");
        }
        this.skin = skin;
        this.panelBackground = panelBackground;
        this.healAction = healAction;
        this.inspectAction = inspectAction;
        this.portraitFont = FontManager.createMonoFont(18);
        this.portraitFont.getRegion().getTexture().setFilter(
            Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        this.portraitFont.setUseIntegerPositions(true);

        setSize(0f, 0f);
        setVisible(false);

        dimBackground = new Image(skin.getRegion("white"));
        dimBackground.setColor(0f, 0f, 0f, 0.70f);
        dimBackground.setTouchable(Touchable.enabled);
        dimBackground.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                close();
            }
        });
        addActor(dimBackground);

        root = new Table();
        root.pad(18f);
        addActor(root);

        Table sableFrame = new Table();
        sableFrame.setBackground(skin.newDrawable("white", COLOR_SABLE));
        sableFrame.pad(3f);

        mainPanel = new Table();
        mainPanel.setBackground(panelBackground.tint(COLOR_WASHI));
        mainPanel.setTouchable(Touchable.enabled);
        mainPanel.pad(20f);
        mainPanel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                event.stop();
            }
        });
        sableFrame.add(mainPanel).grow();
        root.add(sableFrame).width(920f).height(430f).center();

        Table header = new Table();
        speakerLabel = new Label("SHRINE MAIDEN", skin);
        speakerLabel.setFontScale(1.15f);
        speakerLabel.setColor(COLOR_GOLD);
        Label shiftLabel = new Label("SHIFT-JIS // ASCII PORTRAIT", skin);
        shiftLabel.setFontScale(0.52f);
        shiftLabel.setColor(COLOR_MUTED);
        header.add(speakerLabel).left().expandX();
        header.add(shiftLabel).right();
        mainPanel.add(header).growX().row();

        Table rule = new Table();
        rule.setBackground(skin.newDrawable("white", COLOR_VERMILLION));
        mainPanel.add(rule).growX().height(2f).padTop(6f).padBottom(12f).row();

        Table body = new Table();
        Table portraitFrame = new Table();
        portraitFrame.setBackground(skin.newDrawable("white", COLOR_INK));
        portraitFrame.pad(10f);
        Label.LabelStyle portraitStyle = new Label.LabelStyle(portraitFont, COLOR_CREAM);
        portraitLabel = new Label("", portraitStyle);
        portraitLabel.setAlignment(Align.center);
        portraitLabel.setFontScale(0.82f);
        portraitFrame.add(portraitLabel).width(220f).height(220f);
        body.add(portraitFrame).width(242f).growY().padRight(18f);

        Table textColumn = new Table();
        textColumn.top().left();
        dialogueLabel = new Label("", skin);
        dialogueLabel.setWrap(true);
        dialogueLabel.setFontScale(0.90f);
        dialogueLabel.setColor(COLOR_CREAM);
        dialogueLabel.setAlignment(Align.topLeft);
        textColumn.add(dialogueLabel).growX().minHeight(92f).row();

        hintLabel = new Label("[UP]/[DOWN] select   [ENTER]/[SPACE] choose   [ESC] close",
            skin);
        hintLabel.setFontScale(0.52f);
        hintLabel.setColor(COLOR_MUTED);
        textColumn.add(hintLabel).growX().left().padTop(8f).padBottom(10f).row();

        TextButton.TextButtonStyle choiceStyle = dialogueButtonStyle(buttonStyle);
        choicesTable = new Table();
        for (int i = 0; i < CHOICE_LABELS.length; i++) {
            final int choice = i;
            TextButton button = new TextButton(CHOICE_LABELS[i], choiceStyle);
            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!isTypingComplete()) {
                        completeTyping();
                    } else {
                        activateChoice(choice);
                    }
                }
            });
            choiceButtons[i] = button;
            choicesTable.add(button).growX().minWidth(0f).height(36f).padBottom(6f).row();
        }
        textColumn.add(choicesTable).growX().left().row();
        body.add(textColumn).grow();
        mainPanel.add(body).grow().row();
    }


    /** Opens the overlay from the world NPC interaction. */
    public void open() {
        if (open) {
            return;
        }
        open = true;
        selectedChoice = 0;
        portraitClock = 0f;
        lastPortraitUpdate = -1f;
        setVisible(true);
        toFront();
        syncSizeToStage();
        setDialogue(INITIAL_TEXT);
        updateChoiceAppearance();
        mainPanel.setColor(1f, 1f, 1f, 0f);
        mainPanel.clearActions();
        mainPanel.addAction(Actions.fadeIn(0.12f));
    }

    /** Closes the overlay and releases combat-input locking on the next frame. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        mainPanel.clearActions();
        setVisible(false);
    }

    public boolean isOpen() {
        return open;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        syncSizeToStageIfNeeded();
        if (!open) {
            return;
        }

        portraitClock += delta;
        if (!isTypingComplete()) {
            revealedCharacters = Math.min(fullText.length(),
                revealedCharacters + Math.max(0f, delta) * 44f);
            dialogueLabel.setText(fullText.substring(0,
                Math.min(fullText.length(), (int) revealedCharacters)));
            if (isTypingComplete()) {
                dialogueLabel.setText(fullText);
            }
        }
        if (lastPortraitUpdate < 0f || portraitClock - lastPortraitUpdate >= 0.09f) {
            updatePortrait();
            lastPortraitUpdate = portraitClock;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            selectedChoice = Math.floorMod(selectedChoice - 1, choiceButtons.length);
            updateChoiceAppearance();
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            selectedChoice = Math.floorMod(selectedChoice + 1, choiceButtons.length);
            updateChoiceAppearance();
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            selectAndActivate(0);
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            selectAndActivate(1);
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            selectAndActivate(2);
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
            || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            if (!isTypingComplete()) {
                completeTyping();
            } else {
                activateChoice(selectedChoice);
            }
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            close();
        }
    }

    private void selectAndActivate(int choice) {
        if (!isTypingComplete()) {
            completeTyping();
            return;
        }
        selectedChoice = Math.floorMod(choice, choiceButtons.length);
        updateChoiceAppearance();
        activateChoice(selectedChoice);
    }

    private void activateChoice(int choice) {
        switch (choice) {
            case 0 -> {
                if (healAction != null) {
                    healAction.run();
                }
                setDialogue(HEAL_TEXT);
            }
            case 1 -> {
                close();
                if (inspectAction != null) {
                    inspectAction.run();
                }
            }
            case 2 -> close();
            default -> {
            }
        }
    }

    private void setDialogue(String text) {
        fullText = text == null ? "" : text;
        revealedCharacters = 0f;
        dialogueLabel.setText("");
        updatePortrait();
    }

    private boolean isTypingComplete() {
        return revealedCharacters >= fullText.length();
    }

    private void completeTyping() {
        revealedCharacters = fullText.length();
        dialogueLabel.setText(fullText);
    }

    private void updateChoiceAppearance() {
        for (int i = 0; i < choiceButtons.length; i++) {
            TextButton button = choiceButtons[i];
            boolean selected = i == selectedChoice;
            button.setText((selected ? "> " : "  ") + CHOICE_LABELS[i]);
            button.getLabel().setColor(selected ? COLOR_GOLD : COLOR_CREAM);
        }
    }

    private void updatePortrait() {
        boolean blinking = (portraitClock % 4.4f) < 0.13f;
        String eye = blinking ? "-" : "o";
        String mouth;
        if (isTypingComplete()) {
            mouth = "_";
        } else {
            mouth = switch ((int) (portraitClock * 9f) % 3) {
                case 0 -> "^";
                case 1 -> "o";
                default -> "_";
            };
        }
        String art =
            "      .-~~~-.\n"
                + "     /  " + eye + " " + eye + "  \\\n"
                + "    |    " + mouth + "    |\n"
                + "    |   ---   |  [##]\n"
                + "     \\  ---  /   /\n"
                + "      '-...-'\n"
                + "       /|_|\\";
        portraitLabel.setText(art);
    }

    private TextButton.TextButtonStyle dialogueButtonStyle(
        TextButton.TextButtonStyle base) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = base.up;
        style.down = base.down;
        style.over = base.over;
        style.font = base.font;
        style.fontColor = COLOR_CREAM;
        style.overFontColor = COLOR_GOLD;
        style.downFontColor = COLOR_GOLD;
        style.checkedFontColor = COLOR_GOLD;
        return style;
    }

    private void syncSizeToStage() {
        Stage stage = getStage();
        if (stage == null) {
            return;
        }
        Viewport viewport = stage.getViewport();
        float width = viewport.getWorldWidth();
        float height = viewport.getWorldHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }
        setPosition(0f, 0f);
        setSize(width, height);
        dimBackground.setBounds(0f, 0f, width, height);
        root.setBounds(0f, 0f, width, height);
        root.invalidateHierarchy();
        root.validate();
    }

    private void syncSizeToStageIfNeeded() {
        Stage stage = getStage();
        if (stage == null) {
            return;
        }
        Viewport viewport = stage.getViewport();
        if (viewport.getWorldWidth() != getWidth()
            || viewport.getWorldHeight() != getHeight()) {
            syncSizeToStage();
        }
    }

    @Override
    public void dispose() {
        if (portraitFont != null) {
            portraitFont.dispose();
        }
    }
}

