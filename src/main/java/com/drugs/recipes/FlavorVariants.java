package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** Cosmetic variants retain the original drug ID, strain, effects, and use count. */
public final class FlavorVariants {
    public record Flavor(String id, String name, Material ingredient, List<String> products, String ingredientCrop) {
        public Flavor(String id, String name, Material ingredient, List<String> products) {
            this(id, name, ingredient, products, null);
        }
    }
    private static List<Flavor> flavors = defaults();
    public record Infusion(ItemStack base, ItemStack ingredient, ItemStack result) {}
    private static final Map<String, Infusion> infusions = new LinkedHashMap<>();
    private static final Map<String, ItemStack> menu = new LinkedHashMap<>();
    private FlavorVariants() {}

    static List<Flavor> defaults() {
        return List.of(
                new Flavor("berry", "&dBerry", Material.SWEET_BERRIES, List.of("vape", "cart", "edible", "joint", "blunt", "energy_drink")),
                new Flavor("apple", "&cApple", Material.APPLE, List.of("vape", "cart", "edible", "energy_drink")),
                new Flavor("melon", "&aMelon", Material.MELON_SLICE, List.of("vape", "cart", "edible", "energy_drink")),
                new Flavor("honey", "&6Honey", Material.HONEYCOMB, List.of("vape", "edible", "joint", "blunt", "tobacco", "chewing_tobacco", "cigar", "cigarette")),
                new Flavor("chocolate", "&6Chocolate", Material.COCOA_BEANS, List.of("edible", "cigar", "tobacco")),
                new Flavor("vanilla", "&fVanilla", Material.SUGAR, List.of("vape", "edible", "cigar")),
                new Flavor("cherry", "&cCherry", Material.GLOW_BERRIES, List.of("vape", "cart", "edible", "tobacco")),
                new Flavor("citrus", "&6Citrus", Material.ORANGE_DYE, List.of("vape", "cart", "energy_drink")),
                new Flavor("mint", "&aMint", Material.KELP, List.of("vape", "chewing_tobacco", "joint")),
                new Flavor("cinnamon", "&6Cinnamon", Material.BROWN_DYE, List.of("edible", "tobacco", "cigar")),
                new Flavor("coffee", "&6Coffee", Material.COCOA_BEANS, List.of("vape", "edible", "tobacco"), "coffee"),
                new Flavor("grape", "&5Grape", Material.PURPLE_DYE, List.of("vape", "cart", "blunt", "energy_drink")),
                new Flavor("peach", "&dPeach", Material.PINK_DYE, List.of("vape", "cart", "edible")));
    }

    public static void load(File folder) {
        var yaml = YamlConfiguration.loadConfiguration(new File(folder, "mechanics.yml"));
        ConfigurationSection section = yaml.getConfigurationSection("flavors");
        if (section == null) { flavors = defaults(); return; }
        List<Flavor> loaded = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            if (!id.matches("[a-z0-9_]+") || !section.getBoolean(id + ".enabled", true)) continue;
            Material material = Material.matchMaterial(section.getString(id + ".ingredient", ""));
            if (material == null || !material.isItem() || DrugItemMetadata.isUnobtainableMaterial(material)) {
                DrugsV2.getInstance().getLogger().warning("Skipping flavor with invalid ingredient: " + id);
                continue;
            }
            loaded.add(new Flavor(id, section.getString(id + ".name", id), material,
                    section.getStringList(id + ".products"), section.getString(id + ".ingredient-crop")));
        }
        flavors = List.copyOf(loaded);
    }

    public static ItemStack apply(ItemStack base, Flavor flavor) {
        ItemStack result = base.clone();
        var meta = result.getItemMeta();
        String name = meta.hasDisplayName() ? meta.getDisplayName() : result.getType().name();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', flavor.name()) + " " + name);
        var lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.<String>of());
        lore.add("§7Flavor: " + ChatColor.translateAlternateColorCodes('&', flavor.name()));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(new NamespacedKey(DrugsV2.getInstance(), "flavor"),
                PersistentDataType.STRING, flavor.id());
        DrugItemMetadata.applyFlavorModel(meta, DrugItemMetadata.getDrugId(meta), flavor.id());
        result.setItemMeta(meta);
        return result;
    }

    public static void clear() { infusions.clear(); menu.clear(); }
    public static List<ItemStack> menuItems() { return menu.values().stream().map(ItemStack::clone).toList(); }
    public static boolean hasFlavors(String product) {
        return menu.keySet().stream().anyMatch(key -> key.startsWith(product + "_flavor_"));
    }
    public static List<ItemStack> menuItems(String product) {
        return menu.entrySet().stream().filter(entry -> entry.getKey().startsWith(product + "_flavor_"))
                .map(entry -> entry.getValue().clone()).toList();
    }
    public static String recipeFor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String flavor = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(DrugsV2.getInstance(), "flavor"), PersistentDataType.STRING);
        String drug = DrugItemMetadata.getDrugId(item.getItemMeta());
        String key = drug + "_flavor_" + flavor;
        return flavor != null && menu.containsKey(key) ? key : null;
    }

    public static boolean isFlavored(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(DrugsV2.getInstance(), "flavor"), PersistentDataType.STRING);
    }

    public static Flavor matchingFlavor(ItemStack product, ItemStack ingredient) {
        if (product == null || ingredient == null || !product.hasItemMeta() || isFlavored(product)) return null;
        String drug = DrugItemMetadata.getDrugId(product.getItemMeta());
        if (drug == null || !DrugRegistry.isDrugRegistered(drug)) return null;
        for (Flavor flavor : flavors) {
            if (!flavor.products().contains(drug)) continue;
            if (flavor.ingredientCrop() != null) {
                if (ingredient.getType() == flavor.ingredient()
                        && DrugGrowing.isHarvest(ingredient, flavor.ingredientCrop())) return flavor;
            } else if (ingredient.getType() == flavor.ingredient() && !ingredient.hasItemMeta()) return flavor;
        }
        return null;
    }

    public static Infusion infusion(String key) { return infusions.get(key); }

    public static void initialize() {
        clear();
        for (Flavor flavor : flavors) for (String drug : flavor.products()) {
            ItemStack base = DrugRegistry.getDrugItem(drug, 1);
            if (base == null) continue;
            String id = drug + "_flavor_" + flavor.id();
            ItemStack result = apply(base, flavor);
            ItemStack ingredient = flavor.ingredientCrop() == null ? new ItemStack(flavor.ingredient())
                    : DrugGrowing.harvest(flavor.ingredientCrop(), null);
            infusions.put(id, new Infusion(base, ingredient, result));
            menu.put(id, result.clone());
        }
    }
}
