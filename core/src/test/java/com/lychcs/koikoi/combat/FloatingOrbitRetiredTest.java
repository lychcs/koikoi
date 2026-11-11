package com.lychcs.koikoi.combat;

import com.badlogic.ashley.core.Family;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.OrbitComponent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Manual-only overworld combat: no floating companions or Omamori orbit glyphs. */
class FloatingOrbitRetiredTest {

    @Test
    void overworldCreatesNoCompanionOrOrbitEntities() {
        OverworldEcs ecs = new OverworldEcs(0f, 0f,
            List.of(new CompanionSpec("Test-Oni", 0, 1f,
                10f, 1f, 100f, 300f, 100f, 20f, 10f)),
            CombatStats.BASE, 64f, null, (x, y) -> false);

        assertTrue(ecs.getCompanions().isEmpty(), "floating companions are retired");
        assertEquals(0, ecs.getEngine().getEntitiesFor(
            Family.all(CompanionComponent.class).get()).size());

        ecs.rebuildOmamoriOrbits(List.of(
            new OverworldEcs.OrbitGlyph("*", 1f, 1f, 1f)));

        assertTrue(ecs.getOrbits().isEmpty(), "Omamori orbit glyphs are retired");
        assertEquals(0, ecs.getEngine().getEntitiesFor(
            Family.all(OrbitComponent.class).get()).size());
    }
}