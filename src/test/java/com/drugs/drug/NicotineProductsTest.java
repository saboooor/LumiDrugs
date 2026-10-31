package com.drugs.drug;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import com.drugs.DrugsV2;
import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NicotineProductsTest {
    private YamlConfiguration resource(String name) throws Exception {
        var yaml = new YamlConfiguration();
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                getClass().getResourceAsStream("/" + name)), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        return yaml;
    }

    @Test void everyProductHasUsableRecipeAndRiskSettings() throws Exception {
        var config = resource("config.yml");
        var recipes = resource("recipes.yml");
        var addiction = resource("addiction.yml");
        var tolerance = resource("tolerance.yml");
        var overdose = resource("overdose.yml");
        for (String id : List.of("tobacco", "nicotine", "cigarette", "cigar", "chewing_tobacco", "vape")) {
            if (id.equals("vape")) assertNull(DrugGrowing.requirement(id));
            else assertEquals("tobacco", DrugGrowing.requirement(id), id);
            boolean processing = id.equals("tobacco") || id.equals("nicotine");
            boolean production = java.util.Set.of("cigarette", "cigar", "vape").contains(id);
            if (processing) {
                assertEquals(id.equals("tobacco") ? "smoker" : "furnace", recipes.getString(id + ".type"));
                assertEquals("tobacco", recipes.getString(id + ".input-crop"));
                assertTrue(recipes.getInt(id + ".cooking-time") > 0);
            } else if (production) {
                assertEquals("production", recipes.getString(id + ".type"));
            } else {
                assertTrue(recipes.getConfigurationSection(id + ".ingredients").getValues(false)
                        .containsValue("DRIED_KELP"), id);
            }
            assertNotNull(Material.matchMaterial(config.getString(id + ".material")), id);
            assertFalse(config.getConfigurationSection(id + ".effects").getKeys(false).isEmpty(), id);
            var shape = recipes.getStringList(id + ".shape");
            assertEquals(processing || production ? 0 : 3, shape.size(), id);
            for (String row : shape) {
                assertEquals(3, row.length(), id);
                for (char symbol : row.toCharArray()) {
                    if (symbol != ' ') assertNotNull(Material.matchMaterial(
                            recipes.getString(id + ".ingredients." + symbol)), id);
                }
            }
            assertTrue(addiction.getBoolean("drugs." + id + ".addictive"), id);
            assertTrue(tolerance.getInt(id + ".max-tolerance") > 0, id);
            var penalties = overdose.getMapList("drug_specific." + id + ".effects");
            assertFalse(penalties.isEmpty(), id);
            assertTrue(penalties.stream().allMatch(p -> "effects".equals(p.get("type"))), id);
        }
    }

    @Test void noConfiguredDrugCureOrBongUsesUnobtainableMaterialsAndModelDataIsApplied() throws Exception {
        var config = resource("config.yml");
        var addiction = resource("addiction.yml");
        var bong = resource("bong.yml");

        for (String id : config.getKeys(false)) {
            Material material = Material.matchMaterial(config.getString(id + ".material", ""));
            assertNotNull(material, id);
            assertFalse(DrugItemMetadata.isUnobtainableMaterial(material), "Unobtainable material for " + id + ": " + material);
        }
        for (String id : addiction.getConfigurationSection("cures").getKeys(false)) {
            Material material = Material.matchMaterial(addiction.getString("cures." + id + ".material", ""));
            assertNotNull(material, id);
            assertFalse(DrugItemMetadata.isUnobtainableMaterial(material), "Unobtainable material for cure " + id + ": " + material);
        }
        Material bongMaterial = Material.matchMaterial(bong.getString("item.material", ""));
        assertNotNull(bongMaterial);
        assertFalse(DrugItemMetadata.isUnobtainableMaterial(bongMaterial));
        assertEquals(DrugItemMetadata.customModelData("bong"), bong.getInt("item.custom-model-data"));

        assertEquals(Material.PAPER, DrugItemMetadata.sanitizeMaterial("joint", Material.LIGHT, Material.SUGAR));
        assertEquals(Material.BLAZE_ROD, DrugItemMetadata.sanitizeMaterial("cart", Material.COMMAND_BLOCK_MINECART, Material.SUGAR));
        assertEquals(Material.GLASS_BOTTLE, DrugItemMetadata.sanitizeMaterial("bong", Material.STRUCTURE_VOID, Material.GLASS_BOTTLE));

        DrugsV2 plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);

        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        DrugItemMetadata.setItemType(meta, "cart");
        verify(meta).setCustomModelData(DrugItemMetadata.customModelData("cart"));
        verify(meta).setItemModel(new NamespacedKey("lumidrugs", "cart"));

        File rpTextures = new File("resourcepack/assets/minecraft/textures");
        File parentRpTextures = new File("../resourcepack/assets/minecraft/textures");
        assertFalse(rpTextures.exists() || parentRpTextures.exists(),
                "Resource pack must not replace vanilla textures under assets/minecraft/textures");
    }
}
