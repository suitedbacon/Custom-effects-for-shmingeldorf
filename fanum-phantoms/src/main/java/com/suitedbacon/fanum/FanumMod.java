package com.suitedbacon.fanum;

import com.mojang.brigadier.CommandDispatcher;
import com.suitedbacon.fanum.mixin.FanumEyestrain;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import static net.minecraft.server.command.CommandManager.literal;

public class FanumMod implements ModInitializer {
    public static final String EYESTRAIN = "fanum_eyestrain";
    public static final String HALLUCINATION = "hallucination";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("fanum")
            .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
            .executes(ctx -> summon(ctx.getSource(), false))
            .then(literal("normal").executes(ctx -> summon(ctx.getSource(), false)))
            .then(literal("eyestrain").executes(ctx -> summon(ctx.getSource(), true)))
            .then(literal("hallucination").executes(ctx -> hallucination(ctx.getSource()))));
    }

    private static int summon(ServerCommandSource source, boolean eyestrain) {
        ServerWorld world = source.getWorld();
        PhantomEntity phantom = EntityType.PHANTOM.create(world, SpawnReason.COMMAND);
        if (phantom == null) {
            source.sendError(Text.literal("Could not create a Phantom."));
            return 0;
        }
        Vec3d position = source.getPosition().add(0, 3, 0);
        phantom.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
        if (eyestrain) {
            phantom.addCommandTag(EYESTRAIN);
            ((FanumEyestrain) phantom).fanum$setEyestrain(true);
        }
        if (!world.spawnEntity(phantom)) {
            source.sendError(Text.literal("Could not summon Phantom."));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(eyestrain ? "Summoned Eyestrain Phantom." : "Summoned Phantom."), false);
        return 1;
    }

    private static int hallucination(ServerCommandSource source) {
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            player.addCommandTag(HALLUCINATION);
            source.sendFeedback(() -> Text.literal("Added hallucination tag."), false);
            return 1;
        } catch (Exception exception) {
            source.sendError(Text.literal("Run this command as a player."));
            return 0;
        }
    }
}
