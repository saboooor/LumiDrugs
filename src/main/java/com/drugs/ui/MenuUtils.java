package com.drugs.ui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

/**
 * Utility methods for creating GUI items like buttons.
 */
public class MenuUtils {

    /** Visual-only category button: reuse pack models without tagging it as a consumable. */
    public static ItemStack createCategoryButton(DrugCategory category) {
        ItemStack item = createButtonItem("§a" + category.title(), category.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setItemModel(new org.bukkit.NamespacedKey("lumidrugs", category.model()));
        meta.setCustomModelData(com.drugs.items.DrugItemMetadata.customModelData(category.model()));
        meta.setLore(java.util.List.of("§7Browse " + category.title() + ".", "§eClick to open this category."));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a simple item with a name and no lore.
     *
     * @param name     Display name with color codes
     * @param material Item type
     * @return A configured ItemStack
     */
    public static ItemStack createButtonItem(String name, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(Collections.singletonList(" "));
        item.setItemMeta(meta);

        return item;
    }
}
