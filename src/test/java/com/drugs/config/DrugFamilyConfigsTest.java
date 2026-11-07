package com.drugs.config;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugFamilyConfigsTest {
    @TempDir Path folder;
    private static final File BUNDLED = new File("src/main/resources");

    @Test void everyBundledDrugHasRecipeAndExplicitOverdoseEffects() {
        var items = DrugFamilyConfigs.view(BUNDLED, "items");
        var recipes = DrugFamilyConfigs.view(BUNDLED, "recipes");
        var overdose = DrugFamilyConfigs.view(BUNDLED, "overdose");
        assertEquals(21, items.getKeys(false).size());
        assertFalse(items.contains("bong"));
        for (String id : items.getKeys(false)) {
            assertTrue(recipes.isConfigurationSection(id), id);
            assertFalse(overdose.getMapList("drug_specific." + id + ".effects").isEmpty(), id);
        }
        assertTrue(recipes.isConfigurationSection("bong"));
        assertTrue(recipes.isConfigurationSection("farm-controller"));
        assertTrue(DrugFamilyConfigs.view(BUNDLED, "mechanics").contains("bong.item.material"));
    }

    @Test void loadsFamilyCropsCuresStrainsDurabilityAndPrices() {
        assertEquals(600, DrugFamilyConfigs.view(BUNDLED, "growing").getInt("cannabis.growth-seconds"));
        assertTrue(DrugFamilyConfigs.view(BUNDLED, "strains").isConfigurationSection("strains.reggie"));
        var addiction = DrugFamilyConfigs.view(BUNDLED, "addiction");
        assertTrue(addiction.isConfigurationSection("cures.cbd"));
        assertTrue(addiction.getBoolean("drugs.fent.addictive"));
        assertEquals(16, DrugFamilyConfigs.view(BUNDLED, "mechanics").getInt("cart.durability-uses"));
        assertEquals(2, DrugFamilyConfigs.view(BUNDLED, "tolerance").getInt("fent.max-tolerance"));
        assertEquals(12, DrugFamilyConfigs.view(BUNDLED, "cartel").getConfigurationSection("prices").getKeys(false).size());
    }

    @Test void reloadReadsEditsAndRemovedDrugsWithoutRetainingStaleDefinitions() throws Exception {
        Files.createDirectories(folder.resolve("drugs"));
        Path family = folder.resolve("drugs/custom.yml");
        Files.writeString(family, "drugs:\n  custom:\n    item:\n      material: SUGAR\n    price: 15\n");
        assertEquals(Set.of("custom"), DrugFamilyConfigs.view(folder.toFile(), "items").getKeys(false));
        Files.writeString(folder.resolve("prices.yml"), "prices:\n  custom: 15\nstrain-prices:\n  custom:\n    test: 20\n");
        assertEquals(15, DrugFamilyConfigs.view(folder.toFile(), "cartel").getInt("prices.custom"));
        assertEquals(20, DrugFamilyConfigs.view(folder.toFile(), "cartel").getInt("strain-prices.custom.test"));
        Files.writeString(family, "drugs:\n  other:\n    item:\n      material: PAPER\n");
        assertEquals(Set.of("other"), DrugFamilyConfigs.view(folder.toFile(), "items").getKeys(false));
        Files.writeString(folder.resolve("prices.yml"), "prices: {}\n");
        assertFalse(DrugFamilyConfigs.view(folder.toFile(), "cartel").contains("prices.custom"));
    }

    @Test void rejectsDuplicateIdsAndMalformedYamlWithFileContext() throws Exception {
        Files.createDirectories(folder.resolve("drugs"));
        Files.writeString(folder.resolve("drugs/first.yml"), "drugs:\n  custom:\n    item:\n      material: SUGAR\n");
        Path second = folder.resolve("drugs/second.yml");
        Files.writeString(second, Files.readString(folder.resolve("drugs/first.yml")));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> DrugFamilyConfigs.view(folder.toFile(), "items")).getMessage().contains("second.yml"));
        Files.writeString(second, "drugs: [\n");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> DrugFamilyConfigs.view(folder.toFile(), "items")).getMessage().contains("second.yml"));
    }

    @Test void installsDefaultsOnceAndPreservesEditsAndIntentionalDeletions() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        doAnswer(call -> {
            String name = call.getArgument(0);
            Path target = folder.resolve(name);
            Files.createDirectories(target.getParent());
            Files.copy(BUNDLED.toPath().resolve(name), target);
            return null;
        }).when(plugin).saveResource(anyString(), eq(false));
        DrugFamilyConfigs.saveDefaults(plugin);
        assertTrue(Files.exists(folder.resolve("drugs/cannabis.yml")));
        Files.writeString(folder.resolve("drugs/cannabis.yml"), "drugs: {}\n");
        Files.delete(folder.resolve("drugs/opioids.yml"));
        DrugFamilyConfigs.saveDefaults(plugin);
        verify(plugin, times(7)).saveResource(anyString(), eq(false));
        assertEquals("drugs: {}\n", Files.readString(folder.resolve("drugs/cannabis.yml")));
        assertFalse(Files.exists(folder.resolve("drugs/opioids.yml")));
    }
}
