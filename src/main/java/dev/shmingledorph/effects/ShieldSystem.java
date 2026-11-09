package dev.shmingledorph.effects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;

/**
 * A separate health pool: gold absorption is used first, then blue shield,
 * then red health. No passive regeneration. On expiry, remaining shield
 * becomes gold absorption.
 */
public final class ShieldSystem {
    private ShieldSystem() {}

    public static float capacity(StatusEffectInstance active) {
        return active == null ? 0 : 4.0f * Math.min(10, active.getAmplifier() + 1);
    }

    public static void tick(LivingEntity entity) {
        if (entity.getEntityWorld().isClient()) return;
        ShieldAccess state = (ShieldAccess) entity;
        var active = entity.getStatusEffect(ShmingledorphEffects.EFFECTS.get("shield"));
        float target = capacity(active);
        float previousMax = state.shmingledorph$getShieldMax();
        float current = state.shmingledorph$getShield();
        if (target > 0) {
            if (previousMax == 0) {
                state.shmingledorph$setShieldMax(target);
                state.shmingledorph$setShield(target);
            } else if (Math.abs(previousMax - target) > 0.001f) {
                state.shmingledorph$setShieldMax(target);
                state.shmingledorph$setShield(Math.max(0, Math.min(target, current + target - previousMax)));
            }
        } else if (previousMax > 0) {
            if (current > 0) entity.setAbsorptionAmount(entity.getAbsorptionAmount() + current);
            state.shmingledorph$setShield(0);
            state.shmingledorph$setShieldMax(0);
        }
    }

    /**
     * The input is post-armor/resistance vanilla damage.
     * We subtract only damage that would be left AFTER golden absorption.
     * Vanilla then consumes golden absorption and attacks red health as usual.
     */
    public static float absorb(LivingEntity entity, float damage) {
        if (entity.getEntityWorld().isClient() || damage <= 0) return damage;
        ShieldAccess state = (ShieldAccess) entity;
        float current = state.shmingledorph$getShield();
        if (current <= 0) return damage;
        float afterGold = Math.max(0, damage - entity.getAbsorptionAmount());
        float consumed = Math.min(current, afterGold);
        if (consumed > 0) state.shmingledorph$setShield(current - consumed);
        return damage - consumed;
    }

    public static float healShield(LivingEntity entity, float healing) {
        if (healing <= 0 || entity.getEntityWorld().isClient()) return 0;
        ShieldAccess state = (ShieldAccess) entity;
        float missing = state.shmingledorph$getShieldMax() - state.shmingledorph$getShield();
        float added = Math.max(0, Math.min(missing, healing));
        if (added > 0) state.shmingledorph$setShield(state.shmingledorph$getShield() + added);
        return added;
    }
}
