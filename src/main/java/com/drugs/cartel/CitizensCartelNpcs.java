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
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/** Citizens owns persistent buyers; a separate memory-only registry owns temporary enforcers. */
final class CitizensCartelNpcs implements CartelNpcs, Listener {
    static final String ROLE = "lumidrugs-cartel-role";
    private static final String TEMPORARY_REGISTRY = "lumidrugs-cartel-enforcers";
    private final DrugsV2 plugin;
    private final CartelDealer dealer;
    private final NPCRegistry temporary;
    private boolean closed;

    CitizensCartelNpcs(DrugsV2 plugin, CartelDealer dealer) {
        this.plugin = plugin;
        this.dealer = dealer;
        temporary = CitizensAPI.createInMemoryNPCRegistry(TEMPORARY_REGISTRY);
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
        if (!role.equals("enforcer")) return null;
        if (unavailableReason() != null) return null;
        NPCRegistry registry = temporary;
        NPC npc = null;
        try {
            npc = registry.createNPC(EntityType.PLAYER, "Cartel Enforcer");
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
            return new Citizen(npc);
        } catch (RuntimeException exception) {
            if (npc != null) npc.destroy();
            plugin.getLogger().warning("Could not create cartel player NPC: " + exception.getMessage());
            return null;
        }
    }

    @Override public Character find(Entity entity, String role) {
        if (closed || !(entity instanceof LivingEntity)) return null;
        NPC npc = (role.equals("buyer") ? CitizensAPI.getNPCRegistry() : temporary).getNPC(entity);
        return owns(npc, role) ? new Citizen(npc) : null;
    }

    private boolean owns(NPC npc, String role) {
        return npc != null && role.equals(npc.data().get(ROLE, ""));
    }

    @Override public boolean linkBuyer(int npcId) {
        if (unavailableReason() != null) return false;
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) return false;
        String role = npc.data().get(ROLE, "");
        if (!role.isEmpty() && !role.equals("buyer")) return false;
        npc.data().setPersistent(ROLE, "buyer");
        CitizensAPI.getNPCRegistry().saveToStore();
        return true;
    }

    @Override public boolean unlinkBuyer(int npcId) {
        if (unavailableReason() != null) return false;
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (!owns(npc, "buyer")) return false;
        npc.data().remove(ROLE);
        CitizensAPI.getNPCRegistry().saveToStore();
        return true;
    }

    private void applySkin(NPC npc) {
        SkinTrait skin = npc.getOrAddTrait(SkinTrait.class);
        skin.clearTexture();
        skin.setFetchDefaultSkin(false);
        skin.setShouldUpdateSkins(false);
        String username = dealer.settings().getString("npc.skin-name", "").trim();
        if (!username.isEmpty()) skin.setSkinName(username);
        else skin.setSkinPersistent(dealer.settings().getString("npc.skin.name"), dealer.settings().getString("npc.skin.signature"),
                dealer.settings().getString("npc.skin.texture"));
    }

    // Buyer roles persist in Citizens metadata. Citizens alone manages their lifecycle.
    @Override public void reload() {}

    @Override public void shutdown() {
        if (closed) return;
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
    private final class Citizen implements Character {
        private final NPC npc;
        Citizen(NPC npc) { this.npc = npc; }
        public LivingEntity entity() { return (LivingEntity) npc.getEntity(); }
        public UUID id() { return npc.getUniqueId(); }
        public boolean valid() { return npc.isSpawned() && npc.getEntity() != null && npc.getEntity().isValid(); }
        public void destroy() {
            if (owns(npc, "buyer")) {
                unlinkBuyer(npc.getId());
                return;
            }
            npc.destroy();

        }
    }
}
