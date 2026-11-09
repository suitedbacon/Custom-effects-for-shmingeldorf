package dev.shmingledorph.effects.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Actual per-entity physics, on both client prediction and authoritative server.
 * I-IX progressively reduce gravity, X is zero gravity, XI-XV pull upward.
 * No persistent NoGravity flag is set, so expiration restores vanilla physics.
 */
@Mixin(Entity.class)
public abstract class AntiGravityPhysicsMixin {
    @ModifyReturnValue(method = "getFinalGravity", at = @At("RETURN"))
    private double shmingledorph$modifyGravity(double vanilla) {
        if (!((Object)this instanceof LivingEntity entity)) return vanilla;
        var instance = entity.getStatusEffect(ShmingledorphEffects.EFFECTS.get("anti_gravity"));
        if (instance == null) return vanilla;
        int level = Math.min(15, Math.max(1, instance.getAmplifier() + 1));
        if (level <= 9) {
            return vanilla * (1.0 - 0.1 * level);
        }
        if (level == 10) return 0.0;
        // XV is strong reverse gravity (~0.14 blocks/tick^2 at usual 0.08g).
        return -vanilla * 0.35 * (level - 10);
    }
}
