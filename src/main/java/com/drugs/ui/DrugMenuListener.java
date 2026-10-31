package com.drugs.ui;

import com.drugs.DrugsV2;
import com.drugs.addiction.CureProfile;
import com.drugs.addiction.CureRegistry;
import com.drugs.bong.BongItemFactory;
import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import com.drugs.farm.FarmItems;

/**
 * Handles clicks and closes for the Drugs GUI system.
 * Prevents interaction with both drug menu and recipe viewer.
 */
public class DrugMenuListener implements Listener {
    private final java.util.Set<java.util.UUID> openingGrowingGuide = new java.util.HashSet<>();

    /**
     * Detect clicks inside the Drugs Menu or Recipe GUI.
     */
    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        ItemStack clicked = event.getCurrentItem();
        Player player = (Player) event.getWhoClicked();

        if (inv == null) return;

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.startsWith("Drugs Menu") && !title.startsWith("Recipe: ")) return;

        event.setCancelled(true); // Fully prevent item movement/dragging

        if (clicked == null || clicked.getType() == Material.AIR || event.getRawSlot() >= inv.getSize()) return;

        if (title.startsWith("Recipe: ")) {
            // Only the three ingredient columns are interactive; the result is not an ingredient.
            int slot = event.getRawSlot();
            if (slot < 0 || slot % 9 < 1 || slot % 9 > 3) return;
            String ingredientRecipe = com.drugs.recipes.ProductionRecipes.ingredientRecipe(clicked);
            if (ingredientRecipe != null) {
                Bukkit.getScheduler().runTask(DrugsV2.getInstance(), () -> {
                    openingGrowingGuide.add(player.getUniqueId());
                    try {
                        DrugRecipeViewer.openRecipe(player, ingredientRecipe);
                    } finally {
                        openingGrowingGuide.remove(player.getUniqueId());
                    }
                });
                return;
            }
            String crop = GrowingGuide.cropFromIngredient(clicked);
            if (crop != null) {
                String recipe = title.substring("Recipe: ".length());
                Bukkit.getScheduler().runTask(DrugsV2.getInstance(), () -> {
                    openingGrowingGuide.add(player.getUniqueId());
                    player.closeInventory();
                    openingGrowingGuide.remove(player.getUniqueId());
                    GrowingGuide.open(player, crop, recipe);
                });
            }
            return;
        }

        String name = clicked.getItemMeta() != null ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName()) : "";

        switch (name.toLowerCase()) {
            case "next page" -> {
                int currentPage = parsePageNumber(title);
                DrugMenuGUI.open(player, currentPage + 1);
            }
            case "previous page" -> {
                int currentPage = parsePageNumber(title);
                DrugMenuGUI.open(player, currentPage - 1);
            }
            case "close" -> player.closeInventory();
            default -> {
                DrugEffectProfile profile = DrugRegistry.getProfileFromItem(clicked);
                if (profile != null) {
                    DrugRecipeViewer.openRecipe(player, profile.getId());
                    return;
                }
                CureProfile cureProfile = CureRegistry.getProfileFromItem(clicked);
                if (cureProfile != null) {
                    DrugRecipeViewer.openRecipe(player, cureProfile.getId());
                    return;
                }

                String ingredientRecipe = com.drugs.recipes.ProductionRecipes.ingredientRecipe(clicked);
                if (ingredientRecipe != null) {
                    DrugRecipeViewer.openRecipe(player, ingredientRecipe);
                    return;
                }

                if (BongItemFactory.isBongItem(clicked)) {
                    DrugRecipeViewer.openRecipe(player, "bong");
                    return;
                }

                if (FarmItems.isControllerItem(clicked)) {
                    DrugRecipeViewer.openRecipe(player, "farm-controller");
                }
            }
        }
    }

    @EventHandler
    public void onMenuDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.startsWith("Drugs Menu") || title.startsWith("Recipe: ")) event.setCancelled(true);
    }

    /**
     * When closing a recipe GUI, return to the drug menu after a tick.
     */
    @EventHandler
    public void onRecipeClose(InventoryCloseEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.startsWith("Recipe: ")) {
            Player player = (Player) event.getPlayer();
            if (openingGrowingGuide.contains(player.getUniqueId())) return;
            Bukkit.getScheduler().runTaskLater(DrugsV2.getInstance(), () -> {
                DrugMenuGUI.open(player, 0);
            }, 1L); // Delay to prevent recursion
        }
    }

    /**
     * Extracts the page number from the GUI title.
     */
    private int parsePageNumber(String title) {
        try {
            return Integer.parseInt(title.replaceAll("\\D+", "")) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
