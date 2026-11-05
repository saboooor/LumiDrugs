package com.drugs.ui;

import com.drugs.growing.DrugGrowing;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GrowingGuideTest {
    @Test void indexLinksToConfiguredCropsAndSpecificGuideUsesLiveGrowthTime() {
        try (var growing = mockStatic(DrugGrowing.class)) {
            growing.when(DrugGrowing::cropIds).thenReturn(Set.of("coca"));
            growing.when(() -> DrugGrowing.crop("coca")).thenReturn(
                    new DrugGrowing.Crop("coca", "Coca Leaves", Material.VINE, Material.VINE, 123));
            var index = GrowingGuide.createDialog(null, null);
            assertEquals("/drugs growing coca", index.actions().getFirst().action().command());
            var crop = GrowingGuide.createDialog("coca", "cocaine");
            String body = crop.getBase().body().getFirst().contents().toPlainText();
            assertTrue(body.contains("wheat seeds + vine"));
            assertTrue(body.contains("123 seconds"));
            assertTrue(body.contains("light at least 9"));
            assertTrue(body.contains("Breaking early returns only the seed"));
            assertEquals("/drugs recipe cocaine", crop.actions().getFirst().action().command());
        }
    }

    @Test void cannabisExplainsShearsBudsAndPlantingStock() {
        String body = GrowingGuide.createDialog("cannabis", "joint").getBase().body().getFirst().contents().toPlainText();
        assertTrue(body.contains("using shears"));
        assertTrue(body.contains("2 Cannabis Buds"));
        assertTrue(body.contains("Without shears"));
        assertTrue(body.contains("10%"));
        assertTrue(body.contains("Maximize purity"));
    }

    @Test void purityDialogAndGrowingConditionEvaluationReflectEnvironmentAndTips() {
        var purityDialog = GrowingGuide.createPurityDialog();
        String purityText = purityDialog.getBase().body().getFirst().contents().toPlainText();
        assertTrue(purityText.contains("hydrated farmland (moisture 7/7"));
        assertTrue(purityText.contains("light level at 14–15"));
        assertTrue(purityText.contains("overhead grow lamp"));
        assertTrue(purityText.contains("Composter"));
        assertTrue(purityText.contains("Cauldron"));

        var block = mock(org.bukkit.block.Block.class);
        var soil = mock(org.bukkit.block.Block.class);
        var farmland = mock(org.bukkit.block.data.type.Farmland.class);
        when(block.getRelative(0, -1, 0)).thenReturn(soil);
        when(soil.getBlockData()).thenReturn(farmland);

        // Dim light (9), low moisture (2), no lamp, no composter -> 75% purity + 4 tips
        when(farmland.getMoisture()).thenReturn(2);
        when(block.getLightLevel()).thenReturn((byte) 9);
        var low = DrugGrowing.evaluateGrowingQuality(block);
        assertEquals(75, low.purity());
        assertEquals(4, low.tips().size());

        // Standard good conditions: moisture 7 (+5%), light 15 (+10%) -> 90% baseline purity
        when(farmland.getMoisture()).thenReturn(7);
        when(block.getLightLevel()).thenReturn((byte) 15);
        var baseline = DrugGrowing.evaluateGrowingQuality(block);
        assertEquals(90, baseline.purity());
        assertEquals(2, baseline.tips().size());

        // Optimal conditions: moisture 7 (+5%), light 15 (+10%), Sea Lantern above (+5%), Composter nearby (+5%) -> 100% purity
        var lamp = mock(org.bukkit.block.Block.class);
        when(lamp.getType()).thenReturn(Material.SEA_LANTERN);
        when(block.getRelative(0, 2, 0)).thenReturn(lamp);
        var composter = mock(org.bukkit.block.Block.class);
        when(composter.getType()).thenReturn(Material.COMPOSTER);
        when(block.getRelative(1, 0, 1)).thenReturn(composter);
        var optimal = DrugGrowing.evaluateGrowingQuality(block);
        assertEquals(100, optimal.purity());
        assertTrue(optimal.hasGrowLamp());
        assertTrue(optimal.hasRichSoil());
        assertFalse(optimal.overcrowded());
        assertTrue(optimal.tips().isEmpty());
    }
    @Test void clickingHarvestOpensDialogWithoutReopeningMenuOnClose() throws Exception {
        var plugin = mock(com.drugs.DrugsV2.class);
        var instance = com.drugs.DrugsV2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        instance.set(null, plugin);
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class);
             var guides = mockStatic(GrowingGuide.class)) {
            var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
            doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                    .when(scheduler).runTask(eq(plugin), any(Runnable.class));
            var listener = new DrugMenuListener();
            var player = mock(org.bukkit.entity.Player.class);
            when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
            var view = mock(org.bukkit.inventory.InventoryView.class);
            when(view.getTitle()).thenReturn("Recipe: joint");
            var inventory = mock(org.bukkit.inventory.Inventory.class);
            when(inventory.getSize()).thenReturn(27);
            var ingredient = mock(org.bukkit.inventory.ItemStack.class);
            when(ingredient.getType()).thenReturn(Material.FERN);
            guides.when(() -> GrowingGuide.cropFromIngredient(ingredient)).thenReturn("cannabis");
            var close = mock(org.bukkit.event.inventory.InventoryCloseEvent.class);
            when(close.getView()).thenReturn(view);
            when(close.getPlayer()).thenReturn(player);
            doAnswer(call -> { listener.onRecipeClose(close); return null; }).when(player).closeInventory();
            var click = mock(org.bukkit.event.inventory.InventoryClickEvent.class);
            when(click.getInventory()).thenReturn(inventory);
            when(click.getView()).thenReturn(view);
            when(click.getWhoClicked()).thenReturn(player);
            when(click.getCurrentItem()).thenReturn(ingredient);
            when(click.getRawSlot()).thenReturn(2);
            listener.onMenuClick(click);
            verify(click).setCancelled(true);
            verify(player).closeInventory();
            guides.verify(() -> GrowingGuide.open(player, "cannabis", "joint"));
            verify(scheduler, never()).runTaskLater(any(org.bukkit.plugin.Plugin.class), any(Runnable.class), anyLong());
        } finally {
            instance.set(null, previous);
        }
    }

}
