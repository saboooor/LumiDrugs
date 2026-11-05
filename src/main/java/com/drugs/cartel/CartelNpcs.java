package com.drugs.cartel;

import com.drugs.DrugsV2;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import java.util.UUID;

/** Keeps optional Citizens types out of the core plugin's class loading path. */
public interface CartelNpcs {
    interface Character {
        LivingEntity entity();
        boolean valid();
        void destroy();
        default UUID id() { return entity().getUniqueId(); }
    }

    default String unavailableReason() { return null; }

    Character create(Location location, String role);
    Character find(Entity entity, String role);
    void reload();
    void shutdown();

    static CartelNpcs create(DrugsV2 plugin, CartelDealer dealer) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
            return new CitizensCartelNpcs(plugin, dealer);
        }
        plugin.getLogger().warning("Cartel player NPCs require Citizens. Other drug features remain available.");
        return new CartelNpcs() {
            public String unavailableReason() { return "Citizens is not enabled. Install a compatible build and restart the server."; }
            public Character create(Location location, String role) { return null; }
            public Character find(Entity entity, String role) { return null; }
            public void reload() {}
            public void shutdown() {}
        };
    }
}
