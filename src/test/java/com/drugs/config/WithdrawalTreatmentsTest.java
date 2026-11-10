package com.drugs.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalTreatmentsTest {
    @TempDir Path folder;
    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test void bundledTreatmentTargetsOnlyOpioidsAndNicotineHasNoNewItems() {
        var config = DrugFamilyConfigs.view(RESOURCES.toFile(), "addiction");
        assertEquals(Set.of("heroin", "fent"), new HashSet<>(config.getStringList("cures.suboxone.cures")));
        var recipes = DrugFamilyConfigs.view(RESOURCES.toFile(), "recipes");
        for (String id : List.of("suboxone")) {
            String path = "cures." + id;
            assertTrue(config.getBoolean(path + ".enabled"));
            assertFalse(config.getBoolean(path + ".clears_points"));
            assertEquals(1, config.getDouble(path + ".reduce_points"));
            assertEquals(300, config.getInt(path + ".block_withdrawal_seconds"));
            assertTrue(recipes.isConfigurationSection(id));

        }
        for (String id : List.of("nicotine_patch", "varenicline")) {
            assertFalse(config.contains("cures." + id));
            assertFalse(recipes.contains(id));
        }
        assertEquals(Set.of("blunt", "joint", "edible", "cart"), new HashSet<>(config.getStringList("cures.cbd.cures")));
        assertEquals(2, config.getInt("cures.cbd.reduce_points"));
        assertEquals(30, config.getInt("cures.cbd.block_withdrawal_seconds"));
        assertTrue(config.getBoolean("cures.sleep.enabled"));
        assertTrue(config.getBoolean("cures.sleep.clears_points"));

    }

    @Test void upgradePreservesUnrelatedSettingsBacksUpOldFilesAndIsIdempotent() throws Exception {
        Files.createDirectories(folder.resolve("drugs"));
        Files.writeString(folder.resolve("drugs/opioids.yml"), """
                drugs:
                  heroin:
                    custom: keep
                cures:
                  suboxone:
                    cures: [heroin, fent, glue]
                    clears_points: true
                  sleep:
                    enabled: false
                    clears_points: false
                    cures: []
                """);
        Files.writeString(folder.resolve("drugs/cannabis.yml"), "cures:\n  cbd:\n    cures: []\n    reduce_points: 0\n");
        Files.writeString(folder.resolve("drugs/nicotine.yml"), "crops:\n  tobacco:\n    growth-seconds: 123\ncures:\n  nicotine_patch:\n    enabled: true\n  varenicline:\n    enabled: true\nrecipes:\n  nicotine_patch:\n    shape: []\n  varenicline:\n    shape: []\n");
        var plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call -> Files.newInputStream(RESOURCES.resolve((String) call.getArgument(0))));
        WithdrawalTreatmentMigration.apply(plugin);
        var config = DrugFamilyConfigs.view(folder.toFile(), "addiction");
        assertEquals(List.of("heroin", "fent"), config.getStringList("cures.suboxone.cures"));
        assertFalse(config.getBoolean("cures.suboxone.clears_points"));
        assertTrue(config.getBoolean("cures.sleep.enabled"));
        assertEquals(2, config.getInt("cures.cbd.reduce_points"));
        assertEquals(30, config.getInt("cures.cbd.block_withdrawal_seconds"));
        assertFalse(config.contains("cures.nicotine_patch"));
        assertFalse(config.contains("cures.varenicline"));
        assertEquals(123, DrugFamilyConfigs.view(folder.toFile(), "growing").getInt("tobacco.growth-seconds"));
        var opioids = YamlConfiguration.loadConfiguration(folder.resolve("drugs/opioids.yml").toFile());
        assertEquals("keep", opioids.getString("drugs.heroin.custom"));
        Path backup = folder.resolve("drugs/opioids.yml.before-treatments.bak");
        assertTrue(Files.readString(backup).contains("glue"));
        String once = Files.readString(folder.resolve("drugs/opioids.yml"));
        WithdrawalTreatmentMigration.apply(plugin);
        assertEquals(once, Files.readString(folder.resolve("drugs/opioids.yml")));
        Files.delete(folder.resolve("drugs/nicotine.yml"));
        WithdrawalTreatmentMigration.apply(plugin);
        assertFalse(Files.exists(folder.resolve("drugs/nicotine.yml")));
    }
}
