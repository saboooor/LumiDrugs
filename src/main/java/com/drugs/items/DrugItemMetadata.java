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

    private static final List<String> BASE_RESOURCE_PACK_TYPES = List.of(
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

    private static final List<String> FLAVOR_IDS = List.of(
            "berry", "apple", "melon", "honey", "chocolate", "vanilla", "cherry",
            "citrus", "mint", "cinnamon", "coffee", "grape", "peach");
    private static final List<String> FLAVOR_PRODUCTS = List.of(
            "vape", "cart", "edible", "joint", "blunt", "energy_drink", "tobacco",
            "chewing_tobacco", "cigar", "cigarette");
    private static final List<String> RESOURCE_PACK_TYPES = resourcePackCatalog();

    private static List<String> resourcePackCatalog() {
        List<String> types = new ArrayList<>(BASE_RESOURCE_PACK_TYPES);
        // Append variants so existing item model numbers remain stable.
        for (String product : FLAVOR_PRODUCTS)
            for (String flavor : FLAVOR_IDS) types.add(product + "_flavor_" + flavor);
        types.addAll(List.of("farm_controller", "farm_area_wand", "farm_work_contract", "processed_resin"));
        return List.copyOf(types);
    }

    public static void applyFlavorModel(ItemMeta meta, String product, String flavor) {
        String model = product + "_flavor_" + flavor;
        int data = customModelData(model);
        if (data == 0) return; // Custom server flavors retain their base model.
        meta.setCustomModelData(data);
        meta.setItemModel(new NamespacedKey("lumidrugs", model));
    }

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
        String modelType = type.startsWith("farm_area_wand:") ? "farm_area_wand"
                : type.startsWith("farm_work_contract:") ? "farm_work_contract" : type;
        int cmd = customModelData(modelType);
        if (cmd > 0) {
            if (!meta.hasCustomModelData()) {
                meta.setCustomModelData(cmd);
            }
            meta.setItemModel(new NamespacedKey("lumidrugs", modelType));
        }
    }

    public static void setStrainId(ItemMeta meta, String strainId) {
        meta.getPersistentDataContainer().set(key("strain_id"), PersistentDataType.STRING, strainId.toLowerCase());
    }

    /** Hidden item data only: never change a laced product's visible metadata. */
    public static void setLacedDrug(ItemMeta meta, String drugId, String strainId) {
        meta.getPersistentDataContainer().set(key("laced_drug"), PersistentDataType.STRING, drugId.toLowerCase(Locale.ROOT));
        if (strainId == null) meta.getPersistentDataContainer().remove(key("laced_strain"));
        else meta.getPersistentDataContainer().set(key("laced_strain"), PersistentDataType.STRING, strainId.toLowerCase(Locale.ROOT));
        setLacingExpended(meta, false);
    }

    public static String getLacedDrug(ItemMeta meta) {
        return meta == null ? null : meta.getPersistentDataContainer().get(key("laced_drug"), PersistentDataType.STRING);
    }

    public static String getLacedStrain(ItemMeta meta) {
        return meta == null ? null : meta.getPersistentDataContainer().get(key("laced_strain"), PersistentDataType.STRING);
    }

    public static boolean isLacingExpended(ItemMeta meta) {
        return meta != null && Byte.valueOf((byte) 1).equals(
                meta.getPersistentDataContainer().get(key("laced_dose_used"), PersistentDataType.BYTE));
    }

    public static void setLacingExpended(ItemMeta meta, boolean expended) {
        if (expended) meta.getPersistentDataContainer().set(key("laced_dose_used"), PersistentDataType.BYTE, (byte) 1);
        else meta.getPersistentDataContainer().remove(key("laced_dose_used"));
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
