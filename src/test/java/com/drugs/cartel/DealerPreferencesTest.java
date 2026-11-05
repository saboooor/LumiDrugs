package com.drugs.cartel;

import com.drugs.drug.DrugRegistry;
import com.drugs.items.DrugItemMetadata;
import com.drugs.strains.StrainConfigLoader;
import com.drugs.strains.StrainProfile;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DealerPreferencesTest {
    @Test void rotationsAreStableAcrossRestartAndChangeAtTheConfiguredBoundary() {
        var config = new YamlConfiguration();
        config.set("prices.joint", 10);
        config.set("prices.meth", 30);
        config.set("prices.unknown", 50);
        config.set("prices.disabled", 0);
        UUID buyer = new UUID(0, 12);
        try (var registry = mockStatic(DrugRegistry.class)) {
            registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("joint", "meth", "disabled"));
            var first = DealerPreferences.current(config, buyer, 1000);
            var again = DealerPreferences.current(config, buyer, 2000);
            assertEquals(first, again);
            assertEquals(1.5, first.multiplier());
            assertEquals(86_400_000, first.changesAt());
            assertEquals(first, DealerPreferences.current(config, UUID.fromString(buyer.toString()), 86_399_999));
            assertNotEquals(first.drug(), DealerPreferences.current(config, buyer, first.changesAt()).drug());
            assertNotEquals(first.drug(), DealerPreferences.current(config, new UUID(0, 13), 1000).drug());
            config.set("preferences.enabled", false);
            assertNull(DealerPreferences.current(config, buyer, 1000));
        }
    }

    @Test void emptyPoolsAndInvalidConfigurationAreHandledWithoutDisablingNormalSales() {
        var config = new YamlConfiguration();
        try (var registry = mockStatic(DrugRegistry.class)) {
            registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("joint"));
            assertNull(DealerPreferences.current(config, UUID.randomUUID(), 0));
            config.set("prices.joint", 10);
            config.set("preferences.rotation-hours", Long.MAX_VALUE);
            config.set("preferences.price-multiplier", Double.NaN);
            var offer = DealerPreferences.current(config, UUID.randomUUID(), 0);
            assertEquals(1.5, offer.multiplier());
            assertEquals(8760L * 3_600_000L, offer.changesAt());
            assertNull(DealerPreferences.current(config, null, 0));
        }
    }

    @Test void optionalStrainPreferencesMatchOnlyThatBaseDrugAndStrain() {
        var config = new YamlConfiguration();
        config.set("prices.joint", 10);
        config.set("preferences.strains.joint", List.of("blue", "missing"));
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        try (var registry = mockStatic(DrugRegistry.class);
             var strains = mockStatic(StrainConfigLoader.class);
             var metadata = mockStatic(DrugItemMetadata.class)) {
            registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("joint"));
            strains.when(() -> StrainConfigLoader.isCannabisDrug("joint")).thenReturn(true);
            var blue = mock(StrainProfile.class);
            when(blue.getId()).thenReturn("blue");
            strains.when(() -> StrainConfigLoader.getStrain("blue")).thenReturn(blue);
            var fallback = mock(StrainProfile.class);
            when(fallback.getId()).thenReturn("reggie");
            strains.when(() -> StrainConfigLoader.getStrain("missing")).thenReturn(fallback);
            var offer = DealerPreferences.current(config, UUID.randomUUID(), 0);
            assertEquals("blue", offer.strain());
            metadata.when(() -> DrugItemMetadata.getDrugId(meta)).thenReturn("joint");
            metadata.when(() -> DrugItemMetadata.getStrainId(meta)).thenReturn("blue");
            assertTrue(offer.matches(item));
            metadata.when(() -> DrugItemMetadata.getStrainId(meta)).thenReturn("reggie");
            assertFalse(offer.matches(item));
            metadata.when(() -> DrugItemMetadata.getDrugId(meta)).thenReturn("meth");
            assertFalse(offer.matches(item));
        }
    }
}
