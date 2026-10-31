# LumiDrugs resource pack

Targets Minecraft Java 1.21.9–1.21.10 (resource format 69), matching the plugin's Paper 1.21.10 dependency. Pack metadata follows the [Minecraft 1.21.9 format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9).

Install `LumiDrugs-resourcepack.zip` from the repository root in the client's `resourcepacks` directory, or host the ZIP for your server resource pack. Enable it alongside the updated plugin. No OptiFine is required.

The pack contains 50 `lumidrugs:` item definitions, generated models and textures: drugs, cures, bong, production ingredients, crop harvests and seeds. Items sharing a material (such as cocaine and nicotine) have separate models. Original joint, cart and bong artwork is retained. The original weed artwork is used only by the namespaced cannabis plant item; vanilla fern textures and names are untouched.

Newly created plugin items receive the matching `minecraft:item_model` component. Existing items keep their previous metadata; obtain fresh items through the normal commands, recipes or harvests to get the new models. The pack must be enabled to render these namespaced models. All crop blocks retain their vanilla appearance, including planted cannabis. A resource pack cannot distinguish a registered cannabis fern from an ordinary fern with the same block state.

To rebuild the assets and installable ZIP with Python 3 and Pillow:

```sh
python resourcepack/tools/build.py
```

`tools/items.json` lists the model keys and matches `RESOURCE_PACK_TYPES` in `DrugItemMetadata.java`. `tools/build.py` draws the simple 16×16 pixel icons, writes both model layers, and packages only `pack.mcmeta` and `assets/` into the ZIP. The original weed artwork is stored in `tools/artwork/` outside the distributed pack. The original three item textures are copied from `assets/minecraft/textures/item/`.
