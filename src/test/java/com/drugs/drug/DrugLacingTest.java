package com.drugs.drug;

import com.drugs.DrugsV2;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugLacingTest {
    private DrugsV2 plugin;
    private Field instance;
    private Object previous;
    private MockedStatic<DrugRegistry> registry;
    private record Sample(ItemStack item, ItemMeta meta, Map<NamespacedKey, Object> data) {}

    @BeforeEach void setup() throws Exception {
        plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        instance = DrugsV2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previous = instance.get(null);
        instance.set(null, plugin);
        registry = mockStatic(DrugRegistry.class);
        try (var construction = mockConstruction(ItemStack.class)) {
            for (String id : List.of("joint", "meth", "vape", "edible")) {
                var profile = mock(DrugEffectProfile.class);
                when(profile.getId()).thenReturn(id);
                when(profile.matches(any())).thenReturn(true);
                registry.when(() -> DrugRegistry.getProfileById(id)).thenReturn(profile);
            }
        }
    }

    @AfterEach void cleanup() throws Exception {
        registry.close();
        instance.set(null, previous);
    }

    private NamespacedKey key(String key) { return new NamespacedKey(plugin, key); }
    private Sample drug(String id, int count) {
        var sample = sample(new HashMap<>(), count);
        if (id != null) DrugItemMetadata.setDrugId(sample.meta(), id);
        return sample;
    }
    private Sample sample(Map<NamespacedKey, Object> values, int count) {
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        var amount = new AtomicInteger(count);
        when(item.getType()).thenReturn(Material.SUGAR);
        when(item.getAmount()).thenAnswer(call -> amount.get());
        doAnswer(call -> { amount.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(meta.getDisplayName()).thenReturn("Mint product");
        when(meta.getLore()).thenReturn(List.of("Flavor: Mint", "Durability: 7/32"));
        when(data.get(any(), eq(PersistentDataType.STRING))).thenAnswer(call -> values.get(call.getArgument(0)));
        when(data.get(any(), eq(PersistentDataType.BYTE))).thenAnswer(call -> values.get(call.getArgument(0)));
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(data).set(any(), eq(PersistentDataType.STRING), anyString());
        doAnswer(call -> { values.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(data).set(any(), eq(PersistentDataType.BYTE), anyByte());
        doAnswer(call -> { values.remove(call.getArgument(0)); return null; }).when(data).remove(any());
        when(item.clone()).thenAnswer(call -> sample(new HashMap<>(values), amount.get()).item());
        return new Sample(item, meta, values);
    }

    @Test void lacingOnlyAddsHiddenMetadataAndPreservesOriginalItems() {
        var host = drug("vape", 1);
        host.data().put(key("flavor"), "mint");
        host.data().put(key("cart_durability"), 7);
        var donor = drug("edible", 3);
        DrugItemMetadata.setStrainId(donor.meta(), "blue");
        var original = new HashMap<>(host.data());
        var result = DrugLacing.lace(host.item(), donor.item());
        assertNotNull(result);
        assertNotSame(host.item(), result);
        assertEquals("edible", DrugItemMetadata.getLacedDrug(result.getItemMeta()));
        assertEquals("blue", DrugItemMetadata.getLacedStrain(result.getItemMeta()));
        assertEquals("vape", DrugItemMetadata.getDrugId(result.getItemMeta()));
        assertEquals(host.meta().getDisplayName(), result.getItemMeta().getDisplayName());
        assertEquals(host.meta().getLore(), result.getItemMeta().getLore());
        assertEquals(original, host.data());
        assertEquals(3, donor.item().getAmount());
        verify(result.getItemMeta(), never()).setDisplayName(any());
        verify(result.getItemMeta(), never()).setLore(any());
        verify(result.getItemMeta(), never()).setItemModel(any());
        verify(result.getItemMeta(), never()).setCustomModelData(any());
    }

    @Test void rejectsVanillaUnknownSameDrugStacksAndNestedLacing() {
        var host = drug("joint", 1);
        var donor = drug("meth", 1);
        assertNull(DrugLacing.lace(host.item(), drug(null, 1).item()));
        assertNull(DrugLacing.lace(drug(null, 1).item(), donor.item()));
        assertNull(DrugLacing.lace(host.item(), drug("removed", 1).item()));
        assertNull(DrugLacing.lace(host.item(), drug("joint", 1).item()));
        assertNull(DrugLacing.lace(drug("joint", 2).item(), donor.item()));
        assertNull(DrugLacing.lace(host.item(), drug("meth", 0).item()));
        assertNull(DrugLacing.lace(null, donor.item()));
        assertNull(DrugLacing.lace(host.item(), null));
        DrugItemMetadata.setLacedDrug(host.meta(), "meth", null);
        assertNull(DrugLacing.lace(host.item(), donor.item()));
        var nestedDonor = drug("meth", 1);
        DrugItemMetadata.setLacedDrug(nestedDonor.meta(), "edible", null);
        assertNull(DrugLacing.lace(drug("joint", 1).item(), nestedDonor.item()));
    }

    @Test void resolvesOneFreshDoseWithItsOwnStrainAndTracksSpentPuffs() {
        var host = drug("vape", 1);
        DrugItemMetadata.setLacedDrug(host.meta(), "edible", "blue");
        var dose = drug("edible", 1).item();
        registry.when(() -> DrugRegistry.getDrugItem("edible", 1, "blue")).thenReturn(dose);
        assertSame(dose, DrugLacing.additionalDose(host.item()));
        DrugLacing.expendDose(host.item());
        assertTrue(DrugItemMetadata.isLacingExpended(host.meta()));
        assertNull(DrugLacing.additionalDose(host.item()));
        assertEquals("edible", DrugItemMetadata.getLacedDrug(host.meta()));
        // A fresh device in the same stack gets its own added dose.
        DrugItemMetadata.setLacingExpended(host.meta(), false);
        assertSame(dose, DrugLacing.additionalDose(host.item()));
        DrugItemMetadata.setLacedDrug(host.meta(), "removed", null);
        assertNull(DrugLacing.additionalDose(host.item()));
        DrugItemMetadata.setLacedDrug(host.meta(), "vape", null);
        assertNull(DrugLacing.additionalDose(host.item()));
    }
}
