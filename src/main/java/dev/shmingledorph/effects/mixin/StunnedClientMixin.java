package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.DeathScreen;
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
    // The input-event gate alone does not block inventory/key screens opened
    // by separate key handlers or custom UIs. Deny gameplay screens as well.
    // Never trap the player: Escape/pause, death and disconnect still work.
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void shmingledorph$denyMenus(Screen next, CallbackInfo ci) {
        var mc = MinecraftClient.getInstance();
        if (mc.player == null ||
            !mc.player.hasStatusEffect(ShmingledorphEffects.EFFECTS.get("stunned"))) return;
        if (next == null || next instanceof GameMenuScreen ||
            next instanceof DisconnectedScreen || next instanceof DeathScreen) return;
        ci.cancel();
    }
}

