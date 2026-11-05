package com.drugs.recipes;

import com.drugs.items.DrugItemMetadata;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;

class FlavorInfusionListenerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 3})
    void lacingConsumesOneOffhandItemWithoutRevealingContents(int count) {
        var event = mock(PlayerInteractEvent.class);
        var player = mock(Player.class);
        var block = mock(Block.class);
        var inventory = mock(PlayerInventory.class);
        var product = mock(ItemStack.class);
        var productMeta = mock(ItemMeta.class);
        var ingredient = mock(ItemStack.class);
        var ingredientMeta = mock(ItemMeta.class);
        var remainder = mock(ItemStack.class);
        var result = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.CAULDRON);
        when(player.isSneaking()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(product);
        when(inventory.getItemInOffHand()).thenReturn(ingredient);
        when(product.hasItemMeta()).thenReturn(true);
        when(product.getItemMeta()).thenReturn(productMeta);
        when(product.getAmount()).thenReturn(1);
        when(ingredient.hasItemMeta()).thenReturn(true);
        when(ingredient.getItemMeta()).thenReturn(ingredientMeta);
        when(ingredient.getAmount()).thenReturn(count);
        when(ingredient.clone()).thenReturn(remainder);
        when(remainder.getAmount()).thenReturn(count - 1);
        try (var metadata = mockStatic(DrugItemMetadata.class);
             var lacing = mockStatic(com.drugs.drug.DrugLacing.class);
             var flavors = mockStatic(FlavorVariants.class)) {
            metadata.when(() -> DrugItemMetadata.getDrugId(productMeta)).thenReturn("joint");
            metadata.when(() -> DrugItemMetadata.getDrugId(ingredientMeta)).thenReturn("meth");
            lacing.when(() -> com.drugs.drug.DrugLacing.lace(product, ingredient)).thenReturn(result);
            var listener = new FlavorInfusionListener();
            listener.infuse(event);
            verify(event).setCancelled(true);
            verify(inventory).setItemInMainHand(result);
            verify(remainder).setAmount(count - 1);
            verify(inventory).setItemInOffHand(count == 1 ? null : remainder);
            verify(player).sendMessage("§aItems combined.");
            flavors.verifyNoInteractions();
            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
            listener.infuse(event);
            lacing.verify(() -> com.drugs.drug.DrugLacing.lace(product, ingredient), times(1));
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 3})
    void infusionConsumesOneIngredientAndTransformsOriginalItem(int count) {
        var event = mock(PlayerInteractEvent.class);
        var player = mock(Player.class);
        var block = mock(Block.class);
        var inventory = mock(PlayerInventory.class);
        var product = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        var ingredient = mock(ItemStack.class);
        var remainder = mock(ItemStack.class);
        var result = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.WATER_CAULDRON);
        when(player.isSneaking()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getItemInMainHand()).thenReturn(product);
        when(inventory.getItemInOffHand()).thenReturn(ingredient);
        when(product.hasItemMeta()).thenReturn(true);
        when(product.getItemMeta()).thenReturn(meta);
        when(product.getAmount()).thenReturn(1);
        when(ingredient.getAmount()).thenReturn(count);
        when(ingredient.clone()).thenReturn(remainder);
        when(remainder.getAmount()).thenReturn(count - 1);
        var flavor = new FlavorVariants.Flavor("berry", "Berry", Material.SWEET_BERRIES, List.of("vape"));
        try (var metadata = mockStatic(DrugItemMetadata.class); var flavors = mockStatic(FlavorVariants.class)) {
            metadata.when(() -> DrugItemMetadata.getDrugId(meta)).thenReturn("vape");
            flavors.when(() -> FlavorVariants.matchingFlavor(product, ingredient)).thenReturn(flavor);
            flavors.when(() -> FlavorVariants.apply(product, flavor)).thenReturn(result);
            var listener = new FlavorInfusionListener();
            listener.infuse(event);
            verify(event).setCancelled(true);
            verify(remainder).setAmount(count - 1);
            verify(inventory).setItemInOffHand(count == 1 ? null : remainder);
            verify(inventory).setItemInMainHand(result);
            // A second interaction from the offhand must not perform another infusion.
            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
            listener.infuse(event);
            flavors.verify(() -> FlavorVariants.apply(product, flavor), times(1));
        }
    }
}
