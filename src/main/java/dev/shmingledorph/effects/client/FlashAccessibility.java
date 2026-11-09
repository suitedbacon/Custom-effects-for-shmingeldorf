package dev.shmingledorph.effects.client;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Client accessibility preference, created before effects are rendered. */
public final class FlashAccessibility {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
        .resolve("shmingledorph-effects.json");
    private static boolean amber = false;

    private FlashAccessibility() {}

    public static void load() {
        try {
            if (Files.notExists(FILE)) {
                Files.writeString(FILE,
                    "{\n  \"flashColor\": \"white\",\n  \"comment\": \"Set flashColor to amber for a dark yellow, non-pulsing Flash overlay\"\n}\n",
                    StandardCharsets.UTF_8);
            }
            String raw = Files.readString(FILE).toLowerCase(java.util.Locale.ROOT);
            amber = raw.matches("(?s).*\\\"flashcolor\\\"\\s*:\\s*\\\"(amber|dark_yellow)\\\".*");
        } catch (IOException ignored) { amber = false; }
    }

    public static boolean amber() { return amber; }

    public static void toggle() {
        amber = !amber;
        try {
            Files.writeString(FILE,
                "{\\n  \\"flashColor\\": \\"" + (amber ? "amber" : "white") + "\\"\\n}\\n",
                StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
