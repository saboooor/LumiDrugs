package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugPurity;
import org.bukkit.Keyed;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.inventory.ItemStack;

/** Carry grade through each production step without rolling new quality in previews. */
public final class PurityProductionListener implements Listener {
    private static boolean ours(org.bukkit.inventory.Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(DrugsV2.getInstance().namespace());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if (ours(event.getRecipe())) apply(event.getInventory());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (ours(event.getRecipe())) apply(event.getInventory());
    }
    private void apply(org.bukkit.inventory.CraftingInventory inventory) {
        ItemStack result = inventory.getResult();
        if (result != null) inventory.setResult(DrugPurity.propagate(result, inventory.getMatrix()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void autoCraft(org.bukkit.event.block.CrafterCraftEvent event) {
        if (!ours(event.getRecipe()) || !(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)) return;
        event.setResult(DrugPurity.propagate(event.getResult(), crafter.getInventory().getContents()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cook(BlockCookEvent event) {
        if (!DrugPurity.ingredient(event.getResult())) return;
        var source = event.getSource();
        if (source != null && source.hasItemMeta()
                && com.drugs.items.DrugItemMetadata.getLacedDrug(source.getItemMeta()) != null) {
            event.setCancelled(true);
            return;
        }
        event.setResult(DrugPurity.propagate(event.getResult(), source));
    }
}
