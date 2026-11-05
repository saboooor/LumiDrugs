package com.drugs.quests;

import com.drugs.DrugsV2;
import org.bukkit.entity.Player;

/** Keeps the optional BeautyQuests API out of core command and persistence classes. */
public interface ProgressQuests {
    enum Kind { LEARNING, SUPPLY }
    String unavailable(Player player, Kind kind);
    boolean begin(Player player, Kind kind);
    void describe(Player player, Kind kind, String text);
    boolean finish(Player player, Kind kind);
    void cancel(Player player, Kind kind);
    static ProgressQuests create(DrugsV2 plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("BeautyQuests")) {
            try { return new BeautyQuestsProgress(plugin); }
            catch (Exception | LinkageError error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Production quests unavailable", error); }
        }
        return new ProgressQuests() {
            public String unavailable(Player p, Kind k) { return "Install BeautyQuests 2.0+ and restart to enable production quests."; }
            public boolean begin(Player p, Kind k) { return false; }
            public void describe(Player p, Kind k, String t) {}
            public boolean finish(Player p, Kind k) { return false; }
            public void cancel(Player p, Kind k) {}
        };
    }
}
