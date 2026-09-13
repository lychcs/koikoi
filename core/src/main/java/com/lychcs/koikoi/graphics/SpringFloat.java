package com.lychcs.koikoi.graphics;

public class SpringFloat {
    public float value;
    public float target;
    public float velocity = 0f;

    private final float stiffness; // z.B. 180f für knackige Federn
    private final float damping;   // z.B. 12f für weiches Ausschwingen

    public SpringFloat(float initial, float stiffness, float damping) {
        this.value = initial;
        this.target = initial;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public void update(float delta) {
        float force = -stiffness * (value - target);
        float dampingForce = -damping * velocity;
        velocity += (force + dampingForce) * delta;
        value += velocity * delta;
    }

    public void punch(float impulse) {
        this.velocity += impulse;
    }
}
