package com.suitedbacon.fanum.mixin;

import com.suitedbacon.fanum.api.FanumReplaced;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobReplacementMixin implements FanumReplaced {
    @Unique private static final TrackedData<Boolean> FANUM_REPLACED =
        DataTracker.registerData(MobEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void fanum$addTrackedReplaceFlag(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(FANUM_REPLACED, false);
    }

    @Override
    public boolean fanum$isReplaced() {
        return ((Entity)(Object)this).getDataTracker().get(FANUM_REPLACED);
    }

    @Override
    public void fanum$setReplaced(boolean value) {
        ((Entity)(Object)this).getDataTracker().set(FANUM_REPLACED, value);
    }
}
