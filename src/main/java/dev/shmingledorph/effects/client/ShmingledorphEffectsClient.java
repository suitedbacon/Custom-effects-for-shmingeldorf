package dev.shmingledorph.effects.client;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Flashed whiteout renders before vanilla crosshair, hearts and hotbar. */
public final class ShmingledorphEffectsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CROSSHAIR,
            Identifier.of(ShmingledorphEffects.MOD_ID, "flashed_overlay"),
            (draw, tick) -> {
                var player = MinecraftClient.getInstance().player;
                if (player == null) return;
                var flashed = player.getStatusEffect(ShmingledorphEffects.EFFECTS.get("flashed"));
                if (flashed == null) return;
                int amplifier = flashed.getAmplifier();
                int argb = amplifier >= 2 ? 0xFFFFFFFF : (amplifier == 1 ? 0xC8FFFFFF : 0x77FFFFFF);
                draw.fill(0, 0, draw.getScaledWindowWidth(), draw.getScaledWindowHeight(), argb);
            }
        );
    }
}
