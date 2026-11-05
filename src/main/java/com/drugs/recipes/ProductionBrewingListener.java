package com.drugs.recipes;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;

/** Resolve custom outputs even when the server selects an overlapping vanilla recipe. */
public final class ProductionBrewingListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        var inventory = event.getContents();
        var results = event.getResults();
        for (int slot = 0; slot < Math.min(3, results.size()); slot++) {
            // BrewEvent still exposes the original bottles and ingredient here.
            var result = ProductionRecipes.brewingResult(inventory.getItem(slot), inventory.getIngredient());
            if (result != null) results.set(slot, result);
        }
    }
}
