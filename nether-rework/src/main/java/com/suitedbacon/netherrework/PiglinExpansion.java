package com.suitedbacon.netherrework;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PiglinBruteEntity;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.equipment.trim.ArmorTrim;
import net.minecraft.item.equipment.trim.ArmorTrimMaterials;
import net.minecraft.item.equipment.trim.ArmorTrimPatterns;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.ItemEntity;
import java.util.*;

public final class PiglinExpansion {
    private static final String INIT = "nr_initialized";
    private static final String PREFIX = "nr_";
    private static final List<String> VARIANTS = List.of("banner", "elite", "wildcard", "barrageman", "magic", "charger", "harbinger");
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final Map<UUID, Long> HARBINGER_CLEAR = new HashMap<>();

    private PiglinExpansion() {}

    public static void initialize() {
        ServerEntityEvents.ENTITY_LOAD.register(PiglinExpansion::onEntityLoad);
        ServerTickEvents.END_WORLD_TICK.register(PiglinExpansion::onWorldTick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            if (victim instanceof PlayerEntity player && source.getAttacker() instanceof PiglinBruteEntity && wearingOrHoldingGold(player))
                return false;
            if (!(victim instanceof PiglinEntity piglin) || !(victim.getEntityWorld() instanceof ServerWorld world))
                return true;
            if (!source.isOf(DamageTypes.ARROW))
                return true;
            if (hasBarragemanNear(world, piglin, 24))
                return false;
            PiglinEntity magic = findMagic(world, piglin, 15);
            if (magic == null) return true;
            Entity attacker = source.getAttacker();
            if (attacker != null && attacker.isAlive() && attacker != piglin && !attacker.getType().equals(EntityType.PIGLIN)) {
                Vec3d aim = attacker.getEyePos().subtract(piglin.getEyePos()).normalize();
                ArrowEntity rebound = new ArrowEntity(world, magic, new ItemStack(Items.ARROW), null);
                rebound.refreshPositionAndAngles(piglin.getX(), piglin.getEyeY(), piglin.getZ(), 0, 0);
                rebound.setVelocity(aim.multiply(2.6));
                rebound.setDamage(5.0);
                rebound.addCommandTag("nr_rebound");
                world.spawnEntity(rebound);
            }
            world.spawnParticles(ParticleTypes.ENCHANT, piglin.getX(), piglin.getBodyY(0.5), piglin.getZ(), 24, 0.6, 0.7, 0.6, 0.2);
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
            if (dead instanceof PiglinBruteEntity brute && dead.getEntityWorld() instanceof ServerWorld world) {
                world.spawnEntity(new ItemEntity(world, brute.getX(), brute.getY(), brute.getZ(), new ItemStack(Items.GOLD_INGOT, 7)));
            }
            CHARGES.remove(dead.getUuid());
            HARBINGER_CLEAR.remove(dead.getUuid());
        });
    }

    private static void onEntityLoad(Entity entity, ServerWorld world) {
        if (entity instanceof PiglinBruteEntity brute) {
            if (brute.getCommandTags().add(INIT)) {
                var follow = brute.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
                if (follow != null) follow.setBaseValue(Math.max(40.0, follow.getBaseValue()));
            }
            return;
        }
        if (!(entity instanceof PiglinEntity piglin) || !piglin.getCommandTags().add(INIT)) return;
        var follow = piglin.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(Math.max(42.0, follow.getBaseValue()));

        // Existing manually marked piglins keep their chosen variant.
        for (String variant : VARIANTS) {
            if (piglin.getCommandTags().contains(PREFIX + variant)) {
                configure(piglin, world, variant);
                return;
            }
        }

        if (world.getRegistryKey() != World.NETHER) return;
        int roll = world.getRandom().nextInt(300);
        String variant = roll < 12 ? "banner"
            : roll < 20 ? "elite"
            : roll < 27 ? "wildcard"
            : roll < 33 ? "barrageman"
            : roll < 39 ? "magic"
            : roll < 47 ? "charger"
            : roll < 54 ? "harbinger" : null;
        if (variant != null) configure(piglin, world, variant);
        // A rarer wild Piglin Brute may appear among ordinary Nether piglins.
        if (world.getRandom().nextInt(65) == 0) {
            PiglinBruteEntity brute = EntityType.PIGLIN_BRUTE.create(world, SpawnReason.COMMAND);
            if (brute != null) {
                brute.addCommandTag(INIT);
                brute.refreshPositionAndAngles(piglin.getX() + 1, piglin.getY(), piglin.getZ() + 1, 0, 0);
                world.spawnEntity(brute);
            }
        }
    }

    public static boolean isVariant(String variant) {
        return VARIANTS.contains(variant) || "brute".equals(variant);
    }

    public static Entity spawn(ServerWorld world, Vec3d pos, String variant) {
        if ("brute".equals(variant)) {
            PiglinBruteEntity brute = EntityType.PIGLIN_BRUTE.create(world, SpawnReason.COMMAND);
            if (brute != null) {
                brute.addCommandTag(INIT);
                brute.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
                world.spawnEntity(brute);
            }
            return brute;
        }
        PiglinEntity piglin = EntityType.PIGLIN.create(world, SpawnReason.COMMAND);
        if (piglin == null) return null;
        piglin.addCommandTag(INIT);
        if (isVariant(variant)) configure(piglin, world, variant);
        else {
            var follow = piglin.getAttributeInstance(EntityAttributes.FOLLOW_RANGE);
            if (follow != null) follow.setBaseValue(42.0);
        }
        piglin.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        world.spawnEntity(piglin);
        return piglin;
    }

    public static int horde(ServerWorld world, Vec3d center, int amount) {
        int total = 0;
        for (int i = 0; i < amount; i++) {
            double angle = 2 * Math.PI * i / amount;
            String variant = world.getRandom().nextInt(3) == 0
                ? VARIANTS.get(world.getRandom().nextInt(VARIANTS.size())) : "normal";
            Entity entity = spawn(world, center.add(Math.cos(angle) * 3.5, 0, Math.sin(angle) * 3.5), variant);
            if (entity != null) total++;
        }
        return total;
    }

    private static void configure(PiglinEntity p, ServerWorld world, String variant) {
        p.addCommandTag(PREFIX + variant);
        p.setCustomName(Text.literal(switch (variant) {
            case "banner" -> "Snout Captain";
            case "elite" -> "Elite Piglin";
            case "wildcard" -> "Wildcard";
            case "barrageman" -> "The Barrageman";
            case "magic" -> "Magic Pig";
            case "charger" -> "Charger";
            case "harbinger" -> "Harbinger";
            default -> "Piglin";
        }));
        p.setCustomNameVisible(true);
        switch (variant) {
            case "banner" -> {
                ItemStack banner = new ItemStack(Items.BLACK_BANNER);
                banner.set(DataComponentTypes.BANNER_PATTERNS,
                    new BannerPatternsComponent.Builder()
                        .add(world.getRegistryManager().getOrThrow(RegistryKeys.BANNER_PATTERN).getOrThrow(BannerPatterns.SNOUT), DyeColor.WHITE)
                        .build());
                p.equipStack(EquipmentSlot.HEAD, banner);
                ItemStack crossbow = new ItemStack(Items.CROSSBOW);
                enchant(world, crossbow, Enchantments.MULTISHOT, 1);
                enchant(world, crossbow, Enchantments.PIERCING, 4);
                enchant(world, crossbow, Enchantments.QUICK_CHARGE, 3);
                p.equipStack(EquipmentSlot.MAINHAND, crossbow);
                p.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
                p.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
            }
            case "elite" -> {
                armor(p, EquipmentSlot.HEAD, Items.NETHERITE_HELMET, world);
                armor(p, EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, world);
                armor(p, EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, world);
                armor(p, EquipmentSlot.FEET, Items.NETHERITE_BOOTS, world);
                p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
                p.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
            }
            case "wildcard" -> p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            case "barrageman" -> {
                p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                var hp = p.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (hp != null) { hp.setBaseValue(28.0); p.setHealth(28f); }
            }
            case "magic" -> {
                ItemStack helmet = new ItemStack(Items.GOLDEN_HELMET);
                ItemStack chest = new ItemStack(Items.GOLDEN_CHESTPLATE);
                ItemStack legs = new ItemStack(Items.GOLDEN_LEGGINGS);
                ItemStack boots = new ItemStack(Items.GOLDEN_BOOTS);
                for (ItemStack part : List.of(helmet, chest, legs, boots)) {
                    part.set(DataComponentTypes.TRIM, new ArmorTrim(
                        world.getRegistryManager().getOrThrow(RegistryKeys.TRIM_MATERIAL).getOrThrow(ArmorTrimMaterials.DIAMOND),
                        world.getRegistryManager().getOrThrow(RegistryKeys.TRIM_PATTERN).getOrThrow(ArmorTrimPatterns.SENTRY)));
                }
                p.equipStack(EquipmentSlot.HEAD, helmet);
                p.equipStack(EquipmentSlot.CHEST, chest);
                p.equipStack(EquipmentSlot.LEGS, legs);
                p.equipStack(EquipmentSlot.FEET, boots);
                var hp = p.getAttributeInstance(EntityAttributes.MAX_HEALTH);
                if (hp != null) { hp.setBaseValue(32.0); p.setHealth(32f); }
                var scale = p.getAttributeInstance(EntityAttributes.SCALE);
                if (scale != null) scale.setBaseValue(0.78);
            }
            case "charger" -> {
                p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
                var speed = p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (speed != null) speed.setBaseValue(0.36);
            }
            case "harbinger" -> {
                p.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
                p.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
                p.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
                p.equipStack(EquipmentSlot.FEET, new ItemStack(Items.GOLDEN_BOOTS));
                var scale = p.getAttributeInstance(EntityAttributes.SCALE);
                if (scale != null) scale.setBaseValue(0.7);
                var speed = p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (speed != null) speed.setBaseValue(0.4);
            }
        }
    }

    private static void armor(PiglinEntity p, EquipmentSlot slot, net.minecraft.item.Item item, ServerWorld world) {
        ItemStack stack = new ItemStack(item);
        enchant(world, stack, Enchantments.BINDING_CURSE, 1);
        p.equipStack(slot, stack);
        p.setEquipmentDropChance(slot, 0f);
    }
    private static void enchant(ServerWorld world, ItemStack stack,
                                net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key, int level) {
        stack.addEnchantment(world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(key), level);
    }

    private static boolean wearingOrHoldingGold(PlayerEntity p) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack s = p.getEquippedStack(slot);
            if (s.isOf(Items.GOLDEN_HELMET) || s.isOf(Items.GOLDEN_CHESTPLATE)
                || s.isOf(Items.GOLDEN_LEGGINGS) || s.isOf(Items.GOLDEN_BOOTS)) return true;
        }
        for (ItemStack s : List.of(p.getMainHandStack(), p.getOffHandStack())) {
            if (s.isOf(Items.GOLD_INGOT) || s.isOf(Items.GOLD_BLOCK)
                || s.isOf(Items.GOLD_NUGGET) || s.isOf(Items.GOLDEN_SWORD)
                || s.isOf(Items.GOLDEN_AXE)) return true;
        }
        return false;
    }

    private static void onWorldTick(ServerWorld world) {
        long time = world.getTime();
        if (time % 2 != 0) return;
        HashSet<UUID> handled = new HashSet<>();
        for (PlayerEntity player : world.getPlayers()) {
            Box region = player.getBoundingBox().expand(72);
            for (PiglinEntity piglin : world.getEntitiesByClass(PiglinEntity.class, region, Entity::isAlive)) {
                if (!handled.add(piglin.getUuid())) continue;
                tickPiglin(world, piglin, time);
            }
            for (PiglinBruteEntity brute : world.getEntitiesByClass(PiglinBruteEntity.class, region, Entity::isAlive)) {
                if (brute.getTarget() instanceof PlayerEntity target && wearingOrHoldingGold(target)) {
                    brute.setTarget(null);
                    brute.getBrain().forget(MemoryModuleType.ATTACK_TARGET);
                    brute.getBrain().forget(MemoryModuleType.ANGRY_AT);
                }
            }
            for (ArrowEntity arrow : world.getEntitiesByClass(ArrowEntity.class, region, e -> e.isAlive() && !e.getCommandTags().contains("nr_arrow_done"))) {
                arrow.addCommandTag("nr_arrow_done");
                if (arrow.getOwner() instanceof PiglinEntity piglin) {
                    if (piglin.getCommandTags().contains(PREFIX + "wildcard")) {
                        fireworkFromArrow(world, arrow, piglin);
                    } else {
                        arrow.setDamage(arrow.getDamage() * 1.6);
                    }
                }
            }
        }
        if (time % 600 == 0) {
            CHARGES.entrySet().removeIf(e -> world.getServer().getPlayerManager().getPlayer(e.getKey()) == null && !handled.contains(e.getKey()) && e.getValue().lastTick < time - 400);
            HARBINGER_CLEAR.entrySet().removeIf(e -> !handled.contains(e.getKey()));
        }
    }

    private static void fireworkFromArrow(ServerWorld world, ArrowEntity arrow, PiglinEntity owner) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponentTypes.FIREWORKS, new FireworksComponent(1,
            List.of(new FireworkExplosionComponent(FireworkExplosionComponent.Type.BURST,
                new IntArrayList(new int[] {0xFFAA00, 0xFF4400}), new IntArrayList(new int[] {0xFFFF77}), true, true))));
        FireworkRocketEntity firework = new FireworkRocketEntity(world, rocket, owner);
        firework.refreshPositionAndAngles(arrow.getX(), arrow.getY(), arrow.getZ(), arrow.getYaw(), arrow.getPitch());
        firework.setVelocity(arrow.getVelocity().multiply(1.35));
        firework.addCommandTag("nr_wildcard_firework");
        world.spawnEntity(firework);
        arrow.discard();
    }

    private static void tickPiglin(ServerWorld world, PiglinEntity piglin, long tick) {
        if (piglin.getCommandTags().contains(PREFIX + "banner") && tick % 20 == 0) {
            for (PiglinEntity ally : world.getEntitiesByClass(PiglinEntity.class, piglin.getBoundingBox().expand(12), Entity::isAlive)) {
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 45, 0, true, true));
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 45, 0, true, true));
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 45, 0, true, true));
            }
        }
        if (piglin.getCommandTags().contains(PREFIX + "magic")) tickMagic(world, piglin, tick);
        if (piglin.getCommandTags().contains(PREFIX + "barrageman")) tickBarrage(world, piglin, tick);
        if (piglin.getCommandTags().contains(PREFIX + "charger")) tickCharger(world, piglin, tick);
        if (piglin.getCommandTags().contains(PREFIX + "harbinger")) tickHarbinger(world, piglin, tick);
    }

    private static PlayerEntity nearestPlayer(ServerWorld world, Entity entity, double range) {
        PlayerEntity nearest = null;
        double best = range * range;
        for (PlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            double dist = p.squaredDistanceTo(entity);
            if (dist < best) { nearest = p; best = dist; }
        }
        return nearest;
    }

    private static PiglinEntity findMagic(ServerWorld world, Entity p, int range) {
        for (PiglinEntity m : world.getEntitiesByClass(PiglinEntity.class, p.getBoundingBox().expand(range), Entity::isAlive))
            if (m.getCommandTags().contains(PREFIX + "magic")) return m;
        return null;
    }

    private static boolean hasBarragemanNear(ServerWorld world, Entity p, int range) {
        for (PiglinEntity m : world.getEntitiesByClass(PiglinEntity.class, p.getBoundingBox().expand(range), Entity::isAlive))
            if (m.getCommandTags().contains(PREFIX + "barrageman")) return true;
        return false;
    }

    private static void tickMagic(ServerWorld world, PiglinEntity magic, long tick) {
        if (tick % 10 == 0) {
            for (PiglinEntity ally : world.getEntitiesByClass(PiglinEntity.class, magic.getBoundingBox().expand(15), Entity::isAlive)) {
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 32, 0, true, false));
                world.spawnParticles(ParticleTypes.ENCHANT, ally.getX(), ally.getBodyY(0.5), ally.getZ(), 7, 0.55, 0.55, 0.55, 0.02);
            }
        }
        PlayerEntity target = nearestPlayer(world, magic, 19);
        if (target == null || !magic.canSee(target)) return;
        if (tick % 100 >= 80) {
            Vec3d start = magic.getEyePos(), end = target.getEyePos();
            for (int i = 0; i <= 16; i++) {
                Vec3d point = start.lerp(end, i / 16.0);
                world.spawnParticles(ParticleTypes.END_ROD, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.001);
            }
        }
        if (tick % 100 == 98) target.damage(world, magic.getDamageSources().mobAttack(magic), 7f);
        if (tick % 60 == 0)
            for (PiglinEntity ally : world.getEntitiesByClass(PiglinEntity.class, magic.getBoundingBox().expand(15), Entity::isAlive)) {
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 80, 0, true, true));
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 0, true, true));
            }
    }

    private static void tickBarrage(ServerWorld world, PiglinEntity barrage, long tick) {
        PlayerEntity target = nearestPlayer(world, barrage, 28);
        if (target == null) return;
        if (tick % 12 == 6) {
            world.spawnParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 0.15, target.getZ(), 14, 1.7, 0.1, 1.7, 0.005);
        }
        if (tick % 12 != 0) return;
        for (int i = 0; i < 3; i++) {
            double offsetX = (world.getRandom().nextDouble() - 0.5) * 4.0;
            double offsetZ = (world.getRandom().nextDouble() - 0.5) * 4.0;
            ArrowEntity arrow = new ArrowEntity(world, barrage, new ItemStack(Items.ARROW), null);
            arrow.refreshPositionAndAngles(target.getX() + offsetX, target.getY() + 16, target.getZ() + offsetZ, 0, 90);
            arrow.setVelocity(0, -2.5, 0);
            arrow.setDamage(5.0);
            arrow.addCommandTag("nr_arrow_done");
            world.spawnEntity(arrow);
        }
    }

    private record Charge(Vec3d direction, UUID target, long start, long lastTick, boolean hit) {}

    private static void tickCharger(ServerWorld world, PiglinEntity charger, long tick) {
        Charge state = CHARGES.get(charger.getUuid());
        if (state == null || tick > state.start + 135) {
            PlayerEntity target = nearestPlayer(world, charger, 20);
            if (target == null) return;
            Vec3d dir = target.getPos().subtract(charger.getPos()).multiply(1, 0, 1);
            if (dir.lengthSquared() < 0.01) return;
            state = new Charge(dir.normalize(), target.getUuid(), tick, tick, false);
            CHARGES.put(charger.getUuid(), state);
        }
        long phase = tick - state.start;
        if (phase < 34) {
            charger.getNavigation().stop();
            Vec3d origin = charger.getPos();
            for (int n = 1; n <= 16; n++) {
                Vec3d point = origin.add(state.direction.multiply(n));
                world.spawnParticles(ParticleTypes.FLAME, point.x, charger.getY() + 0.3, point.z, 1, 0.02, 0.04, 0.02, 0.0);
            }
        } else if (phase < 46) {
            charger.setVelocity(state.direction.multiply(1.75).add(0, charger.getVelocity().y, 0));
            charger.velocityModified = true;
            if (!state.hit) {
                for (PlayerEntity p : world.getPlayers()) {
                    if (!p.isCreative() && !p.isSpectator() && p.squaredDistanceTo(charger) < 6) {
                        p.damage(world, charger.getDamageSources().mobAttack(charger), 12f);
                        p.addVelocity(state.direction.x * 1.5, 0.4, state.direction.z * 1.5);
                        state = new Charge(state.direction, state.target, state.start, tick, true);
                        CHARGES.put(charger.getUuid(), state);
                        break;
                    }
                }
            }
        }
    }

    private static void tickHarbinger(ServerWorld world, PiglinEntity harbinger, long tick) {
        PlayerEntity player = nearestPlayer(world, harbinger, 28);
        if (player == null) return;
        Vec3d away = harbinger.getPos().subtract(player.getPos()).multiply(1, 0, 1);
        if (away.lengthSquared() > 0.01) {
            Vec3d destination = harbinger.getPos().add(away.normalize().multiply(12));
            harbinger.getNavigation().startMovingTo(destination.x, destination.y, destination.z, 1.5);
        }
        boolean otherPiglins = !world.getEntitiesByClass(PiglinEntity.class,
            harbinger.getBoundingBox().expand(24), other -> other.isAlive() && other != harbinger).isEmpty();
        if (otherPiglins) {
            HARBINGER_CLEAR.remove(harbinger.getUuid());
            return;
        }
        Long clearAt = HARBINGER_CLEAR.putIfAbsent(harbinger.getUuid(), tick);
        if (clearAt == null) clearAt = tick;
        if (tick % 20 == 0) {
            int seconds = Math.max(0, (int) ((600 - (tick - clearAt)) / 20));
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, harbinger.getX(), harbinger.getY() + 1.5, harbinger.getZ(), 8, .35, .35, .35, .01);
            if (seconds == 10) harbinger.setCustomName(Text.literal("Harbinger - 10 seconds!"));
        }
        if (tick - clearAt >= 600) {
            horde(world, harbinger.getPos(), 8);
            harbinger.setCustomName(Text.literal("Harbinger"));
            HARBINGER_CLEAR.remove(harbinger.getUuid());
        }
    }
}
