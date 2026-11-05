package com.drugs.achievements;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.growing.DrugGrowing;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Uses Minecraft's advancement registry, UI, notifications and player persistence. */
public final class NativeAdvancements implements Listener {
    private static final NamespacedKey ROOT = key("root");
    private static BukkitTask registrationTask;
    private static final Map<UUID, Set<String>> pendingAwards = new HashMap<>();
    private static final Map<UUID, Set<String>> pendingDrugs = new HashMap<>();
    private static final Map<UUID, Set<String>> pendingCrops = new HashMap<>();

    private static NamespacedKey key(String id) {
        return new NamespacedKey("lumidrugs", "achievements/" + id);
    }

    private static Advancement getAdvancementSafe(NamespacedKey key) {
        try {
            return Bukkit.getAdvancement(key);
        } catch (NullPointerException ignored) {
            return null;
        }
    }

    public static void reload() {
        if (registrationTask != null) registrationTask.cancel();
        registrationTask = null;
        if (!AchievementSettingsLoader.isEnabled()) {
            pendingAwards.clear();
            pendingDrugs.clear();
            pendingCrops.clear();
            return;
        }
        // Registration may rebuild advancement trees and resend player data. Never do it
        // inside onEnable, and register at most one missing advancement per server tick.
        Map<NamespacedKey, JsonObject> definitions = new LinkedHashMap<>();
        definitions.put(ROOT, definition("Drugs", "Drug milestones", Material.BREWING_STAND, true));
        for (CustomAchievement achievement : CustomAchievementLoader.getAllAchievements()) {
            if (!achievement.isEnabled()) continue;
            try {
                JsonObject json = definition(achievement.getTitle(), achievement.getDescription(),
                        achievement.getCompletedIcon(), false);
                if (achievement.getTrigger().equals("all_drugs_used") && !DrugRegistry.getRegisteredDrugNames().isEmpty()) {
                    JsonObject criteria = new JsonObject();
                    for (String drug : DrugRegistry.getRegisteredDrugNames()) {
                        JsonObject criterion = new JsonObject();
                        criterion.addProperty("trigger", "minecraft:impossible");
                        criteria.add("used_" + drug, criterion);
                    }
                    json.add("criteria", criteria);
                } else if (achievement.getTrigger().equals("all_crops_harvested") && !DrugGrowing.cropIds().isEmpty()) {
                    JsonObject criteria = new JsonObject();
                    for (String crop : DrugGrowing.cropIds()) {
                        JsonObject criterion = new JsonObject();
                        criterion.addProperty("trigger", "minecraft:impossible");
                        criteria.add("harvested_" + crop, criterion);
                    }
                    json.add("criteria", criteria);
                }
                definitions.put(key(achievement.getId()), json);
            } catch (IllegalArgumentException exception) {
                DrugsV2.getInstance().getLogger().warning("Cannot register advancement "
                        + achievement.getId() + ": " + exception.getMessage());
            }
        }
        Queue<Map.Entry<NamespacedKey, JsonObject>> queue = new ArrayDeque<>(definitions.entrySet());
        registrationTask = Bukkit.getScheduler().runTaskTimer(DrugsV2.getInstance(), () -> {
            while (!queue.isEmpty()) {
                var entry = queue.remove();
                if (getAdvancementSafe(entry.getKey()) != null) continue;
                try {
                    register(entry.getKey(), entry.getValue());
                } catch (RuntimeException exception) {
                    DrugsV2.getInstance().getLogger().warning("Cannot register advancement "
                            + entry.getKey() + ": " + exception.getMessage());
                }
                break;
            }
            if (queue.isEmpty()) {
                registrationTask.cancel();
                registrationTask = null;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    prepare(player);
                    for (String id : pendingAwards.getOrDefault(player.getUniqueId(), Set.of())) award(player, id);
                    for (String drug : pendingDrugs.getOrDefault(player.getUniqueId(), Set.of())) trackDrug(player, drug);
                    for (String crop : pendingCrops.getOrDefault(player.getUniqueId(), Set.of())) trackCrop(player, crop);
                }
                pendingAwards.clear();
                pendingDrugs.clear();
                pendingCrops.clear();
            }
        }, 1L, 1L);
    }

    public static void shutdown() {
        if (registrationTask != null) registrationTask.cancel();
        registrationTask = null;
        pendingAwards.clear();
        pendingDrugs.clear();
        pendingCrops.clear();
    }

    @SuppressWarnings("deprecation")
    private static void register(NamespacedKey key, JsonObject definition) {
        Advancement advancement = Bukkit.getUnsafe().loadAdvancement(key, definition.toString());
        if (advancement == null) throw new IllegalArgumentException("Server rejected " + key);
    }

    static JsonObject definition(String title, String description, Material icon, boolean root) {
        JsonObject json = new JsonObject();
        if (!root) json.addProperty("parent", ROOT.toString());
        JsonObject display = new JsonObject();
        JsonObject item = new JsonObject();
        item.addProperty("id", icon.getKey().toString());
        display.add("icon", item);
        display.addProperty("title", plain(title));
        display.addProperty("description", plain(description));
        display.addProperty("frame", "task");
        display.addProperty("show_toast", !root && AchievementSettingsLoader.isSoundNotificationsEnabled());
        display.addProperty("announce_to_chat", !root && AchievementSettingsLoader.isChatNotificationsEnabled());
        display.addProperty("hidden", false);
        if (root) display.addProperty("background", "minecraft:textures/gui/advancements/backgrounds/stone.png");
        json.add("display", display);
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("unlock", criterion);
        json.add("criteria", criteria);
        return json;
    }

    private static String plain(String text) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    public static void award(Player player, String id) {
        if (player == null) return;
        Advancement advancement = getAdvancementSafe(key(id));
        if (advancement == null) {
            if (registrationTask != null) {
                pendingAwards.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(id);
            }
            return;
        }
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress == null) return;
        for (String criterion : progress.getRemainingCriteria()) progress.awardCriteria(criterion);
    }

    public static boolean has(Player player, String id) {
        if (player == null) return false;
        Advancement advancement = getAdvancementSafe(key(id));
        if (advancement == null) return false;
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        return progress != null && progress.isDone();
    }

    public static void trackDrug(Player player, String drug) {
        if (player == null || !AchievementSettingsLoader.isEnabled()) return;
        if (registrationTask != null) {
            pendingDrugs.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(drug);
        }
        for (CustomAchievement achievement : CustomAchievementLoader.getAchievementsByTrigger("all_drugs_used")) {
            if (!achievement.isEnabled()) continue;
            Advancement advancement = getAdvancementSafe(key(achievement.getId()));
            if (advancement != null && advancement.getCriteria().contains("used_" + drug)) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                if (progress != null) progress.awardCriteria("used_" + drug);
            }
        }
    }

    public static void trackCrop(Player player, String crop) {
        if (player == null || !AchievementSettingsLoader.isEnabled()) return;
        if (registrationTask != null) {
            pendingCrops.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(crop);
        }
        for (CustomAchievement achievement : CustomAchievementLoader.getAchievementsByTrigger("all_crops_harvested")) {
            if (!achievement.isEnabled()) continue;
            Advancement advancement = getAdvancementSafe(key(achievement.getId()));
            if (advancement != null && advancement.getCriteria().contains("harvested_" + crop)) {
                AdvancementProgress progress = player.getAdvancementProgress(advancement);
                if (progress != null) progress.awardCriteria("harvested_" + crop);
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        prepare(event.getPlayer());
    }

    private static void prepare(Player player) {
        if (!AchievementSettingsLoader.isEnabled() || registrationTask != null) return;
        award(player, "root");
        File file = new File(DrugsV2.getInstance().getDataFolder(),
                "data/achievements/" + player.getUniqueId() + ".yml");
        if (!file.isFile()) return;
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;
        for (String drug : DrugRegistry.getRegisteredDrugNames()) {
            if (legacy.getBoolean("connoisseur-used-" + drug)
                    && !legacy.getBoolean("native-migrated.connoisseur-used-" + drug)) {
                trackDrug(player, drug);
                // Keep legacy tracking available if the connoisseur advancement is disabled.
                boolean migrated = CustomAchievementLoader.getAchievementsByTrigger("all_drugs_used").stream()
                        .anyMatch(a -> a.isEnabled() && Bukkit.getAdvancement(key(a.getId())) != null);
                if (migrated) {
                    legacy.set("native-migrated.connoisseur-used-" + drug, true);
                    changed = true;
                }
            }
        }
        for (CustomAchievement achievement : CustomAchievementLoader.getAllAchievements()) {
            String id = achievement.getId();
            if (achievement.isEnabled() && legacy.getBoolean(id) && !legacy.getBoolean("native-migrated." + id)) {
                award(player, id);
                if (has(player, id)) {
                    legacy.set("native-migrated." + id, true);
                    changed = true;
                }
            }
        }
        if (changed) {
            try {
                legacy.save(file);
            } catch (IOException exception) {
                DrugsV2.getInstance().getLogger().warning("Could not save advancement migration: " + exception.getMessage());
            }
        }
    }
}
