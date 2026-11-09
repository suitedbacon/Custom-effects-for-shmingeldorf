package dev.shmingledorph.effects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.HashMap;

/**
 * Initial playable mechanics: registered effects, Cursed scoreboard integration,
 * player-only hallucination sounds and Void Omen darkness.
 * Remaining mechanics are explicitly not implemented yet.
 */
public final class ShmingledorphEffects implements ModInitializer {
    public static final String MOD_ID = "shmingledorph";
    public static final Map<String, RegistryEntry<StatusEffect>> EFFECTS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> SOUND_COOLDOWNS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private static void register(String name, int color, StatusEffectCategory category) {
        var effect = Registry.register(Registries.STATUS_EFFECT,
            Identifier.of(MOD_ID, name), new BasicEffect(category, color));
        EFFECTS.put(name, Registries.STATUS_EFFECT.getEntry(effect));
    }

    @Override
    public void onInitialize() {
        register("flashed", 0xFFFFFF, StatusEffectCategory.HARMFUL);
        register("hallucinating", 0x8972A8, StatusEffectCategory.HARMFUL);
        register("quiet_hallucination", 0x39343F, StatusEffectCategory.HARMFUL);
        register("void_omen", 0x151021, StatusEffectCategory.HARMFUL);
        register("stunned", 0xA6A6A6, StatusEffectCategory.HARMFUL);
        register("shield", 0x55DDF5, StatusEffectCategory.BENEFICIAL);
        register("immortality", 0xD8CFA0, StatusEffectCategory.BENEFICIAL);
        register("inverted", 0xC35DEB, StatusEffectCategory.HARMFUL);
        register("vulnerable", 0xB33044, StatusEffectCategory.HARMFUL);
        register("nimble", 0x8CEAC8, StatusEffectCategory.BENEFICIAL);
        register("cursed", 0x4A1324, StatusEffectCategory.HARMFUL);

        // Nimble: a 10% independent chance to evade an eligible hit.
        // Avoid cancelling environmental damage such as fire, falling or starvation.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.hasStatusEffect(EFFECTS.get("nimble"))) return true;
            if (!isCombatHit(source)) return true;
            if (RANDOM.nextFloat() >= 0.10f) return true;
            if (entity instanceof ServerPlayerEntity player) {
                run(player, "particle minecraft:cloud ~ ~1 ~ 0.35 0.4 0.35 0.03 14 force");
                run(player, "playsound minecraft:entity.player.attack.sweep player @s ~ ~ ~ 0.9 1.5");
            }
            return false;
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            if (!player.hasStatusEffect(EFFECTS.get("cursed"))) return;
            // Existing SMP datapack handles Death's Domain when life reaches zero.
            run(player, "scoreboard players set @s life 0");
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) tick(player);
        });
    }

    private static boolean isCombatHit(DamageSource source) {
        // Direct attacks have a living attacker; projectiles carry an owner.
        return source.getAttacker() instanceof LivingEntity
            || source.getSource() instanceof ProjectileEntity;
    }

    private static void run(ServerPlayerEntity player, String command) {
        // Executing as the actual player also avoids unescaped name selectors.
        var source = player.getCommandSource().withLevel(4);
        source.getServer().getCommandManager().executeWithPrefix(source, command);
    }

    private static void tick(ServerPlayerEntity player) {
        // Stunned phase 1: server-enforced severe movement, attack, and mining
        // penalties. Full stun still needs server interaction hooks and client
        // input blocking; do not mistake this for complete paralysis.
        if (player.hasStatusEffect(EFFECTS.get("stunned")) && player.age % 20 == 0) {
            run(player, "effect give @s minecraft:slowness 2 255 true");
            run(player, "effect give @s minecraft:mining_fatigue 2 255 true");
            run(player, "effect give @s minecraft:weakness 2 255 true");
        }
        var omen = player.getStatusEffect(EFFECTS.get("void_omen"));
        if (omen != null && player.age % 80 == 0) {
            int level = Math.min(5, omen.getAmplifier() + 1);
            run(player, "effect give @s minecraft:darkness " + (5 + level) + " 0 true");
        }

        boolean quiet = player.hasStatusEffect(EFFECTS.get("quiet_hallucination"));
        boolean hall = player.hasStatusEffect(EFFECTS.get("hallucinating"));
        if (!quiet && !hall && omen == null) {
            SOUND_COOLDOWNS.remove(player.getUuid());
            return;
        }
        int remaining = SOUND_COOLDOWNS.getOrDefault(player.getUuid(), 0) - 1;
        if (remaining > 0) {
            SOUND_COOLDOWNS.put(player.getUuid(), remaining);
            return;
        }
        SOUND_COOLDOWNS.put(player.getUuid(), quiet ? 65 + RANDOM.nextInt(160) : 110 + RANDOM.nextInt(240));
        String[] sounds = quiet
            ? new String[]{"minecraft:block.wooden_door.open", "minecraft:block.chest.open",
                "minecraft:block.glass.break", "minecraft:block.wood.break"}
            : new String[]{"minecraft:ambient.cave", "minecraft:entity.enderman.ambient",
                "minecraft:entity.creeper.primed", "minecraft:entity.warden.ambient"};
        String sound = sounds[RANDOM.nextInt(sounds.length)];
        // Use an offset sound origin: a fake noise should seem to come from
        // somewhere nearby, rather than always from inside the listener.
        int dx = RANDOM.nextInt(19) - 9;
        int dy = RANDOM.nextInt(7) - 3;
        int dz = RANDOM.nextInt(19) - 9;
        run(player, "execute positioned ~" + dx + " ~" + dy + " ~" + dz
            + " run playsound " + sound + " ambient @s ~ ~ ~ 1 1");
        if (omen != null && RANDOM.nextInt(4) == 0) {
            run(player, "playsound minecraft:entity.warden.heartbeat ambient @s ~ ~ ~ 0.65 0.75");
        }
    }

    private static final class BasicEffect extends StatusEffect {
        private BasicEffect(StatusEffectCategory category, int color) {
            super(category, color);
        }
    }
}
