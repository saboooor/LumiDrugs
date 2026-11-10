package com.drugs.addiction;

import com.drugs.DrugsV2;
import com.drugs.achievements.AchievementManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnchantedGoldenAppleTest {
    @TempDir Path folder;

    @Test void consumptionResetsOnlyForAnUncancelledEnchantedApple() {
        var event = mock(PlayerItemConsumeEvent.class);
        var player = mock(Player.class);
        var item = mock(ItemStack.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getItem()).thenReturn(item);
        try (var manager = mockStatic(AddictionManager.class);
             var achievements = mockStatic(AchievementManager.class)) {
            manager.when(() -> AddictionManager.resetAllAddictions(player)).thenReturn(true);
            var listener = new AddictionListener();
            when(item.getType()).thenReturn(Material.GOLDEN_APPLE);
            listener.onEnchantedGoldenAppleConsume(event);
            when(item.getType()).thenReturn(Material.ENCHANTED_GOLDEN_APPLE);
            when(event.isCancelled()).thenReturn(true);
            listener.onEnchantedGoldenAppleConsume(event);
            manager.verifyNoInteractions();
            when(event.isCancelled()).thenReturn(false);
            listener.onEnchantedGoldenAppleConsume(event);
            manager.verify(() -> AddictionManager.resetAllAddictions(player), times(1));
            achievements.verify(() -> AchievementManager.processTrigger(player, "use_cure"));
            verify(event, never()).setCancelled(anyBoolean()); // Normal eating and buffs remain intact.
        }
    }

    @Test void resetRemovesAllDrugStatesAndStaysResetInSavedData() throws Exception {
        var field = AddictionManager.class.getDeclaredField("addictionData");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var data = (Map<UUID, Map<String, AddictionState>>) field.get(null);
        var fileField = AddictionManager.class.getDeclaredField("dataFile");
        fileField.setAccessible(true);
        var previousFile = fileField.get(null);
        UUID uuid = UUID.randomUUID(), other = UUID.randomUUID();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        var state = new AddictionState(10, 123, 456, 789);
        state.setWithdrawalActive(true);
        data.put(uuid, Map.of("glue", state, "fent", state, "retired_drug", state));
        data.put(other, Map.of("glue", new AddictionState(5, 0, 0, 0)));
        var profile = mock(AddictionDrugProfile.class);
        when(profile.getWithdrawalEffects()).thenReturn(List.of());
        try (var config = mockStatic(AddictionConfigLoader.class);
             var plugins = mockStatic(DrugsV2.class)) {
            config.when(() -> AddictionConfigLoader.getDrugProfile("glue")).thenReturn(profile);
            config.when(() -> AddictionConfigLoader.getDrugProfile("fent")).thenReturn(profile);
            assertTrue(AddictionManager.resetAllAddictions(player));
            assertFalse(data.containsKey(uuid));
            assertEquals(5, data.get(other).get("glue").getPoints());
            verify(profile, times(2)).getWithdrawalEffects();
            assertFalse(AddictionManager.resetAllAddictions(player));
            var file = folder.resolve("addiction_data.yml").toFile();
            fileField.set(null, file);
            AddictionManager.saveData();
            var saved = YamlConfiguration.loadConfiguration(file);
            assertFalse(saved.contains("players." + uuid));
            assertEquals(5, saved.getDouble("players." + other + ".glue.points"));
        } finally {
            data.remove(uuid);
            data.remove(other);
            fileField.set(null, previousFile);
        }
    }
}
