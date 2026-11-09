package dev.shmingledorph.effects.client;

import dev.shmingledorph.effects.ShmingledorphEffects;
import dev.shmingledorph.effects.ShieldAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Effects overlay, accessible Flash palette, and separate blue Shield heart row. */
public final class ShmingledorphEffectsClient implements ClientModInitializer {
    // Each '1' is a filled pixel of a small 9x9 Minecraft-style heart.
    private static final String[] HEART = {
        " 11  11 ", "11111111", "11111111", "11111111",
        " 111111 ", "  1111  ", "   11   "
    };

    @Override
    public void onInitializeClient() {
        FlashAccessibility.load();
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

        // Draw after the vanilla health layer, in its own row above ordinary red hearts.
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.BOSS_BAR,
            Identifier.of(ShmingledorphEffects.MOD_ID, "shield_hearts"),
            (draw, tick) -> {
                var player = MinecraftClient.getInstance().player;
                if (player == null || player.isSpectator()) return;
                ShieldAccess shield = (ShieldAccess) player;
                int totalHearts = (int)Math.ceil(shield.shmingledorph$getShieldMax() / 2f);
                if (totalHearts <= 0) return;
                float current = shield.shmingledorph$getShield();
                int x = draw.getScaledWindowWidth() / 2 - 91;
                // Reserve a row above vanilla hearts; leave an extra row if vanilla has multiple rows.
                int regularRows = Math.max(1, (int)Math.ceil((player.getMaxHealth()
                    + player.getAbsorptionAmount()) / 20f));
                int baseline = draw.getScaledWindowHeight() - 39 - regularRows * 10;
                for (int i = 0; i < totalHearts; i++) {
                    int xx = x + (i % 10) * 8;
                    int yy = baseline - (i / 10) * 10;
                    float remain = current - i * 2;
                    drawHeart(draw, xx, yy, remain >= 2 ? 2 : remain > 0 ? 1 : 0);
                }
            }
        );
    }

    private static void drawHeart(DrawContext draw, int x, int y, int state) {
        // Dark empty heart, cyan filled half or full heart, pale blue highlight.
        for (int row = 0; row < HEART.length; row++) {
            String line = HEART[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '1') continue;
                draw.fill(x + col, y + row, x + col + 1, y + row + 1, 0xFF103A4D);
                boolean filled = state == 2 || (state == 1 && col <= 3);
                if (filled) {
                    int color = row < 2 ? 0xFFB6FAFF : (col % 3 == 0 ? 0xFF64E4F9 : 0xFF39BBDD);
                    draw.fill(x + col, y + row, x + col + 1, y + row + 1, color);
                }
            }
        }
    }
}
