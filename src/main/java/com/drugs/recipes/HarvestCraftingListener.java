package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.achievements.AchievementManager;
import com.drugs.drug.DrugRegistry;
import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;

import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Reject vanilla substitutes and recheck at extraction, including shift crafting. */
public final class HarvestCraftingListener implements Listener {
    public static boolean valid(CraftingInventory inventory) {
        return valid(inventory.getRecipe(), inventory.getMatrix());
    }
    static boolean valid(Recipe recipe, ItemStack[] matrix) {
        if (!(recipe instanceof org.bukkit.Keyed keyed)
            || !keyed.getKey().getNamespace().equals(DrugsV2.getInstance().getName().toLowerCase(java.util.Locale.ROOT))) return true;
        String id = keyed.getKey().getKey();
        for (ItemStack item : matrix) {
            if (item != null && item.hasItemMeta() && DrugItemMetadata.getLacedDrug(item.getItemMeta()) != null) return false;
        }
        if (id.equals("vape")) return containsOnlyRequired(matrix, "vape_liquid", false);
        if (id.equals("cart") || id.startsWith("cart_strain_"))
            return containsOnlyRequired(matrix, "cannabis_extract", false);
        if (id.equals("cigarette") || id.equals("cigar"))
            return containsOnlyRequired(matrix, "tobacco", true);
        String crop = DrugGrowing.requirement(keyed.getKey().getKey());
        if (crop == null) return true;
        boolean found = false;
        for (ItemStack item : matrix) {
            if (item == null || item.getType() != DrugGrowing.material(crop)) continue;
            if (!DrugGrowing.isHarvest(item, crop)) return false;
            found = true;
        }
        return found;
    }
    private static boolean containsOnlyRequired(ItemStack[] matrix, String required, boolean drug) {
        boolean found = false;
        org.bukkit.Material material = drug ? org.bukkit.Material.DRIED_KELP : org.bukkit.Material.POTION;
        for (ItemStack item : matrix) {
            if (item == null || item.getType() != material) continue;
            if (!(drug ? ProductionRecipes.drug(item, required) : ProductionRecipes.tagged(item, required))) return false;
            found = true;
        }
        return found;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void autoCraft(org.bukkit.event.block.CrafterCraftEvent event) {
        if (!(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)) return;
        ItemStack[] matrix = crafter.getInventory().getContents();
        if (!valid(event.getRecipe(), matrix)) { event.setCancelled(true); return; }
        if (!event.getRecipe().getKey().getNamespace().equals(DrugsV2.getInstance().getName().toLowerCase(java.util.Locale.ROOT))) return;
        if (event.getRecipe().getKey().getKey().equals("cart")) return;
        String crop = DrugGrowing.requirement(event.getRecipe().getKey().getKey());
        if (!"cannabis".equals(crop)) return;
        String strain = null;
        for (ItemStack item : matrix) {
            if (!DrugGrowing.isHarvest(item, crop)) continue;
            String next = DrugItemMetadata.getStrainId(item);
            if (next == null) next = DrugItemMetadata.DEFAULT_STRAIN_ID;
            if (strain != null && !strain.equalsIgnoreCase(next)) { event.setCancelled(true); return; }
            strain = next;
        }
        ItemStack result = DrugRegistry.getDrugItem(event.getRecipe().getKey().getKey(), event.getResult().getAmount(), strain);
        if (result != null) event.setResult(com.drugs.drug.DrugPurity.propagate(result, matrix));
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if (!valid(event.getInventory())) event.getInventory().setResult(null);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!valid(event.getInventory())) { event.setCancelled(true); event.getInventory().setResult(null); return; }
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        if (!(event.getRecipe() instanceof org.bukkit.Keyed keyed)
                || !keyed.getKey().getNamespace().equals(DrugsV2.getInstance().getName().toLowerCase(java.util.Locale.ROOT))) return;
        String key = keyed.getKey().getKey();
        int strainIdx = key.indexOf("_strain_");
        String drugId = strainIdx > 0 ? key.substring(0, strainIdx) : key;
        if (DrugRegistry.isDrugRegistered(drugId)) {
            java.util.Map<String, Object> context = AchievementManager.createContext();
            context.put("drug_id", drugId);
            AchievementManager.processTrigger(player, "craft_drug", context);
            AchievementManager.processTrigger(player, "craft_specific", context);
        }
    }
}
