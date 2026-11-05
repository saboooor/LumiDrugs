package com.drugs.cartel;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Small, atomic fallback record independent of Citizens' registry and save timing. */
final class CartelBuyerStore {
    record Buyer(UUID uuid, int npcId, UUID worldId, String worldName,
                 double x, double y, double z, float yaw, float pitch) {
        boolean inChunk(UUID world, int chunkX, int chunkZ) {
            return worldId.equals(world) && ((int) Math.floor(x) >> 4) == chunkX && ((int) Math.floor(z) >> 4) == chunkZ;
        }
    }
    private final Plugin plugin;
    private final Path file;
    private final Map<UUID, Buyer> buyers = new LinkedHashMap<>();
    private boolean writable = true;

    CartelBuyerStore(Plugin plugin) {
        this.plugin = plugin;
        file = plugin.getDataFolder().toPath().resolve("cartel-buyers.yml");
        if (!Files.exists(file)) return;
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            var section = yaml.getConfigurationSection("buyers");
            if (section == null) return;
            for (String id : section.getKeys(false)) {
                try {
                    String path = "buyers." + id + ".";
                    var buyer = new Buyer(UUID.fromString(id), yaml.getInt(path + "npc-id"),
                            UUID.fromString(Objects.requireNonNull(yaml.getString(path + "world-uuid"))),
                            Objects.requireNonNull(yaml.getString(path + "world-name")),
                            yaml.getDouble(path + "x"), yaml.getDouble(path + "y"), yaml.getDouble(path + "z"),
                            (float) yaml.getDouble(path + "yaw"), (float) yaml.getDouble(path + "pitch"));
                    if (!Double.isFinite(buyer.x()) || !Double.isFinite(buyer.y()) || !Double.isFinite(buyer.z())
                            || !Float.isFinite(buyer.yaw()) || !Float.isFinite(buyer.pitch())) throw new IllegalArgumentException("Non-finite location");
                    buyers.put(buyer.uuid(), buyer);
                } catch (RuntimeException exception) {
                    writable = false;
                    plugin.getLogger().warning("Invalid saved cartel buyer " + id + "; preserving the original file.");
                }
            }
        } catch (Exception exception) {
            writable = false;
            plugin.getLogger().warning("Cannot load cartel buyers; preserving the original file: " + exception.getMessage());
        }
    }

    List<Buyer> all() { return List.copyOf(buyers.values()); }

    void remember(UUID uuid, int npcId, Location location) {
        if (uuid == null || location == null || location.getWorld() == null) return;
        var world = location.getWorld();
        var buyer = new Buyer(uuid, npcId, world.getUID(), world.getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        if (!buyer.equals(buyers.put(uuid, buyer))) save();
    }

    void forget(UUID uuid) { if (buyers.remove(uuid) != null) save(); }

    private void save() {
        if (!writable) return;
        var yaml = new YamlConfiguration();
        for (Buyer buyer : buyers.values()) {
            String path = "buyers." + buyer.uuid() + ".";
            yaml.set(path + "npc-id", buyer.npcId());
            yaml.set(path + "world-uuid", buyer.worldId().toString());
            yaml.set(path + "world-name", buyer.worldName());
            yaml.set(path + "x", buyer.x()); yaml.set(path + "y", buyer.y()); yaml.set(path + "z", buyer.z());
            yaml.set(path + "yaw", buyer.yaw()); yaml.set(path + "pitch", buyer.pitch());
        }
        Path temp = null;
        try {
            Files.createDirectories(file.getParent());
            temp = Files.createTempFile(file.getParent(), "cartel-buyers-", ".tmp");
            Files.writeString(temp, yaml.saveToString());
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save cartel buyers: " + exception.getMessage());
        } finally {
            if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) {}
        }
    }
}
