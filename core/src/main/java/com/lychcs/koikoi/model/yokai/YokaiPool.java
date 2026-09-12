package com.lychcs.koikoi.model.yokai;

import com.badlogic.gdx.math.MathUtils;
import java.util.List;
import java.util.function.Supplier;

public class YokaiPool {

    private static final List<Supplier<Yokai>> ALL_YOKAI_FACTORIES = List.of(
        () -> new Oni(YokaiStage.LEVEL_1)
    );

    public static Yokai getRandomYokai() {
        if (ALL_YOKAI_FACTORIES.isEmpty()) {
            return new Oni(YokaiStage.LEVEL_1);
        }
        int index = MathUtils.random(0, ALL_YOKAI_FACTORIES.size() - 1);
        return ALL_YOKAI_FACTORIES.get(index).get();
    }
}
