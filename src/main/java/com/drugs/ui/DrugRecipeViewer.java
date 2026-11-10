package com.drugs.ui;

import com.drugs.DrugsV2;
import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.Map;

/** Read-only display of the registered recipe, including harvested ingredients. */
public class DrugRecipeViewer {
    public static ItemStack decorateIngredient(ItemStack ingredient) {
        if (ingredient == null) return null;
        String prompt = recipeFor(ingredient) != null
                ? "§eClick to view how to make this ingredient."
                : GrowingGuide.cropFromIngredient(ingredient) != null
                ? "§eClick to learn how to grow this ingredient." : null;
        if (prompt == null) return ingredient;
        ItemStack copy = ingredient.clone();
        var meta = copy.getItemMeta();
        if (meta != null) {
            var lore = meta.hasLore() ? new java.util.ArrayList<>(meta.getLore()) : new java.util.ArrayList<String>();
            if (!lore.contains(prompt)) lore.add(prompt);
            meta.setLore(lore);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    /** Resolves custom ingredients and products using the same rules as the menu. */
    public static String recipeFor(ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR || !item.hasItemMeta()) return null;
        String flavor = com.drugs.recipes.FlavorVariants.recipeFor(item);
        if (flavor != null) return flavor;
        String intermediate = com.drugs.recipes.ProductionRecipes.ingredientRecipe(item);
        if (intermediate != null) return intermediate;
        var drug = com.drugs.drug.DrugRegistry.getProfileFromItem(item);
        if (drug != null) return drug.getId();
        var cure = com.drugs.addiction.CureRegistry.getProfileFromItem(item);
        if (cure != null) return cure.getId();
        if (com.drugs.bong.BongItemFactory.isBongItem(item)) return "bong";
        if (com.drugs.farm.FarmItems.isControllerItem(item)) return "farm-controller";
        return null;
    }

    private static void addPurityGuide(Inventory gui) {
        if (!com.drugs.config.MechanicsConfig.isPurityEnabled()) return;
        org.bukkit.Material mat = com.drugs.config.MechanicsConfig.getPurityRefiningIngredient();
        ItemStack guide = new ItemStack(mat);
        var meta = guide.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bHow to Increase Purity");
            String itemName = mat.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            int pts = com.drugs.config.MechanicsConfig.getPurityRefiningPoints();
            meta.setLore(java.util.List.of(
                    "§7Growing Conditions (65%–100%):",
                    "§7 • Hydrated soil (7/7): §a+5%",
                    "§7 • Bright light (14–15): §a+10%",
                    "§7 • Grow lamp 1–4 blocks above: §a+5%",
                    "§7 • Composter/Moss within 2 blocks: §a+5%",
                    "§7 • Open spacing on 2+ sides: §a+5%",
                    "§7Cauldron Refining (+§f" + pts + "%§7 per step):",
                    "§7 • Hold 1 drug + §f" + itemName + " §7in offhand",
                    "§7 • Sneak-right-click a Cauldron",
                    "§eClick to open the full Purity Guide."
            ));
            guide.setItemMeta(meta);
        }
        gui.setItem(25, guide);
    }

    public static void openRecipe(Player player, String drugId) {
        String key = "farm-controller".equals(drugId) ? "farm_controller" : drugId;
        var infusion = com.drugs.recipes.FlavorVariants.infusion(drugId);
        if (infusion != null) {
            Inventory gui = Bukkit.createInventory(null, 27, "Recipe: " + drugId);
            gui.setItem(10, decorateIngredient(infusion.base()));
            gui.setItem(11, decorateIngredient(infusion.ingredient()));
            ItemStack station = new ItemStack(org.bukkit.Material.CAULDRON);
            var meta = station.getItemMeta();
            meta.setLore(java.util.List.of("§7Hold one unflavored product in your main hand.",
                    "§7Hold the flavor ingredient in your offhand.",
                    "§7Sneak-right-click an empty or water cauldron.",
                    "§7Consumes one ingredient; preserves strain and puffs."));
            station.setItemMeta(meta);
            gui.setItem(13, station);
            gui.setItem(15, infusion.result());
            addPurityGuide(gui);
            player.openInventory(gui);
            return;
        }
        if (drugId.equals("processed_resin")) {
            Inventory gui = Bukkit.createInventory(null, 27, "Recipe: Processed Resin");
            gui.setItem(10, new ItemStack(org.bukkit.Material.RESIN_CLUMP));
            ItemStack station = new ItemStack(org.bukkit.Material.CAULDRON);
            var meta = station.getItemMeta();
            meta.setLore(java.util.List.of("§7Hold resin clumps in your main hand.",
                    "§7Right-click an empty or water cauldron.",
                    "§7One resin clump makes one Processed Resin."));
            station.setItemMeta(meta);
            gui.setItem(13, station);
            gui.setItem(15, com.drugs.recipes.ProductionRecipes.intermediate("processed_resin", null));
            player.openInventory(gui);
            return;
        }
        var brewing = com.drugs.recipes.ProductionRecipes.brewingPreview(drugId);
        if (brewing != null) {
            Inventory gui = Bukkit.createInventory(null, 27, "Recipe: " + drugId);
            gui.setItem(10, decorateIngredient(brewing.base()));
            gui.setItem(11, decorateIngredient(brewing.ingredient()));
            ItemStack stand = new ItemStack(org.bukkit.Material.BREWING_STAND);
            var meta = stand.getItemMeta();
            meta.setLore(java.util.List.of("§7Base in bottom slots, ingredient on top.", "§7Fuel: blaze powder. Brewing time: 20 seconds.", "§eClick ingredients to view their recipes."));
            stand.setItemMeta(meta);
            gui.setItem(13, stand);
            gui.setItem(15, brewing.result());
            addPurityGuide(gui);
            player.openInventory(gui);
            return;
        }
        Recipe registered = Bukkit.getRecipe(new NamespacedKey(DrugsV2.getInstance(), key));
        if (registered instanceof CookingRecipe<?> cooking) {
            Inventory gui = Bukkit.createInventory(null, 27, "Recipe: " + drugId);
            gui.setItem(10, decorateIngredient(cooking.getInputChoice().getItemStack()));
            org.bukkit.Material station = cooking instanceof SmokingRecipe ? org.bukkit.Material.SMOKER
                    : cooking instanceof BlastingRecipe ? org.bukkit.Material.BLAST_FURNACE
                    : cooking instanceof CampfireRecipe ? org.bukkit.Material.CAMPFIRE : org.bukkit.Material.FURNACE;
            ItemStack info = new ItemStack(station);
            var meta = info.getItemMeta();
            meta.setLore(java.util.List.of("§7Processing time: " + cooking.getCookingTime() / 20.0 + " seconds.", "§eClick ingredients to view their recipes."));
            info.setItemMeta(meta);
            gui.setItem(13, info);
            gui.setItem(15, cooking.getResult());
            addPurityGuide(gui);
            player.openInventory(gui);
            return;
        }
        if (registered instanceof StonecuttingRecipe cutting) {
            Inventory gui = Bukkit.createInventory(null, 27, "Recipe: " + drugId);
            gui.setItem(10, decorateIngredient(cutting.getInputChoice().getItemStack()));
            gui.setItem(13, new ItemStack(org.bukkit.Material.STONECUTTER));
            gui.setItem(15, cutting.getResult());
            addPurityGuide(gui);
            player.openInventory(gui);
            return;
        }
        if (registered instanceof ShapelessRecipe shapeless) {
            Inventory gui = Bukkit.createInventory(null, 27, "Recipe: " + drugId);
            int index = 0;
            for (RecipeChoice choice : shapeless.getChoiceList()) {
                gui.setItem(index / 3 * 9 + index % 3 + 1, decorateIngredient(choice.getItemStack()));
                index++;
            }
            gui.setItem(15, shapeless.getResult());
            addPurityGuide(gui);
            player.openInventory(gui);
            return;
        }
        if (!(registered instanceof ShapedRecipe recipe)) {
            player.sendMessage("§cRecipe not found for: " + drugId);
            return;
        }
        Inventory gui = Bukkit.createInventory(null, 27, "Recipe: " + drugId);
        Map<Character, RecipeChoice> ingredients = recipe.getChoiceMap();
        String crop = DrugGrowing.requirement(drugId);
        String[] shape = recipe.getShape();
        for (int row = 0; row < shape.length; row++) {
            for (int column = 0; column < shape[row].length(); column++) {
                RecipeChoice choice = ingredients.get(shape[row].charAt(column));
                if (choice == null) continue;
                ItemStack ingredient = com.drugs.recipes.ProductionRecipes.previewIngredient(drugId, choice);
                if (choice instanceof RecipeChoice.MaterialChoice && crop != null && ingredient.getType() == DrugGrowing.material(crop))
                    ingredient = DrugGrowing.harvest(crop, "cannabis".equals(crop) ? DrugItemMetadata.DEFAULT_STRAIN_ID : null);
                gui.setItem(row * 9 + column + 1, decorateIngredient(ingredient));
            }
        }
        gui.setItem(15, recipe.getResult());
        addPurityGuide(gui);
        player.openInventory(gui);

    }
}
