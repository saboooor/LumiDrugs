package com.drugs.recipes;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.Recipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class RecipeUpdatesTest {
    public interface BulkRecipes { boolean addRecipes(Iterable<Recipe> recipes); boolean removeRecipes(Iterable<NamespacedKey> keys); }
    @AfterEach void reset() { RecipeUpdates.reset(); }

    @Test void startupBatchPublishesOnceAfterAllRecipeChangesOnOlderPaper() {
        var recipe = mock(Recipe.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            RecipeUpdates.begin();
            for (int i = 0; i < 25; i++)
                RecipeUpdates.replace(new NamespacedKey("lumidrugs", "test_" + i), recipe);
            bukkit.verify(() -> Bukkit.addRecipe(recipe, false), never());
            RecipeUpdates.end();
            bukkit.verify(() -> Bukkit.addRecipe(recipe, false), times(25));
            bukkit.verify(Bukkit::updateRecipes, times(1));
            RecipeUpdates.end();
            bukkit.verify(Bukkit::updateRecipes, times(1));
        }
    }

    @Test void modernPaperUsesOneBulkCallAndKeepsLatestReplacement() {
        Server server = mock(Server.class, withSettings().extraInterfaces(BulkRecipes.class));
        Recipe first = mock(Recipe.class), replacement = mock(Recipe.class), second = mock(Recipe.class);
        var key = new NamespacedKey("lumidrugs", "test");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            RecipeUpdates.begin();
            RecipeUpdates.begin();
            RecipeUpdates.replace(key, first);
            RecipeUpdates.replace(key, replacement);
            RecipeUpdates.replace(new NamespacedKey("lumidrugs", "second"), second);
            RecipeUpdates.end();
            verifyNoInteractions(server);
            RecipeUpdates.end();
            verify((BulkRecipes) server).addRecipes(List.of(replacement, second));
            verify((BulkRecipes) server).removeRecipes(List.of(key, new NamespacedKey("lumidrugs", "second")));
            bukkit.verify(() -> Bukkit.removeRecipe(any(), anyBoolean()), never());
            bukkit.verify(() -> Bukkit.addRecipe(any(), anyBoolean()), never());
            bukkit.verify(Bukkit::updateRecipes, never());
        }
    }

    @Test void removalCancelsQueuedAdditionAndPublishesOnlyRemoval() {
        Server server = mock(Server.class, withSettings().extraInterfaces(BulkRecipes.class));
        var key = new NamespacedKey("lumidrugs", "test");
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            RecipeUpdates.begin();
            RecipeUpdates.replace(key, mock(Recipe.class));
            RecipeUpdates.remove(key);
            RecipeUpdates.end();
            verify((BulkRecipes) server).removeRecipes(List.of(key));
            verify((BulkRecipes) server, never()).addRecipes(any());
        }
    }

    @Test void disablingProductionRecipesUsesOneBulkRemoval() throws Exception {
        Server server = mock(Server.class, withSettings().extraInterfaces(BulkRecipes.class));
        var field = ProductionRecipes.class.getDeclaredField("recipeKeys");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var keys = (java.util.Set<NamespacedKey>) field.get(null);
        var expected = new java.util.HashSet<NamespacedKey>();
        for (int i = 0; i < 100; i++) expected.add(new NamespacedKey("lumidrugs", "cleanup_" + i));
        keys.addAll(expected);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            ProductionRecipes.clear();
            verify((BulkRecipes) server, times(1)).removeRecipes(argThat(removed -> {
                var actual = new java.util.HashSet<NamespacedKey>();
                removed.forEach(actual::add);
                return expected.equals(actual);
            }));
            verify((BulkRecipes) server, never()).addRecipes(any());
            bukkit.verify(() -> Bukkit.removeRecipe(any(), anyBoolean()), never());
            bukkit.verify(Bukkit::updateRecipes, never());
            ProductionRecipes.clear();
            verifyNoMoreInteractions(server);
        } finally {
            keys.clear();
        }
    }

    @Test void productionCleanupRespectsOuterBatchOnOlderPaper() throws Exception {
        var field = ProductionRecipes.class.getDeclaredField("recipeKeys");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var keys = (java.util.Set<NamespacedKey>) field.get(null);
        var key = new NamespacedKey("lumidrugs", "cleanup");
        keys.add(key);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(mock(Server.class));
            RecipeUpdates.begin();
            ProductionRecipes.clear();
            bukkit.verify(() -> Bukkit.removeRecipe(key, false), never());
            RecipeUpdates.end();
            bukkit.verify(() -> Bukkit.removeRecipe(key, false), times(1));
            bukkit.verify(Bukkit::updateRecipes, times(1));
        } finally {
            keys.clear();
        }
    }

    @Test void bulkFailureDoesNotRetryIndividualAdditions() {
        Server server = mock(Server.class, withSettings().extraInterfaces(BulkRecipes.class));
        when(((BulkRecipes) server).addRecipes(any())).thenThrow(new IllegalStateException("failed"));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            assertThrows(IllegalStateException.class, () -> RecipeUpdates.replace(
                    new NamespacedKey("lumidrugs", "test"), mock(Recipe.class)));
            bukkit.verify(() -> Bukkit.addRecipe(any(), anyBoolean()), never());
        }
    }

    @Test void resetDiscardsPendingRecipes() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            RecipeUpdates.begin();
            RecipeUpdates.replace(new NamespacedKey("lumidrugs", "test"), mock(Recipe.class));
            RecipeUpdates.reset();
            RecipeUpdates.end();
            bukkit.verifyNoInteractions();
        }
    }

    @Test void unbatchedReplacementStillUpdatesClientsAndEmptyBatchDoesNot() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            RecipeUpdates.begin();
            RecipeUpdates.end();
            bukkit.verify(Bukkit::updateRecipes, never());
            RecipeUpdates.replace(new NamespacedKey("lumidrugs", "test"), mock(Recipe.class));
            bukkit.verify(Bukkit::updateRecipes);
        }
    }
}
