# LumiDrugs resource pack

Targets Minecraft Java 1.21.9–1.21.10 (resource format 69), matching the plugin's Paper 1.21.10 dependency. Pack metadata follows the [Minecraft 1.21.9 format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9).

Install `LumiDrugs-resourcepack.zip` from the repository root in the client's `resourcepacks` directory, or host the ZIP for your server resource pack. Enable it alongside the updated plugin. No OptiFine is required.

The pack contains 50 `lumidrugs:` item definitions, generated models and textures: drugs, cures, bong, production ingredients, crop harvests and seeds. Items sharing a material (such as cocaine and nicotine) have separate models and `custom_model_data` IDs (`1001`–`1050`). Original joint, cart, bong and cannabis plant artwork is retained in `tools/artwork/` and applied via item models and `custom_model_data` without replacing any vanilla textures.

Newly created plugin items receive both the matching `minecraft:custom_model_data` (`1001`–`1050`) and `minecraft:item_model` components. Existing items keep their previous metadata; obtain fresh items through the normal commands, recipes or harvests to get the new models. The pack must be enabled to render these custom models. All crop blocks and vanilla items retain their vanilla appearance.

To rebuild the assets and installable ZIP with Python 3 and Pillow:

```sh
python resourcepack/tools/build.py
```

`tools/items.json` lists the model keys and matches `RESOURCE_PACK_TYPES` in `DrugItemMetadata.java` (with `custom_model_data` `1001 + index`). `tools/build.py` draws the simple 16×16 pixel icons, writes the `lumidrugs:` item/model layers and `minecraft:custom_model_data` range-dispatch item definitions, and packages only `pack.mcmeta` and `assets/` into the ZIP. All source artwork is stored in `tools/artwork/` outside the distributed pack, and no vanilla textures under `assets/minecraft/textures/` are replaced.
