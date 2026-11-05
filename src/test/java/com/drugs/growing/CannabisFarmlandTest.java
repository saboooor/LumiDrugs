package com.drugs.growing;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CannabisFarmlandTest {
    @Test void cannabisRequiresHydratedFarmland() {
        Block plant = mock(Block.class), soil = mock(Block.class);
        when(plant.getRelative(0, -1, 0)).thenReturn(soil);
        when(soil.getBlockData()).thenReturn(mock(BlockData.class));
        assertFalse(CannabisPlantListener.hasHydratedFarmland(plant));
        var farmland = mock(Farmland.class);
        when(soil.getBlockData()).thenReturn(farmland);
        assertFalse(CannabisPlantListener.hasHydratedFarmland(plant));
        when(farmland.getMoisture()).thenReturn(7);
        assertTrue(CannabisPlantListener.hasHydratedFarmland(plant));
    }

    @Test void registrationRejectsCannabisOnOtherSoil() {
        Block plant = mock(Block.class), soil = mock(Block.class);
        ItemStack seed = mock(ItemStack.class);
        when(seed.getType()).thenReturn(Material.FERN);
        when(plant.getRelative(0, -1, 0)).thenReturn(soil);
        when(soil.getBlockData()).thenReturn(mock(BlockData.class));
        try (var registry = mockStatic(CannabisPlantRegistry.class)) {
            assertFalse(CannabisPlantListener.applyPlacedCannabis(plant, seed));
            registry.verifyNoInteractions();
        }
    }
    @Test void onlyTaggedCannabisPlantsRegisterEvenOnHydratedFarmland() {
        Block plant = mock(Block.class), soil = mock(Block.class);
        when(plant.getRelative(0, -1, 0)).thenReturn(soil);
        var farmland = mock(Farmland.class);
        when(soil.getBlockData()).thenReturn(farmland);
        when(farmland.getMoisture()).thenReturn(7);
        ItemStack seed = mock(ItemStack.class);
        when(seed.getType()).thenReturn(Material.FERN);
        try (var registry = mockStatic(CannabisPlantRegistry.class);
             var metadata = mockStatic(com.drugs.items.DrugItemMetadata.class)) {
            assertFalse(CannabisPlantListener.applyPlacedCannabis(plant, seed));
            var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
            when(seed.hasItemMeta()).thenReturn(true);
            when(seed.getItemMeta()).thenReturn(meta);
            assertFalse(CannabisPlantListener.applyPlacedCannabis(plant, seed));
            metadata.when(() -> com.drugs.items.DrugItemMetadata.getItemType(meta)).thenReturn("seed_salvia");
            assertFalse(CannabisPlantListener.applyPlacedCannabis(plant, seed));
            registry.verifyNoInteractions();
            metadata.when(() -> com.drugs.items.DrugItemMetadata.getItemType(meta)).thenReturn("cannabis_plant");
            assertTrue(CannabisPlantListener.applyPlacedCannabis(plant, seed));
            registry.verify(() -> CannabisPlantRegistry.setPlant(null, com.drugs.items.DrugItemMetadata.DEFAULT_STRAIN_ID));
        }
    }

}
