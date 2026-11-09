package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Alter keyboard movement after vanilla has sampled keys; look controls unaffected. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void shmingledorph$transformKeys(CallbackInfo info) {
        var player = MinecraftClient.getInstance().player;
        if (player == null) return;
        if (player.hasStatusEffect(ShmingledorphEffects.EFFECTS.get("stunned"))) {
            this.playerInput = PlayerInput.DEFAULT;
            this.movementVector = new Vec2f(0, 0);
            return;
        }
        if (player.hasStatusEffect(ShmingledorphEffects.EFFECTS.get("inverted"))) {
            PlayerInput input = this.playerInput;
            this.playerInput = new PlayerInput(
                input.backward(), input.forward(),
                input.right(), input.left(),
                input.sneak(), input.jump(), input.sprint()
            );
            this.movementVector = new Vec2f(-movementVector.x, -movementVector.y);
        }
    }
}
