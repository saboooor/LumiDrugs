package com.drugs.recipes;

import com.drugs.growing.DrugGrowing;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.*;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessingRecipeTest {
    @Test void processingTypesRegisterExactHarvestInputsAndConfiguredTimes() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        ItemStack harvest = mock(ItemStack.class);
        when(harvest.clone()).thenReturn(harvest);
        when(harvest.getType()).thenReturn(Material.DRIED_KELP);
        ItemStack result = mock(ItemStack.class);
        when(result.clone()).thenReturn(result);
        when(result.getType()).thenReturn(Material.SUGAR);
        try (var growing = mockStatic(DrugGrowing.class); var updates = mockStatic(RecipeUpdates.class);
             var choices = mockConstruction(RecipeChoice.ExactChoice.class, (choice, context) -> {
                 when(choice.validate(anyBoolean())).thenReturn(choice);
                 when(choice.clone()).thenReturn(choice);
             })) {
            growing.when(() -> DrugGrowing.crop("tobacco")).thenReturn(
                    new DrugGrowing.Crop("tobacco", "Tobacco Leaves", Material.DRIED_KELP, Material.DRIED_KELP, 600));
            growing.when(() -> DrugGrowing.harvest("tobacco", null)).thenReturn(harvest);
            for (String type : new String[]{"furnace", "smoker", "blast-furnace", "campfire", "stonecutter"}) {
                var config = new YamlConfiguration();
                config.set("type", type);
                config.set("input-crop", "tobacco");
                config.set("cooking-time", 400);
                DrugRecipeHelper.registerItemRecipe("test_" + type, config, result, plugin);
                updates.verify(() -> RecipeUpdates.replace(any(), argThat(recipe -> {
                    RecipeChoice input;
                    if (recipe instanceof CookingRecipe<?> cooking) {
                        assertEquals(400, cooking.getCookingTime());
                        input = cooking.getInputChoice();
                    } else {
                        assertInstanceOf(StonecuttingRecipe.class, recipe);
                        input = ((StonecuttingRecipe) recipe).getInputChoice();
                    }
                    assertInstanceOf(RecipeChoice.ExactChoice.class, input);
                    return true;
                })));
                updates.clearInvocations();
                assertFalse(choices.constructed().isEmpty());
            }
            var productionConfig = new YamlConfiguration();
            productionConfig.set("type", "production");
            assertDoesNotThrow(() -> DrugRecipeHelper.registerItemRecipe("cbd", productionConfig, result, plugin));
            updates.verifyNoInteractions();
        }
    }
}
