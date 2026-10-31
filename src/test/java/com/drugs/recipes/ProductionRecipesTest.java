package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.growing.DrugGrowing;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.*;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionRecipesTest {
    private DrugsV2 plugin;
    @BeforeEach void setup() throws Exception {
        plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
    }
    private ItemStack item(Material material, String type, String strain, boolean potion) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = potion ? mock(PotionMeta.class) : mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(material);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new NamespacedKey(plugin, "item_type"), PersistentDataType.STRING)).thenReturn(type);
        when(data.get(new NamespacedKey(plugin, "strain_id"), PersistentDataType.STRING)).thenReturn(strain);
        return item;
    }
    @Test void brewingOnlyAcceptsTheMatchingHarvestAndStrain() {
        try (var growing = mockStatic(DrugGrowing.class)) {
            growing.when(() -> DrugGrowing.material("cannabis")).thenReturn(Material.FERN);
            growing.when(() -> DrugGrowing.isHarvest(any(), eq("cannabis"))).thenCallRealMethod();
            ItemStack buds = item(Material.FERN, "harvest_cannabis", "blue", false);
            assertTrue(ProductionRecipes.acceptsBuds(buds, "blue"));
            assertFalse(ProductionRecipes.acceptsBuds(buds, "red"));
            assertFalse(ProductionRecipes.acceptsBuds(item(Material.FERN, "cannabis_plant", "blue", false), "blue"));
            assertFalse(ProductionRecipes.acceptsBuds(item(Material.FERN, null, "blue", false), "blue"));
            assertTrue(ProductionRecipes.acceptsBuds(item(Material.FERN, "harvest_cannabis", null, false), "reggie"));
        }
    }
    @Test void brewedLiquidsCannotBeReusedAsPlainWaterToDuplicateProducts() {
        ItemStack water = item(Material.POTION, null, null, true);
        when(((PotionMeta) water.getItemMeta()).getBasePotionType()).thenReturn(PotionType.WATER);
        assertTrue(ProductionRecipes.plainPotion(water, PotionType.WATER));
        assertFalse(ProductionRecipes.plainPotion(water, PotionType.AWKWARD));
        for (String type : new String[]{"vape_liquid", "cannabis_extract"}) {
            ItemStack custom = item(Material.POTION, type, null, true);
            when(((PotionMeta) custom.getItemMeta()).getBasePotionType()).thenReturn(PotionType.WATER);
            assertFalse(ProductionRecipes.plainPotion(custom, PotionType.WATER));
        }
    }
    @Test void nicotinePredicateRejectsVanillaSugarAndHarvestedLeaves() {
        ItemStack nicotine = item(Material.SUGAR, "drug", null, false);
        var key = new NamespacedKey(plugin, "drug_id");
        when(nicotine.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING)).thenReturn("nicotine");
        assertTrue(ProductionRecipes.drug(nicotine, "nicotine"));
        assertFalse(ProductionRecipes.drug(item(Material.SUGAR, null, null, false), "nicotine"));
        assertFalse(ProductionRecipes.drug(item(Material.DRIED_KELP, "harvest_tobacco", null, false), "nicotine"));
    }
}
