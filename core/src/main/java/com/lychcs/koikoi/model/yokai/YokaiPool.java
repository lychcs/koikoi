package com.lychcs.koikoi.model.yokai;

import com.badlogic.gdx.math.MathUtils;
import java.util.List;
import java.util.function.Supplier;

public class YokaiPool {

    // Registriere hier alle verfügbaren Yokai-Klassen als Supplier (Starten auf LEVEL_1)
    private static final List<Supplier<Yokai>> ALL_YOKAI_FACTORIES = List.of(
        // später: neue yokai, z.B.: () -> new Kitsune(YokaiStage.LEVEL_1)
    );

    public static Yokai getRandomYokai() {
        if (ALL_YOKAI_FACTORIES.isEmpty()) {
            // return new Ryumon(YokaiStage.LEVEL_1); // Fallback
        }
        int index = MathUtils.random(0, ALL_YOKAI_FACTORIES.size() - 1);
        return ALL_YOKAI_FACTORIES.get(index).get();
    }
}
