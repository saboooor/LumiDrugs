package com.drugs.ui;

import com.drugs.addiction.CureProfile;
import com.drugs.addiction.CureRegistry;
import com.drugs.bong.BongConfigLoader;
import com.drugs.bong.BongItemFactory;
import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import com.drugs.farm.FarmItems;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates a category index and paginated product menus.
 */
public class DrugMenuGUI {

    private static final int ITEMS_PER_PAGE = 27; // 3 rows (excluding control row)
    private static final int GUI_SIZE = 36;       // 3x9 grid + 1 row for controls

    /**
     * Opens the drug selection menu for a player.
     *
     * @param player The player to open the menu for
     * @param page   Retained for command compatibility; the category index has one page
     */
    public static void open(Player player, int page) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_GREEN + "Drugs Menu");
        for (DrugCategory category : DrugCategory.values()) {
            gui.setItem(category.ordinal(), MenuUtils.createButtonItem("§a" + category.title(), category.icon()));
        }
        gui.setItem(31, MenuUtils.createButtonItem("§cClose", org.bukkit.Material.BARRIER));
        player.openInventory(gui);
    }

    public static void openCategory(Player player, DrugCategory category, int page) {
        List<ItemStack> menuItems = new ArrayList<>();
        List<String> drugIds = new ArrayList<>(DrugRegistry.getRegisteredDrugNames());
        List<String> cureIds = new ArrayList<>(CureRegistry.getRegisteredCureNames());
        drugIds.sort(String::compareToIgnoreCase);
        cureIds.sort(String::compareToIgnoreCase);

        for (String drugId : drugIds) {
            DrugEffectProfile profile = DrugRegistry.getProfileById(drugId);
            if (profile != null && DrugCategory.forItem(drugId) == category) {
                ItemStack item = profile.createItem(1);
                if (com.drugs.recipes.FlavorVariants.hasFlavors(drugId)) {
                    var meta = item.getItemMeta();
                    var lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.<String>of());
                    lore.add("§eClick to browse flavors and the base recipe.");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                menuItems.add(item);
            }
        }

        for (String cureId : cureIds) {
            CureProfile profile = CureRegistry.getProfileById(cureId);
            if (profile != null && profile.isEnabled() && profile.isItemEnabled()
                    && (cureId.equals("cbd") ? DrugCategory.CANNABIS : DrugCategory.CURES) == category) {
                menuItems.add(profile.createItem(1));
            }
        }

        for (String intermediateId : com.drugs.recipes.ProductionRecipes.intermediates()) {
            if (DrugCategory.forItem(intermediateId) == category)
                menuItems.add(com.drugs.recipes.ProductionRecipes.intermediate(intermediateId, null));
        }

        if (category == DrugCategory.CANNABIS && BongConfigLoader.isEnabled()) {
            menuItems.add(BongItemFactory.createBongItem(1));
        }
        if (category == DrugCategory.EQUIPMENT) menuItems.add(FarmItems.createControllerItem());

        int maxPage = Math.max(1, (int) Math.ceil(menuItems.size() / (double) ITEMS_PER_PAGE));

        // Clamp page number
        if (page < 0) page = 0;
        if (page >= maxPage) page = maxPage - 1;

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_GREEN + "Drugs Menu: " + category.title() + " (Page " + (page + 1) + ")");

        // Add drug items for current page
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, menuItems.size());

        for (int i = start; i < end; i++) {
            gui.setItem(i - start, menuItems.get(i));
        }

        // Add navigation controls (last row)
        if (page > 0) {
            gui.setItem(27, MenuUtils.createButtonItem("§ePrevious Page", org.bukkit.Material.ARROW));
        }
        if (page < maxPage - 1) {
            gui.setItem(35, MenuUtils.createButtonItem("§eNext Page", org.bukkit.Material.ARROW));
        }

        // Return to the category index or close the menu
        gui.setItem(31, MenuUtils.createButtonItem("§eBack to Categories", org.bukkit.Material.ARROW));
        gui.setItem(33, MenuUtils.createButtonItem("§cClose", org.bukkit.Material.BARRIER));

        player.openInventory(gui);
    }
    public static void openFlavors(Player player, String product, int page) {
        List<ItemStack> items = com.drugs.recipes.FlavorVariants.menuItems(product);
        if (items.isEmpty()) { DrugRecipeViewer.openRecipe(player, product); return; }
        int maxPage = Math.max(1, (items.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, maxPage - 1));
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, "§2Flavors: " + product + " (Page " + (page + 1) + ")");
        int start = page * ITEMS_PER_PAGE;
        for (int i = start; i < Math.min(start + ITEMS_PER_PAGE, items.size()); i++)
            gui.setItem(i - start, items.get(i));
        if (page > 0) gui.setItem(27, MenuUtils.createButtonItem("§ePrevious Page", org.bukkit.Material.ARROW));
        if (page + 1 < maxPage) gui.setItem(35, MenuUtils.createButtonItem("§eNext Page", org.bukkit.Material.ARROW));
        ItemStack base = DrugRegistry.getDrugItem(product, 1);
        if (base != null) {
            var meta = base.getItemMeta();
            var lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.<String>of());
            lore.add("§eClick to view the unflavored recipe.");
            meta.setLore(lore);
            base.setItemMeta(meta);
            gui.setItem(30, base);
        }
        gui.setItem(31, MenuUtils.createButtonItem("§eBack to " + DrugCategory.forItem(product).title(), org.bukkit.Material.ARROW));
        player.openInventory(gui);
    }

    public static void openRecipeCategory(Player player, String recipe) {
        DrugCategory category = DrugCategory.forItem(recipe);
        if (category == DrugCategory.OTHER && CureRegistry.getProfileById(recipe) != null) category = DrugCategory.CURES;
        openCategory(player, category, 0);
    }

}
