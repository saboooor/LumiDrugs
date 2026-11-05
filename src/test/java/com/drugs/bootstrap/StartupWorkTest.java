package com.drugs.bootstrap;

import com.drugs.DrugsV2;
import com.drugs.achievements.AchievementSettingsLoader;
import com.drugs.achievements.CustomAchievement;
import com.drugs.achievements.CustomAchievementLoader;
import com.drugs.achievements.NativeAdvancements;
import com.drugs.bong.BongListener;
import com.drugs.bong.BongRegistry;

import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StartupWorkTest {
    @Test void cartelIntegrationIsCreatedBeforeDeferredStartupRuns() {
        DrugsV2 plugin = mock(DrugsV2.class);
        when(plugin.getCommand(anyString())).thenReturn(mock(org.bukkit.command.PluginCommand.class));
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        doCallRealMethod().when(plugin).onEnable();
        try (var progress = mockStatic(com.drugs.quests.ProgressQuests.class);
             var dealers = mockConstruction(com.drugs.cartel.CartelDealer.class);
             var enforcers = mockConstruction(com.drugs.cartel.CartelEnforcer.class);
             var startup = mockConstruction(PluginStartup.class, (created, context) -> {
                 assertEquals(1, dealers.constructed().size());
                 assertEquals(1, enforcers.constructed().size());
             })) {
            plugin.onEnable();
            verify(dealers.constructed().getFirst()).setEnforcer(enforcers.constructed().getFirst());
            progress.verify(() -> com.drugs.quests.ProgressQuests.create(plugin));
            verify(startup.constructed().getFirst()).start();
            verify(enforcers.constructed().getFirst(), never()).start();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @Test void advancementRegistrationIsDeferredAndLimitedToOnePerTick() throws Exception {
        DrugsV2 plugin = mock(DrugsV2.class);
        var instance = DrugsV2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, plugin);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        UnsafeValues unsafe = mock(UnsafeValues.class);
        CustomAchievement achievement = mock(CustomAchievement.class);
        when(achievement.isEnabled()).thenReturn(true);
        when(achievement.getId()).thenReturn("first-dose");
        when(achievement.getTitle()).thenReturn("First Dose");
        when(achievement.getDescription()).thenReturn("Use a drug");
        when(achievement.getTrigger()).thenReturn("first_drug_use");
        when(achievement.getCompletedIcon()).thenReturn(Material.BOOK);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenReturn(task);
        when(unsafe.loadAdvancement(any(NamespacedKey.class), anyString())).thenReturn(mock(Advancement.class));
        try (var bukkit = mockStatic(Bukkit.class);
             var settings = mockStatic(AchievementSettingsLoader.class);
             var loader = mockStatic(CustomAchievementLoader.class)) {
            settings.when(AchievementSettingsLoader::isEnabled).thenReturn(true);
            loader.when(CustomAchievementLoader::getAllAchievements).thenReturn(List.of(achievement));
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            NativeAdvancements.reload();
            verifyNoInteractions(unsafe);
            var callback = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(plugin), callback.capture(), eq(1L), eq(1L));
            callback.getValue().run();
            verify(unsafe, times(1)).loadAdvancement(any(NamespacedKey.class), anyString());
            verify(task, never()).cancel();
            callback.getValue().run();
            verify(unsafe, times(2)).loadAdvancement(any(NamespacedKey.class), anyString());
            verify(task).cancel();
            verify(unsafe, never()).removeAdvancement(any());
        } finally {
            NativeAdvancements.shutdown();
        }
    }

    @Test void bongRestorationDoesNotForceLoadChunks() {
        UUID worldId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(world.isChunkLoaded(10, 20)).thenReturn(false);
        Location anchor = new Location(world, 160, 64, 320);
        BongListener listener = mock(BongListener.class);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
            BongRegistry.put(anchor, new BongRegistry.BongData(anchor, 0, List.of(), null, 10));
            BongRegistry.respawnMissing(listener);
            verify(world, never()).getChunkAt(anyInt(), anyInt());
            verifyNoInteractions(listener);
            assertNotNull(BongRegistry.get(anchor));
            BongRegistry.remove(anchor);
        }
    }
}
