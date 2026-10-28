package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.growing.DrugGrowing;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HarvestCraftingListenerTest {
    private CraftingInventory inventory;
    @BeforeEach void setup() throws Exception {
        DrugsV2 plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("DrugsV2");
        when(plugin.namespace()).thenReturn("drugsv2");
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
        inventory = mock(CraftingInventory.class);
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        var key = new NamespacedKey(plugin, "joint");
        when(recipe.getKey()).thenReturn(key);
        when(inventory.getRecipe()).thenReturn(recipe);
    }
    private ItemStack ingredient(String type, Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.hasItemMeta()).thenReturn(type != null);
        if (type != null) {
            ItemMeta meta = mock(ItemMeta.class);
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(item.getItemMeta()).thenReturn(meta);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            when(data.get(new NamespacedKey(DrugsV2.getInstance(), "item_type"), PersistentDataType.STRING)).thenReturn(type);
        }
        return item;
    }
    @Test void tobaccoRecipesRequireHarvestedLeaves() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        var tobaccoKey = new NamespacedKey(DrugsV2.getInstance(), "cigarette");
        when(recipe.getKey()).thenReturn(tobaccoKey);
        when(inventory.getRecipe()).thenReturn(recipe);
        try (var growing = mockStatic(DrugGrowing.class)) {
            growing.when(() -> DrugGrowing.requirement("cigarette")).thenReturn("tobacco");
            growing.when(() -> DrugGrowing.isHarvest(any(ItemStack.class), eq("tobacco"))).thenCallRealMethod();
            growing.when(() -> DrugGrowing.material("tobacco")).thenReturn(Material.DRIED_KELP);
            for (String type : new String[]{null, "seed_tobacco", "tobacco", "harvest_coca"}) {
                doReturn(new ItemStack[]{ingredient(type, Material.DRIED_KELP)}).when(inventory).getMatrix();
                assertFalse(HarvestCraftingListener.valid(inventory));
            }
            doReturn(new ItemStack[]{ingredient("harvest_tobacco", Material.DRIED_KELP),
                    ingredient(null, Material.PAPER)}).when(inventory).getMatrix();
            assertTrue(HarvestCraftingListener.valid(inventory));
        }
    }
    @Test void ordinaryFernsCannotCraftDrugs() {
        doReturn(new ItemStack[]{ingredient(null, Material.FERN)}).when(inventory).getMatrix();
        assertFalse(HarvestCraftingListener.valid(inventory));
    }
    @Test void PlantingStockCannotSubstituteForHarvest() {
        doReturn(new ItemStack[]{ingredient("cannabis_plant", Material.FERN)}).when(inventory).getMatrix();
        assertFalse(HarvestCraftingListener.valid(inventory));
    }
    @Test void matureHarvestCanCraft() {
        doReturn(new ItemStack[]{ingredient("harvest_cannabis", Material.FERN), ingredient(null, Material.PAPER)}).when(inventory).getMatrix();
        assertTrue(HarvestCraftingListener.valid(inventory));
    }
    @Test void partialSubstitutionIsRejected() {
        doReturn(new ItemStack[]{ingredient("harvest_cannabis", Material.FERN), ingredient(null, Material.FERN)}).when(inventory).getMatrix();
        assertFalse(HarvestCraftingListener.valid(inventory));
    }
    @Test void differentCropCannotSubstitute() {
        doReturn(new ItemStack[]{ingredient("harvest_salvia", Material.FERN)}).when(inventory).getMatrix();
        assertFalse(HarvestCraftingListener.valid(inventory));
    }
    @Test void missingHarvestFailsClosed() {
        doReturn(new ItemStack[]{ingredient(null, Material.PAPER)}).when(inventory).getMatrix();
        assertFalse(HarvestCraftingListener.valid(inventory));
    }
    @Test void invalidExtractionIsCancelled() {
        doReturn(new ItemStack[]{ingredient(null, Material.FERN)}).when(inventory).getMatrix();
        var event = mock(org.bukkit.event.inventory.CraftItemEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        new HarvestCraftingListener().craft(event);
        verify(event).setCancelled(true);
        verify(inventory).setResult(null);
    }
    @Test void unrelatedRecipesStayAvailable() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        when(recipe.getKey()).thenReturn(NamespacedKey.minecraft("bread"));
        when(inventory.getRecipe()).thenReturn(recipe);
        assertTrue(HarvestCraftingListener.valid(inventory));
    }
    @Test void automaticCrafterCannotUseOrdinaryFerns() {
        var block = mock(org.bukkit.block.Block.class);
        var crafter = mock(org.bukkit.block.Crafter.class);
        var contents = mock(CrafterInventory.class);
        when(block.getState()).thenReturn(crafter);
        when(crafter.getInventory()).thenReturn(contents);
        doReturn(new ItemStack[]{ingredient(null, Material.FERN)}).when(contents).getContents();
        var event = mock(org.bukkit.event.block.CrafterCraftEvent.class);
        when(event.getBlock()).thenReturn(block);
        var recipe = (ShapedRecipe) inventory.getRecipe();
        when(event.getRecipe()).thenReturn(recipe);
        new HarvestCraftingListener().autoCraft(event);
        verify(event).setCancelled(true);
    }
    @Test void everyConfiguredDrugHasGrowingRequirement() {
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/config.yml"));
        for (String drug : config.getKeys(false)) assertNotNull(DrugGrowing.requirement(drug), drug);
    }
}
