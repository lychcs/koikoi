package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.GameAssets;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.ui.FirstScreen;

public class KoiKoiGame extends Game {

    /** Einziger Eigentuemer der gemeinsam verwendeten Dateiassets. */
    private final GameAssets assets = new GameAssets();

    private boolean screenChangePending = false;

    @Override
    public void create() {
        HankoShaderManager.initialize();
        FontManager.initialize();
        assets.load();
        setScreen(new FirstScreen(this, assets));
    }

    /**
     * Liefert die vom Spiel verwalteten gemeinsamen Assets. Screens behandeln
     * alle zurueckgegebenen Objekte als geliehen.
     *
     * @throws com.badlogic.gdx.utils.GdxRuntimeException wenn die Assets bereits
     *         freigegeben wurden
     */
    public GameAssets getAssets() {
        if (assets.isDisposed()) {
            throw new GdxRuntimeException("Attempted to use disposed GameAssets in getAssets().");
        }
        return assets;
    }

    public void changeScreen(Screen nextScreen) {
        if (nextScreen == null) {
            throw new IllegalArgumentException("nextScreen must not be null");
        }

        if (screenChangePending) {
            // Es laeuft bereits ein Wechsel: der gerade erzeugte Screen erreicht
            // nie show()/render(). Nur seine screen-eigenen Ressourcen werden
            // freigegeben; die gemeinsamen Assets bleiben unberuehrt.
            if (nextScreen != getScreen()) {
                nextScreen.dispose();
            }
            return;
        }

        if (nextScreen == getScreen()) {
            // Kein Wechsel: der aktuelle Screen darf nicht disposet werden.
            return;
        }

        screenChangePending = true;
        // Der Wechsel wird postRunnable ausgefuehrt: kein Screen wird mitten in
        // seiner eigenen render()-Methode disposet.
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
        Screen activeScreen = getScreen();

        if (activeScreen != null) {
            setScreen(null);
            activeScreen.dispose();
        }

        if (assets != null) {
            assets.dispose();
        }

        HankoShaderManager.dispose();
        FontManager.dispose();
    }
}

