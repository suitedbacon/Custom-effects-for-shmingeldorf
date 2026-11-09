package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Vulnerable increases raw incoming damage before armor and resistance. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float shmingledorph$vulnerableDamage(float amount) {
        LivingEntity self = (LivingEntity)(Object)this;
        var effect = self.getStatusEffect(ShmingledorphEffects.EFFECTS.get("vulnerable"));
        if (effect == null || amount <= 0) return amount;
        int level = Math.min(10, effect.getAmplifier() + 1);
        return amount * (1.0f + level * 0.40f);
    }
}
