package com.suitedbacon.netherrework;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class NetherRework implements ModInitializer {
    private static final int HORDE_SIZE = 8;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            register(dispatcher, "piggies", EntityType.PIGLIN);
            register(dispatcher, "mama", EntityType.MAGMA_CUBE);
            register(dispatcher, "skeledark", EntityType.WITHER_SKELETON);
            register(dispatcher, "blast", EntityType.BLAZE);
        });
    }

    private static void register(CommandDispatcher<ServerCommandSource> dispatcher, String command, EntityType<?> type) {
        dispatcher.register(CommandManager.literal(command)
            .requires(source -> source.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS)))
            .then(CommandManager.literal("normal")
                .executes(context -> summon(context.getSource(), type, 1)))
            .then(CommandManager.literal("horde")
                .executes(context -> summon(context.getSource(), type, HORDE_SIZE))));
    }

    private static int summon(ServerCommandSource source, EntityType<?> type, int count) {
        ServerWorld world = source.getWorld();
        Vec3d center = source.getPosition();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            Entity entity = type.create(world, SpawnReason.COMMAND);
            if (entity == null) continue;
            double angle = (2.0 * Math.PI * i) / count;
            double radius = count == 1 ? 0 : 3.0;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            entity.refreshPositionAndAngles(x, center.y, z, 0.0f, 0.0f);
            if (world.spawnEntity(entity)) spawned++;
        }
        final int result = spawned;
        source.sendFeedback(() -> Text.literal("Summoned " + result + " " + type.getName().getString() + "(s)."), true);
        return spawned;
    }
}
