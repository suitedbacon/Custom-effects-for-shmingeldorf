package com.suitedbacon.fanum;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PhantomEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Eyestrain's server-authoritative encounter. No client packet or renderer mixin is
 * needed for the gameplay; the Phantom's normal entity position is synchronized.
 *
 * Trigger: direct, unobstructed eye contact from <= 32 blocks.
 * Encounter: a fast approach, then slow orbit for 20 seconds.
 * Damage: 1 health (half a heart) every 5 ticks of direct eye contact, bypassing
 * armor, Resistance, absorption and vanilla damage cooldown by directly reducing
 * health. A final vanilla void damage event handles death correctly.
 */
public final class EyestrainEncounter {
    private static final String ACTIVE_TAG = "fanum_eyestrain_active";
    private static final int DURATION = 400; // 20 seconds
    private static final int DAMAGE_INTERVAL = 5; // four half-hearts each second
    private static final int COOLDOWN = 240; // 12 seconds
    private static final double DETECTION_RANGE = 32.0;
    private static final double ORBIT_RADIUS = 3.7;
    private static final double ORBIT_SPEED = 0.028; // radians per tick
    private static final Map<UUID, Game> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static long clock;

    private EyestrainEncounter() {}

    private static final class Game {
        final UUID phantomId;
        final ServerWorld world;
        final boolean previousAiDisabled;
        final double startAngle;
        int ticks;
        int stareTicks;

        Game(PhantomEntity phantom, ServerPlayerEntity player) {
            this.phantomId = phantom.getUuid();
            this.world = player.getServerWorld();
            this.previousAiDisabled = phantom.isAiDisabled();
            Vec3d delta = phantom.getPos().subtract(player.getPos());
            this.startAngle = Math.atan2(delta.z, delta.x);
        }
    }

    public static boolean isActive(ServerPlayerEntity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    public static void tick(MinecraftServer server) {
        clock++;
        // New encounters only begin for spectators/creative never and never for
        // another player's already claimed Eyestrain phantom.
        Set<UUID> claimed = new HashSet<>();
        for (Game game : ACTIVE.values()) claimed.add(game.phantomId);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            Game game = ACTIVE.get(playerId);
            if (game != null) {
                step(player, game);
            } else if (eligible(player) && clock >= COOLDOWN_UNTIL.getOrDefault(playerId, 0L)) {
                if ((clock & 1) == 0) {
                    findEyestrain(player, claimed);
                }
            }
        }

        // A player may disconnect without a final player tick.
        Iterator<Map.Entry<UUID, Game>> itr = ACTIVE.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<UUID, Game> entry = itr.next();
            if (server.getPlayerManager().getPlayer(entry.getKey()) == null) {
                release(entry.getValue());
                itr.remove();
            }
        }
        if ((clock % 1200) == 0) {
            COOLDOWN_UNTIL.entrySet().removeIf(e -> e.getValue() < clock);
        }
    }

    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().creativeMode;
    }

    private static void findEyestrain(ServerPlayerEntity player, Set<UUID> claimed) {
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getEyePos();
        Box bounds = new Box(center.add(-DETECTION_RANGE, -DETECTION_RANGE, -DETECTION_RANGE),
                             center.add(DETECTION_RANGE, DETECTION_RANGE, DETECTION_RANGE));
        for (PhantomEntity phantom : world.getEntitiesByClass(PhantomEntity.class, bounds,
                p -> p.isAlive() && p.getCommandTags().contains(FanumMod.EYESTRAIN) && !claimed.contains(p.getUuid()))) {
            if (staring(player, phantom, DETECTION_RANGE, 0.982)) {
                start(player, phantom);
                claimed.add(phantom.getUuid());
                return;
            }
        }
    }

    private static void start(ServerPlayerEntity player, PhantomEntity phantom) {
        Game game = new Game(phantom, player);
        ACTIVE.put(player.getUuid(), game);
        phantom.setAiDisabled(true);
        phantom.setVelocity(Vec3d.ZERO);
        player.addCommandTag(ACTIVE_TAG);
        applyEffects(player);
        player.sendMessage(Text.literal("EYESTRAIN - DON'T LOOK AT IT").formatted(Formatting.DARK_RED), true);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 1.8f, 0.65f);
    }

    private static void applyEffects(ServerPlayerEntity player) {
        // Amplifier 4 means Resistance V, not IV.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 42, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 65, 0, false, false, true));
    }

    private static void step(ServerPlayerEntity player, Game game) {
        ServerWorld world = player.getServerWorld();
        Entity entity = game.world.getEntity(game.phantomId);
        if (world != game.world || !(entity instanceof PhantomEntity phantom)
                || !phantom.isAlive() || !eligible(player) || player.squaredDistanceTo(phantom) > 64 * 64
                || game.ticks >= DURATION) {
            finish(player, game);
            return;
        }

        game.ticks++;
        if (game.ticks % 20 == 0) applyEffects(player);
        if (game.ticks % 47 == 0) {
            world.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(),
                    SoundEvents.ENTITY_PHANTOM_AMBIENT, SoundCategory.HOSTILE, 1.2f,
                    0.65f + (game.ticks % 5) * 0.06f);
        }

        // Phase 1: the Phantom surges toward the player.
        // Phase 2: once close enough, orbit slowly at eye level.
        Vec3d eye = player.getEyePos();
        Vec3d goal;
        double distanceToPlayer = phantom.getPos().distanceTo(eye);
        if (game.ticks < 40 && distanceToPlayer > ORBIT_RADIUS + 0.4) {
            Vec3d approach = eye.subtract(phantom.getPos()).normalize();
            goal = phantom.getPos().add(approach.multiply(Math.min(1.18, distanceToPlayer - ORBIT_RADIUS)));
        } else {
            double theta = game.startAngle + game.ticks * ORBIT_SPEED;
            goal = eye.add(
                Math.cos(theta) * ORBIT_RADIUS,
                0.15 + Math.sin(game.ticks * 0.095) * 0.42,
                Math.sin(theta) * ORBIT_RADIUS
            );
        }
        phantom.setPosition(goal.x, goal.y, goal.z);
        phantom.setVelocity(Vec3d.ZERO);
        phantom.setTarget(null);
        Vec3d facing = eye.subtract(goal);
        phantom.setYaw((float) (Math.atan2(-facing.x, facing.z) * 180.0 / Math.PI));

        boolean looking = staring(player, phantom, 19.0, 0.970);
        if (looking) {
            game.stareTicks++;
            if (game.ticks % DAMAGE_INTERVAL == 0) {
                // Direct-health damage ignores Resistance V, armor, shields, and
                // vanilla hurt invulnerability frames. Fatal damage is delegated
                // to Minecraft to preserve death and life-system callbacks.
                if (player.getHealth() <= 1.0f) {
                    player.damage(world, player.getDamageSources().outOfWorld(), 1000.0f);
                } else {
                    player.setHealth(Math.max(1.0f, player.getHealth() - 1.0f));
                    world.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.4f, 0.8f);
                }
            }
        }

        if (game.ticks % 20 == 0) {
            int seconds = Math.max(0, (DURATION - game.ticks + 19) / 20);
            player.sendMessage(Text.literal(
                (looking ? "LOOK AWAY!  " : "Don't look at Eyestrain  ") + seconds + "s"
            ).formatted(looking ? Formatting.RED : Formatting.GRAY), true);
        }
    }

    private static boolean staring(ServerPlayerEntity player, PhantomEntity phantom, double maxDistance, double minDot) {
        Vec3d origin = player.getEyePos();
        Vec3d target = phantom.getBoundingBox().getCenter();
        Vec3d difference = target.subtract(origin);
        double distanceSquared = difference.lengthSquared();
        if (distanceSquared < 0.001 || distanceSquared > maxDistance * maxDistance) return false;
        Vec3d direction = player.getRotationVec(1.0F);
        if (direction.dotProduct(difference.normalize()) < minDot) return false;
        HitResult hit = player.getServerWorld().raycast(
            new RaycastContext(origin, target, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void release(Game game) {
        Entity entity = game.world.getEntity(game.phantomId);
        if (entity instanceof PhantomEntity phantom && phantom.isAlive()) {
            phantom.setAiDisabled(game.previousAiDisabled);
            phantom.setVelocity(Vec3d.ZERO);
        }
    }

    private static void finish(ServerPlayerEntity player, Game game) {
        ACTIVE.remove(player.getUuid());
        COOLDOWN_UNTIL.put(player.getUuid(), clock + COOLDOWN);
        player.removeCommandTag(ACTIVE_TAG);
        release(game);
        if (player.isAlive()) {
            player.sendMessage(Text.literal("Eyestrain has stopped circling you.")
                .formatted(Formatting.GRAY), true);
        }
    }
}
