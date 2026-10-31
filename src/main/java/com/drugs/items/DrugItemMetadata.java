package com.drugs.items;

import com.drugs.DrugsV2;
import com.drugs.strains.StrainProfile;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for writing/reading persistent metadata on drug items.
 */
public final class DrugItemMetadata {

    public static final String DEFAULT_STRAIN_ID = "reggie";

    private static final List<String> RESOURCE_PACK_TYPES = List.of(
            "heroin", "fent", "shrooms", "meth", "blunt",
            "cocaine", "joint", "cart", "edible", "glue",
            "molly", "lsd", "salvia", "tobacco", "nicotine",
            "cigarette", "cigar", "chewing_tobacco", "vape", "caffeine",
            "energy_drink", "suboxone", "cbd", "bong", "infused_dough",
            "cannabis_extract", "vape_liquid", "cannabis_plant", "seed_cannabis", "seed_poppy",
            "seed_mushroom", "seed_ephedra", "seed_coca", "seed_sassafras", "seed_ergot",
            "seed_salvia", "seed_resin", "seed_tobacco", "seed_coffee", "harvest_cannabis",
            "harvest_poppy", "harvest_mushroom", "harvest_ephedra", "harvest_coca", "harvest_sassafras",
            "harvest_ergot", "harvest_salvia", "harvest_resin", "harvest_tobacco", "harvest_coffee");

    private static final Set<Material> UNOBTAINABLE_MATERIALS = Set.of(
            Material.COMMAND_BLOCK_MINECART,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.LIGHT,
            Material.STRUCTURE_VOID,
            Material.STRUCTURE_BLOCK,
            Material.JIGSAW,
            Material.BARRIER,
            Material.DEBUG_STICK,
            Material.BEDROCK,
            Material.KNOWLEDGE_BOOK,
            Material.SPAWNER,
            Material.TRIAL_SPAWNER,
            Material.VAULT,
            Material.END_PORTAL_FRAME,
            Material.REINFORCED_DEEPSLATE,
            Material.PETRIFIED_OAK_SLAB);

    private DrugItemMetadata() {
    }

    private static NamespacedKey key(String key) {
        return new NamespacedKey(DrugsV2.getInstance(), key);
    }

    public static List<String> resourcePackTypes() {
        return RESOURCE_PACK_TYPES;
    }

    public static int customModelData(String itemType) {
        if (itemType == null) return 0;
        int index = RESOURCE_PACK_TYPES.indexOf(itemType.toLowerCase(Locale.ROOT));
        return index >= 0 ? 1001 + index : 0;
    }

    public static boolean isUnobtainableMaterial(Material material) {
        return material == null
                || material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || UNOBTAINABLE_MATERIALS.contains(material);
    }

    public static Material sanitizeMaterial(String id, Material configured, Material fallback) {
        if (!isUnobtainableMaterial(configured)) {
            return configured;
        }
        if (id != null) {
            return switch (id.toLowerCase(Locale.ROOT)) {
                case "joint" -> Material.PAPER;
                case "cart" -> Material.BLAZE_ROD;
                case "bong" -> Material.GLASS_BOTTLE;
                default -> fallback;
            };
        }
        return fallback;
    }

    public static void setDrugId(ItemMeta meta, String drugId) {
        meta.getPersistentDataContainer().set(key("drug_id"), PersistentDataType.STRING, drugId.toLowerCase());
    }

    public static void setItemType(ItemMeta meta, String itemType) {
        String type = itemType.toLowerCase(Locale.ROOT);
        meta.getPersistentDataContainer().set(key("item_type"), PersistentDataType.STRING, type);
        // Use separate models and custom model data for items that share a vanilla material.
        int cmd = customModelData(type);
        if (cmd > 0) {
            if (!meta.hasCustomModelData()) {
                meta.setCustomModelData(cmd);
            }
            meta.setItemModel(new NamespacedKey("lumidrugs", type));
        }
    }

    public static void setStrainId(ItemMeta meta, String strainId) {
        meta.getPersistentDataContainer().set(key("strain_id"), PersistentDataType.STRING, strainId.toLowerCase());
    }

    public static String getDrugId(ItemMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key("drug_id"), PersistentDataType.STRING);
    }

    public static String getItemType(ItemMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key("item_type"), PersistentDataType.STRING);
    }

    public static String getStrainId(ItemMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key("strain_id"), PersistentDataType.STRING);
    }

    public static String getStrainId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return getStrainId(item.getItemMeta());
    }

    public static boolean hasDrugId(ItemMeta meta) {
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(key("drug_id"), PersistentDataType.STRING);
    }

    public static void applyStrainLore(ItemMeta meta, StrainProfile strain) {
        if (meta == null) return;

        String strainName = strain != null ? strain.getDisplayName() : DEFAULT_STRAIN_ID;
        List<String> lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.getLore()) : Collections.emptyList());

        lore.removeIf(line -> {
            String stripped = ChatColor.stripColor(line);
            if (stripped == null) return false;
            String lowered = stripped.toLowerCase(Locale.ROOT);
            return lowered.startsWith("strain:") || lowered.startsWith("effects:") || lowered.startsWith("effect:");
        });

        lore.add(ChatColor.DARK_GREEN + "Strain: " + ChatColor.GREEN + strainName);
        if (strain != null && strain.getId().equalsIgnoreCase(DEFAULT_STRAIN_ID)) {
            lore.add(ChatColor.DARK_GREEN + "Effects: " + ChatColor.GRAY + "Unknown");
        } else {
            lore.add(ChatColor.DARK_GREEN + "Effects:");
            if (strain == null || strain.getEffects().isEmpty()) {
                lore.add(ChatColor.GRAY + "Effect: None");
            } else {
                for (PotionEffect effect : strain.getEffects()) {
                    lore.add(ChatColor.GRAY + "Effect: " + formatEffect(effect));
                }
            }
        }

        meta.setLore(lore);
    }

    private static String formatEffect(PotionEffect effect) {
        String name = effect.getType().getName().toLowerCase(Locale.ROOT).replace('_', ' ');
        String pretty = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        int seconds = Math.max(1, effect.getDuration() / 20);
        return pretty + " " + (effect.getAmplifier() + 1) + " (" + seconds + "s)";
    }

    public static void setBongDurability(ItemMeta meta, int durability) {
        meta.getPersistentDataContainer().set(key("bong_durability"), PersistentDataType.INTEGER, Math.max(0, durability));
    }

    public static Integer getBongDurability(ItemMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key("bong_durability"), PersistentDataType.INTEGER);
    }

    public static void applyBongDurabilityLore(ItemMeta meta, int durability, int maxDurability) {
        if (meta == null) return;

        List<String> lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.getLore()) : Collections.emptyList());
        lore.removeIf(line -> ChatColor.stripColor(line).toLowerCase().startsWith("durability:"));
        lore.add(ChatColor.GRAY + "Durability: " + ChatColor.GREEN + durability + ChatColor.DARK_GRAY + "/" + ChatColor.GREEN + maxDurability);
        meta.setLore(lore);
    }

    public static void setCartDurability(ItemMeta meta, int durability) {
        meta.getPersistentDataContainer().set(key("cart_durability"), PersistentDataType.INTEGER, Math.max(0, durability));
    }

    public static Integer getCartDurability(ItemMeta meta) {
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(key("cart_durability"), PersistentDataType.INTEGER);
    }

    public static void applyCartDurabilityLore(ItemMeta meta, int durability, int maxDurability) {
        if (meta == null) return;

        List<String> lore = new ArrayList<>(meta.hasLore() ? Objects.requireNonNull(meta.getLore()) : Collections.emptyList());
        lore.removeIf(line -> ChatColor.stripColor(line).toLowerCase().startsWith("durability:"));
        lore.add(ChatColor.GRAY + "Durability: " + ChatColor.GREEN + durability + ChatColor.DARK_GRAY + "/" + ChatColor.GREEN + maxDurability);
        meta.setLore(lore);
    }
}
