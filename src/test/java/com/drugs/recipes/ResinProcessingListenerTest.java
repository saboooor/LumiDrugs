package com.drugs.recipes;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import static org.mockito.Mockito.*;

class ResinProcessingListenerTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void processesOneClumpAndIgnoresOffhandAndOtherStations(int count) {
        var event = mock(PlayerInteractEvent.class);
        var player = mock(Player.class, RETURNS_DEEP_STUBS);
        var block = mock(Block.class);
        var input = mock(ItemStack.class);
        var remaining = mock(ItemStack.class);
        var output = mock(ItemStack.class);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(event.getPlayer()).thenReturn(player);
        when(block.getType()).thenReturn(Material.WATER_CAULDRON);
        var inventory = player.getInventory();
        when(inventory.getItemInMainHand()).thenReturn(input);
        when(input.getAmount()).thenReturn(count);
        when(input.clone()).thenReturn(remaining);
        when(inventory.addItem(output)).thenReturn(new HashMap<>());
        try (var production = mockStatic(ProductionRecipes.class)) {
            production.when(() -> ProductionRecipes.acceptsResin(input)).thenReturn(true);
            production.when(() -> ProductionRecipes.intermediate("processed_resin", null)).thenReturn(output);
            var listener = new ResinProcessingListener();
            listener.process(event);
            verify(event).setCancelled(true);
            if (count == 1) {
                verify(inventory).setItemInMainHand(output);
                verify(inventory, never()).addItem(any(ItemStack.class));
            } else {
                verify(remaining).setAmount(count - 1);
                verify(inventory).setItemInMainHand(remaining);
                verify(inventory).addItem(output);
            }
            clearInvocations(inventory, event);
            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
            listener.process(event);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(block.getType()).thenReturn(Material.LAVA_CAULDRON);
            listener.process(event);
            verifyNoInteractions(inventory);
            verify(event, never()).setCancelled(true);
        }
    }
}
