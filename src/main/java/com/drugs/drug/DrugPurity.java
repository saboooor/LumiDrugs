package com.drugs.drug;

import com.drugs.DrugsV2;
import com.drugs.config.MechanicsConfig;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/** A predictable quality grade: legacy items and ordinary production are neutral at 90%. */
public final class DrugPurity {
    public static final int BASELINE = 90;
    private DrugPurity() {}
    private static NamespacedKey key(String name) { return new NamespacedKey(DrugsV2.getInstance(), name); }

    public static int get(ItemStack item) {
        return item == null || !item.hasItemMeta() ? BASELINE : get(item.getItemMeta());
    }
    public static int get(ItemMeta meta) {
        if (meta == null) return BASELINE;
        Integer value = meta.getPersistentDataContainer().get(key("purity"), PersistentDataType.INTEGER);
        return value == null ? BASELINE : Math.clamp(value, 1, 100);
    }
    public static void set(ItemMeta meta, int purity) {
        if (meta == null || !MechanicsConfig.isPurityEnabled()) return;
        purity = Math.clamp(purity, 1, 100);
        meta.getPersistentDataContainer().set(key("purity"), PersistentDataType.INTEGER, purity);
        var lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNullElse(meta.getLore(), List.of()) : List.<String>of());
        lore.removeIf(line -> Objects.requireNonNullElse(ChatColor.stripColor(line), "").toLowerCase(Locale.ROOT).startsWith("purity:"));
        lore.add("§7Purity: §f" + purity + "%");
        meta.setLore(lore);
    }
    public static double multiplier(ItemStack item) {
        return MechanicsConfig.isPurityEnabled() ? get(item) / (double) BASELINE : 1;
    }
    public static boolean ingredient(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        String type = DrugItemMetadata.getItemType(meta);
        return DrugItemMetadata.getDrugId(meta) != null || type != null &&
                (type.startsWith("harvest_") || Set.of("infused_dough", "cannabis_extract", "vape_liquid").contains(type));
    }
    public static int combined(ItemStack... inputs) {
        int sum = 0, count = 0;
        for (ItemStack input : inputs) if (ingredient(input)) { sum += get(input); count++; }
        return count == 0 ? BASELINE : (int) Math.round(sum / (double) count);
    }
    public static ItemStack propagate(ItemStack result, ItemStack... inputs) {
        if (!MechanicsConfig.isPurityEnabled() || !ingredient(result)) return result;
        var copy = result.clone();
        var meta = copy.getItemMeta();
        set(meta, combined(inputs));
        copy.setItemMeta(meta);
        return copy;
    }
    public static ItemStack refine(ItemStack product) {
        if (!MechanicsConfig.isPurityEnabled() || product == null || product.getAmount() != 1 || !product.hasItemMeta()) return null;
        String drug = DrugItemMetadata.getDrugId(product.getItemMeta());
        if (drug == null || !DrugRegistry.isDrugRegistered(drug) || get(product) >= 100) return null;
        var copy = product.clone();
        var meta = copy.getItemMeta();
        set(meta, Math.min(100, get(product) + MechanicsConfig.getPurityRefiningPoints()));
        copy.setItemMeta(meta);
        return copy;
    }
    public static void storeLacing(ItemMeta target, ItemStack donor) {
        target.getPersistentDataContainer().set(key("laced_purity"), PersistentDataType.INTEGER, get(donor));
    }
    public static void restoreLacing(ItemMeta source, ItemStack dose) {
        if (dose == null || !dose.hasItemMeta()) return;
        Integer value = source.getPersistentDataContainer().get(key("laced_purity"), PersistentDataType.INTEGER);
        if (value == null) return;
        var meta = dose.getItemMeta();
        set(meta, value);
        dose.setItemMeta(meta);
    }
}
