package com.drugs.achievements;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeAdvancementsTest {
    @AfterEach void cleanupStaticState() throws Exception {
        Field achievementsField = CustomAchievementLoader.class.getDeclaredField("achievements");
        achievementsField.setAccessible(true);
        ((Map<?, ?>) achievementsField.get(null)).clear();

        Field triggerMapField = CustomAchievementLoader.class.getDeclaredField("triggerMap");
        triggerMapField.setAccessible(true);
        ((Map<?, ?>) triggerMapField.get(null)).clear();
    }

    private static void registerTestAchievement(CustomAchievement achievement) throws Exception {
        Field achievementsField = CustomAchievementLoader.class.getDeclaredField("achievements");
        achievementsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, CustomAchievement> achievements = (Map<String, CustomAchievement>) achievementsField.get(null);
        achievements.put(achievement.getId(), achievement);

        Field triggerMapField = CustomAchievementLoader.class.getDeclaredField("triggerMap");
        triggerMapField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, List<CustomAchievement>> triggerMap = (Map<String, List<CustomAchievement>>) triggerMapField.get(null);
        triggerMap.computeIfAbsent(achievement.getTrigger(), k -> new ArrayList<>()).add(achievement);
    }

    @Test void grantAwardsOnlyRemainingNativeCriteria() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player player = mock(Player.class);
            Advancement advancement = mock(Advancement.class);
            AdvancementProgress progress = mock(AdvancementProgress.class);
            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/first-dose")))
                    .thenReturn(advancement);
            when(player.getAdvancementProgress(advancement)).thenReturn(progress);
            when(progress.getRemainingCriteria()).thenReturn(List.of("unlock"));
            NativeAdvancements.award(player, "first-dose");
            verify(progress).awardCriteria("unlock");
            when(progress.getRemainingCriteria()).thenReturn(List.of());
            NativeAdvancements.award(player, "first-dose");
            verify(progress, times(1)).awardCriteria("unlock");
            verify(player, never()).sendMessage(anyString());
        }
    }

    @Test void completionUsesMinecraftProgress() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player player = mock(Player.class);
            Advancement advancement = mock(Advancement.class);
            AdvancementProgress progress = mock(AdvancementProgress.class);
            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/first-dose")))
                    .thenReturn(advancement);
            when(player.getAdvancementProgress(advancement)).thenReturn(progress);
            when(progress.isDone()).thenReturn(false, true);
            assertFalse(NativeAdvancements.has(player, "first-dose"));
            assertTrue(NativeAdvancements.has(player, "first-dose"));
            assertFalse(NativeAdvancements.has(player, "missing"));
            NativeAdvancements.award(player, "missing");
        }
    }

    @Test void trackCropAwardsMultiCriterionCropAdvancements() throws Exception {
        registerTestAchievement(new CustomAchievement(
                "master-botanist", "Master Botanist", "Harvest all crops", "all_crops_harvested", Material.GRAY_DYE, Material.SPORE_BLOSSOM));

        try (var bukkit = mockStatic(Bukkit.class)) {
            Player player = mock(Player.class);
            Advancement advancement = mock(Advancement.class);
            AdvancementProgress progress = mock(AdvancementProgress.class);
            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/master-botanist")))
                    .thenReturn(advancement);
            when(advancement.getCriteria()).thenReturn(Set.of("harvested_cannabis", "harvested_coca"));
            when(player.getAdvancementProgress(advancement)).thenReturn(progress);

            NativeAdvancements.trackCrop(player, "cannabis");
            verify(progress).awardCriteria("harvested_cannabis");
        }
    }

    @Test void processTriggerMatchesGrowingCraftingAndSpecificTriggers() throws Exception {
        registerTestAchievement(new CustomAchievement(
                "green-thumb", "Green Thumb", "Plant a seed", "plant_crop", Material.WHEAT_SEEDS, Material.WHEAT_SEEDS));
        registerTestAchievement(new CustomAchievement(
                "coca-planter", "Coca Planter", "Plant coca", "plant_specific_crop", Material.SUGAR_CANE, Material.SUGAR_CANE, null, "coca", 0));
        registerTestAchievement(new CustomAchievement(
                "home-chemist", "Home Chemist", "Craft a drug", "craft_drug", Material.BREWING_STAND, Material.BREWING_STAND));
        registerTestAchievement(new CustomAchievement(
                "meth-cook", "Meth Cook", "Craft meth", "craft_specific", Material.QUARTZ, Material.QUARTZ, "meth", null, 0));

        try (var bukkit = mockStatic(Bukkit.class)) {
            Player player = mock(Player.class);
            Advancement greenThumb = mock(Advancement.class);
            Advancement cocaPlanter = mock(Advancement.class);
            Advancement homeChemist = mock(Advancement.class);
            Advancement methCook = mock(Advancement.class);
            AdvancementProgress greenThumbProgress = mock(AdvancementProgress.class);
            AdvancementProgress cocaProgress = mock(AdvancementProgress.class);
            AdvancementProgress chemistProgress = mock(AdvancementProgress.class);
            AdvancementProgress methProgress = mock(AdvancementProgress.class);
            for (AdvancementProgress p : List.of(greenThumbProgress, cocaProgress, chemistProgress, methProgress)) {
                when(p.getRemainingCriteria()).thenReturn(List.of("unlock"));
            }

            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/green-thumb"))).thenReturn(greenThumb);
            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/coca-planter"))).thenReturn(cocaPlanter);
            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/home-chemist"))).thenReturn(homeChemist);
            bukkit.when(() -> Bukkit.getAdvancement(new NamespacedKey("lumidrugs", "achievements/meth-cook"))).thenReturn(methCook);

            when(player.getAdvancementProgress(greenThumb)).thenReturn(greenThumbProgress);
            when(player.getAdvancementProgress(cocaPlanter)).thenReturn(cocaProgress);
            when(player.getAdvancementProgress(homeChemist)).thenReturn(chemistProgress);
            when(player.getAdvancementProgress(methCook)).thenReturn(methProgress);

            AchievementManager.processTrigger(player, "plant_crop");
            verify(greenThumbProgress).awardCriteria("unlock");

            AchievementManager.processTrigger(player, "plant_specific_crop", Map.of("crop_id", "cannabis"));
            verify(cocaProgress, never()).awardCriteria(anyString());

            AchievementManager.processTrigger(player, "plant_specific_crop", Map.of("crop_id", "coca"));
            verify(cocaProgress).awardCriteria("unlock");

            AchievementManager.processTrigger(player, "craft_drug");
            verify(chemistProgress).awardCriteria("unlock");

            AchievementManager.processTrigger(player, "craft_specific", Map.of("drug_id", "lsd"));
            verify(methProgress, never()).awardCriteria(anyString());

            AchievementManager.processTrigger(player, "craft_specific", Map.of("drug_id", "meth"));
            verify(methProgress).awardCriteria("unlock");
        }
    }

    @Test void defaultAchievementConfigsContainValidMaterialsAndExpectedGrowingAchievements() {
        YamlConfiguration achievementsYaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/achievements.yml"));
        YamlConfiguration settingsYaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/achievement_settings.yml"));
        ConfigurationSection settingsSection = settingsYaml.getConfigurationSection("achievements");
        assertNotNull(settingsSection, "Missing achievements section in achievement_settings.yml");

        for (String key : List.of(
                "first-dose", "connoisseur", "risky", "maxed-three", "clean-slate",
                "green-thumb", "first-harvest", "master-botanist", "strain-mutator",
                "rare-strain", "industrial-grower", "hired-help", "home-chemist",
                "pure-product", "master-refiner", "flavor-infuser", "secret-ingredient",
                "bong-rip", "road-to-recovery", "street-dealer", "cartel-associate",
                "contract-fulfilled"
        )) {
            ConfigurationSection ach = achievementsYaml.getConfigurationSection(key);
            assertNotNull(ach, "Missing achievement " + key + " in achievements.yml");
            assertNotNull(ach.getString("title"), "Missing title for " + key);
            assertNotNull(ach.getString("description"), "Missing description for " + key);
            assertNotNull(ach.getString("trigger"), "Missing trigger for " + key);
            assertNotNull(Material.matchMaterial(ach.getString("icon", "")), "Invalid icon for " + key);
            assertNotNull(Material.matchMaterial(ach.getString("completed_icon", "")), "Invalid completed_icon for " + key);

            ConfigurationSection setting = settingsSection.getConfigurationSection(key);
            assertNotNull(setting, "Missing achievement " + key + " in achievement_settings.yml");
            assertTrue(setting.getBoolean("enabled"), "Achievement " + key + " should be enabled by default");
        }
    }
}
