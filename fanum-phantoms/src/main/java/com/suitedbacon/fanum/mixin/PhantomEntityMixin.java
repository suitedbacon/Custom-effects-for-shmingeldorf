package com.suitedbacon.fanum.mixin;

import com.suitedbacon.fanum.FanumMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomEntity.class)
public abstract class PhantomEntityMixin implements FanumEyestrain {
    @Unique
    private static final TrackedData<Boolean> FANUM_BLACK =
        DataTracker.registerData(PhantomEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void fanum$initData(DataTracker.Builder builder, CallbackInfo info) {
        builder.add(FANUM_BLACK, false);
    }

    @Override
    public boolean fanum$isEyestrain() {
        return ((Entity)(Object)this).getDataTracker().get(FANUM_BLACK);
    }

    @Override
    public void fanum$setEyestrain(boolean eyestrain) {
        ((Entity)(Object)this).getDataTracker().set(FANUM_BLACK, eyestrain);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void fanum$updateTag(CallbackInfo info) {
        Entity entity = (Entity)(Object)this;
        if (entity.getEntityWorld() instanceof ServerWorld) {
            boolean tagged = entity.getCommandTags().contains(FanumMod.EYESTRAIN);
            if (tagged != fanum$isEyestrain()) fanum$setEyestrain(tagged);
        }
    }
}
