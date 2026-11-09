package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShmingledorphEffects;
import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Five-stage near-black fog, affecting what the camera can see, NOT the user's real video options. */
@Mixin(FogRenderer.class)
public abstract class VoidFogMixin {
    private static float shmingledorph$range() {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return -1;
        var omen = player.getStatusEffect(ShmingledorphEffects.EFFECTS.get("void_omen"));
        if (omen == null) return -1;
        float[] caps = { 96f, 64f, 42f, 26f, 12f };
        return caps[Math.min(4, Math.max(0, omen.getAmplifier()))];
    }

    @ModifyVariable(method="applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
        at=@At("HEAD"), argsOnly=true, ordinal=2)
    private float shmingledorph$startFog(float original) {
        float limit = shmingledorph$range();
        return limit < 0 ? original : Math.min(original, limit * 0.35f);
    }

    @ModifyVariable(method="applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
        at=@At("HEAD"), argsOnly=true, ordinal=3)
    private float shmingledorph$endFog(float original) {
        float limit = shmingledorph$range();
        return limit < 0 ? original : Math.min(original, limit);
    }

    @Inject(method="applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at=@At("HEAD"))
    private void shmingledorph$darkFog(ByteBuffer buffer, int position, Vector4f color,
            float envStart, float envEnd, float viewStart, float viewEnd,
            float skyEnd, float cloudEnd, CallbackInfo ci) {
        if (shmingledorph$range() > 0) color.set(0.035f, 0.019f, 0.072f, 1f);
    }
}
