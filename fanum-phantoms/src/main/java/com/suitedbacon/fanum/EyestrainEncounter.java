package com.suitedbacon.fanum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * A single Eyestrain minigame per player, with any number of nearby Eyestrains.
 * The FIRST surviving Eyestrain relentlessly follows the player's look direction;
 * the rest orbit farther out around the same player.
 *
 * Gameplay is server-authoritative. The vanilla entity data tracker supplies
 * the clients with the Phantom's location and black texture selection.
 */
public final class EyestrainEncounter {
    private static final String ACTIVE_TAG = "fanum_eyestrain_active";
    private static final int DURATION = 400; // 20 seconds
    private static final int DAMAGE_INTERVAL = 3; // half heart per 0.15 seconds
    private static final int COOLDOWN = 240; // 12 seconds
    private static final double DETECTION_RANGE = 32.0;
    private static final double GROUP_RANGE = 36.0;
    private static final double MAX_CHASE_DISTANCE = 64.0;
    private static final double LEAD_DISTANCE = 4.1;
    private static final double EXTRA_ORBIT_SPEED = 0.027;
    private static final Map<UUID, Game> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();
    private static long clock;

    private EyestrainEncounter() {}

    /** The individual phantom's pre-encounter state must be restored on exit. */
    private static final class Participant {
        final UUID phantomId;
        final boolean previousAiDisabled;

        Participant(PhantomEntity phantom) {
            phantomId = phantom.getUuid();
            previousAiDisabled = phantom.isAiDisabled();
        }
    }

    /** Ordered map: first living member is the one that stalks the crosshair. */
    private static final class Game {
        final ServerWorld world;
        final LinkedHashMap<UUID, Participant> members = new LinkedHashMap<>();
        int ticks;
        int stareTicks;

        Game(ServerWorld world) {
            this.world = world;
        }
    }

    public static boolean isActive(ServerPlayerEntity player) {
        return ACTIVE.containsKey(player.getUuid());
    }

    public static void tick(MinecraftServer server) {
        clock++;

        // Reserve all participants so two different players cannot control
        // the same phantom simultaneously. A game may gain new members later.
        Set<UUID> claimed = new HashSet<>();
        for (Game game : ACTIVE.values()) claimed.addAll(game.members.keySet());

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID playerId = player.getUuid();
            Game game = ACTIVE.get(playerId);
            if (game != null) {
                step(player, game, claimed);
            } else if (eligible(player) && clock >= COOLDOWN_UNTIL.getOrDefault(playerId, 0L)) {
                if ((clock & 1) == 0) {
                    findEyestrain(player, claimed);
                }
            }
        }

        // Make sure disconnected players do not leave permanently frozen Phantoms.
        Iterator<Map.Entry<UUID, Game>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Game> entry = iterator.next();
            if (server.getPlayerManager().getPlayer(entry.getKey()) == null) {
                releaseAll(entry.getValue());
                iterator.remove();
            }
        }
        if (clock % 1200 == 0) {
            COOLDOWN_UNTIL.entrySet().removeIf(entry -> entry.getValue() < clock);
        }
    }

    private static boolean eligible(ServerPlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().creativeMode;
    }

    private static boolean available(PhantomEntity phantom, Set<UUID> claimed) {
        return phantom.isAlive()
            && phantom.getCommandTags().contains(FanumMod.EYESTRAIN)
            && !claimed.contains(phantom.getUuid());
    }

    private static List<PhantomEntity> nearby(ServerPlayerEntity player, double range,
                                               Set<UUID> claimed) {
        ServerWorld world = player.getEntityWorld();
        Vec3d pos = player.getEyePos();
        Box bounds = new Box(pos.add(-range, -range, -range),
                             pos.add(range, range, range));
        return world.getEntitiesByClass(PhantomEntity.class, bounds,
            phantom -> available(phantom, claimed)
                && phantom.squaredDistanceTo(player) <= range * range);
    }

    private static void findEyestrain(ServerPlayerEntity player, Set<UUID> claimed) {
        List<PhantomEntity> candidates = nearby(player, DETECTION_RANGE, claimed);
        for (PhantomEntity phantom : candidates) {
            if (staring(player, phantom, DETECTION_RANGE, 0.982)) {
                start(player, phantom, claimed);
                return;
            }
        }
    }

    private static void enlist(Game game, PhantomEntity phantom, Set<UUID> claimed) {
        if (claimed.contains(phantom.getUuid())) return;
        game.members.put(phantom.getUuid(), new Participant(phantom));
        claimed.add(phantom.getUuid());
        phantom.setAiDisabled(true);
        phantom.setVelocity(Vec3d.ZERO);
        phantom.setTarget(null);
    }

    private static void recruitNearby(Game game, ServerPlayerEntity player, Set<UUID> claimed) {
        // Newly summoned Eyestrains may join an encounter already in progress.
        for (PhantomEntity phantom : nearby(player, GROUP_RANGE, claimed)) {
            enlist(game, phantom, claimed);
        }
    }

    private static void start(ServerPlayerEntity player, PhantomEntity staredAt,
                              Set<UUID> claimed) {
        Game game = new Game(player.getEntityWorld());
        // The entity that was actually looked at leads; other nearby
        // Eyestrains are activated without needing the player's gaze.
        enlist(game, staredAt, claimed);
        recruitNearby(game, player, claimed);
        ACTIVE.put(player.getUuid(), game);

        // Exactly 20 health points (10 hearts) on encounter entry.
        player.setHealth(Math.min(20.0f, player.getMaxHealth()));
        player.addCommandTag(ACTIVE_TAG);
        applyEffects(player);
        int count = game.members.size();
        player.sendMessage(Text.literal("EYESTRAIN x" + count + " - DON'T LOOK!")
            .formatted(Formatting.DARK_RED), true);

        ServerWorld world = player.getEntityWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.HOSTILE, 1.8f, 0.65f);
    }

    private static void applyEffects(ServerPlayerEntity player) {
        // Resistance V is amplifier 4. Eyestrain's direct-health damage ignores it.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 42, 4, false, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 65, 0, false, false, true));
    }

    private static PhantomEntity alivePhantom(Game game, UUID id) {
        Entity entity = game.world.getEntity(id);
        return entity instanceof PhantomEntity phantom && phantom.isAlive() ? phantom : null;
    }

    private static void step(ServerPlayerEntity player, Game game, Set<UUID> claimed) {
        if (player.getEntityWorld() != game.world || !eligible(player) || game.ticks >= DURATION) {
            finish(player, game);
            return;
        }

        // Every half-second, gather additional unclaimed Eyestrains.
        if (game.ticks % 10 == 0) recruitNearby(game, player, claimed);

        // Dead / removed / escaped entities do not end an encounter when others survive.
        Iterator<Map.Entry<UUID, Participant>> it = game.members.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Participant> e = it.next();
            PhantomEntity phantom = alivePhantom(game, e.getKey());
            if (phantom == null || phantom.squaredDistanceTo(player)
                    > MAX_CHASE_DISTANCE * MAX_CHASE_DISTANCE) {
                if (phantom != null) release(game, e.getValue());
                it.remove();
            }
        }

        if (game.members.isEmpty()) {
            finish(player, game);
            return;
        }

        game.ticks++;
        if (game.ticks % 20 == 0) applyEffects(player);

        Vec3d eye = player.getEyePos();
        Vec3d gaze = player.getRotationVec(1.0F).normalize();
        int count = game.members.size();
        int index = 0;
        boolean lookingAtAny = false;

        for (Participant participant : game.members.values()) {
            PhantomEntity phantom = alivePhantom(game, participant.phantomId);
            if (phantom == null) continue;

            Vec3d goal;
            if (index == 0) {
                // The lead stalker constantly STEERS toward the player's gaze.
                // Movement is rate-limited, so a sudden turn still lets the
                // player avert their eyes briefly before it catches up again.
                Vec3d target = eye.add(gaze.multiply(LEAD_DISTANCE));
                // Don't burrow underground or disappear high above a player
                // who looks straight up or down.
                double y = Math.max(player.getY() + 0.85,
                                    Math.min(player.getY() + 3.15, target.y + 0.15));
                goal = new Vec3d(target.x, y, target.z);
            } else {
                // Extra Eyestrains no longer cling to the center of vision:
                // they spread around the general area and sweep in an orbit.
                // The radius grows with the group size, avoiding overlap.
                int satellites = count - 1;
                double radius = Math.min(10.5, 4.4 + satellites * 0.62);
                double phase = 2.0 * Math.PI * (index - 1) / satellites;
                double angle = game.ticks * EXTRA_ORBIT_SPEED + phase;
                goal = eye.add(Math.cos(angle) * radius,
                    0.3 + Math.sin(game.ticks * 0.067 + index) * 0.8,
                    Math.sin(angle) * radius);
            }

            Vec3d current = phantom.getEntityPos();
            Vec3d delta = goal.subtract(current);
            double length = delta.length();
            // Dramatic initial charge from farther away, then relentless
            // follow. 1.45 blocks/tick far away; 0.65 nearby.
            double maxMove = length > 9.0 ? 1.45 : (index == 0 ? 0.72 : 0.51);
            if (length > 0.0001) {
                Vec3d next = current.add(delta.multiply(Math.min(1.0, maxMove / length)));
                phantom.setPosition(next.x, next.y, next.z);
            }
            phantom.setVelocity(Vec3d.ZERO);
            phantom.setTarget(null);
            Vec3d facing = eye.subtract(phantom.getEntityPos());
            phantom.setYaw((float) (Math.atan2(-facing.x, facing.z) * 180.0 / Math.PI));

            // Any Eyestrain being stared at counts, but don't multiply the
            // damage just because several happen to overlap in the crosshair.
            if (staring(player, phantom, 20.0, 0.970)) lookingAtAny = true;
            if ((game.ticks + index * 13) % 47 == 0) {
                game.world.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(),
                    SoundEvents.ENTITY_PHANTOM_AMBIENT, SoundCategory.HOSTILE,
                    1.0f, 0.65f + (index % 4) * 0.075f);
            }
            index++;
        }

        if (lookingAtAny) {
            game.stareTicks++;
            if (game.ticks % DAMAGE_INTERVAL == 0) {
                // Direct-health hits bypass Resistance V, armor, shields,
                // absorption and invulnerability frames.
                if (player.getHealth() <= 1.0f) {
                    player.damage(game.world, player.getDamageSources().outOfWorld(), 1000.0f);
                } else {
                    player.setHealth(Math.max(1.0f, player.getHealth() - 1.0f));
                    game.world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.4f, 0.8f);
                }
            }
        }

        if (game.ticks % 20 == 0) {
            int seconds = Math.max(0, (DURATION - game.ticks + 19) / 20);
            player.sendMessage(Text.literal(
                (lookingAtAny ? "LOOK AWAY!  " : "DON'T LOOK  ")
                    + seconds + "s  |  Eyestrains: " + count
            ).formatted(lookingAtAny ? Formatting.RED : Formatting.GRAY), true);
        }
    }

    private static boolean staring(ServerPlayerEntity player, PhantomEntity phantom,
                                   double maxDistance, double minDot) {
        Vec3d origin = player.getEyePos();
        Vec3d target = phantom.getBoundingBox().getCenter();
        Vec3d difference = target.subtract(origin);
        double distanceSquared = difference.lengthSquared();
        if (distanceSquared < 0.001 || distanceSquared > maxDistance * maxDistance) return false;
        Vec3d direction = player.getRotationVec(1.0F);
        if (direction.dotProduct(difference.normalize()) < minDot) return false;
        HitResult hit = player.getEntityWorld().raycast(
            new RaycastContext(origin, target, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static void release(Game game, Participant member) {
        PhantomEntity phantom = alivePhantom(game, member.phantomId);
        if (phantom != null) {
            phantom.setAiDisabled(member.previousAiDisabled);
            phantom.setVelocity(Vec3d.ZERO);
        }
    }

    private static void releaseAll(Game game) {
        for (Participant member : game.members.values()) release(game, member);
        game.members.clear();
    }

    private static void finish(ServerPlayerEntity player, Game game) {
        ACTIVE.remove(player.getUuid());
        COOLDOWN_UNTIL.put(player.getUuid(), clock + COOLDOWN);
        player.removeCommandTag(ACTIVE_TAG);
        releaseAll(game);
        if (player.isAlive()) {
            player.sendMessage(Text.literal("The Eyestrains finally lose interest.")
                .formatted(Formatting.GRAY), true);
        }
    }
}
