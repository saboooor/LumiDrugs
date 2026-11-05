package com.drugs.overdose;

import com.drugs.addiction.AddictionManager;
import com.drugs.config.MechanicsConfig;
import com.drugs.drug.DrugRegistry;
import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Private, generic warnings never name a hidden added drug or claim withdrawal changes the overdose formula. */
public final class OverdoseWarnings implements Listener {
    enum Risk {
        NONE(""),
        WITHDRAWAL("Withdrawal is active. More doses can still lead to an overdose, even if they relieve withdrawal. Check /drugstatus."),
        NEAR_LIMIT("You are approaching the overdose limit. Further doses can put you at risk. Check /drugstatus."),
        WITHDRAWAL_NEAR("You are in withdrawal and approaching the overdose limit. Do not keep taking doses to chase relief. Check /drugstatus."),
        HIGH("You are at the overdose limit. Taking more doses can trigger an overdose. Check /drugstatus."),
        WITHDRAWAL_HIGH("OVERDOSE RISK: you are in withdrawal and at the overdose limit. Further doses can trigger an overdose, even if withdrawal feels better. Check /drugstatus.");
        final String message;
        Risk(String message) { this.message = message; }
    }
    private record Notice(Risk risk, long sentAt) {}
    private static final Map<UUID, Notice> notices = new HashMap<>();
    private final BukkitTask task;

    public OverdoseWarnings(Plugin plugin) {
        notices.clear();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) check(player);
        }, 100L, 100L);
    }

    static Risk assess(int level, int maximum, boolean withdrawal) {
        if (level >= maximum) return withdrawal ? Risk.WITHDRAWAL_HIGH : Risk.HIGH;
        if (level >= maximum - 1) return withdrawal ? Risk.WITHDRAWAL_NEAR : Risk.NEAR_LIMIT;
        return withdrawal ? Risk.WITHDRAWAL : Risk.NONE;
    }
    private static Risk risk(Player player, String drug, boolean withdrawal) {
        return assess(ToleranceTracker.getToleranceLevel(player, drug), ToleranceConfigLoader.getMaxTolerance(drug), withdrawal);
    }
    public static void beforeUse(Player player, String drug) {
        Risk risk = risk(player, drug, AddictionManager.hasActiveWithdrawal(player));
        // A deliberate dangerous dose bypasses the normal reminder delay, with a short
        // duplicate guard for two profiles being applied during one laced-item interaction.
        if (risk != Risk.NONE) send(player, risk, risk == Risk.HIGH || risk == Risk.WITHDRAWAL_HIGH, System.currentTimeMillis());
    }
    public static void check(Player player) {
        if (player.isDead() || player.hasMetadata("NPC")) { notices.remove(player.getUniqueId()); return; }
        boolean withdrawal = AddictionManager.hasActiveWithdrawal(player);
        Risk strongest = withdrawal ? Risk.WITHDRAWAL : Risk.NONE;
        for (String drug : DrugRegistry.getRegisteredDrugNames()) {
            Risk current = risk(player, drug, withdrawal);
            if (current.ordinal() > strongest.ordinal()) strongest = current;
        }
        send(player, strongest, false, System.currentTimeMillis());
    }
    static void send(Player player, Risk risk, boolean deliberateDose, long now) {
        UUID uuid = player.getUniqueId();
        if (risk == Risk.NONE || !MechanicsConfig.isOverdoseWarningsEnabled()) { notices.remove(uuid); return; }
        Notice last = notices.get(uuid);
        long interval = deliberateDose ? 1000L : MechanicsConfig.getOverdoseWarningSeconds() * 1000L;
        if (last != null && risk.ordinal() <= last.risk().ordinal() && now - last.sentAt() < interval) return;
        player.sendMessage("§c§lWARNING §8» §c" + risk.message);
        notices.put(uuid, new Notice(risk, now));
    }
    @EventHandler public void quit(PlayerQuitEvent event) { notices.remove(event.getPlayer().getUniqueId()); }
    public void shutdown() { if (task != null) task.cancel(); notices.clear(); }
    static void reset() { notices.clear(); }
}
