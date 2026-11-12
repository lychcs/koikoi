package com.lychcs.koikoi.combat.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.lychcs.koikoi.combat.component.CardBurstComponent;
import com.lychcs.koikoi.combat.component.CardDeckComponent;
import com.lychcs.koikoi.combat.component.CardProjectileComponent;
import com.lychcs.koikoi.combat.component.CompanionComponent;
import com.lychcs.koikoi.combat.component.HealthComponent;
import com.lychcs.koikoi.combat.component.HostileComponent;
import com.lychcs.koikoi.combat.component.MotionComponent;
import com.lychcs.koikoi.combat.component.OrbitComponent;
import com.lychcs.koikoi.combat.component.ParticleComponent;
import com.lychcs.koikoi.combat.component.PlayerComponent;
import com.lychcs.koikoi.combat.component.ProjectileComponent;
import com.lychcs.koikoi.combat.component.SealComponent;
import com.lychcs.koikoi.combat.component.SlowComponent;
import com.lychcs.koikoi.combat.component.SpriteComponent;
import com.lychcs.koikoi.combat.component.SquashStretchComponent;
import com.lychcs.koikoi.combat.component.TransformComponent;

/** Shared {@link ComponentMapper} instances (created once, used by all systems). */
public final class Mappers {

    public static final ComponentMapper<TransformComponent> TRANSFORM =
        ComponentMapper.getFor(TransformComponent.class);
    public static final ComponentMapper<MotionComponent> MOTION =
        ComponentMapper.getFor(MotionComponent.class);
    public static final ComponentMapper<PlayerComponent> PLAYER =
        ComponentMapper.getFor(PlayerComponent.class);
    public static final ComponentMapper<SquashStretchComponent> SQUASH =
        ComponentMapper.getFor(SquashStretchComponent.class);
    public static final ComponentMapper<CompanionComponent> COMPANION =
        ComponentMapper.getFor(CompanionComponent.class);
    public static final ComponentMapper<SpriteComponent> SPRITE =
        ComponentMapper.getFor(SpriteComponent.class);
    public static final ComponentMapper<HostileComponent> HOSTILE =
        ComponentMapper.getFor(HostileComponent.class);
    public static final ComponentMapper<HealthComponent> HEALTH =
        ComponentMapper.getFor(HealthComponent.class);
    public static final ComponentMapper<SlowComponent> SLOW =
        ComponentMapper.getFor(SlowComponent.class);
    public static final ComponentMapper<SealComponent> SEAL =
        ComponentMapper.getFor(SealComponent.class);
    public static final ComponentMapper<OrbitComponent> ORBIT =
        ComponentMapper.getFor(OrbitComponent.class);
    public static final ComponentMapper<ParticleComponent> PARTICLE =
        ComponentMapper.getFor(ParticleComponent.class);
    public static final ComponentMapper<ProjectileComponent> PROJECTILE =
        ComponentMapper.getFor(ProjectileComponent.class);
    public static final ComponentMapper<CardProjectileComponent> CARD_PROJECTILE =
        ComponentMapper.getFor(CardProjectileComponent.class);
    public static final ComponentMapper<CardBurstComponent> CARD_BURST =
        ComponentMapper.getFor(CardBurstComponent.class);
    public static final ComponentMapper<CardDeckComponent> CARD_DECK =
        ComponentMapper.getFor(CardDeckComponent.class);

    private Mappers() {
    }
}
