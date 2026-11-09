package dev.shmingledorph.effects.mixin;

import dev.shmingledorph.effects.ShmingledorphEffects;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-authoritative stun. A modified client cannot bypass this by sending
 * use, swing, movement, inventory, drop, swap or attack packets.
 * Rotation-only updates, chat, pause and disconnect still work.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class StunnedServerNetworkMixin {
    @Shadow public ServerPlayerEntity player;

    private boolean shmingledorph$stunned() {
        return player != null && player.hasStatusEffect(ShmingledorphEffects.EFFECTS.get("stunned"));
    }

    @Inject(method="onPlayerAction", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$action(PlayerActionC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onPlayerInteractBlock", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$block(PlayerInteractBlockC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onPlayerInteractItem", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$item(PlayerInteractItemC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onPlayerInteractEntity", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$entity(PlayerInteractEntityC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onHandSwing", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$swing(HandSwingC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onUpdateSelectedSlot", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$hotbar(UpdateSelectedSlotC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onClickSlot", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$inventory(ClickSlotC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onCreativeInventoryAction", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$creative(CreativeInventoryActionC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onPlayerInput", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$input(PlayerInputC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onClientCommand", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$command(ClientCommandC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned()) ci.cancel();
    }
    @Inject(method="onPlayerMove", at=@At("HEAD"), cancellable=true)
    private void shmingledorph$move(PlayerMoveC2SPacket p, CallbackInfo ci) {
        if (shmingledorph$stunned() && p.changesPosition()) ci.cancel();
    }
}
