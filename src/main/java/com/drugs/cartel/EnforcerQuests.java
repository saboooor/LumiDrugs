package com.drugs.cartel;

import com.drugs.DrugsV2;
import org.bukkit.entity.Player;

/** Optional integration boundary: buyers and other drug features do not require BeautyQuests. */
interface EnforcerQuests {
    String unavailableReason(Player player);
    default void bind(CartelEnforcer enforcer) {}
    Session begin(Player player, String drug, int amount);

    interface Session {
        boolean active();
        /** Publish the encounter entity as generic quest data for optional stage behaviors. */
        default void npc(java.util.UUID entityId) {}
        void progress(int delivered);
        boolean complete();
        void cancel();
    }

    static EnforcerQuests create(DrugsV2 plugin, CartelDealer dealer) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("BeautyQuests")) {
            try {
                return new BeautyQuestsEnforcers(plugin, dealer);
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "BeautyQuests integration unavailable; enforcers disabled", exception);
            }
        }
        plugin.getLogger().warning("Cartel enforcers require BeautyQuests; ordinary cartel buyers remain available.");
        return new EnforcerQuests() {
            public String unavailableReason(Player player) { return "Install BeautyQuests 2.0+ and restart to enable cartel enforcers."; }
            public Session begin(Player player, String drug, int amount) { return null; }
        };
    }
}
