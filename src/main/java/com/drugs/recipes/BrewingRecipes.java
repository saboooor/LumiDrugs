package com.drugs.recipes;

import io.papermc.paper.potion.PotionMix;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

import java.lang.reflect.InvocationTargetException;

/** Bridge 26.3 brewing recipes into the shared recipe batch, retaining 1.21 support. */
public final class BrewingRecipes {
    private BrewingRecipes() {}

    static Class<? extends Recipe> recipeType() {
        try {
            return Class.forName("org.bukkit.inventory.BrewingRecipe").asSubclass(Recipe.class);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    public static void replace(PotionMix mix) {
        var type = recipeType();
        if (type == null) {
            Bukkit.getPotionBrewer().removePotionMix(mix.getKey());
            Bukkit.getPotionBrewer().addPotionMix(mix);
            return;
        }
        try {
            Recipe recipe = type.getConstructor(NamespacedKey.class, ItemStack.class,
                    RecipeChoice.class, RecipeChoice.class).newInstance(
                    mix.getKey(), mix.getResult(), mix.getInput(), mix.getIngredient());
            RecipeUpdates.replace(mix.getKey(), recipe);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Cannot create brewing recipe", cause);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot access brewing recipe API", exception);
        }
    }

    public static void remove(NamespacedKey key) {
        if (recipeType() != null) RecipeUpdates.remove(key);
        else Bukkit.getPotionBrewer().removePotionMix(key);
    }
}
