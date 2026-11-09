package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShieldAccess;
import dev.shmingledorph.effects.client.ShieldHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reflow the existing gold hearts ABOVE Shield while leaving the red hearts
 * at their original locations. Draw Shield in the vacated middle rows.
 *
 * Rendering is tied directly to vanilla renderHealthBar's x/y/lines params,
 * so high amplifiers and GUI scale no longer cause double offsets or drift.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudShieldMixin {
    @Unique private int shmingledorph$redHearts;
    @Unique private int shmingledorph$redRows;
    @Unique private int shmingledorph$blueRows;
    @Unique private int shmingledorph$currentHeartIndex;
    @Unique private int shmingledorph$left;
    @Unique private int shmingledorph$baseY;
    @Unique private int shmingledorph$rowSpacing;

    @Inject(method = "renderHealthBar", at = @At("HEAD"))
    private void shmingledorph$beforeHealth(
            DrawContext draw, PlayerEntity player, int x, int y, int lines,
            int regeneratingHeartIndex, float maxHealth, int lastHealth,
            int health, int absorption, boolean blinking, CallbackInfo ci) {
        shmingledorph$left = x;
        shmingledorph$baseY = y;
        shmingledorph$rowSpacing = lines;
        shmingledorph$redHearts = (int) Math.ceil(maxHealth / 2.0f);
        shmingledorph$redRows = ShieldHudRenderer.redRows(maxHealth);
        shmingledorph$blueRows = ShieldHudRenderer.rows(
            ((ShieldAccess)player).shmingledorph$getShieldMax());
        shmingledorph$currentHeartIndex = shmingledorph$redHearts
            + (int) Math.ceil(absorption / 2.0f) - 1;
    }

    @ModifyArgs(
        method = "renderHealthBar",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/hud/InGameHud;drawHeart(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/gui/hud/InGameHud$HeartType;IIZZZ)V"))
    private void shmingledorph$relocateGoldenHearts(Args args) {
        // Vanilla draws the CONTAINER first, then the filling of that same
        // indexed heart, counting backwards from total health + absorption.
        Object type = args.get(1);
        if (!(type instanceof Enum<?> heartType)) return;
        if ("CONTAINER".equals(heartType.name())) shmingledorph$currentHeartIndex--;
        if (shmingledorph$blueRows == 0
                || shmingledorph$currentHeartIndex < shmingledorph$redHearts) return;

        // Reflow absorption into its own rows above blue, rather than leaving
        // yellow hearts halfway through a red or blue row.
        int goldSlot = shmingledorph$currentHeartIndex - shmingledorph$redHearts;
        args.set(2, shmingledorph$left + (goldSlot % 10) * 8);
        args.set(3, shmingledorph$baseY
            - (shmingledorph$redRows + shmingledorph$blueRows
                + goldSlot / 10) * shmingledorph$rowSpacing);
    }

    @Inject(method = "renderHealthBar", at = @At("TAIL"))
    private void shmingledorph$blueBetweenRedAndGold(
            DrawContext draw, PlayerEntity player, int x, int y, int lines,
            int regeneratingHeartIndex, float maxHealth, int lastHealth,
            int health, int absorption, boolean blinking, CallbackInfo ci) {
        ShieldHudRenderer.render(draw, player, x, y, lines, maxHealth);
    }
}
