package dev.shmingledorph.effects.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Reduce computed fall damage without affecting combat/explosion damage.
 * X-XV are fully immune to fall damage while effect is active.
 */
@Mixin(LivingEntity.class)
public abstract class AntiGravityFallMixin {
    @ModifyReturnValue(method = "computeFallDamage", at = @At("RETURN"))
    private int shmingledorph$reduceFallDamage(int vanillaDamage) {
        if (vanillaDamage <= 0) return vanillaDamage;
        LivingEntity self = (LivingEntity)(Object)this;
        var effect = self.getStatusEffect(ShmingledorphEffects.EFFECTS.get("anti_gravity"));
        if (effect == null) return vanillaDamage;
        int level = Math.min(15, Math.max(1, effect.getAmplifier() + 1));
        if (level >= 10) return 0;
        // Level I receives 26% less damage; IX receives ~93.4% less.
        return Math.max(0, (int)Math.floor(vanillaDamage * Math.pow(0.74, level)));
    }
}
