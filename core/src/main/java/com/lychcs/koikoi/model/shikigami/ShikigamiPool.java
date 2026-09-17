package com.lychcs.koikoi.model.shikigami;

import com.badlogic.gdx.math.MathUtils;
import java.util.List;
import java.util.function.Supplier;

public class ShikigamiPool {

    private static final List<Supplier<Shikigami>> ALL_SHIKIGAMI_FACTORIES = List.of(
        () -> new Oni(ShikigamiStage.LEVEL_1)
    );

    public static Shikigami getRandomSHIKIGAMI() {
        if (ALL_SHIKIGAMI_FACTORIES.isEmpty()) {
            return new Oni(ShikigamiStage.LEVEL_1);
        }
        int index = MathUtils.random(0, ALL_SHIKIGAMI_FACTORIES.size() - 1);
        return ALL_SHIKIGAMI_FACTORIES.get(index).get();
    }
}
