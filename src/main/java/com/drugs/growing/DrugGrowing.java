package com.drugs.growing;

import com.drugs.DrugsV2;
import com.drugs.items.DrugItemMetadata;
import com.drugs.recipes.RecipeUpdates;
import com.drugs.strains.StrainConfigLoader;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Timed, persistent ingredient crops. All mutations run on the server thread. */
public final class DrugGrowing implements Listener {
    /** Mature crops always return planting stock, with a 10% chance of one extra. */
    static int harvestSeedCount() {
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble() < 0.10 ? 2 : 1;
    }
    public record Crop(String id, String name, Material ingredient, Material starter, int seconds) {}
    private static final Map<String, Material> plantBlocks = new HashMap<>();
    private static final Map<String, Crop> crops = new LinkedHashMap<>();
    private static final Map<String, String> requirements = Map.ofEntries(
        Map.entry("heroin", "poppy"), Map.entry("fent", "poppy"),
        Map.entry("shrooms", "mushroom"), Map.entry("meth", "ephedra"),
        Map.entry("cocaine", "coca"), Map.entry("molly", "sassafras"),
        Map.entry("lsd", "ergot"), Map.entry("salvia", "salvia"),
        Map.entry("glue", "resin"), Map.entry("joint", "cannabis"),
        Map.entry("blunt", "cannabis"), Map.entry("cart", "cannabis"),
        Map.entry("edible", "cannabis"), Map.entry("cbd", "cannabis"),
        Map.entry("tobacco", "tobacco"), Map.entry("nicotine", "tobacco"),
        Map.entry("cigarette", "tobacco"), Map.entry("cigar", "tobacco"),
        Map.entry("chewing_tobacco", "tobacco"),
        Map.entry("caffeine", "coffee"), Map.entry("energy_drink", "coffee"));
    private record Plant(String crop, int elapsed) {}
    private final Map<String, Plant> plants = new HashMap<>();
    private final DrugsV2 plugin;
    private final File file;

    public DrugGrowing(DrugsV2 plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "ingredient_plants.yml");
        if (!new File(plugin.getDataFolder(), "growing.yml").exists()) plugin.saveResource("growing.yml", false);
        reloadConfiguration();
        var saved = YamlConfiguration.loadConfiguration(file);
        for (String key : saved.getKeys(false)) {
            String crop = saved.getString(key + ".crop");
            if (crops.containsKey(crop)) plants.put(key, new Plant(crop, Math.max(0, saved.getInt(key + ".elapsed"))));
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::save, 1200L, 1200L);
    }

    public void reloadConfiguration() {
        var config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "growing.yml"));
        crops.clear();
        add("cannabis", "Cannabis Buds", Material.FERN, com.drugs.config.MechanicsConfig.getCannabisGrowthSeconds(), config);
        add("poppy", "Poppy Pods", Material.POPPY, 600, config);
        add("mushroom", "Cultivated Mushrooms", Material.RED_MUSHROOM, 480, config);
        add("ephedra", "Ephedra Leaves", Material.QUARTZ, 900, config);
        add("coca", "Coca Leaves", Material.VINE, 900, config);
        add("sassafras", "Sassafras Leaves", Material.MAGENTA_DYE, 900, config);
        add("ergot", "Ergot Culture", Material.SPORE_BLOSSOM, 1200, config);
        add("salvia", "Salvia Leaves", Material.LILAC, 600, config);
        add("resin", "Harvested Resin", Material.BONE_MEAL, 600, config);
        add("tobacco", "Tobacco Leaves", Material.DRIED_KELP, 600, config);
        add("coffee", "Coffee Beans", Material.COCOA_BEANS, 480, config);
        Map<String, Material> defaults = Map.ofEntries(
                Map.entry("poppy", Material.POPPY), Map.entry("mushroom", Material.RED_MUSHROOM),
                Map.entry("ephedra", Material.DEAD_BUSH), Map.entry("coca", Material.AZALEA),
                Map.entry("sassafras", Material.OAK_SAPLING), Map.entry("ergot", Material.WHEAT),
                Map.entry("salvia", Material.LILAC), Map.entry("resin", Material.SPRUCE_SAPLING),
                Map.entry("tobacco", Material.AZALEA), Map.entry("coffee", Material.OAK_SAPLING));
        plantBlocks.clear();
        for (String id : crops.keySet()) {
            if (id.equals("cannabis")) { plantBlocks.put(id, Material.FERN); continue; }
            Material chosen = Material.matchMaterial(config.getString(id + ".plant-block", defaults.get(id).name()));
            if (id.equals("salvia") && chosen == Material.FERN) chosen = Material.LILAC;
            if (chosen == null || !defaults.containsValue(chosen)) {
                plugin.getLogger().warning("Invalid plant-block for " + id + "; using " + defaults.get(id));
                chosen = defaults.get(id);
            }
            plantBlocks.put(id, chosen);
        }
        registerSeeds();
    }

    private static void add(String id, String name, Material ingredient, int seconds, YamlConfiguration config) {
        crops.put(id, new Crop(id, name, ingredient, ingredient, Math.max(8, config.getInt(id + ".growth-seconds", seconds))));
    }

    public static Crop crop(String id) { return crops.get(id); }

    public static int growthSeconds(String crop) {
        Crop definition = crops.get(crop);
        return definition != null ? definition.seconds() : com.drugs.config.MechanicsConfig.getCannabisGrowthSeconds();
    }

    public static Set<String> cropIds() { return Collections.unmodifiableSet(crops.keySet()); }
    public static String requirement(String drug) { return requirements.get(drug.toLowerCase(Locale.ROOT)); }
    public static Material material(String crop) { return "cannabis".equals(crop) ? Material.FERN : crops.get(crop).ingredient(); }
    public static boolean isHarvest(ItemStack item, String crop) {
        return item != null && item.getType() == material(crop) && item.hasItemMeta()
            && ("harvest_" + crop).equals(DrugItemMetadata.getItemType(item.getItemMeta()));
    }
    public static ItemStack harvest(String crop, String strain) {
        ItemStack item = new ItemStack(material(crop));
        ItemMeta meta = item.getItemMeta();
        DrugItemMetadata.setItemType(meta, "harvest_" + crop);
        meta.setDisplayName("§a" + ("cannabis".equals(crop) ? "Cannabis Buds" : crops.get(crop).name()));
        meta.setLore(List.of("§7Harvested ingredient — use in drug recipes."));
        if (strain != null) {
            DrugItemMetadata.setStrainId(meta, strain);
            DrugItemMetadata.applyStrainLore(meta, StrainConfigLoader.getStrain(strain));
        }
        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack seed(String crop) {
        if (crop.equals("cannabis")) return CannabisPlantListener.createStrainFern(DrugItemMetadata.DEFAULT_STRAIN_ID);
        ItemStack item = new ItemStack(Material.WHEAT_SEEDS);
        ItemMeta meta = item.getItemMeta();
        DrugItemMetadata.setItemType(meta, "seed_" + crop);
        meta.setDisplayName("§e" + crops.get(crop).name() + " Seeds");
        meta.setLore(List.of("§7Right click hydrated farmland to plant.",
            "§7Growth: " + crops.get(crop).seconds() + " seconds in loaded chunks.",
            "§7Needs water and light; bone meal has no effect.",
            "§7Right-click the plant empty-handed to check growth."));
        item.setItemMeta(meta);
        return item;
    }
    private void registerSeeds() {
        for (Crop crop : crops.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "seed_" + crop.id());
            var recipe = new ShapelessRecipe(key, seed(crop.id()));
            recipe.addIngredient(Material.WHEAT_SEEDS);
            recipe.addIngredient(crop.starter());
            RecipeUpdates.replace(key, recipe);
        }
    }
    private static String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
    static Material plantBlock(String crop) { return plantBlocks.getOrDefault(crop, Material.WHEAT); }

    private Block root(Block block) {
        if (block.getType() == Material.LARGE_FERN) block = CannabisPlantListener.getRootFernBlock(block);
        if (plants.containsKey(key(block))) return block;
        if (block.getType() == Material.LILAC
                && block.getBlockData() instanceof org.bukkit.block.data.Bisected half
                && half.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) return block.getRelative(0, -1, 0);
        return block;
    }

    static boolean placeModel(Block block, String crop) {
        Material model = plantBlock(crop);
        block.setType(model, false);
        if (model == Material.LILAC) {
            var flower = (org.bukkit.block.data.Bisected) block.getBlockData();
            flower.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
            block.setBlockData(flower, false);
        }
        return true;
    }

    private void clearModel(Block block) {
        // Clean up the upper half of plants placed before the single-block model.
        if (block.getType() == Material.LILAC
                && block.getBlockData() instanceof org.bukkit.block.data.Bisected half
                && half.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) {
            Block upper = block.getRelative(0, 1, 0);
            if (upper.getType() == Material.LILAC && !plants.containsKey(key(upper))) upper.setType(Material.AIR, false);
        }
        block.setType(Material.AIR, false);
    }

    public void trackCannabis(Block block) {
        plants.putIfAbsent(key(block), new Plant("cannabis", block.getType() == Material.LARGE_FERN ? crops.get("cannabis").seconds() : 0));
    }

    public void forget(Block block) { plants.remove(key(block)); }

    private boolean matchesPlant(Block block, Plant plant) {
        if (plant != null && plant.crop().equals("cannabis"))
            return block.getType() == Material.FERN || block.getType() == Material.LARGE_FERN;

        return plant != null && crops.containsKey(plant.crop())
                && (block.getType() == plantBlock(plant.crop()) || block.getType() == Material.WHEAT
                    || (plant.crop().equals("salvia") && block.getType() == Material.FERN));
    }

    @EventHandler(ignoreCancelled = true)
    public void inspect(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null
                || !event.getPlayer().getInventory().getItemInMainHand().isEmpty()) return;
        Block block = root(event.getClickedBlock());
        Plant plant = plants.get(key(block));
        if (!matchesPlant(block, plant)) return;
        event.setCancelled(true);
        Crop crop = crops.get(plant.crop());
        int percent = Math.min(100, plant.elapsed() * 100 / crop.seconds());
        int filled = percent / 10;
        String status;
        if (plant.elapsed() >= crop.seconds()) status = "§aReady to harvest";
        else if (!(block.getRelative(0, -1, 0).getBlockData() instanceof org.bukkit.block.data.type.Farmland soil)
                || soil.getMoisture() == 0) status = "§ePaused: needs hydrated farmland";
        else if (plant.crop().equals("cannabis") && block.getType() == Material.FERN && !block.getRelative(0, 1, 0).isEmpty()) status = "§ePaused: needs space above";
        else if (block.getLightLevel() < 9) status = "§ePaused: needs light level 9+";
        else status = "§7" + (crop.seconds() - plant.elapsed()) + "s of active growth left";
        event.getPlayer().sendActionBar("§a" + crop.name() + " §8[§a" + "|".repeat(filled)
                + "§7" + "|".repeat(10 - filled) + "§8] §f" + percent + "% §8— " + status);
    }

    public boolean isMature(Block block) {
        block = root(block);
        Plant plant = plants.get(key(block));
        return matchesPlant(block, plant)
                && (!plant.crop().equals("cannabis") || block.getType() == Material.LARGE_FERN)
                && plant.elapsed() >= crops.get(plant.crop()).seconds();
    }

    /** Harvest only tracked, mature ingredient crops; untracked plants are never touched. */
    public List<ItemStack> harvestMature(Block block) {
        block = root(block);
        Plant plant = plants.get(key(block));
        if (!isMature(block)) return List.of();
        if (plant.crop().equals("cannabis")) {
            var result = CannabisPlantListener.harvestCannabis(block, true);
            if (result == null) return List.of();
            forget(block);
            save();
            return result.getDrops();
        }
        plants.remove(key(block));
        clearModel(block);
        save();
        return List.of(seed(plant.crop()).asQuantity(harvestSeedCount()), harvest(plant.crop(), null).asQuantity(2));
    }

    public boolean plantSeed(Block block, ItemStack seed) {
        if (seed.getType() == Material.FERN && block.isEmpty() && CannabisPlantListener.hasHydratedFarmland(block)) {
            block.setType(Material.FERN, false);
            if (!CannabisPlantListener.applyPlacedCannabis(block, seed)) { block.setType(Material.AIR, false); return false; }
            trackCannabis(block);
            save();
            return true;
        }
        String type = seed.hasItemMeta() ? DrugItemMetadata.getItemType(seed.getItemMeta()) : null;
        if (type == null || !type.startsWith("seed_") || !crops.containsKey(type.substring(5))
                || !block.isEmpty()
                || !(block.getRelative(0, -1, 0).getBlockData() instanceof org.bukkit.block.data.type.Farmland soil)
                || soil.getMoisture() == 0) return false;
        if (!placeModel(block, type.substring(5))) return false;
        plants.put(key(block), new Plant(type.substring(5), 0));
        save();
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void plant(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getItem() == null) return;
        String type = DrugItemMetadata.getItemType(event.getItem().getItemMeta());
        if (type == null || !type.startsWith("seed_") || !crops.containsKey(type.substring(5))) return;
        event.setCancelled(true);
        Block soil = event.getClickedBlock();
        Block block = soil.getRelative(0, 1, 0);
        if (event.getBlockFace() != org.bukkit.block.BlockFace.UP || soil.getType() != Material.FARMLAND || !block.getType().isAir()) return;
        if (((org.bukkit.block.data.type.Farmland) soil.getBlockData()).getMoisture() == 0) {
            event.getPlayer().sendMessage("§eWater the farmland before planting.");
            return;
        }
        // Respect protection plugins that guard block placement rather than interaction.
        var replaced = block.getState();
        if (!placeModel(block, type.substring(5))) return;
        var place = new BlockPlaceEvent(block, replaced, soil, event.getItem(), event.getPlayer(), true, event.getHand());
        Bukkit.getPluginManager().callEvent(place);
        if (place.isCancelled() || !place.canBuild()) {
            replaced.update(true, false);
            return;
        }
        plants.put(key(block), new Plant(type.substring(5), 0));
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) event.getItem().setAmount(event.getItem().getAmount() - 1);
        save();
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void harvest(BlockBreakEvent event) {
        Block root = root(event.getBlock());
        Plant plant = plants.get(key(root));
        if (plant != null && plant.crop().equals("cannabis")) return;
        plants.remove(key(root));
        if (plant == null) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (matchesPlant(root, plant) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            Block block = root;
            boolean mature = plant.elapsed() >= crops.get(plant.crop()).seconds();
            block.getWorld().dropItemNaturally(block.getLocation(), seed(plant.crop()).asQuantity(mature ? harvestSeedCount() : 1));
            if (mature)
                block.getWorld().dropItemNaturally(block.getLocation(), harvest(plant.crop(), null).asQuantity(2));
            else event.getPlayer().sendMessage("§eThis crop was not mature. Only its seed was recovered.");
        }
        clearModel(root);
        save();
    }
    @EventHandler(ignoreCancelled = true)
    public void preventVanillaFarmerHarvest(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Villager)) return;
        // Vanilla harvesting or trampling would destroy
        // the identity of a tracked drug ingredient.
        Block block = event.getBlock();
        if (plants.containsKey(key(block))
                || (block.getType() == Material.FARMLAND && plants.containsKey(key(block.getRelative(0, 1, 0))))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void physics(BlockPhysicsEvent event) {
        if (plants.containsKey(key(root(event.getBlock())))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void soilFade(BlockFadeEvent event) {
        // Some decorative plant blocks would otherwise cause their farmland to
        // revert to dirt. Preserve the common soil rule for tracked crops.
        if (event.getBlock().getType() == Material.FARMLAND
                && plants.containsKey(key(event.getBlock().getRelative(0, 1, 0)))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void spread(BlockSpreadEvent event) {
        if (plants.containsKey(key(event.getSource()))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void treeGrowth(org.bukkit.event.world.StructureGrowEvent event) {
        if (plants.containsKey(key(event.getLocation().getBlock()))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void naturalGrowth(BlockGrowEvent event) {
        if (plants.containsKey(key(root(event.getBlock())))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void fertilize(BlockFertilizeEvent event) {
        if (plants.containsKey(key(root(event.getBlock())))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void replaced(BlockPlaceEvent event) {
        plants.remove(key(event.getBlockPlaced()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(org.bukkit.event.entity.EntityExplodeEvent event) {
        for (Block block : event.blockList()) plants.remove(key(block));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void exploded(BlockExplodeEvent event) {
        for (Block block : event.blockList()) plants.remove(key(block));
    }
    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> plants.containsKey(key(block)))) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void piston(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> plants.containsKey(key(block)))) event.setCancelled(true);
    }
    private void tick() {
        // Import legacy strain-only saves, including plants in chunks loaded later.
        for (var location : CannabisPlantRegistry.getPlantsSnapshot().keySet()) {
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            Block block = location.getBlock();
            if (block.getType() == Material.FERN || block.getType() == Material.LARGE_FERN) trackCannabis(block);
        }
        var iterator = plants.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            String[] parts = entry.getKey().split(":");
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block block = world.getBlockAt(x, y, z);
            Plant plant = entry.getValue();
            if (!matchesPlant(block, plant)) { iterator.remove(); continue; }
            if (block.getType() == Material.LILAC
                    && block.getBlockData() instanceof org.bukkit.block.data.Bisected half
                    && half.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) {
                Block upper = block.getRelative(0, 1, 0);
                if (upper.getType() == Material.LILAC && !plants.containsKey(key(upper))
                        && upper.getBlockData() instanceof org.bukkit.block.data.Bisected upperHalf
                        && upperHalf.getHalf() == org.bukkit.block.data.Bisected.Half.TOP) upper.setType(Material.AIR, false);
                placeModel(block, plant.crop());
            }
            if (!plant.crop().equals("cannabis") && block.getType() != plantBlock(plant.crop()) && !placeModel(block, plant.crop())) continue;
            Block soil = block.getRelative(0, -1, 0);
            if (!(soil.getBlockData() instanceof org.bukkit.block.data.type.Farmland farmland) || farmland.getMoisture() == 0 || block.getLightLevel() < 9) continue;
            if (plant.crop().equals("cannabis") && block.getType() == Material.FERN
                    && !block.getRelative(0, 1, 0).isEmpty()) continue;
            int seconds = crops.get(plant.crop()).seconds();
            int elapsed = Math.min(seconds, plant.elapsed() + 1);
            entry.setValue(new Plant(plant.crop(), elapsed));
            if (plant.crop().equals("cannabis") && elapsed >= seconds && block.getType() == Material.FERN)
                CannabisPlantListener.growToLargeFern(block, block.getRelative(0, 1, 0));
            if (block.getBlockData() instanceof Ageable age) {
                int stage = elapsed * age.getMaximumAge() / seconds;
                if (stage != age.getAge()) { age.setAge(stage); block.setBlockData(age, false); }
            }
        }
    }
    public void save() {
        var yaml = new YamlConfiguration();
        plants.forEach((key, plant) -> { yaml.set(key + ".crop", plant.crop()); yaml.set(key + ".elapsed", plant.elapsed()); });
        try { yaml.save(file); } catch (IOException e) { plugin.getLogger().warning("Cannot save ingredient crops: " + e.getMessage()); }
    }
}
