package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lock gameplay clicks/use/pick/hotbar/inventory without blocking Esc, chat or quitting. */
@Mixin(MinecraftClient.class)
public abstract class StunnedClientMixin {
    @Inject(method="handleInputEvents", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$blockActions(CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        if (mc.player != null &&
            mc.player.hasStatusEffect(ShmingledorphEffects.EFFECTS.get("stunned")))
            ci.cancel();
    }
}
