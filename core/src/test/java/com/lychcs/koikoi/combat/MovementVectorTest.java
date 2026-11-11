package com.lychcs.koikoi.combat;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Movement vector tests: 8-way normalisation and diagonal speed parity. */
class MovementVectorTest {

    private final Vector2 out = new Vector2();

    @Test
    void noInputProducesZeroVector() {
        MovementVector.fromAxis(0f, 0f, out);

        assertEquals(0f, out.x, 0.0001f);
        assertEquals(0f, out.y, 0.0001f);
        assertFalse(MovementVector.hasInput(0f, 0f));
    }

    @Test
    void cardinalsAreUnitLength() {
        MovementVector.fromAxis(1f, 0f, out);
        assertEquals(1f, out.x, 0.0001f);
        assertEquals(0f, out.y, 0.0001f);

        MovementVector.fromAxis(0f, -1f, out);
        assertEquals(0f, out.x, 0.0001f);
        assertEquals(-1f, out.y, 0.0001f);
    }

    @Test
    void diagonalsAreNormalised() {
        MovementVector.fromAxis(1f, 1f, out);

        assertEquals(MovementVector.DIAGONAL, out.x, 0.0001f);
        assertEquals(MovementVector.DIAGONAL, out.y, 0.0001f);
        assertEquals(1f, out.len(), 0.0001f);
    }

    @Test
    void diagonalSpeedEqualsCardinalSpeed() {
        float cardinal = MovementVector.lengthOf(-1f, 0f);
        float diagonal = MovementVector.lengthOf(1f, -1f);

        assertEquals(cardinal, diagonal, 0.0001f);
        assertEquals(1f, diagonal, 0.0001f);
    }

    @Test
    void oppositeKeysCancelOut() {
        MovementVector.fromAxis(1f - 1f, 1f - 1f, out);

        assertEquals(0f, out.len(), 0.0001f);
    }

    @Test
    void axisSignsArePreserved() {
        MovementVector.fromAxis(-1f, 1f, out);

        assertEquals(-MovementVector.DIAGONAL, out.x, 0.0001f);
        assertEquals(MovementVector.DIAGONAL, out.y, 0.0001f);
    }
}
