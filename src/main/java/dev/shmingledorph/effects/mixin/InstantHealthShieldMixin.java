package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShieldSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.InstantHealthOrDamageStatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InstantHealthOrDamageStatusEffect.class)
public abstract class InstantHealthShieldMixin {
    private boolean shmingledorph$isHealing() {
        return (Object)this == StatusEffects.INSTANT_HEALTH.value();
    }

    @Inject(method="applyInstantEffect", at=@At("HEAD"))
    private void shmingledorph$instantHeal(ServerWorld world, Entity source, Entity attacker,
            LivingEntity target, int amplifier, double proximity, CallbackInfo ci) {
        if (!shmingledorph$isHealing()) return;
        ShieldSystem.tick(target);
        float amount = Math.min(2048, 4 << Math.min(9, amplifier)) * (float)proximity;
        ShieldSystem.healShield(target, amount);
    }

    @Inject(method="applyUpdateEffect", at=@At("HEAD"))
    private void shmingledorph$effectHeal(ServerWorld world, LivingEntity entity, int amplifier,
            CallbackInfoReturnable<Boolean> cir) {
        if (!shmingledorph$isHealing()) return;
        ShieldSystem.tick(entity);
        ShieldSystem.healShield(entity, Math.min(2048, 4 << Math.min(9, amplifier)));
    }
}
