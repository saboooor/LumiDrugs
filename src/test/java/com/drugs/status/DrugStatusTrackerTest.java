package com.drugs.status;

import com.drugs.DrugsV2;
import com.drugs.effects.SalviaEffectManager;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugStatusTrackerTest {
    private final DrugStatusTracker.Status active = new DrugStatusTracker.Status(1200, 0, List.of("speed 1"));
    private Player player;
    private PersistentDataContainer data;
    private final Map<NamespacedKey, String> stored = new HashMap<>();
    private final NamespacedKey statusKey = new NamespacedKey("lumidrugs", "drug_status/minecraft/speed");

    @BeforeEach void setup() throws Exception {
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        data = mock(PersistentDataContainer.class);
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(player.getActivePotionEffects()).thenReturn(List.of());
        when(data.getKeys()).thenAnswer(a -> new HashSet<>(stored.keySet()));
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(a -> stored.get(a.getArgument(0)));
        doAnswer(a -> { stored.put(a.getArgument(0), a.getArgument(2)); return null; })
                .when(data).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        doAnswer(a -> { stored.remove(a.getArgument(0)); return null; }).when(data).remove(any());
    }

    @Test void untrackedWeaknessIsVisibleWithDurationWithoutInventingDrugSource() {
        NamespacedKey weaknessKey = new NamespacedKey("lumidrugs", "drug_status/minecraft/weakness");
        var weakness = new DrugStatusTracker.Status(600, 0, List.of("weakness 1"));
        assertEquals(Map.of("effect/minecraft/weakness", weakness),
                DrugStatusTracker.potionStatus(data, Map.of(weaknessKey, weakness)));
    }

    @Test void withdrawalIsReportedAsWithdrawal() {
        stored.put(statusKey, "withdrawal/cocaine");
        assertEquals(Map.of("withdrawal/cocaine", active),
                DrugStatusTracker.potionStatus(data, Map.of(statusKey, active)));
    }

    @Test void oversizedLegacyWithdrawalTimersAreReplacedWithoutOverwritingOtherSources() {
        assertTrue(DrugStatusTracker.needsWithdrawalRefresh("withdrawal/cocaine", "withdrawal/cocaine", 99999, 1200));
        assertTrue(DrugStatusTracker.needsWithdrawalRefresh("withdrawal/cocaine", "withdrawal/cocaine", -1, 1200));
        assertFalse(DrugStatusTracker.needsWithdrawalRefresh("withdrawal/cocaine", "withdrawal/cocaine", 1220, 1200));
        assertFalse(DrugStatusTracker.needsWithdrawalRefresh("cocaine", "withdrawal/cocaine", 99999, 1200));
        assertFalse(DrugStatusTracker.needsWithdrawalRefresh("withdrawal/meth", "withdrawal/cocaine", 99999, 1200));
        assertFalse(DrugStatusTracker.needsWithdrawalRefresh("withdrawal/cocaine", "withdrawal/cocaine", 1200, -1));
    }

    @Test void withdrawalStatusUsesRecoveryCountdownEvenWithoutLivePotionEffects() {
        try (var addiction = mockStatic(com.drugs.addiction.AddictionManager.class)) {
            addiction.when(() -> com.drugs.addiction.AddictionManager.getWithdrawalTimers(player))
                    .thenReturn(Map.of("cocaine", 2400), Map.of("cocaine", 1200), Map.of());
            var first = DrugStatusTracker.getStatus(player).get("withdrawal/cocaine");
            var later = DrugStatusTracker.getStatus(player).get("withdrawal/cocaine");
            assertEquals(2400, first.remainingTicks());
            assertEquals(1200, later.remainingTicks());
            assertEquals(List.of("withdrawal symptoms"), later.effects());
            assertTrue(DrugStatusTracker.getStatus(player).isEmpty());
        }
    }

    @Test void trackedAndUntrackedEffectsAppearTogetherWithoutDuplicates() {
        stored.put(statusKey, "cocaine");
        NamespacedKey weaknessKey = new NamespacedKey("lumidrugs", "drug_status/minecraft/weakness");
        var weakness = new DrugStatusTracker.Status(600, 0, List.of("weakness 1"));
        assertEquals(Map.of("cocaine", active, "effect/minecraft/weakness", weakness),
                DrugStatusTracker.potionStatus(data, Map.of(statusKey, active, weaknessKey, weakness)));
    }

    @Test void attributedLiveEffectReportsDrugAndExpires() {
        stored.put(statusKey, "cocaine");
        assertEquals(Map.of("cocaine", active), DrugStatusTracker.potionStatus(data, Map.of(statusKey, active)));
        assertTrue(DrugStatusTracker.potionStatus(data, Map.of()).isEmpty());
        assertFalse(stored.containsKey(statusKey));
    }

    @Test void multipleDrugsAreReportedOnceEach() {
        NamespacedKey second = new NamespacedKey("lumidrugs", "drug_status/minecraft/strength");
        NamespacedKey third = new NamespacedKey("lumidrugs", "drug_status/minecraft/nausea");
        stored.put(statusKey, "cocaine");
        stored.put(second, "cocaine");
        stored.put(third, "lsd");
        var strength = new DrugStatusTracker.Status(600, 0, List.of("strength 2"));
        var nausea = new DrugStatusTracker.Status(3600, 0, List.of("nausea 1"));
        var result = DrugStatusTracker.potionStatus(data, Map.of(statusKey, active, second, strength, third, nausea));
        assertEquals(Set.of("cocaine", "lsd"), result.keySet());
        assertEquals(1200, result.get("cocaine").remainingTicks());
        assertEquals(Set.of("speed 1", "strength 2"), new HashSet<>(result.get("cocaine").effects()));
        assertEquals(3600, result.get("lsd").remainingTicks());
    }

    @Test void pendingSalviaDoesNotReportSober() throws Exception {
        DrugsV2 plugin = mock(DrugsV2.class);
        SalviaEffectManager salvia = mock(SalviaEffectManager.class);
        when(plugin.getSalviaEffectManager()).thenReturn(salvia);
        when(salvia.hasEffect(player)).thenReturn(true);
        when(salvia.isPending(player)).thenReturn(true);
        when(salvia.getDelayTicks(player)).thenReturn(60);
        when(salvia.getRemainingTicks(player)).thenReturn(900);
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
        assertEquals(Map.of("salvia", new DrugStatusTracker.Status(900, 60, List.of("block disguise pending"))), DrugStatusTracker.getStatus(player));
        when(salvia.isPending(player)).thenReturn(false);
        when(salvia.getDelayTicks(player)).thenReturn(0);
        assertEquals(Map.of("salvia", new DrugStatusTracker.Status(900, 0, List.of("block disguise"))), DrugStatusTracker.getStatus(player));
        when(salvia.hasEffect(player)).thenReturn(false);
        assertTrue(DrugStatusTracker.getStatus(player).isEmpty());
    }

    @Test void deathClearsDrugTrackingButKeepsOtherPlayerData() {
        NamespacedKey unrelated = new NamespacedKey("otherplugin", "data");
        stored.put(statusKey, "cocaine");
        stored.put(unrelated, "keep");
        PlayerDeathEvent event = mock(PlayerDeathEvent.class);
        when(event.getEntity()).thenReturn(player);
        new DrugStatusTracker().onDeath(event);
        assertEquals(Map.of(unrelated, "keep"), stored);
    }
}
