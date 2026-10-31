package com.drugs.recipes;

import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;
import com.drugs.growing.DrugGrowing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;


/**
 * Loads crafting and block processing recipes from recipes.yml
 */
public class DrugRecipeHelper {

    /**
     * Registers a crafting or processing recipe from config for a given drug.
     *
     * Expected format in recipes.yml:
     *   drug_id:
     *     shape:
     *       - " A "
     *       - "BCB"
     *       - " D "
     *     ingredients:
     *       A: SUGAR
     *       B: BONE
     *       C: GHAST_TEAR
     *       D: APPLE
     *
     * @param id     The internal drug ID (e.g. "heroin")
     * @param section The section for this drug in recipes.yml
     * @param plugin The plugin instance for key registration
     */
    public static void registerDrugRecipe(String id, ConfigurationSection section, Plugin plugin) {
        if (section == null || ProductionRecipes.manages(id)) return;

        Bukkit.getLogger().info("[LumiDrugs] Attempting to register recipe for: " + id);

        // Build the result item
        DrugEffectProfile profile = DrugRegistry.getProfileById(id);
        if (profile == null) return;

        ItemStack result = profile.createItem(1);
        registerItemRecipe(id, section, result, plugin);
    }

    public static void registerItemRecipe(String id, ConfigurationSection section, ItemStack result, Plugin plugin) {
        if (section == null || result == null || ProductionRecipes.manages(id)) return;

        String type = section.getString("type", "crafting").toLowerCase(java.util.Locale.ROOT);
        if (type.equals("production")) return;
        NamespacedKey processingKey = new NamespacedKey(plugin, id.toLowerCase());
        if (!type.equals("crafting")) {
            String crop = section.getString("input-crop", DrugGrowing.requirement(id));
            if (crop == null || DrugGrowing.crop(crop) == null) return;
            RecipeChoice input = new RecipeChoice.ExactChoice(DrugGrowing.harvest(crop, null));
            int ticks = Math.max(1, section.getInt("cooking-time", 200));
            float experience = Math.max(0, (float) section.getDouble("experience", 0.1));
            Recipe processing = switch (type) {
                case "furnace" -> new FurnaceRecipe(processingKey, result, input, experience, ticks);
                case "smoker" -> new SmokingRecipe(processingKey, result, input, experience, ticks);
                case "blast-furnace" -> new BlastingRecipe(processingKey, result, input, experience, ticks);
                case "campfire" -> new CampfireRecipe(processingKey, result, input, experience, ticks);
                case "stonecutter" -> new StonecuttingRecipe(processingKey, result, input);
                default -> throw new IllegalArgumentException("Unknown recipe type: " + type);
            };
            RecipeUpdates.replace(processingKey, processing);
            return;
        }
        var shape = section.getStringList("shape");
        if (shape.size() != 3) return;

        NamespacedKey key = new NamespacedKey(plugin, id.toLowerCase());

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape(shape.toArray(new String[0]));

        // Set each character as a material
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients == null) return;

        for (String symbol : ingredients.getKeys(false)) {
            String matName = ingredients.getString(symbol);
            Material mat = Material.matchMaterial(matName);
            if (mat != null) {
                recipe.setIngredient(symbol.charAt(0), mat);
            }
        }

        String crop = DrugGrowing.requirement(id);
        if (crop != null) {
            // Replace the first configured ingredient with the harvested crop when an old recipe
            // has no matching material (for example custom recipes on an existing server).
            char required = 0;
            for (String symbol : ingredients.getKeys(false)) {
                if (shape.stream().noneMatch(row -> row.indexOf(symbol.charAt(0)) >= 0)) continue;
                if (required == 0) required = symbol.charAt(0);
                if (Material.matchMaterial(ingredients.getString(symbol)) == DrugGrowing.material(crop)) {
                    required = symbol.charAt(0);
                    break;
                }
            }
            if (required == 0) return;
            recipe.setIngredient(required, DrugGrowing.material(crop));
        }
        if (id.equals("vape")) {
            ItemStack nicotine = DrugRegistry.getDrugItem("nicotine", 1);
            if (nicotine == null) return;
            recipe.setIngredient('S', new RecipeChoice.ExactChoice(nicotine));
        }
        RecipeUpdates.replace(key, recipe);
    }
}
