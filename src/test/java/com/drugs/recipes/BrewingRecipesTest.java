package com.drugs.recipes;

import io.papermc.paper.potion.PotionMix;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.potion.PotionBrewer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrewingRecipesTest {
    public static class ModernBrew implements Recipe {
        final NamespacedKey key;
        final ItemStack result;
        final RecipeChoice input, ingredient;
        public ModernBrew(NamespacedKey key, ItemStack result, RecipeChoice input, RecipeChoice ingredient) {
            this.key = key; this.result = result; this.input = input; this.ingredient = ingredient;
        }
        public ItemStack getResult() { return result; }
    }
    @AfterEach void reset() { RecipeUpdates.reset(); }

    @Test void modernBrewingPreservesPredicatesAndPublishesOneBatch() {
        var server = mock(Server.class, withSettings().extraInterfaces(RecipeUpdatesTest.BulkRecipes.class));
        var result = mock(ItemStack.class);
        var input = mock(RecipeChoice.class);
        var ingredient = mock(RecipeChoice.class);
        try (var bukkit = mockStatic(Bukkit.class);
             var brewing = mockStatic(BrewingRecipes.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            brewing.when(BrewingRecipes::recipeType).thenReturn(ModernBrew.class);
            RecipeUpdates.begin();
            for (int i = 0; i < 100; i++) {
                var key = new NamespacedKey("lumidrugs", "brew_" + i);
                var mix = mock(PotionMix.class);
                when(mix.getKey()).thenReturn(key);
                when(mix.getResult()).thenReturn(result);
                when(mix.getInput()).thenReturn(input);
                when(mix.getIngredient()).thenReturn(ingredient);
                BrewingRecipes.replace(mix);
            }
            verifyNoInteractions(server);
            RecipeUpdates.end();
            verify((RecipeUpdatesTest.BulkRecipes) server).addRecipes(argThat(recipes -> {
                int count = 0;
                for (Recipe recipe : recipes) {
                    var brew = (ModernBrew) recipe;
                    assertSame(result, brew.result);
                    assertSame(input, brew.input);
                    assertSame(ingredient, brew.ingredient);
                    count++;
                }
                return count == 100;
            }));
            bukkit.verify(Bukkit::getPotionBrewer, never());
        }
    }

    @Test void productionCleanupBatchesAllBrewingRemovals() throws Exception {
        var server = mock(Server.class, withSettings().extraInterfaces(RecipeUpdatesTest.BulkRecipes.class));
        var field = ProductionRecipes.class.getDeclaredField("mixKeys");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var keys = (Set<NamespacedKey>) field.get(null);
        var expected = new HashSet<NamespacedKey>();
        for (int i = 0; i < 100; i++) expected.add(new NamespacedKey("lumidrugs", "brew_" + i));
        keys.addAll(expected);
        try (var bukkit = mockStatic(Bukkit.class);
             var brewing = mockStatic(BrewingRecipes.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            brewing.when(BrewingRecipes::recipeType).thenReturn(ModernBrew.class);
            ProductionRecipes.clear();
            verify((RecipeUpdatesTest.BulkRecipes) server, times(1)).removeRecipes(argThat(removed -> {
                var actual = new HashSet<NamespacedKey>();
                removed.forEach(actual::add);
                return actual.equals(expected);
            }));
            verify((RecipeUpdatesTest.BulkRecipes) server, never()).addRecipes(any());
            bukkit.verify(Bukkit::getPotionBrewer, never());
        } finally { keys.clear(); }
    }

    @Test void olderPaperKeepsPotionBrewerRegistrationAndRemoval() {
        var key = new NamespacedKey("lumidrugs", "brew");
        var mix = mock(PotionMix.class);
        when(mix.getKey()).thenReturn(key);
        var brewer = mock(PotionBrewer.class);
        try (var bukkit = mockStatic(Bukkit.class);
             var brewing = mockStatic(BrewingRecipes.class, CALLS_REAL_METHODS)) {
            bukkit.when(Bukkit::getPotionBrewer).thenReturn(brewer);
            brewing.when(BrewingRecipes::recipeType).thenReturn(null);
            BrewingRecipes.replace(mix);
            BrewingRecipes.remove(key);
            verify(brewer, times(2)).removePotionMix(key);
            verify(brewer).addPotionMix(mix);
            bukkit.verify(Bukkit::getServer, never());
        }
    }
}
