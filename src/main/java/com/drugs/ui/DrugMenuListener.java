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
    private final java.util.Set<java.util.UUID> openingRecipeView = new java.util.HashSet<>();

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
        if (!title.startsWith("Drugs Menu") && !title.startsWith("Recipe: ") && !title.startsWith("Flavors: ")) return;

        event.setCancelled(true); // Fully prevent item movement/dragging

        if (clicked == null || clicked.getType() == Material.AIR || event.getRawSlot() < 0) return;
        if (!title.startsWith("Recipe: ") && event.getRawSlot() >= inv.getSize()) return;

        if (title.startsWith("Flavors: ")) {
            String product = title.substring("Flavors: ".length(), title.lastIndexOf(" (Page "));
            int slot = event.getRawSlot();
            if (slot == 27) DrugMenuGUI.openFlavors(player, product, parsePageNumber(title) - 1);
            else if (slot == 35) DrugMenuGUI.openFlavors(player, product, parsePageNumber(title) + 1);
            else if (slot == 31) DrugMenuGUI.openRecipeCategory(player, product);
            else if (slot == 30) DrugRecipeViewer.openRecipe(player, product);
            else if (slot >= 0 && slot < 27) {
                String recipe = com.drugs.recipes.FlavorVariants.recipeFor(clicked);
                if (recipe != null) DrugRecipeViewer.openRecipe(player, recipe);
            }
            return;
        }

        if (title.startsWith("Recipe: ")) {
            // Recipe ingredients and custom items in the player inventory can open recipes.
            int slot = event.getRawSlot();
            if (slot == 25) {
                Bukkit.getScheduler().runTask(DrugsV2.getInstance(), () -> {
                    openingRecipeView.add(player.getUniqueId());
                    player.closeInventory();
                    openingRecipeView.remove(player.getUniqueId());
                    GrowingGuide.openPurity(player);
                });
                return;
            }
            if (slot < inv.getSize() && (slot % 9 < 1 || slot % 9 > 3)) return;
            String ingredientRecipe = DrugRecipeViewer.recipeFor(clicked);
            if (ingredientRecipe != null) {
                Bukkit.getScheduler().runTask(DrugsV2.getInstance(), () -> {
                    openingRecipeView.add(player.getUniqueId());
                    try {
                        DrugRecipeViewer.openRecipe(player, ingredientRecipe);
                    } finally {
                        openingRecipeView.remove(player.getUniqueId());
                    }
                });
                return;
            }
            String crop = GrowingGuide.cropFromIngredient(clicked);
            if (crop != null) {
                String recipe = title.substring("Recipe: ".length());
                Bukkit.getScheduler().runTask(DrugsV2.getInstance(), () -> {
                    openingRecipeView.add(player.getUniqueId());
                    player.closeInventory();
                    openingRecipeView.remove(player.getUniqueId());
                    GrowingGuide.open(player, crop, recipe);
                });
            }
            return;
        }

        DrugCategory category = DrugCategory.fromTitle(title);
        int menuSlot = event.getRawSlot();
        if (category == null) {
            if (menuSlot == 29) {
                var journal = DrugsV2.getInstance().getProductionJournal();
                if (journal != null) Bukkit.getScheduler().runTask(DrugsV2.getInstance(), () -> journal.open(player));
            }
            else if (menuSlot == 31) player.closeInventory();
            else if (menuSlot >= 0 && menuSlot < DrugCategory.values().length)
                DrugMenuGUI.openCategory(player, DrugCategory.values()[menuSlot], 0);
            return;
        }
        if (menuSlot == 31) { DrugMenuGUI.open(player, 0); return; }

        String name = clicked.getItemMeta() != null ? ChatColor.stripColor(clicked.getItemMeta().getDisplayName()) : "";

        switch (name.toLowerCase()) {
            case "next page" -> {
                int currentPage = parsePageNumber(title);
                DrugMenuGUI.openCategory(player, category, currentPage + 1);
            }
            case "previous page" -> {
                int currentPage = parsePageNumber(title);
                DrugMenuGUI.openCategory(player, category, currentPage - 1);
            }
            case "close" -> player.closeInventory();
            default -> {
                String flavoredRecipe = com.drugs.recipes.FlavorVariants.recipeFor(clicked);
                if (flavoredRecipe != null) {
                    DrugRecipeViewer.openRecipe(player, flavoredRecipe);
                    return;
                }
                DrugEffectProfile profile = DrugRegistry.getProfileFromItem(clicked);
                if (profile != null) {
                    if (!com.drugs.recipes.FlavorVariants.hasFlavors(profile.getId()))
                        DrugRecipeViewer.openRecipe(player, profile.getId());
                    else DrugMenuGUI.openFlavors(player, profile.getId(), 0);
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
        if (title.startsWith("Drugs Menu") || title.startsWith("Recipe: ") || title.startsWith("Flavors: ")) event.setCancelled(true);
    }

    /**
     * When closing a recipe GUI, return to the drug menu after a tick.
     */
    @EventHandler
    public void onRecipeClose(InventoryCloseEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title.startsWith("Recipe: ")) {
            Player player = (Player) event.getPlayer();
            if (openingRecipeView.contains(player.getUniqueId())) return;
            Bukkit.getScheduler().runTaskLater(DrugsV2.getInstance(), () -> {
                if (!player.isOnline()) return;
                String recipe = title.substring("Recipe: ".length());
                int flavor = recipe.indexOf("_flavor_");
                if (flavor > 0) DrugMenuGUI.openFlavors(player, recipe.substring(0, flavor), 0);
                else DrugMenuGUI.openRecipeCategory(player, recipe);
            }, 1L); // Delay to prevent recursion
        }
    }

    /**
     * Extracts the page number from the GUI title.
     */
    private int parsePageNumber(String title) {
        try {
            return Integer.parseInt(title.substring(title.lastIndexOf("(Page ") + 6, title.lastIndexOf(')'))) - 1;
        } catch (Exception e) {
            return 0;
        }
    }
}
