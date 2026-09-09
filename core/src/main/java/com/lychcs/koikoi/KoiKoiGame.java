package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.lychcs.koikoi.screens.FirstScreen;
import com.lychcs.koikoi.screens.GameScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class KoiKoiGame extends Game {
    @Override
    public void create() {
//        setScreen(new FirstScreen());
        setScreen(new GameScreen());
    }
}
