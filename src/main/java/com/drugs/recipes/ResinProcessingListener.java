package com.drugs.recipes;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Process one vanilla clump per main-hand cauldron interaction. */
public final class ResinProcessingListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void process(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        Material station = event.getClickedBlock().getType();
        if (station != Material.CAULDRON && station != Material.WATER_CAULDRON) return;
        var player = event.getPlayer();
        var inventory = player.getInventory();
        var input = inventory.getItemInMainHand();
        if (!ProductionRecipes.acceptsResin(input)) return;
        event.setCancelled(true);
        var result = ProductionRecipes.intermediate("processed_resin", null);
        if (input.getAmount() == 1) {
            inventory.setItemInMainHand(result);
        } else {
            var remaining = input.clone();
            remaining.setAmount(input.getAmount() - 1);
            inventory.setItemInMainHand(remaining);
            for (var overflow : inventory.addItem(result).values())
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
        player.sendMessage("§aMade Processed Resin. Brew it with water bottles to make Glue.");
    }
}
