package com.drugs.status;

import com.drugs.DrugsV2;
import com.drugs.effects.LsdEffectManager;
import com.drugs.effects.SalviaEffectManager;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.persistence.PersistentDataContainer;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Records the source of live drug effects in player data, including across reconnects. */
public final class DrugStatusTracker implements Listener {
    public record Status(int remainingTicks, int delayTicks, List<String> effects) {
        public Status {
            effects = List.copyOf(effects);
        }
    }

    public static final String UNKNOWN_PREFIX = "effect/";
    public static final String WITHDRAWAL_PREFIX = "withdrawal/";

    private static final String PREFIX = "drug_status/";

    private static NamespacedKey key(PotionEffectType type) {
        return new NamespacedKey("lumidrugs", PREFIX + type.getKey().getNamespace() + "/" + type.getKey().getKey());
    }

    public static void applyEffect(Player player, String drugId, PotionEffect effect) {
        if (player.addPotionEffect(effect) && player.getPotionEffect(effect.getType()) != null) {
            player.getPersistentDataContainer().set(key(effect.getType()), PersistentDataType.STRING, drugId);
        }
    }

    /** Reports all live effects, preserving known drug sources and unknown sources. */
    public static Map<String, Status> getStatus(Player player) {
        Map<String, Status> status = new TreeMap<>();
        Map<NamespacedKey, Status> activeKeys = new HashMap<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            String name = effect.getType().getKey().getKey().replace('_', ' ');
            activeKeys.put(key(effect.getType()), new Status(effect.getDuration(), 0,
                    List.of(name + " " + (effect.getAmplifier() + 1))));
        }
        status.putAll(potionStatus(player.getPersistentDataContainer(), activeKeys));
        DrugsV2 plugin = DrugsV2.getInstance();
        if (plugin != null) {
            LsdEffectManager lsd = plugin.getLsdEffectManager();
            if (lsd != null && lsd.hasEffect(player)) {
                merge(status, "lsd", new Status(lsd.getRemainingTicks(player), lsd.getDelayTicks(player),
                        List.of(lsd.isPending(player) ? "hallucinations pending" : "hallucinations")));
            }
            SalviaEffectManager salvia = plugin.getSalviaEffectManager();
            if (salvia != null && salvia.hasEffect(player)) {
                merge(status, "salvia", new Status(salvia.getRemainingTicks(player), salvia.getDelayTicks(player),
                        List.of(salvia.isPending(player) ? "block disguise pending" : "block disguise")));
            }
            var sensory = plugin.getSensoryEffectManager();
            if (sensory != null) {
                if (sensory.hasPendingEdible(player)) {
                    merge(status, "edible", new Status(0, sensory.getEdibleDelayTicks(player),
                            List.of("edible kick-in pending")));
                }
                if (sensory.hasSchizophrenia(player)) {
                    merge(status, "schizophrenia", new Status(sensory.getSchizophreniaRemainingTicks(player), 0,
                            List.of("auditory & visual hallucinations")));
                }
            }
        }
        return new LinkedHashMap<>(status);
    }

    static Map<String, Status> potionStatus(PersistentDataContainer data, Map<NamespacedKey, Status> activeKeys) {
        Map<String, Status> status = new TreeMap<>();
        var attributed = new java.util.HashSet<NamespacedKey>();
        for (NamespacedKey key : data.getKeys()) {
            if (!isStatusKey(key)) continue;
            String drug = data.get(key, PersistentDataType.STRING);
            if (drug != null && activeKeys.containsKey(key)) {
                merge(status, drug, activeKeys.get(key));
                attributed.add(key);
            } else {
                data.remove(key);
            }
        }
        for (var entry : activeKeys.entrySet()) {
            if (!attributed.contains(entry.getKey())) {
                status.put(UNKNOWN_PREFIX + entry.getKey().getKey().substring(PREFIX.length()), entry.getValue());
            }
        }
        return status;
    }

    private static void merge(Map<String, Status> statuses, String drug, Status next) {
        Status previous = statuses.get(drug);
        if (previous == null) {
            statuses.put(drug, next);
            return;
        }
        List<String> effects = new ArrayList<>(previous.effects());
        for (String effect : next.effects()) if (!effects.contains(effect)) effects.add(effect);
        int remaining = previous.remainingTicks() < 0 || next.remainingTicks() < 0
                ? -1 : Math.max(previous.remainingTicks(), next.remainingTicks());
        statuses.put(drug, new Status(remaining, Math.max(previous.delayTicks(), next.delayTicks()), effects));
    }

    private static boolean isStatusKey(NamespacedKey key) {
        return key.getNamespace().equals("lumidrugs") && key.getKey().startsWith(PREFIX);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getOldEffect() == null) return;
        if (event.getAction() == EntityPotionEffectEvent.Action.CHANGED && !event.isOverride()) return;
        // Milk, expiration, cures and replacement by non-drug effects clear attribution.
        player.getPersistentDataContainer().remove(key(event.getOldEffect().getType()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        var data = event.getEntity().getPersistentDataContainer();
        for (NamespacedKey key : data.getKeys()) {
            if (isStatusKey(key)) data.remove(key);
        }
    }
}
