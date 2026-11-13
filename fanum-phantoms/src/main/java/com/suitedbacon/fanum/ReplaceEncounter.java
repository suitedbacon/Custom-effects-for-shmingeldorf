package com.suitedbacon.fanum;

import com.suitedbacon.fanum.api.FanumReplaced;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * /fanum replace uses an existing mob. It does not construct or spawn anything.
 * Dark mob -> synced black skin -> accelerated pursuit + recurring scream -> restore.
 */
public final class ReplaceEncounter {
    private static final int MAX_LIGHT = 7;
    private static final double SEARCH_RADIUS = 20.0;
    private static final int DURATION = 260; // 13 seconds at 20 TPS
    private static final double CHASE_SPEED = 1.9;
    private static final Map<UUID, Replacement> ACTIVE = new HashMap<>();

    private ReplaceEncounter() {}

    private static final class Replacement {
        final UUID mobId;
        final UUID playerId;
        final ServerWorld world;
        final LivingEntity oldTarget;
        final boolean oldSprinting;
        int ticks;

        Replacement(MobEntity mob, ServerPlayerEntity player, ServerWorld world) {
            mobId = mob.getUuid();
            playerId = player.getUuid();
            this.world = world;
            oldTarget = mob.getTarget();
            oldSprinting = mob.isSprinting();
        }
    }

    public static int replace(ServerCommandSource source) {
        ServerPlayerEntity player;
        try {
            player = source.getPlayerOrThrow();
        } catch (Exception ex) {
            source.sendError(Text.literal("Run /fanum replace as a player."));
            return 0;
        }

        ServerWorld world = player.getEntityWorld();
        Vec3d center = player.getEntityPos();
        Box area = new Box(center.add(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                           center.add(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS));
        var mobs = new ArrayList<>(world.getEntitiesByClass(MobEntity.class, area,
            mob -> mob.isAlive()
                && !mob.isAiDisabled()
                && !mob.hasPassengers()
                && !ACTIVE.containsKey(mob.getUuid())
                && mob.squaredDistanceTo(player) <= SEARCH_RADIUS * SEARCH_RADIUS
                && world.getLightLevel(mob.getBlockPos()) <= MAX_LIGHT));
        mobs.sort(Comparator.comparingDouble(mob -> mob.squaredDistanceTo(player)));
        if (mobs.isEmpty()) {
            source.sendError(Text.literal("No eligible mob in darkness (light level 7 or less) within 20 blocks."));
            return 0;
        }
        MobEntity mob = mobs.get(0);
        Replacement active = new Replacement(mob, player, world);
        ACTIVE.put(mob.getUuid(), active);
        ((FanumReplaced) mob).fanum$setReplaced(true);
        mob.setSprinting(true);
        mob.setTarget(player);
        mob.getNavigation().startMovingTo(player, CHASE_SPEED);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
            SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 2.2f, 0.63f);
        source.sendFeedback(() -> Text.literal("Something in the dark is running toward you."), false);
        return 1;
    }

    public static void tick(MinecraftServer server) {
        for (Replacement active : new ArrayList<>(ACTIVE.values())) {
            var entity = active.world.getEntity(active.mobId);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(active.playerId);

            if (!(entity instanceof MobEntity mob) || !mob.isAlive() ||
                target == null || !target.isAlive() || target.getEntityWorld() != active.world ||
                mob.squaredDistanceTo(target) > 48 * 48 || active.ticks >= DURATION) {
                finish(active, entity instanceof MobEntity m ? m : null);
                continue;
            }

            active.ticks++;
            mob.setSprinting(true);
            mob.setTarget(target);

            // Reassert navigation because ordinary mob goals can briefly take control.
            if (active.ticks % 5 == 0) {
                mob.getNavigation().startMovingTo(target, CHASE_SPEED);
            }
            if (active.ticks % 23 == 0) {
                float pitch = (active.ticks % 3 == 0) ? 0.58f : 0.86f;
                active.world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 2.0f, pitch);
            }
        }
    }

    private static void finish(Replacement active, MobEntity mob) {
        ACTIVE.remove(active.mobId);
        if (mob != null && mob.isAlive()) {
            ((FanumReplaced) mob).fanum$setReplaced(false);
            mob.getNavigation().stop();
            mob.setSprinting(active.oldSprinting);
            mob.setTarget(active.oldTarget != null && active.oldTarget.isAlive() ? active.oldTarget : null);
        }
    }
}
