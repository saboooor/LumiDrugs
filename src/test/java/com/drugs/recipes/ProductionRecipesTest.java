package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.growing.DrugGrowing;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.*;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.List;
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
    @Test void glueBrewsFromProcessedResinAndWaterOnly() {
        assertTrue(ProductionRecipes.manages("glue"));
        ItemStack resin = item(Material.RESIN_CLUMP, null, null, false);
        assertTrue(ProductionRecipes.acceptsResin(resin));
        assertFalse(ProductionRecipes.acceptsResin(item(Material.BONE_MEAL, "harvest_resin", null, false)));
        assertFalse(ProductionRecipes.acceptsResin(item(Material.RESIN_CLUMP, "harvest_resin", null, false)));
        assertFalse(ProductionRecipes.acceptsResin(null));
        ItemStack processed = item(Material.SUGAR, "processed_resin", null, false);
        assertTrue(ProductionRecipes.acceptsProcessedResin(processed));
        assertFalse(ProductionRecipes.acceptsProcessedResin(resin));
        assertFalse(ProductionRecipes.acceptsProcessedResin(item(Material.SUGAR, null, null, false)));
        assertFalse(ProductionRecipes.acceptsProcessedResin(null));
        ItemStack water = item(Material.POTION, null, null, true);
        when(((PotionMeta) water.getItemMeta()).getBasePotionType()).thenReturn(PotionType.WATER);
        ItemStack awkward = item(Material.POTION, null, null, true);
        when(((PotionMeta) awkward.getItemMeta()).getBasePotionType()).thenReturn(PotionType.AWKWARD);
        ItemStack glue = mock(ItemStack.class);
        when(glue.clone()).thenReturn(glue);
        try (var brewing = mockStatic(BrewingRecipes.class);
             var updates = mockStatic(RecipeUpdates.class);
             var flavors = mockStatic(FlavorVariants.class)) {
            try {
                ProductionRecipes.brew("glue", water, processed, glue, ProductionRecipes::acceptsProcessedResin);
                assertSame(glue, ProductionRecipes.brewingResult(water, processed));
                assertNull(ProductionRecipes.brewingResult(water, resin));
                assertNull(ProductionRecipes.brewingResult(water, item(Material.SUGAR, null, null, false)));
                assertNull(ProductionRecipes.brewingResult(awkward, processed));
                assertNull(ProductionRecipes.brewingResult(water,
                        item(Material.BONE_MEAL, "harvest_resin", null, false)));
                assertSame(processed, ProductionRecipes.brewingPreview("glue").ingredient());
            } finally { ProductionRecipes.clear(); }
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

    @Test void completedBrewOverridesMundaneOnlyForWaterAndRealNicotine() {
        ItemStack water = item(Material.POTION, null, null, true);
        when(((PotionMeta) water.getItemMeta()).getBasePotionType()).thenReturn(PotionType.WATER);
        ItemStack awkward = item(Material.POTION, null, null, true);
        when(((PotionMeta) awkward.getItemMeta()).getBasePotionType()).thenReturn(PotionType.AWKWARD);
        ItemStack liquid = item(Material.POTION, "vape_liquid", null, true);
        when(((PotionMeta) liquid.getItemMeta()).getBasePotionType()).thenReturn(PotionType.WATER);
        ItemStack nicotine = item(Material.SUGAR, "drug", null, false);
        when(nicotine.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "drug_id"), PersistentDataType.STRING)).thenReturn("nicotine");
        ItemStack template = mock(ItemStack.class);
        ItemStack stored = mock(ItemStack.class);
        when(template.clone()).thenReturn(stored);
        when(stored.clone()).thenAnswer(ignored -> mock(ItemStack.class));
        try (var brewing = mockStatic(BrewingRecipes.class);
             var updates = mockStatic(RecipeUpdates.class);
             var flavors = mockStatic(FlavorVariants.class)) {
            ProductionRecipes.brew("vape_liquid", water, nicotine, template,
                    ingredient -> ProductionRecipes.drug(ingredient, "nicotine"));
            var inventory = mock(BrewerInventory.class);
            when(inventory.getIngredient()).thenReturn(nicotine);
            when(inventory.getItem(0)).thenReturn(water);
            when(inventory.getItem(1)).thenReturn(water);
            when(inventory.getItem(2)).thenReturn(awkward);
            var mundane = mock(ItemStack.class);
            var unchanged = mock(ItemStack.class);
            var results = new ArrayList<>(List.of(mundane, mundane, unchanged));
            var event = mock(BrewEvent.class);
            when(event.getContents()).thenReturn(inventory);
            when(event.getResults()).thenReturn(results);
            new ProductionBrewingListener().onBrew(event);
            assertNotSame(mundane, results.get(0));
            assertNotSame(template, results.get(0));
            assertNotSame(stored, results.get(0));
            assertNotSame(results.get(0), results.get(1));
            assertSame(unchanged, results.get(2));
            verify(inventory, never()).setIngredient(any());
            assertNull(ProductionRecipes.brewingResult(water, item(Material.SUGAR, null, null, false)));
            assertNull(ProductionRecipes.brewingResult(liquid, nicotine));
            assertNull(ProductionRecipes.brewingResult(null, nicotine));
            assertNull(ProductionRecipes.brewingResult(water, null));
            ProductionRecipes.clear();
            assertNull(ProductionRecipes.brewingResult(water, nicotine));
        } finally {
            try (var brewing = mockStatic(BrewingRecipes.class);
                 var updates = mockStatic(RecipeUpdates.class);
                 var flavors = mockStatic(FlavorVariants.class)) {
                ProductionRecipes.clear();
            }
        }
    }

    @Test void nicotineCannotFallBackToVanillaMundanePotion() {
        ItemStack water = item(Material.POTION, null, null, true);
        when(((PotionMeta) water.getItemMeta()).getBasePotionType()).thenReturn(PotionType.WATER);
        ItemStack nicotine = item(Material.SUGAR, "drug", null, false);
        when(nicotine.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "drug_id"), PersistentDataType.STRING)).thenReturn("nicotine");
        var inventory = mock(BrewerInventory.class);
        when(inventory.getIngredient()).thenReturn(nicotine);
        when(inventory.getItem(0)).thenReturn(water);
        var event = mock(BrewEvent.class);
        when(event.getContents()).thenReturn(inventory);
        when(event.getResults()).thenReturn(new ArrayList<>(List.of(mock(ItemStack.class))));

        new ProductionBrewingListener().onBrew(event);

        verify(event).setCancelled(true);
    }
}
