package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.ui.FirstScreen;

public class KoiKoiGame extends Game {
    @Override
    public void create() {
        HankoShaderManager.initialize();
        setScreen(new FirstScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        HankoShaderManager.dispose();
    }
}
