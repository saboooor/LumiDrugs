package com.drugs.recipes;

import com.drugs.items.DrugItemMetadata;
import com.google.gson.JsonParser;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlavorResourcePackTest {
    private Path pack() {
        Path root = Path.of("../resourcepack");
        return Files.isDirectory(root) ? root : Path.of("resourcepack");
    }

    @Test void pluginCatalogMatchesPackAndEveryTextureResolves() throws Exception {
        Path root = pack();
        var ids = new ArrayList<String>();
        JsonParser.parseString(Files.readString(root.resolve("tools/items.json"))).getAsJsonArray()
                .forEach(id -> ids.add(id.getAsString()));
        var flavors = JsonParser.parseString(Files.readString(root.resolve("tools/flavors.json"))).getAsJsonObject();
        for (var product : flavors.getAsJsonArray("products"))
            for (var flavor : flavors.getAsJsonObject("flavors").keySet())
                ids.add(product.getAsString() + "_flavor_" + flavor);
        JsonParser.parseString(Files.readString(root.resolve("tools/extra-items.json"))).getAsJsonObject()
                .keySet().forEach(ids::add);
        assertEquals(ids, DrugItemMetadata.resourcePackTypes());
        assertEquals(1001, DrugItemMetadata.customModelData("heroin"));
        assertEquals(1050, DrugItemMetadata.customModelData("harvest_coffee"));
        assertEquals(1184, DrugItemMetadata.customModelData("processed_resin"));
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            assertEquals(1001 + i, DrugItemMetadata.customModelData(id));
            Path assets = root.resolve("assets/lumidrugs");
            var definition = JsonParser.parseString(Files.readString(assets.resolve("items/" + id + ".json"))).getAsJsonObject();
            assertEquals("lumidrugs:item/" + id, definition.getAsJsonObject("model").get("model").getAsString());
            var model = JsonParser.parseString(Files.readString(assets.resolve("models/item/" + id + ".json"))).getAsJsonObject();
            assertEquals("lumidrugs:item/" + id, model.getAsJsonObject("textures").get("layer0").getAsString());
            assertTrue(Files.size(assets.resolve("textures/item/" + id + ".png")) > 0);
        }
    }

    @Test void farmBoundItemsKeepTheirIdentityWhileSelectingSharedModel() throws Exception {
        var plugin = mock(com.drugs.DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        var instance = com.drugs.DrugsV2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, plugin);
        for (String type : java.util.List.of("farm_area_wand", "farm_work_contract")) {
            var meta = mock(ItemMeta.class);
            var data = mock(org.bukkit.persistence.PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            DrugItemMetadata.setItemType(meta, type + ":farm-123");
            verify(data).set(new NamespacedKey(plugin, "item_type"),
                    org.bukkit.persistence.PersistentDataType.STRING, type + ":farm-123");
            verify(meta).setItemModel(new NamespacedKey("lumidrugs", type));
            verify(meta).setCustomModelData(DrugItemMetadata.customModelData(type));
        }
    }

    @Test void everyDefaultFlavorHasModelsAndCustomFlavorsRetainBaseAppearance() {
        for (var flavor : FlavorVariants.defaults()) for (var product : flavor.products()) {
            var meta = mock(ItemMeta.class);
            DrugItemMetadata.applyFlavorModel(meta, product, flavor.id());
            verify(meta).setItemModel(new NamespacedKey("lumidrugs", product + "_flavor_" + flavor.id()));
            verify(meta).setCustomModelData(DrugItemMetadata.customModelData(product + "_flavor_" + flavor.id()));
        }
        var custom = mock(ItemMeta.class);
        DrugItemMetadata.applyFlavorModel(custom, "vape", "custom_server_flavor");
        verifyNoInteractions(custom);
    }
}
