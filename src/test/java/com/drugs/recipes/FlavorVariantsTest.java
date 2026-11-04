package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.strains.StrainConfigLoader;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlavorVariantsTest {
    @TempDir Path folder;
    @BeforeEach void setup() throws Exception {
        var plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
        FlavorVariants.clear();
    }
    @AfterEach void reset() throws Exception {
        FlavorVariants.clear();
        Files.deleteIfExists(folder.resolve("mechanics.yml"));
        FlavorVariants.load(folder.toFile());
    }

    @Test void flavorDecoratesCloneWithoutReplacingDrugStrainOrDurability() {
        ItemStack base = mock(ItemStack.class), copy = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(base.clone()).thenReturn(copy);
        when(copy.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new NamespacedKey(DrugsV2.getInstance(), "drug_id"), PersistentDataType.STRING)).thenReturn("vape");
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Nicotine Vape");
        when(meta.hasLore()).thenReturn(true);
        when(meta.getLore()).thenReturn(List.of("Durability: 32/32"));
        var flavor = new FlavorVariants.Flavor("berry", "&dBerry", Material.SWEET_BERRIES, List.of("vape"));
        assertSame(copy, FlavorVariants.apply(base, flavor));
        verify(base, never()).setItemMeta(any());
        verify(meta).setLore(List.of("Durability: 32/32", "§7Flavor: §dBerry"));
        verify(data).set(new NamespacedKey(DrugsV2.getInstance(), "flavor"), PersistentDataType.STRING, "berry");
        verify(data).get(new NamespacedKey(DrugsV2.getInstance(), "drug_id"), PersistentDataType.STRING);
        verifyNoMoreInteractions(data);
        verify(meta).setItemModel(new NamespacedKey("lumidrugs", "vape_flavor_berry"));
        verify(meta).setCustomModelData(com.drugs.items.DrugItemMetadata.customModelData("vape_flavor_berry"));
        verify(copy).setItemMeta(meta);
    }

    @Test void infusionMatchesVanillaIngredientsAndRejectsFlavoredProducts() {
        FlavorVariants.load(folder.toFile());
        ItemStack product = mock(ItemStack.class), ingredient = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(product.hasItemMeta()).thenReturn(true);
        when(product.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        var key = new NamespacedKey(DrugsV2.getInstance(), "drug_id");
        when(data.get(key, PersistentDataType.STRING)).thenReturn("vape");
        when(ingredient.getType()).thenReturn(Material.APPLE);
        try (var registry = mockStatic(DrugRegistry.class)) {
            registry.when(() -> DrugRegistry.isDrugRegistered("vape")).thenReturn(true);
            assertEquals("apple", FlavorVariants.matchingFlavor(product, ingredient).id());
            when(ingredient.hasItemMeta()).thenReturn(true);
            assertNull(FlavorVariants.matchingFlavor(product, ingredient));
            when(ingredient.hasItemMeta()).thenReturn(false);
            when(data.has(new NamespacedKey(DrugsV2.getInstance(), "flavor"), PersistentDataType.STRING)).thenReturn(true);
            assertNull(FlavorVariants.matchingFlavor(product, ingredient));
        }
    }

    @Test void coffeeInfusionRequiresHarvestedBeansRatherThanVanillaCocoa() {
        FlavorVariants.load(folder.toFile());
        ItemStack product = mock(ItemStack.class), beans = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        when(product.hasItemMeta()).thenReturn(true);
        when(product.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new NamespacedKey(DrugsV2.getInstance(), "drug_id"), PersistentDataType.STRING)).thenReturn("vape");
        when(beans.getType()).thenReturn(Material.COCOA_BEANS);
        when(beans.hasItemMeta()).thenReturn(true);
        try (var registry = mockStatic(DrugRegistry.class); var growing = mockStatic(com.drugs.growing.DrugGrowing.class)) {
            registry.when(() -> DrugRegistry.isDrugRegistered("vape")).thenReturn(true);
            assertNull(FlavorVariants.matchingFlavor(product, beans));
            growing.when(() -> com.drugs.growing.DrugGrowing.isHarvest(beans, "coffee")).thenReturn(true);
            assertEquals("coffee", FlavorVariants.matchingFlavor(product, beans).id());
        }
    }

    @Test void strainListenerPreservesFlavoredOutput() {
        ItemStack output = mock(ItemStack.class);
        var inventory = mock(org.bukkit.inventory.CraftingInventory.class);
        var event = mock(org.bukkit.event.inventory.PrepareItemCraftEvent.class);
        when(event.getInventory()).thenReturn(inventory);
        when(inventory.getResult()).thenReturn(output);
        try (var flavors = mockStatic(FlavorVariants.class); var registry = mockStatic(DrugRegistry.class)) {
            flavors.when(() -> FlavorVariants.recipeFor(output)).thenReturn("edible_flavor_berry");
            new com.drugs.strains.StrainCraftingListener().onPrepare(event);
            verify(inventory, never()).setResult(any());
            registry.verifyNoInteractions();
        }
    }

    @Test void bundledConfigIncludesEveryDefaultAndCoffeeRequiresHarvest() throws Exception {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try (var reader = new java.io.InputStreamReader(
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/mechanics.yml")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            yaml.load(reader);
        }
        assertEquals(13, FlavorVariants.defaults().size());
        for (var flavor : FlavorVariants.defaults()) {
            String path = "flavors." + flavor.id();
            assertEquals(flavor.name(), yaml.getString(path + ".name"));
            assertEquals(flavor.ingredient().name(), yaml.getString(path + ".ingredient"));
            assertEquals(flavor.products(), yaml.getStringList(path + ".products"));
            assertEquals(flavor.ingredientCrop(), yaml.getString(path + ".ingredient-crop"));
        }
    }

    @Test void explicitlyEmptyFlavorConfigDisablesRecipes() throws Exception {
        Files.writeString(folder.resolve("mechanics.yml"), "flavors: {}\n");
        FlavorVariants.load(folder.toFile());
        try (var registry = mockStatic(DrugRegistry.class); var strains = mockStatic(StrainConfigLoader.class)) {
            FlavorVariants.initialize();
            registry.verifyNoInteractions();
            assertTrue(FlavorVariants.menuItems().isEmpty());
        }
    }
}
