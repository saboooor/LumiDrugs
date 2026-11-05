package com.drugs.quests;

import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionPlanTest {
    @Test void vapeAndCannabisChainsIncludeTheirProcessingSteps() {
        try (var growing = mockStatic(DrugGrowing.class)) {
            growing.when(() -> DrugGrowing.requirement("edible")).thenReturn("cannabis");
            assertEquals(List.of("seed_tobacco", "harvest_tobacco", "nicotine", "vape_liquid", "vape"),
                    ProductionPlan.forProduct("vape").stream().map(ProductionPlan.Step::type).toList());
            assertEquals(List.of("cannabis_plant", "harvest_cannabis", "infused_dough", "edible"),
                    ProductionPlan.forProduct("edible").stream().map(ProductionPlan.Step::type).toList());
        }
    }
    @Test void existingIntermediateSkipsEarlierWorkAndVanillaItemsDoNotCount() {
        try (var metadata = mockStatic(DrugItemMetadata.class)) {
            var plan = ProductionPlan.forProduct("vape");
            var potion = mock(ItemStack.class);
            var meta = mock(ItemMeta.class);
            when(potion.getAmount()).thenReturn(1);
            when(potion.hasItemMeta()).thenReturn(true);
            when(potion.getItemMeta()).thenReturn(meta);
            assertEquals(0, ProductionPlan.advance(plan, 0, new ItemStack[]{null, potion}));
            metadata.when(() -> DrugItemMetadata.getItemType(meta)).thenReturn("vape_liquid");
            assertEquals(4, ProductionPlan.advance(plan, 0, new ItemStack[]{potion}));
            assertEquals(4, ProductionPlan.advance(plan, 4, new ItemStack[]{null}));
            metadata.when(() -> DrugItemMetadata.getItemType(meta)).thenReturn("vape");
            assertEquals(5, ProductionPlan.advance(plan, 4, new ItemStack[]{potion}));
            verify(potion, never()).setAmount(anyInt());
        }
    }
}
