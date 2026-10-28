package com.drugs.drug;

import org.bukkit.Material;
import com.drugs.growing.DrugGrowing;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

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
            assertEquals("tobacco", DrugGrowing.requirement(id), id);
            assertTrue(recipes.getConfigurationSection(id + ".ingredients").getValues(false)
                    .containsValue("DRIED_KELP"), id);
            assertNotNull(Material.matchMaterial(config.getString(id + ".material")), id);
            assertFalse(config.getConfigurationSection(id + ".effects").getKeys(false).isEmpty(), id);
            var shape = recipes.getStringList(id + ".shape");
            assertEquals(3, shape.size(), id);
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
}
