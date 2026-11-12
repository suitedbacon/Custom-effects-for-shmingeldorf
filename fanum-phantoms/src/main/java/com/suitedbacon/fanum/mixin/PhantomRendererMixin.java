package com.suitedbacon.fanum.mixin;

import net.minecraft.client.render.entity.PhantomEntityRenderer;
import net.minecraft.client.render.entity.state.PhantomEntityRenderState;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PhantomEntityRenderer.class)
public abstract class PhantomRendererMixin {
    @Unique private static final Identifier FANUM_BLACK_TEXTURE =
        Identifier.of("fanum", "textures/entity/eyestrain.png");

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/mob/PhantomEntity;Lnet/minecraft/client/render/entity/state/PhantomEntityRenderState;F)V", at = @At("TAIL"))
    private void fanum$renderState(PhantomEntity phantom, PhantomEntityRenderState state, float delta, CallbackInfo info) {
        ((FanumRenderState)(Object)state).fanum$setEyestrain(((FanumEyestrain)phantom).fanum$isEyestrain());
    }

    @Inject(method = "getTexture(Lnet/minecraft/client/render/entity/state/PhantomEntityRenderState;)Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
    private void fanum$texture(PhantomEntityRenderState state, CallbackInfoReturnable<Identifier> callback) {
        if (((FanumRenderState)(Object)state).fanum$isEyestrain()) {
            callback.setReturnValue(FANUM_BLACK_TEXTURE);
        }
    }
}
