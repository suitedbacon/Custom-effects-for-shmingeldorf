package dev.shmingledorph.effects.client;

import dev.shmingledorph.effects.ShieldAccess;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Draw blue Shield hearts using the SAME x, y and compressed vertical spacing
 * as vanilla's actual renderHealthBar pass. Each row takes exactly ten hearts.
 *
 * Heart order, from bottom up: regular health / blue shield / gold absorption.
 */
public final class ShieldHudRenderer {
    private static final String[] HEART = {
        " 11  11 ", "11111111", "11111111", "11111111",
        " 111111 ", "  1111  ", "   11   "
    };

    private ShieldHudRenderer() {}

    public static int rows(float capacity) {
        if (capacity <= 0) return 0;
        // 20 health points = 10 hearts = 1 HUD row.
        return ((int) Math.ceil(capacity / 2.0f) + 9) / 10;
    }

    public static int redRows(float maxHealth) {
        return Math.max(1, ((int) Math.ceil(maxHealth / 2.0f) + 9) / 10);
    }

    public static void render(DrawContext draw, PlayerEntity player,
            int x, int vanillaHealthBaseY, int vanillaRowSpacing, float maxHealth) {
        if (!(player instanceof ShieldAccess shield)) return;
        float maxShield = shield.shmingledorph$getShieldMax();
        int count = (int) Math.ceil(maxShield / 2.0f);
        if (count <= 0) return;

        float remaining = shield.shmingledorph$getShield();
        int redRows = redRows(maxHealth);
        int spacing = Math.max(1, vanillaRowSpacing);

        for (int i = 0; i < count; i++) {
            // Share vanilla's horizontal alignment. Never accumulate a
            // second independent 10px offset or use the armor HUD y offset.
            int xx = x + (i % 10) * 8;
            int yy = vanillaHealthBaseY - (redRows + i / 10) * spacing;
            float points = remaining - i * 2.0f;
            int fill = points >= 2.0f ? 2 : points > 0 ? 1 : 0;
            drawHeart(draw, xx, yy, fill);
        }
    }

    private static void drawHeart(DrawContext draw, int x, int y, int state) {
        for (int row = 0; row < HEART.length; row++) {
            String pixels = HEART[row];
            for (int col = 0; col < pixels.length(); col++) {
                if (pixels.charAt(col) != '1') continue;
                int color = 0xFF103A4D;
                if (state == 2 || (state == 1 && col <= 3)) {
                    color = row < 2 ? 0xFFB6FAFF
                        : (col % 3 == 0 ? 0xFF64E4F9 : 0xFF39BBDD);
                }
                draw.fill(x + col, y + row, x + col + 1, y + row + 1, color);
            }
        }
    }
}
