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
        return event.getHand() == EquipmentSlot.HAND && isInfusionInteraction(event);
    }

    public static boolean isInfusionInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
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
            player.sendMessage("§eHold just one product in your main hand.");
            return;
        }
        var ingredient = inventory.getItemInOffHand();
        if (com.drugs.config.MechanicsConfig.isPurityEnabled() && ingredient != null
                && ingredient.getType() == com.drugs.config.MechanicsConfig.getPurityRefiningIngredient()
                && !ingredient.hasItemMeta()) {
            var result = com.drugs.drug.DrugPurity.refine(product);
            if (result == null) {
                player.sendMessage("§eThis product cannot be refined further.");
                return;
            }
            var remaining = ingredient.clone();
            remaining.setAmount(ingredient.getAmount() - 1);
            inventory.setItemInOffHand(remaining.getAmount() == 0 ? null : remaining);
            inventory.setItemInMainHand(result);
            int purity = com.drugs.drug.DrugPurity.get(result);
            player.sendMessage("§aPurity: " + purity + "%.");
            com.drugs.achievements.AchievementManager.processTrigger(player, "refine_purity");
            if (purity >= 100) {
                com.drugs.achievements.AchievementManager.processTrigger(player, "max_purity");
            }
            return;
        }
        if (ingredient != null && ingredient.hasItemMeta()
                && com.drugs.items.DrugItemMetadata.getDrugId(ingredient.getItemMeta()) != null) {
            var result = com.drugs.drug.DrugLacing.lace(product, ingredient);
            if (result == null) {
                player.sendMessage("§eThese items cannot be combined.");
                return;
            }
            var remaining = ingredient.clone();
            remaining.setAmount(ingredient.getAmount() - 1);
            inventory.setItemInOffHand(remaining.getAmount() == 0 ? null : remaining);
            inventory.setItemInMainHand(result);
            player.sendMessage("§aItems combined.");
            com.drugs.achievements.AchievementManager.processTrigger(player, "lace_drug");
            return;
        }
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
        com.drugs.achievements.AchievementManager.processTrigger(player, "infuse_flavor");
    }
}
