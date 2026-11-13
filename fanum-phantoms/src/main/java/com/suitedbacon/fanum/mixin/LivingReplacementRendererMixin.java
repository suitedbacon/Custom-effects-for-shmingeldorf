package com.suitedbacon.fanum.mixin;

import com.suitedbacon.fanum.api.FanumReplaced;
import com.suitedbacon.fanum.api.FanumReplacementRenderState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Overrides the body's render layer for ALL living mob renderers, so the
 * selected entity keeps its original model but uses a pitch-black texture. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingReplacementRendererMixin {
    @Unique private static final Identifier FANUM_BLACK =
        Identifier.of("fanum", "textures/entity/eyestrain.png");

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void fanum$copyReplacement(LivingEntity entity, LivingEntityRenderState state,
                                       float tickDelta, CallbackInfo ci) {
        boolean replaced = entity instanceof MobEntity mob
            && ((FanumReplaced) mob).fanum$isReplaced();
        ((FanumReplacementRenderState) state).fanum$setReplaced(replaced);
    }

    @Inject(method = "getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;",
            at = @At("HEAD"), cancellable = true)
    private void fanum$blackMobLayer(LivingEntityRenderState state, boolean showBody,
                                     boolean translucent, boolean showOutline,
                                     CallbackInfoReturnable<RenderLayer> cir) {
        if (showBody && ((FanumReplacementRenderState) state).fanum$isReplaced()) {
            cir.setReturnValue(RenderLayers.entityCutoutNoCull(FANUM_BLACK));
        }
    }

    @Inject(method = "shouldRenderFeatures(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)Z",
            at = @At("HEAD"), cancellable = true)
    private void fanum$hideOtherTextures(LivingEntityRenderState state,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (((FanumReplacementRenderState) state).fanum$isReplaced()) {
            cir.setReturnValue(false);
        }
    }
}
