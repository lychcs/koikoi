package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.lychcs.koikoi.screens.FirstScreen;

public class KoiKoiGame extends Game {
    @Override
    public void create() {
        setScreen(new FirstScreen(this));
    }
}
