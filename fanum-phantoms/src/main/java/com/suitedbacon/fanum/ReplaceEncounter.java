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
import net.minecraft.world.World;

/**
 * Temporarily turns an existing mob into a screaming black pursuer.
 * No mobs are spawned, cloned, killed or swapped.
 *
 * /fanum replace: manually choose the nearest mob within 20 blocks.
 *   In daytime the mob must be in darkness (light <= 7).
 *   At night, any nearby mob can be selected regardless of light.
 *
 * Natural encounters: at night in the Overworld, every 10 seconds
 * each survival player without an active encounter has a 1/12 chance
 * to trigger one on a random eligible mob 6-26 blocks away.
 * Each player has a two-minute cooldown after a natural trigger.
 */
public final class ReplaceEncounter {
    private static final int MAX_LIGHT = 7;
    private static final double COMMAND_RADIUS = 20.0;
    private static final double NIGHT_RADIUS = 26.0;
    private static final double NIGHT_MIN_RADIUS = 6.0;
    private static final int DURATION = 260; // 13 seconds
    private static final double CHASE_SPEED = 1.9;
    private static final int AUTO_CHECK_TICKS = 200; // every 10 seconds
    private static final int AUTO_CHANCE_DENOMINATOR = 12; // average every 2 min (when eligible)
    private static final int AUTO_COOLDOWN_TICKS = 2400; // 2 minutes per player

    private static final Map<UUID, Replacement> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_NATURAL_EVENT = new HashMap<>();
    private static long clock;

    private ReplaceEncounter() {}

    private static final class Replacement {
        final UUID mobId;
        final UUID playerId;
        final ServerWorld world;
        final LivingEntity oldTarget;
        final boolean oldSprinting;
        final boolean natural;
        int ticks;

        Replacement(MobEntity mob, ServerPlayerEntity player, ServerWorld world, boolean natural) {
            mobId = mob.getUuid();
            playerId = player.getUuid();
            this.world = world;
            oldTarget = mob.getTarget();
            oldSprinting = mob.isSprinting();
            this.natural = natural;
        }
    }

    /** "Night" means actual Overworld night, not darkness in the Nether or End. */
    private static boolean night(ServerWorld world) {
        long time = Math.floorMod(world.getTimeOfDay(), 24000L);
        return world.getRegistryKey().equals(World.OVERWORLD)
            && time >= 13000L && time < 23000L;
    }

    private static boolean eligible(MobEntity mob, ServerPlayerEntity player,
                                    double radius, boolean night, ServerWorld world) {
        return mob.isAlive()
            && !mob.isAiDisabled()
            && !mob.hasPassengers()
            && !mob.hasVehicle()
            && !ACTIVE.containsKey(mob.getUuid())
            && mob.squaredDistanceTo(player) <= radius * radius
            && (night || world.getLightLevel(mob.getBlockPos()) <= MAX_LIGHT);
    }

    private static ArrayList<MobEntity> candidates(ServerPlayerEntity player, double radius,
                                                    boolean atNight) {
        ServerWorld world = player.getEntityWorld();
        Vec3d center = player.getEntityPos();
        Box bounds = new Box(center.add(-radius, -radius, -radius),
                             center.add(radius, radius, radius));
        return new ArrayList<>(world.getEntitiesByClass(MobEntity.class, bounds,
            mob -> eligible(mob, player, radius, atNight, world)));
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
        boolean atNight = night(world);
        var mobs = candidates(player, COMMAND_RADIUS, atNight);
        mobs.sort(Comparator.comparingDouble(mob -> mob.squaredDistanceTo(player)));
        if (mobs.isEmpty()) {
            source.sendError(Text.literal(atNight
                ? "No eligible mob within 20 blocks."
                : "No eligible mob in darkness (light level 7 or less) within 20 blocks."));
            return 0;
        }
        begin(mobs.get(0), player, false);
        source.sendFeedback(() -> Text.literal("Something is running toward you."), false);
        return 1;
    }

    private static void begin(MobEntity mob, ServerPlayerEntity player, boolean natural) {
        ServerWorld world = player.getEntityWorld();
        Replacement event = new Replacement(mob, player, world, natural);
        ACTIVE.put(mob.getUuid(), event);
        // Tracked entity data makes the actual mob's existing render model black
        // for all nearby clients. No client-side replacement entities are spawned.
        ((FanumReplaced) mob).fanum$setReplaced(true);
        mob.setSprinting(true);
        mob.setTarget(player);
        mob.getNavigation().startMovingTo(player, CHASE_SPEED);
        world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
            SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 2.2f, 0.63f);
    }

    private static void checkNighttime(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isSpectator()
                    || player.getAbilities().creativeMode
                    || player.isSleeping()
                    || !night(player.getEntityWorld())
                    || clock < NEXT_NATURAL_EVENT.getOrDefault(player.getUuid(), 0L)) {
                continue;
            }
            // Only one natural scare per player at a time.
            boolean alreadyChased = ACTIVE.values().stream().anyMatch(event ->
                event.natural && event.playerId.equals(player.getUuid()));
            if (alreadyChased) continue;

            // The dice roll happens before the nearby-entity scan, keeping
            // nighttime overhead small even in heavily modded worlds.
            if (player.getEntityWorld().getRandom().nextInt(AUTO_CHANCE_DENOMINATOR) != 0) {
                continue;
            }

            var mobs = candidates(player, NIGHT_RADIUS, true);
            mobs.removeIf(mob -> mob.squaredDistanceTo(player)
                < NIGHT_MIN_RADIUS * NIGHT_MIN_RADIUS);
            if (mobs.isEmpty()) continue;
            MobEntity chosen = mobs.get(player.getEntityWorld().getRandom().nextInt(mobs.size()));
            begin(chosen, player, true);
            NEXT_NATURAL_EVENT.put(player.getUuid(), clock + AUTO_COOLDOWN_TICKS);
        }
    }

    public static void tick(MinecraftServer server) {
        clock++;
        for (Replacement event : new ArrayList<>(ACTIVE.values())) {
            var entity = event.world.getEntity(event.mobId);
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(event.playerId);

            if (!(entity instanceof MobEntity mob) || !mob.isAlive()
                || target == null || !target.isAlive()
                || target.getEntityWorld() != event.world
                || mob.squaredDistanceTo(target) > 48 * 48
                || event.ticks >= DURATION
                || (event.natural && !night(event.world))) {
                finish(event, entity instanceof MobEntity mob ? mob : null);
                continue;
            }

            event.ticks++;
            mob.setSprinting(true);
            mob.setTarget(target);
            if (event.ticks % 5 == 0) {
                mob.getNavigation().startMovingTo(target, CHASE_SPEED);
            }
            if (event.ticks % 23 == 0) {
                float pitch = (event.ticks % 3 == 0) ? 0.58f : 0.86f;
                event.world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 2.0f, pitch);
            }
        }

        if (clock % AUTO_CHECK_TICKS == 0) checkNighttime(server);

        if (clock % 24000 == 0) {
            NEXT_NATURAL_EVENT.entrySet().removeIf(entry -> entry.getValue() < clock);
        }
    }

    private static void finish(Replacement event, MobEntity mob) {
        ACTIVE.remove(event.mobId);
        if (mob != null && mob.isAlive()) {
            ((FanumReplaced) mob).fanum$setReplaced(false);
            mob.getNavigation().stop();
            mob.setSprinting(event.oldSprinting);
            mob.setTarget(event.oldTarget != null && event.oldTarget.isAlive()
                ? event.oldTarget : null);
        }
    }
}
