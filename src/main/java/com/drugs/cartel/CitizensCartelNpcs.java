package com.drugs.cartel;

import com.drugs.DrugsV2;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.CitizensEnableEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Citizens owns persistent buyers; a separate memory-only registry owns temporary enforcers. */
final class CitizensCartelNpcs implements CartelNpcs, Listener {
    static final String ROLE = "lumidrugs-cartel-role";
    private static final String TEMPORARY_REGISTRY = "lumidrugs-cartel-enforcers";
    private final DrugsV2 plugin;
    private final CartelDealer dealer;
    private final NPCRegistry temporary;
    private final YamlConfiguration bundledSkin;
    private final CartelBuyerStore buyers;
    private boolean closed;

    CitizensCartelNpcs(DrugsV2 plugin, CartelDealer dealer) {
        this.plugin = plugin;
        this.dealer = dealer;
        buyers = new CartelBuyerStore(plugin);
        temporary = CitizensAPI.createInMemoryNPCRegistry(TEMPORARY_REGISTRY);
        bundledSkin = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(
                CitizensCartelNpcs.class.getResourceAsStream("/cartel-skin.yml")), StandardCharsets.UTF_8));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Citizens loads saved NPCs during its own startup. Also handle its enable event below.
        plugin.getServer().getScheduler().runTask(plugin, this::reload);
    }

    @Override public String unavailableReason() {
        if (closed) return "Cartel NPC support has shut down. Restart the server.";
        return plugin.getServer().getPluginManager().isPluginEnabled("Citizens") ? null
                : "Citizens is not enabled. Install a compatible build and restart the server.";
    }

    @Override public Character create(Location location, String role) {
        return create(location, role, null, -1);
    }

    private Character create(Location location, String role, UUID uuid, int id) {
        if (unavailableReason() != null) return null;
        NPCRegistry registry = role.equals("buyer") ? CitizensAPI.getNPCRegistry() : temporary;
        NPC npc = null;
        try {
            String name = role.equals("buyer") ? buyerName() : "Cartel Enforcer";
            npc = uuid == null ? registry.createNPC(EntityType.PLAYER, name)
                    : registry.createNPC(EntityType.PLAYER, uuid, id, name);
            npc.data().setPersistent(ROLE, role);
            if (role.equals("enforcer")) {
                npc.data().setPersistent("activation-range", 64);
            }
            npc.setProtected(true);
            npc.data().setPersistent("collidable", false);
            applySkin(npc);
            if (!npc.spawn(location)) {
                plugin.getLogger().warning("Citizens rejected cartel " + role + " spawn at " + location);
                npc.destroy();
                return null;
            }
            if (npc.getEntity() instanceof LivingEntity living) living.setCanPickupItems(false);
            if (role.equals("buyer")) {
                registry.saveToStore();
                buyers.remember(npc.getUniqueId(), npc.getId(), location);
            }
            return new Citizen(npc);
        } catch (RuntimeException exception) {
            if (npc != null) npc.destroy();
            plugin.getLogger().warning("Could not create cartel player NPC: " + exception.getMessage());
            return null;
        }
    }

    @Override public Character find(Entity entity, String role) {
        if (closed || !(entity instanceof Player)) return null;
        NPC npc = (role.equals("buyer") ? CitizensAPI.getNPCRegistry() : temporary).getNPC(entity);
        return owns(npc, role) ? new Citizen(npc) : null;
    }

    private boolean owns(NPC npc, String role) {
        return npc != null && role.equals(npc.data().get(ROLE, ""));
    }

    private String buyerName() {
        String name = dealer.settings().getString("npc.name", "Pablo").trim();
        return name.isEmpty() ? "Pablo" : name;
    }

    private void applySkin(NPC npc) {
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.clearTexture();
        skin.setFetchDefaultSkin(false);
        skin.setShouldUpdateSkins(false);
        String username = dealer.settings().getString("npc.skin-name", "").trim();
        if (!username.isEmpty()) skin.setSkinName(username);
        else skin.setSkinPersistent(bundledSkin.getString("name"), bundledSkin.getString("signature"),
                bundledSkin.getString("texture"));
    }

    @Override public void reload() {
        if (closed) return;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (owns(npc, "buyer")) {
                npc.setName(buyerName());
                applySkin(npc);
                buyers.remember(npc.getUniqueId(), npc.getId(), npc.getStoredLocation());
                if (npc.getEntity() instanceof LivingEntity living) living.setCanPickupItems(false);
            }
        }
        for (var buyer : buyers.all()) recoverBuyer(buyer);
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) migrate(villager);
        }
    }

    private Location savedLocation(CartelBuyerStore.Buyer buyer) {
        World world = Bukkit.getWorld(buyer.worldId());
        if (world == null) world = Bukkit.getWorld(buyer.worldName());
        return world == null ? null : new Location(world, buyer.x(), buyer.y(), buyer.z(), buyer.yaw(), buyer.pitch());
    }

    private void recoverBuyer(CartelBuyerStore.Buyer buyer) {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        NPC existing = registry.getByUniqueId(buyer.uuid());
        if (existing != null) {
            if (!owns(existing, "buyer")) {
                existing.data().setPersistent(ROLE, "buyer");
                existing.setName(buyerName());
                applySkin(existing);
            }
            if (existing.isSpawned()) return;
        }
        Location location = savedLocation(buyer);
        if (location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        if (existing != null) {
            // The manifest's stable UUID identifies our buyer even if its role tag was lost.
            existing.data().setPersistent(ROLE, "buyer");
            restoreBuyer(existing, location);
            return;
        }
        int id = buyer.npcId();
        if (registry.getById(id) != null) {
            // Preserve unrelated Citizens NPCs that have since reused the numeric ID.
            id = 0;
            for (NPC npc : registry) id = Math.max(id, npc.getId() + 1);
        }
        create(location, "buyer", buyer.uuid(), id);
    }

    private void restoreBuyer(NPC npc, Location fallback) {
        if (closed || !owns(npc, "buyer") || npc.isSpawned()) return;
        Location location = npc.getStoredLocation();
        if (location == null || location.getWorld() == null) location = fallback;
        if (location == null || location.getWorld() == null) return;
        // Recovery must not synchronously load or generate chunks during a reload.
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        if (!npc.spawn(location)) {
            plugin.getLogger().warning("Could not restore cartel buyer at " + location
                    + "; will retry when its chunk loads.");
        } else if (npc.getEntity() instanceof LivingEntity living) {
            living.setCanPickupItems(false);
        }
    }

    private void migrate(Villager villager) {
        NamespacedKey oldKey = new NamespacedKey(plugin, "cartel_dealer");
        if (villager.getPersistentDataContainer().has(oldKey, PersistentDataType.BYTE)
                && create(villager.getLocation(), "buyer") != null) villager.remove();
    }

    @Override public void shutdown() {
        if (closed) return;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (owns(npc, "buyer")) buyers.remember(npc.getUniqueId(), npc.getId(), npc.getStoredLocation());
        }
        closed = true;
        temporary.deregisterAll();
        CitizensAPI.removeNamedNPCRegistry(TEMPORARY_REGISTRY);
        // Buyers remain owned by Citizens, which handles its own persistence.
        // Saving its entire registry here makes unloading depend on every NPC trait.
    }

    @EventHandler(ignoreCancelled = true) public void onRightClick(NPCRightClickEvent event) {
        if (closed || !event.getNPC().isSpawned()) return;
        Entity entity = event.getNPC().getEntity();
        if (owns(event.getNPC(), "buyer")) {
            event.setCancelled(true);
            dealer.interact(event.getClicker(), entity);
        } else if (owns(event.getNPC(), "enforcer")) {
            event.setCancelled(true);
            dealer.interactEnforcer(event.getClicker(), entity);
        }
    }

    @EventHandler(ignoreCancelled = true) public void onLeftClick(NPCLeftClickEvent event) {
        if (closed || !event.getNPC().isSpawned() || !owns(event.getNPC(), "enforcer")) return;
        event.setCancelled(true);
        dealer.interactEnforcer(event.getClicker(), event.getNPC().getEntity());
    }

    @EventHandler public void onCitizensEnable(CitizensEnableEvent event) { reload(); }
    @EventHandler public void onWorldLoad(WorldLoadEvent event) {
        // Let Citizens resolve stored world references before attempting recovery.
        if (!closed) plugin.getServer().getScheduler().runTask(plugin, this::reload);
    }

    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        if (closed) return;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (owns(npc, "buyer")) buyers.remember(npc.getUniqueId(), npc.getId(), npc.getStoredLocation());
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager) migrate(villager);
        }
        for (var buyer : buyers.all()) {
            if (buyer.inChunk(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ())) recoverBuyer(buyer);
        }
    }

    private final class Citizen implements Character {
        private final NPC npc;
        Citizen(NPC npc) { this.npc = npc; }
        public LivingEntity entity() { return (LivingEntity) npc.getEntity(); }
        public UUID id() { return npc.getUniqueId(); }
        public boolean valid() { return npc.isSpawned() && npc.getEntity() != null && npc.getEntity().isValid(); }
        public void destroy() {
            NPCRegistry registry = npc.getOwningRegistry();
            buyers.forget(npc.getUniqueId());
            npc.destroy();
            registry.saveToStore();
        }
    }
}
