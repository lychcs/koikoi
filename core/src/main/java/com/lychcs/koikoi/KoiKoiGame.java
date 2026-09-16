package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.lychcs.koikoi.graphics.FontManager;
import com.lychcs.koikoi.graphics.HankoShaderManager;
import com.lychcs.koikoi.ui.FirstScreen;
import com.lychcs.koikoi.ui.GrayScottTestScreen;

public class KoiKoiGame extends Game {
    @Override
    public void create() {
        HankoShaderManager.initialize();
        FontManager.initialize();
        setScreen(new FirstScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        HankoShaderManager.dispose();
        FontManager.dispose();
    }
}
