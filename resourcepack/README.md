# LumiDrugs resource pack

Declares support for Minecraft Java 1.21.9–26.3 (resource formats 69–97.1). Live client rendering still needs verification. Pack metadata follows the [Minecraft 1.21.9 format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9).

Install `LumiDrugs-resourcepack.zip` from the repository root in the client's `resourcepacks` directory, or host the ZIP for your server resource pack. Enable it alongside the updated plugin. No OptiFine is required.

The pack contains 183 `lumidrugs:` item definitions, generated models and textures: drugs, cures, bong, production ingredients, crop harvests and seeds. Items sharing a material (such as cocaine and nicotine) have separate models and `custom_model_data` IDs (`1001`–`1183`). Original joint, cart, bong and cannabis plant artwork is retained in `tools/artwork/` and applied via item models and `custom_model_data` without replacing any vanilla textures.

Newly created plugin items receive both the matching `minecraft:custom_model_data` (`1001`–`1183`) and `minecraft:item_model` components. Existing items keep their previous metadata; obtain fresh items through the normal commands, recipes or harvests to get the new models. The pack must be enabled to render these custom models. All crop blocks and vanilla items retain their vanilla appearance.

To rebuild the assets and installable ZIP with Python 3 and Pillow:

```sh
python resourcepack/tools/build.py
```

`tools/items.json` lists the original model keys and matches `RESOURCE_PACK_TYPES` in `DrugItemMetadata.java` (with `custom_model_data` `1001 + index`). `tools/build.py` draws the simple 16×16 pixel icons, writes the `lumidrugs:` item/model layers and `minecraft:custom_model_data` range-dispatch item definitions, and packages only `pack.mcmeta` and `assets/` into the ZIP. All source artwork is stored in `tools/artwork/` outside the distributed pack, and no vanilla textures under `assets/minecraft/textures/` are replaced.

## Flavored items

All 13 built-in flavors have 32×32 pixel textures for vapes, carts, edibles, joints, blunts, energy drinks, tobacco, chewing tobacco, cigars, and cigarettes (130 variants). Each keeps its product silhouette and adds a flavor color plus an ingredient seal. Models are included for every combination; `mechanics.yml` controls which combinations can actually be infused. Custom flavor IDs retain their base texture until a matching model is added to the catalog.

The original 50 model IDs remain unchanged. Flavored models append IDs 1051–1180; both item-model components and custom-model-data dispatch select them. Install the updated plugin and pack together, then infuse unflavored items to receive these models. Existing flavored items keep their old appearance.

`tools/flavors.json` defines the product/flavor ordering and palette, matching the appended catalog in `DrugItemMetadata.java`. `tools/flavor_icons.py` draws the variant sprites. Rebuilding also creates `tools/flavor-preview.png`, a labeled contact sheet excluded from the distributed ZIP.

Farm Controller, Farm Area Wand, and Work Contract have their own textures (IDs 1181–1183). Farm-bound tool metadata remains unchanged. `tools/extra-items.json` lists these items after the flavor catalog.
