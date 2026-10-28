package com.drugs.growing;

import com.drugs.items.DrugItemMetadata;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugGrowingAutomationTest {
    @Test
    void onlyMatureTrackedIngredientCropsAreHarvested() throws Exception {
        var growing = mock(DrugGrowing.class, CALLS_REAL_METHODS);
        var world = mock(World.class);
        var block = mock(Block.class);
        UUID id = UUID.randomUUID();
        when(world.getUID()).thenReturn(id);
        when(block.getWorld()).thenReturn(world);
        when(block.getType()).thenReturn(Material.WHEAT);
        var field = DrugGrowing.class.getDeclaredField("plants");
        field.setAccessible(true);
        var plants = new HashMap<String, Object>();
        field.set(growing, plants);
        doNothing().when(growing).save();
        assertTrue(growing.harvestMature(block).isEmpty()); // Vanilla wheat.
        var farmerChange = mock(org.bukkit.event.entity.EntityChangeBlockEvent.class);
        when(farmerChange.getEntity()).thenReturn(mock(org.bukkit.entity.Villager.class));
        when(farmerChange.getBlock()).thenReturn(block);
        growing.preventVanillaFarmerHarvest(farmerChange);
        verify(farmerChange, never()).setCancelled(true); // Ordinary wheat is unaffected.
        var cropField = DrugGrowing.class.getDeclaredField("crops");
        cropField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var crops = (Map<String, DrugGrowing.Crop>) cropField.get(null);
        var previous = new HashMap<>(crops);
        var appearanceField = DrugGrowing.class.getDeclaredField("plantBlocks");
        appearanceField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var appearances = (Map<String, Material>) appearanceField.get(null);
        var previousAppearances = new HashMap<>(appearances);
        var inspect = mock(org.bukkit.event.player.PlayerInteractEvent.class);
        var player = mock(org.bukkit.entity.Player.class, RETURNS_DEEP_STUBS);
        when(inspect.getPlayer()).thenReturn(player);
        when(inspect.getClickedBlock()).thenReturn(block);
        when(inspect.getHand()).thenReturn(org.bukkit.inventory.EquipmentSlot.HAND);
        when(inspect.getAction()).thenReturn(org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK);
        when(player.getInventory().getItemInMainHand().isEmpty()).thenReturn(true);
        var plantType = Class.forName("com.drugs.growing.DrugGrowing$Plant");
        var constructor = plantType.getDeclaredConstructor(String.class, int.class);
        constructor.setAccessible(true);
        try (var items = mockConstruction(ItemStack.class, (item, context) -> {
            when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
            when(item.asQuantity(anyInt())).thenReturn(item);
        }); var metadata = mockStatic(DrugItemMetadata.class)) {
            for (String crop : new String[]{"poppy", "mushroom", "ephedra", "coca", "sassafras", "ergot", "salvia", "resin", "tobacco"}) {
                crops.put(crop, new DrugGrowing.Crop(crop, crop, Material.POPPY, Material.POPPY, 10));
                plants.put(id + ":0:0:0", constructor.newInstance(crop, 9));
                assertTrue(growing.harvestMature(block).isEmpty());
                plants.put(id + ":0:0:0", constructor.newInstance(crop, 10));
                growing.preventVanillaFarmerHarvest(farmerChange);
                assertTrue(growing.isMature(block)); // Legacy wheat is still recognized.
                appearances.put(crop, Material.POPPY);
                when(block.getType()).thenReturn(Material.POPPY);
                assertTrue(growing.isMature(block)); // Native plant model is recognized too.
                growing.inspect(inspect);
                verify(player).sendActionBar(contains(crop + " "));
                clearInvocations(player);
                assertEquals(2, growing.harvestMature(block).size());
                assertTrue(plants.isEmpty());
                var seed = mock(ItemStack.class);
                var meta = mock(ItemMeta.class);
                when(seed.hasItemMeta()).thenReturn(true);
                when(seed.getItemMeta()).thenReturn(meta);
                metadata.when(() -> DrugItemMetadata.getItemType(meta)).thenReturn("seed_" + crop);
                var soil = mock(Block.class);
                var farmland = mock(org.bukkit.block.data.type.Farmland.class);
                when(block.getRelative(0, -1, 0)).thenReturn(soil);
                when(soil.getBlockData()).thenReturn(farmland);
                when(block.getType()).thenReturn(Material.AIR);
                when(block.isEmpty()).thenReturn(true);
                when(farmland.getMoisture()).thenReturn(0);
                assertFalse(growing.plantSeed(block, seed));
                when(farmland.getMoisture()).thenReturn(7);
                assertTrue(growing.plantSeed(block, seed));
                assertEquals(1, plants.size());
                plants.clear();
                when(block.getType()).thenReturn(Material.WHEAT);
            }
        } finally {
            appearances.clear();
            appearances.putAll(previousAppearances);
            crops.clear();
            crops.putAll(previous);
        }
        verify(block, times(9)).setType(Material.AIR, false);
        verify(farmerChange, times(9)).setCancelled(true);
    }
}
