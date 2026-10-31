package com.drugs.bootstrap;

import com.drugs.DrugsV2;
import com.drugs.achievements.NativeAdvancements;
import com.drugs.growing.CannabisPlantRegistry;
import com.drugs.recipes.ProductionRecipes;
import com.drugs.recipes.RecipeUpdates;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.mockito.Mockito.*;

class ShutdownCleanupTest {
    @Test void cleanupFailureDoesNotSkipLoadedDataSaves() throws Exception {
        DrugsV2 plugin = mock(DrugsV2.class, CALLS_REAL_METHODS);
        doReturn(mock(Logger.class)).when(plugin).getLogger();
        var loaded = DrugsV2.class.getDeclaredField("cannabisLoaded");
        loaded.setAccessible(true);
        loaded.setBoolean(plugin, true);
        try (var production = mockStatic(ProductionRecipes.class);
             var recipes = mockStatic(RecipeUpdates.class);
             var advancements = mockStatic(NativeAdvancements.class);
             var plants = mockStatic(CannabisPlantRegistry.class)) {
            production.when(ProductionRecipes::clear).thenThrow(new IllegalStateException("recipe cleanup failed"));
            plugin.onDisable();
            recipes.verify(RecipeUpdates::reset);
            advancements.verify(NativeAdvancements::shutdown);
            plants.verify(CannabisPlantRegistry::save);
            verify(plugin.getLogger()).info("LumiDrugs disabled.");
        }
    }

    @Test void unloadingBeforeDataLoadsDoesNotOverwriteSavedPlants() {
        DrugsV2 plugin = mock(DrugsV2.class, CALLS_REAL_METHODS);
        doReturn(mock(Logger.class)).when(plugin).getLogger();
        try (var production = mockStatic(ProductionRecipes.class);
             var recipes = mockStatic(RecipeUpdates.class);
             var advancements = mockStatic(NativeAdvancements.class);
             var plants = mockStatic(CannabisPlantRegistry.class)) {
            plugin.onDisable();
            plants.verifyNoInteractions();
        }
    }
}
