package dev.shmingledorph.effects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ShmingledorphEffects implements ModInitializer {
    public static final String MOD_ID = "shmingledorph";
    public static final Map<String, RegistryEntry<StatusEffect>> EFFECTS = new LinkedHashMap<>();

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

        // Only an actual death triggers the curse; totems and prevented lethal
        // damage do not fire this callback. The existing SMP life system
        // handles transferring players to Death's Domain.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            if (!player.hasStatusEffect(EFFECTS.get("cursed"))) return;
            var server = player.getServer();
            if (server == null) return;
            String name = player.getGameProfile().getName();
            server.getCommandManager().executeWithPrefix(
                server.getCommandSource().withLevel(4),
                "scoreboard players set " + name + " life 0"
            );
        });
    }

    private static final class BasicEffect extends StatusEffect {
        private BasicEffect(StatusEffectCategory category, int color) {
            super(category, color);
        }
    }
}
