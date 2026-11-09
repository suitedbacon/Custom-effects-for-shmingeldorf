package dev.shmingledorph.effects.client;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

/** Effects overlay, accessible Flash palette, and separate blue Shield heart row. */
public final class ShmingledorphEffectsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FlashAccessibility.load();
        var flashKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.shmingledorph.flash_palette", InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8, KeyBinding.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (flashKey.wasPressed()) {
                FlashAccessibility.toggle();
                if (client.player != null) client.player.sendMessage(Text.literal(
                    "Flash overlay: " + (FlashAccessibility.amber() ? "DARK AMBER" : "WHITE")), true);
            }
        });
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CROSSHAIR,
            Identifier.of(ShmingledorphEffects.MOD_ID, "vision_overlays"),
            (draw, tick) -> {
                var player = MinecraftClient.getInstance().player;
                if (player == null) return;
                int fill = 0;
                if (player.hasStatusEffect(ShmingledorphEffects.EFFECTS.get("true_blindness"))) {
                    fill = 0xFF000000;
                } else {
                    var flash = player.getStatusEffect(ShmingledorphEffects.EFFECTS.get("flashed"));
                    if (flash != null) {
                        int a = flash.getAmplifier();
                        if (FlashAccessibility.amber()) {
                            fill = a >= 2 ? 0xFF8A6C18 : (a == 1 ? 0xD185671B : 0x88745A13);
                        } else {
                            fill = a >= 2 ? 0xFFFFFFFF : (a == 1 ? 0xC8FFFFFF : 0x77FFFFFF);
                        }
                    }
                }
                if (fill != 0) draw.fill(0, 0, draw.getScaledWindowWidth(), draw.getScaledWindowHeight(), fill);
            }
        );

    }
}
