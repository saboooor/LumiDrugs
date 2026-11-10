package com.drugs.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Upgrade the old bundled treatment presets without replacing unrelated family settings. */
final class WithdrawalTreatmentMigration {
    private WithdrawalTreatmentMigration() {}

    static void apply(Plugin plugin) {
        for (String family : List.of("opioids", "cannabis", "nicotine")) {
            File file = new File(plugin.getDataFolder(), "drugs/" + family + ".yml");
            if (!file.exists()) continue; // Preserve deliberately deleted families.
            try (InputStream stream = plugin.getResource("drugs/" + family + ".yml")) {
                if (stream == null) continue;
                var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
                var current = new YamlConfiguration();
                current.load(file);
                String before = current.saveToString();
                if (family.equals("opioids")) {
                    if (current.isConfigurationSection("cures.suboxone")) {
                        var targets = current.getStringList("cures.suboxone.cures").stream()
                                .filter(id -> id.equals("heroin") || id.equals("fent")).toList();
                        current.set("cures.suboxone.cures", targets);
                        if (current.getBoolean("cures.suboxone.clears_points"))
                            copy(defaults, current, "cures.suboxone");
                    }
                    if (current.isConfigurationSection("cures.sleep")
                            && !current.getBoolean("cures.sleep.enabled")
                            && current.getStringList("cures.sleep.cures").isEmpty())
                        copy(defaults, current, "cures.sleep");
                }
                if (family.equals("cannabis") && current.isConfigurationSection("cures.cbd")
                        && current.getStringList("cures.cbd.cures").isEmpty())
                    copy(defaults, current, "cures.cbd");
                if (family.equals("nicotine")) {
                    for (String id : List.of("nicotine_patch", "varenicline")) {
                        current.set("cures." + id, null);
                        current.set("recipes." + id, null);
                    }
                }
                if (!before.equals(current.saveToString())) {
                    var backup = file.toPath().resolveSibling(file.getName() + ".before-treatments.bak");
                    if (!Files.exists(backup)) Files.copy(file.toPath(), backup);
                    current.save(file);
                }
            } catch (IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                throw new IllegalArgumentException("Cannot update withdrawal treatments in " + file, exception);
            }
        }
    }

    private static void copy(YamlConfiguration source, YamlConfiguration target, String path) {
        target.set(path, null);
        var section = source.getConfigurationSection(path);
        if (section != null) section.getValues(true).forEach((key, value) -> {
            if (!(value instanceof org.bukkit.configuration.ConfigurationSection)) target.set(path + "." + key, value);
        });
    }
}
