package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShieldAccess;
import dev.shmingledorph.effects.ShieldSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.TrackedDataHandler;
import net.minecraft.entity.data.TrackedDataHandlers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** All LivingEntity instances have two server-owned, automatically synchronized shield values. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityShieldMixin implements ShieldAccess {
    @Unique private static final TrackedData<Float> SHM_SHIELD =
        DataTracker.registerData(LivingEntity.class, TrackedDataHandler.FLOAT);
    @Unique private static final TrackedData<Float> SHM_SHIELD_MAX =
        DataTracker.registerData(LivingEntity.class, TrackedDataHandler.FLOAT);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void shmingledorph$initShield(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(SHM_SHIELD, 0.0f);
        builder.add(SHM_SHIELD_MAX, 0.0f);
    }

    @Override public float shmingledorph$getShield() {
        return ((LivingEntity)(Object)this).getDataTracker().get(SHM_SHIELD);
    }
    @Override public float shmingledorph$getShieldMax() {
        return ((LivingEntity)(Object)this).getDataTracker().get(SHM_SHIELD_MAX);
    }
    @Override public void shmingledorph$setShield(float amount) {
        ((LivingEntity)(Object)this).getDataTracker().set(SHM_SHIELD, Math.max(0f, amount));
    }
    @Override public void shmingledorph$setShieldMax(float amount) {
        ((LivingEntity)(Object)this).getDataTracker().set(SHM_SHIELD_MAX, Math.max(0f, amount));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void shmingledorph$shieldTick(CallbackInfo ci) {
        ShieldSystem.tick((LivingEntity)(Object)this);
    }

    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void shmingledorph$afterArmor(net.minecraft.entity.damage.DamageSource source,
            float rawDamage, CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(ShieldSystem.absorb((LivingEntity)(Object)this, cir.getReturnValueF()));
    }
}
