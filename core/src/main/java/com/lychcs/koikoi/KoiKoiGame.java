package com.lychcs.koikoi;

import com.badlogic.gdx.Game;
import com.lychcs.koikoi.run.RunSession;
import com.lychcs.koikoi.screens.GameScreen;

public class KoiKoiGame extends Game {
    @Override
    public void create() {
        RunSession currentRun = new RunSession();
        setScreen(new GameScreen(currentRun));
    }
}
