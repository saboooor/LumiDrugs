package com.drugs.recipes;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Map;

/** Main-thread recipe batches avoid rebuilding and resending the recipe list for every change. */
public final class RecipeUpdates {
    private static int batches;
    private static final Set<NamespacedKey> removals = new LinkedHashSet<>();
    private static final Map<NamespacedKey, Recipe> pending = new LinkedHashMap<>();

    private RecipeUpdates() {}
    public static void begin() { batches++; }
    public static void end() {
        if (batches == 0) return;
        if (--batches == 0) publish();
    }
    public static void replace(NamespacedKey key, Recipe recipe) {
        removals.add(key);
        pending.put(key, recipe);
        if (batches == 0) publish();
    }
    public static void remove(NamespacedKey key) {
        pending.remove(key);
        removals.add(key);
        if (batches == 0) publish();
    }
    private static void publish() {
        if (pending.isEmpty() && removals.isEmpty()) return;
        var recipes = new LinkedHashMap<>(pending);
        pending.clear();
        var keys = List.copyOf(removals);
        removals.clear();
        // 26.3 ignores addRecipe's resend flag. Its bulk API is required to avoid
        // reloading every online player's advancements once per added recipe.
        // Reflect only this newer API so the plugin still runs on Paper 1.21.x.
        var server = Bukkit.getServer();
        if (server != null) {
            try {
                var addRecipes = server.getClass().getMethod("addRecipes", Iterable.class);
                var removeRecipes = server.getClass().getMethod("removeRecipes", Iterable.class);
                removeRecipes.invoke(server, keys);
                if (!recipes.isEmpty()) addRecipes.invoke(server, List.copyOf(recipes.values()));
                return;
            } catch (NoSuchMethodException ignored) {
                // Older Paper supports suppressing updates on individual additions.
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access bulk recipe registration", exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw new IllegalStateException("Bulk recipe registration failed", cause);
            }
        }
        for (NamespacedKey key : keys) Bukkit.removeRecipe(key, false);
        for (Recipe recipe : recipes.values()) Bukkit.addRecipe(recipe, false);
        Bukkit.updateRecipes();
    }
    public static void reset() { batches = 0; pending.clear(); removals.clear(); }
}
