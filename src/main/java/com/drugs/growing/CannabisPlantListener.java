package com.drugs.growing;

import com.drugs.DrugsV2;
import com.drugs.items.DrugItemMetadata;
import com.drugs.strains.StrainConfigLoader;
import com.drugs.strains.StrainProfile;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles placing and harvesting of strain-tagged fern plants.
 */
public class CannabisPlantListener implements Listener {

    private final DrugGrowing growing;

    public CannabisPlantListener(DrugGrowing growing) { this.growing = growing; }

    public static boolean hasHydratedFarmland(Block plant) {
        return plant.getRelative(0, -1, 0).getBlockData() instanceof org.bukkit.block.data.type.Farmland soil
                && soil.getMoisture() > 0;
    }

    static boolean isCannabisItem(ItemStack item) {
        if (item == null || item.getType() != Material.FERN) return false;
        String type = item.hasItemMeta() ? DrugItemMetadata.getItemType(item.getItemMeta()) : null;
        return "cannabis_plant".equalsIgnoreCase(type);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void validateSoil(BlockPlaceEvent event) {
        if (isCannabisItem(event.getItemInHand()) && !hasHydratedFarmland(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§ePlant cannabis on hydrated farmland.");
        }
    }

    // Ferns need an explicit planting interaction to work reliably on farmland.
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void plantOnFarmland(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || !isCannabisItem(event.getItem())) return;
        Block soil = event.getClickedBlock();
        if (soil.getType() != Material.FARMLAND || event.getBlockFace() != org.bukkit.block.BlockFace.UP) return;
        event.setCancelled(true);
        Block plant = soil.getRelative(0, 1, 0);
        if (!plant.isEmpty()) return;
        if (!hasHydratedFarmland(plant)) {
            event.getPlayer().sendMessage("§eWater the farmland before planting cannabis.");
            return;
        }
        var replaced = plant.getState();
        plant.setType(Material.FERN, false);
        var place = new BlockPlaceEvent(plant, replaced, soil, event.getItem(), event.getPlayer(), true, event.getHand());
        org.bukkit.Bukkit.getPluginManager().callEvent(place);
        if (place.isCancelled() || !place.canBuild()) {
            CannabisPlantRegistry.removePlant(plant.getLocation());
            growing.forget(plant);
            replaced.update(true, false);
            return;
        }
        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE)
            event.getItem().setAmount(event.getItem().getAmount() - 1);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getItemInHand().getType() == Material.FERN
                && applyPlacedCannabis(event.getBlockPlaced(), event.getItemInHand())) growing.trackCannabis(event.getBlockPlaced());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        HarvestResult result = harvestCannabis(event.getBlock(), event.getPlayer() != null
                && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.SHEARS);
        if (result == null) {
            return;
        }

        growing.forget(result.getRootBlock());
        event.setDropItems(false);
        event.setExpToDrop(0);

        for (ItemStack drop : result.getDrops()) {
            Item droppedItem = result.getRootBlock().getWorld().dropItemNaturally(result.getRootBlock().getLocation().add(0.5, 0.2, 0.5), drop);
            droppedItem.setPickupDelay(10);
        }
    }

    @EventHandler
    public void onFernBonemeal(BlockFertilizeEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.FERN) return;

        String parentStrain = CannabisPlantRegistry.getPlant(block.getLocation());
        if (parentStrain == null) return;

        // Cannabis must grow over time; bone meal cannot skip maturity.
        event.setCancelled(true);

        // Keep strain anchored at bottom block.
        CannabisPlantRegistry.setPlant(block.getLocation(), parentStrain);
    }

    public static boolean applyPlacedCannabis(Block placedBlock, ItemStack item) {
        if (placedBlock == null || !isCannabisItem(item)) {
            return false;
        }

        if (!hasHydratedFarmland(placedBlock)) return false;

        String strainId = DrugItemMetadata.DEFAULT_STRAIN_ID;
        if (item.hasItemMeta()) {
            strainId = DrugItemMetadata.getStrainId(item);
            if (strainId == null) {
                strainId = DrugItemMetadata.DEFAULT_STRAIN_ID;
            }
        }

        CannabisPlantRegistry.setPlant(placedBlock.getLocation(), strainId);
        return true;
    }

    public static HarvestResult harvestCannabis(Block block, boolean usingShears) {
        if (block == null || (block.getType() != Material.FERN && block.getType() != Material.LARGE_FERN)) {
            return null;
        }

        Block rootBlock = getRootFernBlock(block);
        String parentStrain = CannabisPlantRegistry.getPlant(rootBlock.getLocation());
        if (parentStrain == null) {
            return null;
        }

        List<ItemStack> drops = new ArrayList<>();
        if (usingShears) {
            String childStrain = rootBlock.getType() == Material.LARGE_FERN
                    ? StrainConfigLoader.rollChildStrain(parentStrain) : parentStrain;
            drops.add(createStrainFern(childStrain).asQuantity(
                    rootBlock.getType() == Material.LARGE_FERN ? DrugGrowing.harvestSeedCount() : 1));
        }

        if (rootBlock.getType() == Material.LARGE_FERN && usingShears) {
            drops.add(DrugGrowing.harvest("cannabis", parentStrain).asQuantity(2));
        }
        clearFernPlant(rootBlock);
        CannabisPlantRegistry.removePlant(rootBlock.getLocation());
        return new HarvestResult(rootBlock, drops);
    }

    static void growToLargeFern(Block root, Block top) {
        root.setType(Material.LARGE_FERN, false);
        top.setType(Material.LARGE_FERN, false);

        BlockData lowerData = root.getBlockData();
        if (lowerData instanceof Bisected bisectedLower) {
            bisectedLower.setHalf(Bisected.Half.BOTTOM);
            root.setBlockData(bisectedLower, false);
        }

        BlockData upperData = top.getBlockData();
        if (upperData instanceof Bisected bisectedUpper) {
            bisectedUpper.setHalf(Bisected.Half.TOP);
            top.setBlockData(bisectedUpper, false);
        }
    }

    public static Block getRootFernBlock(Block block) {
        if (block.getType() == Material.LARGE_FERN && block.getRelative(0, -1, 0).getType() == Material.LARGE_FERN) {
            return block.getRelative(0, -1, 0);
        }
        return block;
    }

    public static void clearFernPlant(Block rootBlock) {
        if (rootBlock.getType() == Material.FERN) {
            rootBlock.setType(Material.AIR);
            return;
        }

        if (rootBlock.getType() == Material.LARGE_FERN) {
            Block top = rootBlock.getRelative(0, 1, 0);
            if (top.getType() == Material.LARGE_FERN) {
                top.setType(Material.AIR);
            }
            rootBlock.setType(Material.AIR);
        }
    }

    public static ItemStack createStrainFern(String strainId) {
        StrainProfile profile = StrainConfigLoader.getStrain(strainId);
        String resolved = profile != null ? profile.getId() : DrugItemMetadata.DEFAULT_STRAIN_ID;

        ItemStack item = new ItemStack(Material.FERN, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            DrugItemMetadata.setItemType(meta, "cannabis_plant");
            DrugItemMetadata.setStrainId(meta, resolved);
            meta.setDisplayName("§2Weed §7(§f" + (profile != null ? profile.getDisplayName() : resolved) + "§7)");
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7Plant on hydrated farmland; needs light to grow.");
            lore.add("§7Rarity: §e" + (profile != null ? profile.getRarity() : "common"));
            lore.add("§7Mutation Chance: §f" + String.format("%.3f%%", (profile != null ? profile.getMutationChance() : 0.0005) * 100.0));
            if (profile != null && DrugItemMetadata.DEFAULT_STRAIN_ID.equalsIgnoreCase(profile.getId())) {
                lore.add("§7Effects: §8Unknown");
            } else if (profile != null) {
                lore.add("§7Effects:");
                for (org.bukkit.potion.PotionEffect effect : profile.getEffects()) {
                    lore.add("§7 - §d" + effect.getType().getName() + " §f" + (effect.getAmplifier() + 1) + " §7(" + Math.max(1, effect.getDuration() / 20) + "s)");
                }
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static final class HarvestResult {
        private final Block rootBlock;
        private final List<ItemStack> drops;

        public HarvestResult(Block rootBlock, List<ItemStack> drops) {
            this.rootBlock = rootBlock;
            this.drops = drops;
        }

        public Block getRootBlock() {
            return rootBlock;
        }

        public List<ItemStack> getDrops() {
            return drops;
        }
    }
}
