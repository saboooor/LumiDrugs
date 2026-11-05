package com.drugs.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DrugCategoryTest {
    @Test void groupsProductionChainsWithTheirDrugFamily() {
        for (String id : new String[]{"tobacco", "nicotine", "cigarette", "cigar", "chewing_tobacco", "vape", "vape_liquid"})
            assertEquals(DrugCategory.NICOTINE, DrugCategory.forItem(id), id);
        for (String id : new String[]{"joint", "blunt", "cart", "edible", "cbd", "infused_dough", "cannabis_extract", "bong"})
            assertEquals(DrugCategory.CANNABIS, DrugCategory.forItem(id), id);
        assertEquals(DrugCategory.CAFFEINE, DrugCategory.forItem("energy_drink"));
        assertEquals(DrugCategory.EQUIPMENT, DrugCategory.forItem("farm-controller"));
        assertEquals(DrugCategory.OTHER, DrugCategory.forItem("custom_drug"));
    }

    @Test void variantsReturnToTheSameCategory() {
        assertEquals(DrugCategory.NICOTINE, DrugCategory.forItem("vape_flavor_mint"));
        assertEquals(DrugCategory.CANNABIS, DrugCategory.forItem("cbd_strain_2"));
        assertEquals(DrugCategory.NICOTINE, DrugCategory.fromTitle("Drugs Menu: Nicotine (Page 2)"));
        assertNull(DrugCategory.fromTitle("Drugs Menu"));
    }
}
