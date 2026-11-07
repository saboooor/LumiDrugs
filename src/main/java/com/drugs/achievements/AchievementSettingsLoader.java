package com.drugs.achievements;

import com.drugs.DrugsV2;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Global and per-achievement toggles from the single achievements.yml configuration. */
public final class AchievementSettingsLoader {
    private static boolean enabled = true;
    private static boolean chatNotifications = true;
    private static boolean soundNotifications = true;
    private static final Set<String> disabledAchievements = new HashSet<>();

    public static void load(File dataFolder) {
        File file = new File(dataFolder, "achievements.yml");
        if (!file.exists()) DrugsV2.getInstance().saveResource("achievements.yml", false);
        var config = YamlConfiguration.loadConfiguration(file);
        enabled = config.getBoolean("enabled", true);
        chatNotifications = config.getBoolean("notifications.chat_message", true);
        soundNotifications = config.getBoolean("notifications.sound", true);
        disabledAchievements.clear();
        for (String id : config.getKeys(false)) {
            var section = config.getConfigurationSection(id);
            if (section != null && section.contains("trigger") && !section.getBoolean("enabled", true)) {
                disabledAchievements.add(id);
            }
        }
    }

    public static boolean isEnabled() { return enabled; }
    public static boolean isChatNotificationsEnabled() { return chatNotifications; }
    public static boolean isSoundNotificationsEnabled() { return soundNotifications; }
    public static boolean isAchievementEnabled(String id) {
        return enabled && !disabledAchievements.contains(id);
    }
}
