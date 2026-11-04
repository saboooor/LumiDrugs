package com.drugs.recipes;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Infuse one held product without registering any client recipe-book entries. */
public final class FlavorInfusionListener implements Listener {
    public static boolean isInfusionGesture(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().isSneaking() || event.getClickedBlock() == null) return false;
        Material type = event.getClickedBlock().getType();
        return type == Material.CAULDRON || type == Material.WATER_CAULDRON;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void infuse(PlayerInteractEvent event) {
        if (!isInfusionGesture(event)) return;
        var player = event.getPlayer();
        var inventory = player.getInventory();
        var product = inventory.getItemInMainHand();
        if (product == null || !product.hasItemMeta()
                || com.drugs.items.DrugItemMetadata.getDrugId(product.getItemMeta()) == null) return;
        event.setCancelled(true);
        if (product.getAmount() != 1) {
            player.sendMessage("§eHold just one product to infuse it.");
            return;
        }
        var ingredient = inventory.getItemInOffHand();
        var flavor = FlavorVariants.matchingFlavor(product, ingredient);
        if (flavor == null) {
            player.sendMessage("§eUse an unflavored product and its matching ingredient in your offhand. See /drugs.");
            return;
        }
        // Prepare the output before mutating either hand; cloning preserves remaining uses.
        var result = FlavorVariants.apply(product, flavor);
        var remaining = ingredient.clone();
        remaining.setAmount(ingredient.getAmount() - 1);
        inventory.setItemInOffHand(remaining.getAmount() == 0 ? null : remaining);
        inventory.setItemInMainHand(result);
        player.sendMessage("§aFlavor infused. Strain and remaining uses preserved.");
    }
}
