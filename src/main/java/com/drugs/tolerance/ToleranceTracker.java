package com.drugs.tolerance;

import com.drugs.achievements.AchievementManager;
import com.drugs.bootstrap.PerformanceOptimizer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks tolerance and overdose behavior for players.
 */
public class ToleranceTracker {

    private static final Map<UUID, Map<String, Integer>> toleranceLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> lastUseTimestamps = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Integer>> overdoseAttempts = new ConcurrentHashMap<>();
    private static final Set<String> cleanSlateProgress = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void onDrugUse(Player player, String drugId) {
        UUID uuid = player.getUniqueId();
        int current = getToleranceLevel(player, drugId);
        int max = ToleranceConfigLoader.getMaxTolerance(drugId);

        if (current < max) {
            setToleranceLevel(player, drugId, current + 1);
            resetOverdoseCount(player, drugId);
        }

        updateLastUse(player, drugId);

        // Track that they reached max tolerance for Clean Slate later
        if (current + 1 >= max) {
            cleanSlateProgress.add(uuid + ":" + drugId.toLowerCase());
        }

        // Achievement: I Can Stop Anytime – max tolerance on 3+ drugs
        int maxedDrugs = 0;
        Map<String, Integer> playerTolerances = toleranceLevels.getOrDefault(uuid, Collections.emptyMap());

        for (Map.Entry<String, Integer> entry : playerTolerances.entrySet()) {
            int tolLevel = entry.getValue();
            int tolMax = ToleranceConfigLoader.getMaxTolerance(entry.getKey());
            if (tolLevel >= tolMax) {
                maxedDrugs++;
            }
        }

        if (maxedDrugs >= 3) {
            Map<String, Object> context = AchievementManager.createContext();
            context.put("maxed_count", maxedDrugs);
            AchievementManager.processTrigger(player, "maxed_three", context);
        }
        
        // Update cache after changes
        PerformanceOptimizer.updateToleranceCache(uuid, toleranceLevels.getOrDefault(uuid, new HashMap<>()));
    }

    public static int getToleranceLevel(Player player, String drugId) {
        if (player == null || drugId == null) return 0;
        UUID uuid = player.getUniqueId();
        if (uuid == null) return 0;
        
        // Try to get from cache first
        if (PerformanceOptimizer.isToleranceCached(uuid) && !PerformanceOptimizer.isCacheExpired(uuid)) {
            Map<String, Integer> cachedData = PerformanceOptimizer.getCachedToleranceData(uuid);
            if (cachedData != null) {
                return cachedData.getOrDefault(drugId.toLowerCase(), 0);
            }
        }
        
        // Fall back to main storage
        return toleranceLevels
                .getOrDefault(uuid, Collections.emptyMap())
                .getOrDefault(drugId.toLowerCase(), 0);
    }

    public static int getOverdoseAttempts(Player player, String drugId) {
        if (player == null || player.getUniqueId() == null || drugId == null) return 0;
        return overdoseAttempts
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(drugId.toLowerCase(), 0);
    }

    public static int getRemainingDecaySeconds(Player player, String drugId) {
        if (player == null || player.getUniqueId() == null || drugId == null) return 0;
        int level = getToleranceLevel(player, drugId);
        if (level <= 0) return 0;
        long lastUsed = lastUseTimestamps
                .getOrDefault(player.getUniqueId(), Collections.emptyMap())
                .getOrDefault(drugId.toLowerCase(), 0L);
        int decayMinutes = ToleranceConfigLoader.getDecayMinutes(drugId);
        long decayMillis = decayMinutes * 60_000L;
        if (lastUsed <= 0L) return decayMinutes * 60;
        long remainingMillis = Math.max(0L, (lastUsed + decayMillis) - System.currentTimeMillis());
        return (int) ((remainingMillis + 999L) / 1000L);
    }

    public static Set<String> getTrackedDrugs(Player player) {
        if (player == null || player.getUniqueId() == null) return Set.of();
        UUID uuid = player.getUniqueId();
        Set<String> tracked = new TreeSet<>();
        for (var entry : toleranceLevels.getOrDefault(uuid, Collections.emptyMap()).entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) tracked.add(entry.getKey());
        }
        for (var entry : overdoseAttempts.getOrDefault(uuid, Collections.emptyMap()).entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) tracked.add(entry.getKey());
        }
        return tracked;
    }

    private static void setToleranceLevel(Player player, String drugId, int value) {
        UUID uuid = player.getUniqueId();
        toleranceLevels.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(drugId.toLowerCase(), value);
    }

    private static void updateLastUse(Player player, String drugId) {
        UUID uuid = player.getUniqueId();
        lastUseTimestamps.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(drugId.toLowerCase(), System.currentTimeMillis());
    }

    public static double getEffectivenessMultiplier(Player player, String drugId) {
        int level = getToleranceLevel(player, drugId);
        return ToleranceConfigLoader.getEffectivenessMultiplier(drugId, level);
    }

    /**
     * Processes tolerance decay for a specific player
     * Called by the PerformanceOptimizer in batches
     * 
     * @param player The player to process
     */
    public static void processDecay(Player player) {
        if (player == null) return;
        
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        Map<String, Integer> playerTolerance = toleranceLevels.get(uuid);
        if (playerTolerance == null || playerTolerance.isEmpty()) return;
        
        Map<String, Long> playerTimestamps = lastUseTimestamps.getOrDefault(uuid, Collections.emptyMap());
        boolean changed = false;

        for (String drugId : new HashSet<>(playerTolerance.keySet())) {
            int level = playerTolerance.get(drugId);
            if (level <= 0) continue;

            long lastUsed = playerTimestamps.getOrDefault(drugId, 0L);
            int decayMinutes = ToleranceConfigLoader.getDecayMinutes(drugId);
            long decayMillis = decayMinutes * 60_000L;

            if (now - lastUsed >= decayMillis) {
                int newLevel = level - 1;
                playerTolerance.put(drugId, newLevel);
                updateLastUse(player, drugId); // restart timer
                changed = true;

                // Grant Clean Slate if it decays from max to 0
                String key = uuid + ":" + drugId.toLowerCase();
                if (newLevel == 0 && cleanSlateProgress.contains(key)) {
                    cleanSlateProgress.remove(key);
                    
                    Map<String, Object> context = AchievementManager.createContext();
                    context.put("drug_id", drugId);
                    AchievementManager.processTrigger(player, "decay_full", context);
                }
            }
        }
        
        // Update cache if changes were made
        if (changed) {
            PerformanceOptimizer.updateToleranceCache(uuid, playerTolerance);
        }
    }

    public static int incrementOverdoseCount(Player player, String drugId) {
        UUID uuid = player.getUniqueId();
        String id = drugId.toLowerCase();

        Map<String, Integer> map = overdoseAttempts.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        int current = map.getOrDefault(id, 0);
        current++;
        map.put(id, current);
        
        // Trigger overdose survival achievement if they're not dead yet
        if (current > 0 && current < 3) {
            Map<String, Object> context = AchievementManager.createContext();
            context.put("drug_id", drugId);
            context.put("attempt", current);
            AchievementManager.processTrigger(player, "overdose_survive", context);
        }
        
        return current;
    }

    public static void resetOverdoseCount(Player player, String drugId) {
        overdoseAttempts.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(drugId.toLowerCase(), 0);
    }

    public static void resetAllTolerance(Player player) {
        UUID uuid = player.getUniqueId();
        toleranceLevels.remove(uuid);
        lastUseTimestamps.remove(uuid);
        overdoseAttempts.remove(uuid);
        PerformanceOptimizer.invalidateToleranceCache(uuid);
    }

    public static boolean isAtMaxTolerance(UUID playerId, String drugId) {
        // Try to get from cache first
        if (PerformanceOptimizer.isToleranceCached(playerId) && !PerformanceOptimizer.isCacheExpired(playerId)) {
            Map<String, Integer> cachedData = PerformanceOptimizer.getCachedToleranceData(playerId);
            if (cachedData != null) {
                int current = cachedData.getOrDefault(drugId.toLowerCase(), 0);
                int max = ToleranceConfigLoader.getMaxTolerance(drugId);
                return current >= max;
            }
        }
        
        // Fall back to main storage
        int current = toleranceLevels
                .getOrDefault(playerId, Collections.emptyMap())
                .getOrDefault(drugId.toLowerCase(), 0);

        int max = ToleranceConfigLoader.getMaxTolerance(drugId);
        return current >= max;
    }
}
