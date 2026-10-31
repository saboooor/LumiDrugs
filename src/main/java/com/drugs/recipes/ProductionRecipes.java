package com.drugs.recipes;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;
import com.drugs.strains.StrainConfigLoader;
import io.papermc.paper.potion.PotionMix;
import org.bukkit.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import java.util.*;

/** Registers the assembly and brewing chains after all drug profiles have loaded. */
public final class ProductionRecipes {
    private static final Set<String> MANAGED = Set.of("edible", "cbd", "cart", "vape", "cigarette", "cigar", "energy_drink");
    private static final List<String> INTERMEDIATES = List.of("infused_dough", "cannabis_extract", "vape_liquid");
    private static final Set<NamespacedKey> recipeKeys = new HashSet<>();
    private static final Set<NamespacedKey> mixKeys = new HashSet<>();
    public record Brew(ItemStack base, ItemStack ingredient, ItemStack result) {}
    private static final Map<String, Brew> previews = new HashMap<>();
    private ProductionRecipes() {}

    public static boolean manages(String id) { return MANAGED.contains(id); }
    public static List<String> intermediates() { return INTERMEDIATES; }
    public static Brew brewingPreview(String id) { return previews.get(id); }
    public static ItemStack intermediate(String id, String strain) {
        Material material = id.equals("infused_dough") ? Material.WHEAT : Material.POTION;
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        DrugItemMetadata.setItemType(meta, id);
        meta.setDisplayName("§a" + switch (id) {
            case "infused_dough" -> "Infused Dough";
            case "cannabis_extract" -> "Cannabis Extract";
            case "vape_liquid" -> "Nicotine Vape Liquid";
            default -> throw new IllegalArgumentException("Unknown intermediate: " + id);
        });
        meta.setLore(List.of("§7Processed ingredient — use in production recipes."));
        if (meta instanceof PotionMeta potion) potion.setBasePotionType(PotionType.WATER);
        if (strain != null) {
            DrugItemMetadata.setStrainId(meta, strain);
            DrugItemMetadata.applyStrainLore(meta, StrainConfigLoader.getStrain(strain));
        }
        item.setItemMeta(meta);
        return item;
    }
    public static boolean tagged(ItemStack item, String type) {
        return item != null && item.hasItemMeta() && type.equals(DrugItemMetadata.getItemType(item.getItemMeta()));
    }
    public static boolean drug(ItemStack item, String id) {
        return item != null && item.hasItemMeta() && id.equals(DrugItemMetadata.getDrugId(item.getItemMeta()));
    }
    static boolean acceptsBuds(ItemStack item, String strain) {
        return DrugGrowing.isHarvest(item, "cannabis") && strain.equalsIgnoreCase(
                Objects.requireNonNullElse(DrugItemMetadata.getStrainId(item), DrugItemMetadata.DEFAULT_STRAIN_ID));
    }
    public static String ingredientRecipe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String type = DrugItemMetadata.getItemType(item.getItemMeta());
        if (Set.of("infused_dough", "cannabis_extract", "vape_liquid").contains(Objects.requireNonNullElse(type, ""))) return type;
        if (drug(item, "tobacco")) return "tobacco";
        if (drug(item, "nicotine")) return "nicotine";
        if (drug(item, "caffeine")) return "caffeine";
        return null;
    }
    public static boolean plainPotion(ItemStack item, PotionType type) {
        return item != null && item.getType() == Material.POTION
                && item.getItemMeta() instanceof PotionMeta potion && potion.getBasePotionType() == type
                && DrugItemMetadata.getItemType(potion) == null && !DrugItemMetadata.hasDrugId(potion);
    }
    private static ItemStack potion(PotionType type) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }
    private static NamespacedKey key(String id) { return new NamespacedKey(DrugsV2.getInstance(), id); }
    private static void add(String id, Recipe recipe) {
        NamespacedKey key = key(id);
        recipeKeys.add(key);
        RecipeUpdates.replace(key, recipe);
    }
    private static void brew(String id, ItemStack base, ItemStack ingredient, ItemStack result,
                             java.util.function.Predicate<ItemStack> acceptsIngredient) {
        NamespacedKey key = key(id);
        Bukkit.getPotionBrewer().removePotionMix(key);
        PotionType baseType = ((PotionMeta) base.getItemMeta()).getBasePotionType();
        Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, result,
                PotionMix.createPredicateChoice(item -> plainPotion(item, baseType)),
                PotionMix.createPredicateChoice(acceptsIngredient)));
        mixKeys.add(key);
        previews.put(id, new Brew(base, ingredient, result));
    }
    public static void clear() {
        for (NamespacedKey key : recipeKeys) Bukkit.removeRecipe(key, false);
        for (NamespacedKey key : mixKeys) Bukkit.getPotionBrewer().removePotionMix(key);
        recipeKeys.clear();
        mixKeys.clear();
        previews.clear();
    }
    public static void register() {
        clear();
        for (String id : MANAGED) Bukkit.removeRecipe(key(id), false);
        ItemStack tobacco = DrugRegistry.getDrugItem("tobacco", 1);
        for (String id : List.of("cigarette", "cigar")) {
            ItemStack result = DrugRegistry.getDrugItem(id, 1);
            if (tobacco == null || result == null) continue;
            ShapedRecipe recipe = new ShapedRecipe(key(id), result);
            if (id.equals("cigarette")) recipe.shape(" PT", "   ", "   ");
            else recipe.shape("TTT", "LLL", "   ").setIngredient('L', Material.LEAF_LITTER);
            if (id.equals("cigarette")) recipe.setIngredient('P', Material.PAPER);
            recipe.setIngredient('T', new RecipeChoice.ExactChoice(tobacco));
            add(id, recipe);
        }
        ItemStack nicotine = DrugRegistry.getDrugItem("nicotine", 1);
        ItemStack vape = DrugRegistry.getDrugItem("vape", 1);
        if (nicotine != null) brew("vape_liquid", potion(PotionType.WATER), nicotine,
                intermediate("vape_liquid", null), item -> drug(item, "nicotine"));
        if (vape != null && nicotine != null) {
            ShapedRecipe recipe = new ShapedRecipe(key("vape"), vape).shape(" G ", "RSB", " C ")
                    .setIngredient('G', Material.GLASS).setIngredient('R', Material.REDSTONE)
                    .setIngredient('B', Material.STONE_BUTTON).setIngredient('C', Material.COPPER_INGOT)
                    .setIngredient('S', new RecipeChoice.ExactChoice(intermediate("vape_liquid", null)));
            add("vape", recipe);
        }
        ItemStack caffeine = DrugRegistry.getDrugItem("caffeine", 1);
        ItemStack energyDrink = DrugRegistry.getDrugItem("energy_drink", 1);
        if (caffeine != null && energyDrink != null) {
            brew("energy_drink", potion(PotionType.AWKWARD), caffeine, energyDrink, item -> drug(item, "caffeine"));
        }
        Set<String> strains = new LinkedHashSet<>();
        strains.add(DrugItemMetadata.DEFAULT_STRAIN_ID);
        StrainConfigLoader.getAllStrains().forEach(strain -> strains.add(strain.getId()));
        int index = 0;
        for (String strain : strains) {
            // Numeric suffixes also support strain IDs that are unsuitable for NamespacedKey.
            String suffix = index++ == 0 ? "" : "_strain_" + (index - 1);
            ItemStack buds = DrugGrowing.harvest("cannabis", strain);
            ItemStack dough = intermediate("infused_dough", strain);
            String doughId = "infused_dough" + suffix;
            ShapelessRecipe doughRecipe = new ShapelessRecipe(key(doughId), dough)
                    .addIngredient(Material.WHEAT).addIngredient(Material.SUGAR)
                    .addIngredient(new RecipeChoice.ExactChoice(buds));
            add(doughId, doughRecipe);
            ItemStack edible = DrugRegistry.getDrugItem("edible", 1, strain);
            if (edible != null) add("edible" + suffix, new SmokingRecipe(key("edible" + suffix), edible,
                    new RecipeChoice.ExactChoice(dough), 0.1f, 200));
            String extractId = "cannabis_extract" + suffix;
            ItemStack extract = intermediate("cannabis_extract", strain);
            java.util.function.Predicate<ItemStack> acceptsBuds = item -> acceptsBuds(item, strain);
            brew(extractId, potion(PotionType.AWKWARD), buds, extract, acceptsBuds);
            ItemStack cbd = DrugRegistry.getDrugItem("cbd", 1, strain);
            if (cbd == null) cbd = com.drugs.addiction.CureRegistry.getCureItem("cbd", 1);
            if (cbd != null) {
                var meta = cbd.getItemMeta();
                DrugItemMetadata.setStrainId(meta, strain);
                DrugItemMetadata.applyStrainLore(meta, StrainConfigLoader.getStrain(strain));
                cbd.setItemMeta(meta);
                brew("cbd" + suffix, potion(PotionType.WATER), buds, cbd, acceptsBuds);
            }
            ItemStack cart = DrugRegistry.getDrugItem("cart", 1, strain);
            if (cart != null) {
                String cartId = "cart" + suffix;
                ShapedRecipe recipe = new ShapedRecipe(key(cartId), cart).shape(" G ", "RFB", " C ")
                        .setIngredient('G', Material.GLASS).setIngredient('R', Material.REDSTONE)
                        .setIngredient('B', Material.STONE_BUTTON).setIngredient('C', Material.COPPER_INGOT)
                        .setIngredient('F', new RecipeChoice.ExactChoice(extract));
                add(cartId, recipe);
            }
        }
    }
}
