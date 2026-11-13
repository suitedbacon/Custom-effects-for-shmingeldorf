package com.suitedbacon.netherrework;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.*;

public final class BlazeExpansion {
    private static final String INIT = "nr_blaze_initialized";
    private static final String BRUTE = "nr_blaze_brute";
    private static final String LASER = "nr_blaze_laser";
    private static final String ORBIT = "nr_blaze_orbit";
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final boolean HAS_BEAMS = FabricLoader.getInstance().isModLoaded("shmingledorph_beams");

    private static final class State {
        long nextDash, warningEnd, dashEnd, stunEnd;
        Vec3d dashDirection = Vec3d.ZERO;
        long laserNext, laserFireEnd;
        UUID laserTarget;
        Vec3d laserDirection = Vec3d.ZERO;
        boolean laserViaBeam;
        long lastSeen;
    }

    private BlazeExpansion() {}

    public static void initialize() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof SmallFireballEntity fireball
                    && fireball.getOwner() instanceof BlazeEntity blaze
                    && blaze.getCommandTags().contains(LASER)) {
                fireball.discard();
            }
            if (entity instanceof BlazeEntity blaze) onLoad(blaze, world);
        });
        ServerTickEvents.END_WORLD_TICK.register(BlazeExpansion::tickWorld);
    }

    private static void onLoad(BlazeEntity blaze, ServerWorld world) {
        State state = STATES.computeIfAbsent(blaze.getUuid(), id -> new State());
        state.nextDash = world.getTime() + 600;
        state.laserNext = world.getTime() + 100;
        state.lastSeen = world.getTime();
        if (!blaze.getCommandTags().add(INIT)) return;
        if (isSpecial(blaze)) {
            configure(blaze);
            return;
        }
        if (world.getRegistryKey() != World.NETHER) return;
        int roll = world.getRandom().nextInt(100);
        if (roll < 6) setVariant(blaze, "brute");
        else if (roll < 11) setVariant(blaze, "laser");
        else if (roll < 19) setVariant(blaze, "orbit");
    }

    private static boolean isSpecial(BlazeEntity blaze) {
        return blaze.getCommandTags().contains(BRUTE)
            || blaze.getCommandTags().contains(LASER)
            || blaze.getCommandTags().contains(ORBIT);
    }

    private static void setVariant(BlazeEntity blaze, String variant) {
        String tag = switch (variant) {
            case "brute" -> BRUTE;
            case "laser" -> LASER;
            case "orbit" -> ORBIT;
            default -> null;
        };
        if (tag != null) {
            blaze.addCommandTag(tag);
            configure(blaze);
        }
    }

    private static void configure(BlazeEntity blaze) {
        if (blaze.getCommandTags().contains(BRUTE)) {
            blaze.setCustomName(Text.literal("Brute Blaze"));
            var health = blaze.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (health != null) { health.setBaseValue(48.0); blaze.setHealth(48.0f); }
            var scale = blaze.getAttributeInstance(EntityAttributes.SCALE);
            if (scale != null) scale.setBaseValue(1.35);
        } else if (blaze.getCommandTags().contains(LASER)) {
            blaze.setCustomName(Text.literal("Laser Blaze"));
            var health = blaze.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (health != null) { health.setBaseValue(12.0); blaze.setHealth(12.0f); }
        } else if (blaze.getCommandTags().contains(ORBIT)) {
            blaze.setCustomName(Text.literal("Orbit Blaze"));
        }
        blaze.setCustomNameVisible(true);
    }

    public static BlazeEntity spawn(ServerWorld world, Vec3d pos, String variant) {
        BlazeEntity blaze = EntityType.BLAZE.create(world, SpawnReason.COMMAND);
        if (blaze == null) return null;
        blaze.addCommandTag(INIT);
        if (!"normal".equals(variant)) setVariant(blaze, variant);
        blaze.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        if (!world.spawnEntity(blaze)) return null;
        State state = STATES.computeIfAbsent(blaze.getUuid(), id -> new State());
        state.nextDash = world.getTime() + 600;
        state.laserNext = world.getTime() + 100;
        return blaze;
    }

    public static int horde(ServerWorld world, Vec3d center, int number) {
        int successes = 0;
        String[] types = {"normal", "normal", "normal", "normal", "brute", "laser", "orbit"};
        for (int i = 0; i < number; i++) {
            double angle = 2 * Math.PI * i / number;
            Vec3d pos = center.add(4 * Math.cos(angle), 0, 4 * Math.sin(angle));
            if (spawn(world, pos, types[world.getRandom().nextInt(types.length)]) != null) successes++;
        }
        return successes;
    }

    private static void tickWorld(ServerWorld world) {
        long tick = world.getTime();
        if (tick % 2 != 0) return;
        Set<UUID> handled = new HashSet<>();
        for (PlayerEntity player : world.getPlayers()) {
            Box region = player.getBoundingBox().expand(65);
            for (BlazeEntity blaze : world.getEntitiesByClass(BlazeEntity.class, region, Entity::isAlive)) {
                if (!handled.add(blaze.getUuid())) continue;
                State state = STATES.computeIfAbsent(blaze.getUuid(), id -> {
                    State s = new State();
                    s.nextDash = tick + 600;
                    s.laserNext = tick + 100;
                    return s;
                });
                state.lastSeen = tick;
                tickDash(world, blaze, state, tick);
                if (blaze.getCommandTags().contains(LASER)) tickLaser(world, blaze, state, tick);
                if (blaze.getCommandTags().contains(ORBIT) && state.warningEnd == 0 && tick >= state.stunEnd) tickOrbit(world, blaze, tick);
            }
        }
        if (tick % 600 == 0) STATES.entrySet().removeIf(e -> tick - e.getValue().lastSeen > 1200);
    }

    private static LivingEntity chooseTarget(ServerWorld world, BlazeEntity blaze, double range) {
        LivingEntity target = blaze.getTarget();
        if (target != null && target.isAlive() && target.squaredDistanceTo(blaze) <= range * range)
            return target;
        PlayerEntity nearest = null;
        double best = range * range;
        for (PlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double dist = p.squaredDistanceTo(blaze);
            if (dist < best) { best = dist; nearest = p; }
        }
        return nearest;
    }

    private static void tickDash(ServerWorld world, BlazeEntity blaze, State state, long tick) {
        if (tick < state.stunEnd) {
            blaze.setVelocity(Vec3d.ZERO);
            blaze.getNavigation().stop();
            return;
        }
        boolean brute = blaze.getCommandTags().contains(BRUTE);
        if (state.warningEnd > 0 && tick < state.warningEnd) {
            blaze.setVelocity(blaze.getVelocity().multiply(0.4));
            blaze.getNavigation().stop();
            world.spawnParticles(ParticleTypes.CRIMSON_SPORE, blaze.getX(), blaze.getBodyY(0.5),
                blaze.getZ(), 14, 0.7, 0.6, 0.7, 0.015);
            if (tick % 8 == 0) {
                Vec3d start = blaze.getEyePos();
                for (int i = 1; i <= 12; i++) {
                    Vec3d p = start.add(state.dashDirection.multiply(i * 1.5));
                    world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z,
                        1, 0.01, 0.01, 0.01, 0);
                }
            }
            return;
        }
        if (state.warningEnd > 0 && tick < state.dashEnd) {
            double speed = brute ? 2.25 : 1.65;
            Vec3d delta = state.dashDirection.multiply(speed * 2);
            Vec3d next = blaze.getEntityPos().add(delta);
            boolean wall = false;
            double verticalOffset = blaze.getBodyY(0.5) - blaze.getY();
            for (double step = 0.25; step <= delta.length() + 0.25; step += 0.35) {
                BlockPos ahead = BlockPos.ofFloored(blaze.getEntityPos()
                    .add(state.dashDirection.multiply(step)).add(0, verticalOffset, 0));
                if (!world.getBlockState(ahead).getCollisionShape(world, ahead).isEmpty()) {
                    wall = true;
                    break;
                }
            }
            Entity collision = null;
            for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                    blaze.getBoundingBox().stretch(delta).expand(0.8),
                    e -> e.isAlive() && e != blaze)) {
                if (entity.squaredDistanceTo(blaze) < 14 || entity.squaredDistanceTo(next) < 9) {
                    collision = entity;
                    break;
                }
            }
            if (wall || collision != null) {
                explode(world, blaze, brute, wall);
                state.dashEnd = 0;
                state.warningEnd = 0;
                state.nextDash = tick + 600;
                if (wall) state.stunEnd = tick + 48;
                blaze.setVelocity(Vec3d.ZERO);
                return;
            }
            blaze.setVelocity(state.dashDirection.multiply(speed));
            world.spawnParticles(ParticleTypes.FLAME, blaze.getX(), blaze.getBodyY(0.5),
                blaze.getZ(), 10, 0.5, 0.5, 0.5, 0.02);
            return;
        }
        if (state.warningEnd > 0) {
            state.warningEnd = 0;
            state.dashEnd = 0;
            state.nextDash = tick + 600;
        }
        if (tick < state.nextDash) return;
        LivingEntity target = chooseTarget(world, blaze, 30);
        if (target == null) { state.nextDash = tick + 40; return; }
        Vec3d direction = target.getEyePos().subtract(blaze.getEyePos());
        if (direction.lengthSquared() < 0.5) { state.nextDash = tick + 40; return; }
        state.dashDirection = direction.normalize();
        state.warningEnd = tick + 40;
        state.dashEnd = tick + 72;
    }

    private static void explode(ServerWorld world, BlazeEntity blaze, boolean brute, boolean wall) {
        double radius = brute ? 5.5 : 3.3;
        world.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(),
            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, brute ? 0.75f : 1.2f);
        world.spawnParticles(ParticleTypes.FLAME, blaze.getX(), blaze.getBodyY(0.5), blaze.getZ(),
            brute ? 200 : 110, radius * 0.45, radius * 0.35, radius * 0.45, 0.25);
        world.spawnParticles(ParticleTypes.LAVA, blaze.getX(), blaze.getBodyY(0.5), blaze.getZ(),
            brute ? 36 : 20, 1.2, 0.7, 1.2, 0.08);
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                blaze.getBoundingBox().expand(radius), e -> e.isAlive() && e != blaze)) {
            double distance = Math.sqrt(entity.squaredDistanceTo(blaze));
            if (distance > radius) continue;
            double strength = 1.0 - distance / radius;
            entity.damage(world, blaze.getDamageSources().mobAttack(blaze),
                (float) ((brute ? 14 : 8) * (0.35 + 0.65 * strength)));
            Vec3d away = entity.getEntityPos().subtract(blaze.getEntityPos());
            if (away.lengthSquared() > 0.01) {
                Vec3d push = away.normalize().multiply((brute ? 3.0 : 2.0) * (0.4 + 0.6 * strength));
                entity.addVelocity(push.x, Math.max(0.45, push.y + 0.5), push.z);
            }
            entity.setOnFireFor(4);
        }
        // Precisely bounded block destruction: no block entities, indestructibles, or hard blocks.
        int limit = brute ? 2 : 1;
        for (int dx = -limit; dx <= limit; dx++) for (int dy = -limit; dy <= limit; dy++)
            for (int dz = -limit; dz <= limit; dz++) {
                if (dx * dx + dy * dy + dz * dz > limit * limit + 1) continue;
                BlockPos pos = blaze.getBlockPos().add(dx, dy, dz);
                var block = world.getBlockState(pos);
                float hardness = block.getHardness(world, pos);
                if (!block.isAir() && world.getBlockEntity(pos) == null
                    && hardness >= 0 && hardness <= (brute ? 1.5f : 0.4f)
                    && block.getFluidState().isEmpty())
                    world.breakBlock(pos, false, blaze);
            }
        blaze.damage(world, blaze.getDamageSources().generic(), brute ? 5.0f : 3.0f);
        if (wall) blaze.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 48, 7));
    }

    private static void tickLaser(ServerWorld world, BlazeEntity blaze, State state, long tick) {
        if (state.laserFireEnd > tick) {
            if (!state.laserViaBeam) fallbackBeam(world, blaze, state, tick);
            return;
        }
        if (state.laserFireEnd > 0) {
            state.laserFireEnd = 0;
            state.laserTarget = null;
            state.laserNext = tick + 110;
        }
        if (tick < state.laserNext) return;
        LivingEntity target = chooseTarget(world, blaze, 35);
        if (target == null || !blaze.canSee(target)) { state.laserNext = tick + 30; return; }
        state.laserTarget = target.getUuid();
        state.laserDirection = target.getEyePos().subtract(blaze.getEyePos()).normalize();
        long elapsed = tick - state.laserNext;
        // Six seconds of sustained, visible charge.
        world.spawnParticles(ParticleTypes.FLAME, blaze.getX(), blaze.getBodyY(0.5),
            blaze.getZ(), 8 + (int)(elapsed / 12), 0.35, 0.45, 0.35, 0.005);
        if (elapsed % 8 == 0) {
            Vec3d start = blaze.getEyePos();
            for (int n = 1; n <= 24; n++) {
                Vec3d p = start.add(state.laserDirection.multiply(n * 1.2));
                world.spawnParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z,
                    1, 0.02, 0.02, 0.02, 0.0);
            }
        }
        if (elapsed < 120) return;
        state.laserDirection = target.getEyePos().subtract(blaze.getEyePos()).normalize();
        state.laserViaBeam = shootBeamMod(world, blaze, target);
        state.laserFireEnd = tick + 46;
        world.playSound(null, blaze.getX(), blaze.getY(), blaze.getZ(),
            SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.8f, 0.65f);
    }

    private static boolean shootBeamMod(ServerWorld world, BlazeEntity blaze, LivingEntity target) {
        if (!HAS_BEAMS) return false;
        // Beam V18 has a native /beam as <shooter> toward <target> <options> interface.
        // The temporary tags are removed immediately after dispatch to prevent selector leaks.
        blaze.addCommandTag("nr_beam_shooter_temp");
        target.addCommandTag("nr_beam_target_temp");
        try {
            String command = "beam as @e[tag=nr_beam_shooter_temp,limit=1] toward "
                + "@e[tag=nr_beam_target_temp,limit=1] "
                + "width:1.3 length:38 damage:4 playerdamage:4 duration:46 "
                + "interval:4 playerinterval:4 pierce:5 destructive:false fire:true color:255,90,15";
            var manager = world.getServer().getCommandManager();
            var source = world.getServer().getCommandSource().withWorld(world).withPosition(blaze.getEntityPos());
            var parsed = manager.getDispatcher().parse(command, source);
            if (!parsed.getExceptions().isEmpty()) return false;
            manager.parseAndExecute(source, command);
            return true;
        } catch (Exception ex) {
            return false;
        } finally {
            blaze.removeCommandTag("nr_beam_shooter_temp");
            target.removeCommandTag("nr_beam_target_temp");
        }
    }

    private static void fallbackBeam(ServerWorld world, BlazeEntity blaze, State state, long tick) {
        Vec3d start = blaze.getEyePos();
        Vec3d direction = state.laserDirection;
        double max = 36;
        for (double d = 0.5; d <= max; d += 0.65) {
            Vec3d p = start.add(direction.multiply(d));
            BlockPos pos = BlockPos.ofFloored(p);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
                max = d;
                break;
            }
            world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, .08, .08, .08, 0.0);
        }
        if (tick % 4 != 0) return;
        final double finalLength = max;
        Vec3d end = start.add(direction.multiply(finalLength));
        for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class,
                new Box(start, end).expand(1.6), e -> e.isAlive() && e != blaze)) {
            Vec3d relative = entity.getEyePos().subtract(start);
            double along = relative.dotProduct(direction);
            if (along < 0 || along > finalLength) continue;
            double distanceSquared = relative.subtract(direction.multiply(along)).lengthSquared();
            if (distanceSquared > 1.6) continue;
            entity.damage(world, blaze.getDamageSources().mobAttack(blaze), 4.0f);
            entity.setOnFireFor(4);
        }
    }

    private static void tickOrbit(ServerWorld world, BlazeEntity blaze, long tick) {
        LivingEntity target = chooseTarget(world, blaze, 30);
        if (target == null) return;
        double angle = tick * 0.027 + (blaze.getId() % 11) * 0.5;
        Vec3d desired = target.getEntityPos().add(Math.cos(angle) * 9, 2.7, Math.sin(angle) * 9);
        Vec3d change = desired.subtract(blaze.getEntityPos());
        if (change.lengthSquared() > 0.05) {
            blaze.setVelocity(change.normalize().multiply(Math.min(0.33, change.length() * 0.065)));
        }
    }
}
