package com.suitedbacon.fanum.mixin;

import com.suitedbacon.fanum.api.FanumRenderState;
import net.minecraft.client.render.entity.state.PhantomEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(PhantomEntityRenderState.class)
public class PhantomStateMixin implements FanumRenderState {
    @Unique private boolean fanum$black;
    @Override public boolean fanum$isEyestrain() { return fanum$black; }
    @Override public void fanum$setEyestrain(boolean black) { fanum$black = black; }
}
