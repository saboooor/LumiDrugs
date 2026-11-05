package com.drugs.farm;

import com.drugs.ui.DialogView;

import com.drugs.achievements.AchievementManager;
import com.drugs.items.DrugItemMetadata;
import com.drugs.DrugsV2;
import com.drugs.ui.Dialogs;
import com.drugs.ui.DialogView.Icon;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;

public class FarmControllerListener implements Listener {
    private final Map<UUID, String> selectingChest = new HashMap<>();
    private final Map<UUID, String> openControllers = new HashMap<>();

    private final FarmManager manager;
    private final Map<Player, Location> pos1 = new HashMap<>();
    private final Map<Player, Location> pos2 = new HashMap<>();

    public FarmControllerListener(FarmManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWorkerPlant(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || manager.getByVillager(villager.getUniqueId()) == null) return;
        // Controller planting uses the tracked drug-crop API directly. Suppress
        // vanilla farmer AI sowing ordinary seeds from the villager's inventory.
        switch (event.getTo()) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, TORCHFLOWER_CROP, PITCHER_CROP -> event.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onControllerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        String type = DrugItemMetadata.getItemType(item.getItemMeta());
        if (!FarmItems.ITEM_TYPE_CONTROLLER.equalsIgnoreCase(type)) {
            return;
        }

        Farm farm;
        try {
            farm = FarmItems.savedFarm(item, event.getBlockPlaced().getLocation());
        } catch (IllegalArgumentException exception) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "This controller has invalid saved farm data.");
            return;
        }
        if (farm == null) farm = manager.createFarm(event.getBlockPlaced().getLocation());
        else if (!manager.restoreFarm(farm)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "This farm controller is already placed.");
            return;
        }
        manager.save();
        event.getPlayer().sendMessage(ChatColor.GREEN + "Farm controller created (ID " + farm.getFarmId() + ").");
        AchievementManager.processTrigger(event.getPlayer(), "place_farm_controller");
    }

    @EventHandler(ignoreCancelled = true)
    public void onControllerBreak(BlockBreakEvent event) {
        Farm farm = manager.getByController(event.getBlock().getLocation());
        if (farm == null) {
            return;
        }

        ItemStack saved = FarmItems.createControllerItem(farm);
        manager.packFarm(farm);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), saved);
        event.setDropItems(false);
        event.getPlayer().sendMessage(ChatColor.RED + "Farm controller packed. Place it again to restore its area and workers.");
    }

    @EventHandler
    public void onVillagerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            manager.unassignVillager(villager.getUniqueId());
        }
    }

    @EventHandler
    public void onInteractController(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || FarmItems.parseFarmId(event.getItem(), FarmItems.ITEM_TYPE_AREA_WAND) != null) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || (event.getClickedBlock().getType() != Material.COMPOSTER && event.getClickedBlock().getType() != Material.BARREL)) {
            return;
        }

        Farm farm = manager.getByController(event.getClickedBlock().getLocation());
        if (farm == null) {
            return;
        }

        manager.pruneInvalidVillagers(farm);
        event.setCancelled(true);
        openControllerDialog(event.getPlayer(), farm);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refundInvalidContracts(event.getPlayer());
    }

    public void refundInvalidContracts(Player player) {
        int refunded = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            String farmId = FarmItems.parseFarmId(stack, FarmItems.ITEM_TYPE_CONTRACT);
            if (farmId == null || manager.getByFarmId(farmId) != null) continue;
            refunded += stack.getAmount();
            inventory.setItem(slot, null);
        }
        if (refunded == 0) return;
        while (refunded > 0) {
            int amount = Math.min(64, refunded);
            for (ItemStack leftover : inventory.addItem(new ItemStack(Material.EMERALD, amount)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            refunded -= amount;
        }
        player.sendMessage(ChatColor.GREEN + "Removed contracts for missing farms and refunded 1 emerald per contract.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selectingChest.remove(event.getPlayer().getUniqueId());
        openControllers.remove(event.getPlayer().getUniqueId());
    }

    public void handleDialogAction(Player player, String action) {
        String farmId = openControllers.remove(player.getUniqueId());
        if (farmId == null || action.equals("close")) return;
        Farm farm = manager.getByFarmId(farmId);
        if (farm == null) return;
        Location controller = farm.getControllerLocation();
        if (manager.getByController(controller) != farm) return;
        if (!player.getWorld().equals(controller.getWorld())
                || player.getLocation().distanceSquared(controller) > 64) {
            player.sendMessage(ChatColor.RED + "Move closer to the farm controller and open it again.");
            return;
        }
        manager.pruneInvalidVillagers(farm);
        switch (action) {
            case "area" -> {
                player.getInventory().addItem(FarmItems.createAreaWand(farmId));
                player.sendMessage(ChatColor.AQUA + "Use the arrow wand to select two corners (max 16x16).");
            }
            case "toggle" -> {
                farm.setEnabled(!farm.isEnabled());
                player.sendMessage(farm.isEnabled() ? ChatColor.GREEN + "Farm started." : ChatColor.RED + "Farm stopped.");
                openControllerDialog(player, farm);
            }
            case "contract" -> {
                if (!consumeEmerald(player)) {
                    player.sendMessage(ChatColor.RED + "You need 1 emerald for a work contract.");
                    openControllerDialog(player, farm);
                    return;
                }
                player.getInventory().addItem(FarmItems.createContract(farmId));
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Work contract created. Hold it and right-click a villager to assign them.");
            }
            case "chest" -> {
                selectingChest.put(player.getUniqueId(), farmId);
                player.sendMessage(ChatColor.AQUA + "Right-click a chest to set this farm's output chest.");
            }
            case "toggle-overflow" -> {
                farm.setDropOverflowItems(!farm.shouldDropOverflowItems());
                manager.save();
                openControllerDialog(player, farm);
            }
            case "clear-chest" -> {
                selectingChest.remove(player.getUniqueId());
                farm.setOutputChest(null);
                manager.save();
                openControllerDialog(player, farm);
            }
            case "info" -> openControllerDialog(player, farm);
            default -> { }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSelectChest(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null) return;
        String farmId = selectingChest.get(event.getPlayer().getUniqueId());
        if (farmId == null) return;
        Farm farm = manager.getByFarmId(farmId);
        if (farm == null || manager.getByController(farm.getControllerLocation()) != farm) {
            selectingChest.remove(event.getPlayer().getUniqueId());
            return;
        }
        if (!(event.getClickedBlock().getState() instanceof org.bukkit.block.Chest)) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Right-click a chest to link it to the farm.");
            return;
        }
        if (!event.getClickedBlock().getWorld().equals(farm.getControllerLocation().getWorld())) {
            event.getPlayer().sendMessage(ChatColor.RED + "Choose a chest in the same world as the controller.");
            return;
        }
        event.setCancelled(true);
        farm.setOutputChest(event.getClickedBlock().getLocation());
        selectingChest.remove(event.getPlayer().getUniqueId());
        manager.save();
        event.getPlayer().sendMessage(ChatColor.GREEN + "Farm output chest set.");
    }

    @EventHandler
    public void onWandInteract(PlayerInteractEvent event) {
        ItemStack inHand = event.getItem();
        if (inHand == null || !inHand.hasItemMeta()) {
            return;
        }

        String areaFarmId = FarmItems.parseFarmId(inHand, FarmItems.ITEM_TYPE_AREA_WAND);
        if (areaFarmId == null || event.getClickedBlock() == null) {
            return;
        }

        event.setCancelled(true);
        Farm farm = manager.getByFarmId(areaFarmId);
        if (farm == null) return;

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            pos1.put(event.getPlayer(), event.getClickedBlock().getLocation());
            event.getPlayer().sendActionBar(ChatColor.AQUA + "Pos1: " + format(event.getClickedBlock().getLocation()));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            pos2.put(event.getPlayer(), event.getClickedBlock().getLocation());
            event.getPlayer().sendActionBar(ChatColor.AQUA + "Pos2: " + format(event.getClickedBlock().getLocation()));
        }

        if (pos1.containsKey(event.getPlayer()) && pos2.containsKey(event.getPlayer())) {
            Location first = pos1.remove(event.getPlayer());
            Location second = pos2.remove(event.getPlayer());
            if (first != null && second != null && first.getWorld() != null && first.getWorld().equals(second.getWorld())) {
                if (!farm.canApplyRegion(first, second)) {
                    event.getPlayer().sendMessage(ChatColor.RED + "Farm area too large. Max size is 16x16 blocks.");
                    return;
                }
                farm.setRegion(first.getWorld(), first, second);
                consumeAreaWand(event.getPlayer(), areaFarmId);
                event.getPlayer().sendMessage(ChatColor.GREEN + "Farm area updated.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVillagerContract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Villager villager)) {
            return;
        }

        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
        String farmId = FarmItems.parseFarmId(inHand, FarmItems.ITEM_TYPE_CONTRACT);
        if (farmId == null) {
            return;
        }
        event.setCancelled(true);

        Farm farm = manager.getByFarmId(farmId);
        if (farm == null) {
            refundInvalidContracts(event.getPlayer());
            return;
        }

        if (farm.getAssignedVillagers().contains(villager.getUniqueId())) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "This villager already works at this farm.");
            return;
        }
        manager.pruneInvalidVillagers(farm);
        if (!manager.assignVillager(farm, villager.getUniqueId())) {
            event.getPlayer().sendMessage(ChatColor.RED + "This farm already has the max of 5 villagers.");
            return;
        }

        decrementHeld(event.getPlayer());
        event.getPlayer().sendMessage(ChatColor.LIGHT_PURPLE + "Villager assigned to farm " + farm.getFarmId() + ".");
        AchievementManager.processTrigger(event.getPlayer(), "assign_farm_worker");
        event.setCancelled(true);
    }

    private void consumeAreaWand(Player player, String farmId) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (farmId.equals(FarmItems.parseFarmId(stack, FarmItems.ITEM_TYPE_AREA_WAND))) {
                player.getInventory().setItem(i, null);
                return;
            }
        }
    }

    private void openControllerDialog(Player player, Farm farm) {
        openControllers.put(player.getUniqueId(), farm.getFarmId());
        DrugsV2.getInstance().showDialog(player, Dialogs.menu(
                Dialogs.base("Farm Controller " + farm.getFarmId(), farmInfo(farm)),
                List.of(
                        Dialogs.button("Define Farm Area", "/drugs farm area", false, Icon.ARROW, "Select two corners with a wand (max 16x16)."),
                        Dialogs.button(farm.isEnabled() ? "Stop Farm" : "Start Farm", "/drugs farm toggle", false, Icon.REDSTONE),
                        Dialogs.button("Get Work Contract (1 emerald)", "/drugs farm contract", false, Icon.SHARED, "Assign a villager to this farm. Maximum 5 workers."),
                        Dialogs.button("Set Output Chest", "/drugs farm chest", false, Icon.RETRIEVE, "Then right-click a chest to store harvested crops and unused seeds."),
                        Dialogs.button("Clear Output Chest", "/drugs farm clear-chest", false, Icon.CLOSE, "Drop farm output at the controller again."),
                        Dialogs.button("Drop Chest Overflow: " + (farm.shouldDropOverflowItems() ? "ON" : "OFF"), "/drugs farm toggle-overflow", false, Icon.RETRIEVE, "Toggle dropping items that do not fit in the chest. OFF discards overflow."),
                        Dialogs.button("Refresh Farm Info", "/drugs farm info", false, Icon.BOOK)),
                Dialogs.button("Close", "/drugs farm close", false, Icon.CLOSE)));
    }

    private String format(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private boolean consumeEmerald(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == Material.EMERALD && stack.getAmount() > 0) {
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    player.getInventory().setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private void decrementHeld(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private String farmInfo(Farm farm) {
        int x = farm.hasRegion() ? (farm.getMaxX() - farm.getMinX() + 1) : 0;
        int z = farm.hasRegion() ? (farm.getMaxZ() - farm.getMinZ() + 1) : 0;
        return "Farm " + farm.getFarmId() + " | "
                + (farm.isEnabled() ? "Enabled" : "Disabled")
                + " | Villagers: " + farm.getAssignedVillagers().size() + "/" + Farm.MAX_WORKERS
                + " | Area: " + x + "x" + z
                + " | Output: " + (farm.getOutputChest() == null ? "Controller drops" : "Chest at " + format(farm.getOutputChest()));
    }
}
