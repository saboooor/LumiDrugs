package com.drugs.achievements;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AchievementSettingsLoaderTest {
    @TempDir Path directory;

    @Test void readsUnifiedSettingsAndResetsRemovedOverridesOnReload() throws Exception {
        Path config = directory.resolve("achievements.yml");
        try {
            Files.writeString(config, "enabled: true\nnotifications:\n  chat_message: false\n  sound: false\nfirst-dose:\n  enabled: false\n  trigger: first_drug_use\n");
            AchievementSettingsLoader.load(directory.toFile());
            assertTrue(AchievementSettingsLoader.isEnabled());
            assertFalse(AchievementSettingsLoader.isChatNotificationsEnabled());
            assertFalse(AchievementSettingsLoader.isSoundNotificationsEnabled());
            assertFalse(AchievementSettingsLoader.isAchievementEnabled("first-dose"));
            assertTrue(AchievementSettingsLoader.isAchievementEnabled("custom"));
            Files.writeString(config, "first-dose:\n  trigger: first_drug_use\n");
            AchievementSettingsLoader.load(directory.toFile());
            assertTrue(AchievementSettingsLoader.isChatNotificationsEnabled());
            assertTrue(AchievementSettingsLoader.isSoundNotificationsEnabled());
            assertTrue(AchievementSettingsLoader.isAchievementEnabled("first-dose"));
            Files.writeString(config, "enabled: false\n");
            AchievementSettingsLoader.load(directory.toFile());
            assertFalse(AchievementSettingsLoader.isAchievementEnabled("custom"));
        } finally {
            Files.writeString(config, "enabled: true\n");
            AchievementSettingsLoader.load(directory.toFile());
        }
    }
}
