package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShieldAccess;
import dev.shmingledorph.effects.ShieldSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
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
        DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.FLOAT);
    @Unique private static final TrackedData<Float> SHM_SHIELD_MAX =
        DataTracker.registerData(LivingEntity.class, TrackedDataHandlerRegistry.FLOAT);

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
    // Save the exact remaining shield and its previous max so relog/restart
    // cannot refill a damaged blue shield for free.
    @Inject(method = "writeCustomData", at = @At("TAIL"))
    private void shmingledorph$writeShield(WriteView view, CallbackInfo ci) {
        float max = shmingledorph$getShieldMax();
        if (max > 0) {
            view.putFloat("shmingledorph_blue_shield", shmingledorph$getShield());
            view.putFloat("shmingledorph_blue_shield_max", max);
        }
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void shmingledorph$readShield(ReadView view, CallbackInfo ci) {
        float max = Math.max(0f, view.getFloat("shmingledorph_blue_shield_max", 0f));
        float value = Math.max(0f, Math.min(max,
            view.getFloat("shmingledorph_blue_shield", 0f)));
        shmingledorph$setShieldMax(max);
        shmingledorph$setShield(value);
    }

}
