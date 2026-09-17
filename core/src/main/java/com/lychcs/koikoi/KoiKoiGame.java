package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.ui.FirstScreen;
import com.lychcs.koikoi.ui.GrayScottTestScreen;

public class KoiKoiGame extends Game {
    private boolean screenChangePending = false;

    @Override
    public void create() {
        HankoShaderManager.initialize();
        FontManager.initialize();
        setScreen(new FirstScreen(this));
    }

    public void changeScreen(Screen nextScreen) {
        if (nextScreen == null) {
            throw new IllegalArgumentException("nextScreen darf nicht null sein");
        }

        if (screenChangePending) {
            if (nextScreen != getScreen()) {
                nextScreen.dispose();
            }
            return;
        }

        screenChangePending = true;
        Gdx.app.postRunnable(() -> {
            try {
                Screen previous = getScreen();
                setScreen(nextScreen);

                if (previous != null && previous != nextScreen) {
                    previous.dispose();
                }
            } finally {
                screenChangePending = false;
            }
        });
    }

    @Override
    public void dispose() {
        super.dispose();
        HankoShaderManager.dispose();
        FontManager.dispose();
    }
}
