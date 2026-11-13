package com.suitedbacon.fanum.mixin;

import com.suitedbacon.fanum.api.FanumReplacementRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public abstract class LivingReplacementStateMixin implements FanumReplacementRenderState {
    @Unique private boolean fanum$replaced;

    @Override
    public boolean fanum$isReplaced() {
        return fanum$replaced;
    }

    @Override
    public void fanum$setReplaced(boolean value) {
        fanum$replaced = value;
    }
}
