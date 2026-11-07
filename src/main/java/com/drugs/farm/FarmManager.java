package com.drugs.farm;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FarmManager {
    private final Map<String, Farm> farmsById = new ConcurrentHashMap<>();
    private final Map<String, String> farmByController = new ConcurrentHashMap<>();
    private final Map<UUID, String> farmByVillager = new ConcurrentHashMap<>();
    private final java.util.Set<String> packed = ConcurrentHashMap.newKeySet();
    private final File file;

    public FarmManager(File dataFolder) {
        this.file = new File(dataFolder, "farms.yml");
    }

    public Farm createFarm(Location controller) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Farm farm = new Farm(id, controller);
        farmsById.put(id, farm);
        farmByController.put(locKey(controller), id);
        return farm;
    }

    public void packFarm(Farm farm) {
        farmByController.remove(locKey(farm.getControllerLocation()));
        packed.add(farm.getFarmId());
        save();
    }

    public boolean restoreFarm(Farm farm) {
        String id = farm.getFarmId();
        if (farmsById.containsKey(id) && !packed.contains(id)) return false;
        Farm old = farmsById.get(id);
        if (old != null) {
            for (UUID worker : old.getAssignedVillagers()) farmByVillager.remove(worker, id);
            // Assignments may have changed while the controller was packed.
            farm.getAssignedVillagers().retainAll(old.getAssignedVillagers());
        }
        farm.getAssignedVillagers().removeIf(worker -> farmByVillager.containsKey(worker)
                && !id.equals(farmByVillager.get(worker)));
        // Placement deliberately drops links to workers that cannot currently be found.
        farm.getAssignedVillagers().removeIf(worker -> {
            Entity entity = Bukkit.getEntity(worker);
            return !(entity instanceof Villager villager) || !villager.isValid() || villager.isDead();
        });
        farmsById.put(id, farm);
        packed.remove(id);
        farmByController.put(locKey(farm.getControllerLocation()), id);
        for (UUID worker : farm.getAssignedVillagers()) farmByVillager.put(worker, id);
        refreshVillagerPersistence(farm);
        save();
        return true;
    }

    public Farm getByController(Location location) {
        String id = farmByController.get(locKey(location));
        return id == null ? null : farmsById.get(id);
    }

    public Collection<Farm> getFarms() {
        return farmsById.values().stream().filter(farm -> !packed.contains(farm.getFarmId())).toList();
    }

    public Farm getByVillager(UUID uuid) {
        String farmId = farmByVillager.get(uuid);
        return farmId == null ? null : farmsById.get(farmId);
    }

    public boolean assignVillager(Farm farm, UUID villagerId) {
        if (farm.getAssignedVillagers().size() >= Farm.MAX_WORKERS && !farm.getAssignedVillagers().contains(villagerId)) {
            return false;
        }

        Farm existing = getByVillager(villagerId);
        if (existing != null && existing != farm) {
            existing.getAssignedVillagers().remove(villagerId);
        }
        farmByVillager.put(villagerId, farm.getFarmId());
        boolean assigned = farm.getAssignedVillagers().add(villagerId);
        Entity entity = Bukkit.getEntity(villagerId);
        if (entity instanceof Villager villager && villager.isValid() && !villager.isDead()) {
            villager.setProfession(Villager.Profession.FARMER);
            villager.setPersistent(true);
            villager.setRemoveWhenFarAway(false);
        }
        return assigned;
    }

    public void unassignVillager(UUID villagerId) {
        String farmId = farmByVillager.remove(villagerId);
        if (farmId == null) {
            return;
        }
        Farm farm = farmsById.get(farmId);
        if (farm != null) {
            farm.getAssignedVillagers().remove(villagerId);
        }
    }

    public int pruneInvalidVillagers(Farm farm) {
        List<UUID> toRemove = new ArrayList<>();
        for (UUID villagerId : farm.getAssignedVillagers()) {
            Entity entity = Bukkit.getEntity(villagerId);
            // Bukkit#getEntity only returns loaded entities. A null value here can mean
            // the villager is in an unloaded chunk (or the world has not finished loading).
            // Keep the assignment so worker links persist across restarts/chunk unloads.
            if (entity == null) {
                continue;
            }

            if (!(entity instanceof Villager villager) || !villager.isValid() || villager.isDead()) {
                toRemove.add(villagerId);
            }
        }

        for (UUID villagerId : toRemove) {
            farm.getAssignedVillagers().remove(villagerId);
            farmByVillager.remove(villagerId);
        }
        return toRemove.size();
    }

    public void refreshVillagerPersistence(Farm farm) {
        for (UUID villagerId : farm.getAssignedVillagers()) {
            Entity entity = Bukkit.getEntity(villagerId);
            if (entity instanceof Villager villager && villager.isValid() && !villager.isDead()) {
                villager.setPersistent(true);
                villager.setRemoveWhenFarAway(false);
            }
        }
    }

    public void removeFarm(Farm farm) {
        packed.remove(farm.getFarmId());
        farmsById.remove(farm.getFarmId());
        farmByController.remove(locKey(farm.getControllerLocation()));
        for (UUID villagerId : farm.getAssignedVillagers()) {
            farmByVillager.remove(villagerId);
        }
        farm.getAssignedVillagers().clear();
    }

    public Farm getByFarmId(String farmId) {
        return farmsById.get(farmId);
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Farm farm : farmsById.values()) {
            String base = "farms." + farm.getFarmId();
            yaml.set(base + ".controller", serializeLocation(farm.getControllerLocation()));
            yaml.set(base + ".world", farm.getWorldId() == null ? null : farm.getWorldId().toString());
            yaml.set(base + ".output-chest", serializeLocation(farm.getOutputChest()));
            yaml.set(base + ".enabled", farm.isEnabled());
            yaml.set(base + ".drop-overflow-items", farm.shouldDropOverflowItems());
            yaml.set(base + ".packed", packed.contains(farm.getFarmId()));
            yaml.set(base + ".region-defined", farm.hasRegion());
            yaml.set(base + ".region.minX", farm.getMinX());
            yaml.set(base + ".region.minY", farm.getMinY());
            yaml.set(base + ".region.minZ", farm.getMinZ());
            yaml.set(base + ".region.maxX", farm.getMaxX());
            yaml.set(base + ".region.maxY", farm.getMaxY());
            yaml.set(base + ".region.maxZ", farm.getMaxZ());
            List<String> villagers = new ArrayList<>();
            for (UUID id : farm.getAssignedVillagers()) {
                villagers.add(id.toString());
            }
            yaml.set(base + ".villagers", villagers);
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[LumiDrugs] Failed to save farms: " + e.getMessage());
        }
    }

    public void load() {
        packed.clear();
        farmsById.clear();
        farmByController.clear();
        farmByVillager.clear();
        File source = file;
        if (!source.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(source);
        ConfigurationSection section = yaml.getConfigurationSection("farms");
        if (section == null) {
            return;
        }

        for (String farmId : section.getKeys(false)) {
            String base = "farms." + farmId;
            Location controller = deserializeLocation(yaml.getString(base + ".controller"));
            if (controller == null) {
                continue;
            }

            Farm farm = new Farm(farmId, controller);
            farm.setOutputChest(deserializeLocation(yaml.getString(base + ".output-chest")));
            farm.setEnabled(yaml.getBoolean(base + ".enabled", true));
            farm.setDropOverflowItems(yaml.getBoolean(base + ".drop-overflow-items", true));
            String worldRaw = yaml.getString(base + ".world");
            World world = worldRaw != null ? Bukkit.getWorld(UUID.fromString(worldRaw)) : controller.getWorld();
            if (world != null && yaml.getBoolean(base + ".region-defined", true)) {
                Location first = new Location(world,
                        yaml.getInt(base + ".region.minX", controller.getBlockX()),
                        yaml.getInt(base + ".region.minY", controller.getBlockY()),
                        yaml.getInt(base + ".region.minZ", controller.getBlockZ()));
                Location second = new Location(world,
                        yaml.getInt(base + ".region.maxX", controller.getBlockX()),
                        yaml.getInt(base + ".region.maxY", controller.getBlockY()),
                        yaml.getInt(base + ".region.maxZ", controller.getBlockZ()));
                farm.setRegion(world, first, second);
            }

            for (String villagerId : yaml.getStringList(base + ".villagers")) {
                try {
                    UUID uuid = UUID.fromString(villagerId);
                    if (farm.getAssignedVillagers().size() >= Farm.MAX_WORKERS) {
                        continue;
                    }
                    farm.getAssignedVillagers().add(uuid);
                    farmByVillager.put(uuid, farmId);
                } catch (IllegalArgumentException ignored) {
                }
            }

            farmsById.put(farmId, farm);
            if (yaml.getBoolean(base + ".packed", false)) packed.add(farmId);
            else farmByController.put(locKey(controller), farmId);
            refreshVillagerPersistence(farm);
        }
    }

    private String locKey(Location location) {
        return serializeLocation(location);
    }

    static String serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) return null;
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    static Location deserializeLocation(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String[] split = raw.split(":");
        if (split.length != 4) return null;
        try {
            World world = Bukkit.getWorld(UUID.fromString(split[0]));
            if (world == null) return null;
            return new Location(world,
                    Integer.parseInt(split[1]),
                    Integer.parseInt(split[2]),
                    Integer.parseInt(split[3]));
        } catch (Exception ex) {
            return null;
        }
    }
}
