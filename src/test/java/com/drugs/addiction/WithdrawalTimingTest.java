package com.drugs.addiction;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.overdose.OverdoseWarnings;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WithdrawalTimingTest {
    private static final long START = 1_000_000L;
    private AddictionDrugProfile profile(boolean decay, double rate, double threshold) {
        return new AddictionDrugProfile("cocaine", true, 1, threshold, 5, decay, rate, List.of());
    }
    private AddictionState state(double points) { return new AddictionState(points, START - 60_000, 0, START); }

    @Test void recoveryCountsDownFromRemainingAddictionAndEndsBelowThreshold() {
        var profile = profile(true, 0.5, 3);
        var state = state(4);
        assertEquals(2400, WithdrawalTiming.remainingTicks(state, profile, START));
        assertEquals(1200, WithdrawalTiming.remainingTicks(state, profile, START + 60_000));
        assertEquals(1, WithdrawalTiming.remainingTicks(state, profile, START + 120_000));
        assertEquals(0, WithdrawalTiming.remainingTicks(state, profile, START + 120_001));
    }
    @Test void heartbeatDecayDoesNotRestartTheRecoveryClock() throws Exception {
        var profile = profile(true, 0.5, 3);
        var state = state(4);
        Method decay = AddictionManager.class.getDeclaredMethod("applyDecay", AddictionState.class, AddictionDrugProfile.class, long.class);
        decay.setAccessible(true);
        decay.invoke(null, state, profile, START + 60_000);
        assertEquals(3.5, state.getPoints());
        assertEquals(1200, WithdrawalTiming.remainingTicks(state, profile, START + 60_000));
        assertEquals(600, WithdrawalTiming.remainingTicks(state, profile, START + 90_000));
        decay.invoke(null, state, profile, START + 120_001);
        assertEquals(0, WithdrawalTiming.remainingTicks(state, profile, START + 120_001));
    }
    @Test void nonDecayingAndInvalidDecayRatesAreTrulyIndefiniteInsteadOfFakeCountdowns() {
        var state = state(4);
        assertEquals(-1, WithdrawalTiming.remainingTicks(state, profile(false, 1, 3), START));
        assertEquals(-1, WithdrawalTiming.remainingTicks(state, profile(false, 1, 3), START + 86_400_000));
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertEquals(-1, WithdrawalTiming.remainingTicks(state, profile(true, rate, 3), START));
        assertEquals(-1, WithdrawalTiming.remainingTicks(state, profile(true, 1, 0), START));
    }
    @Test void curesAndRecoveredStatesDoNotHaveActiveCountdowns() {
        var profile = profile(true, 0.5, 3);
        assertEquals(0, WithdrawalTiming.remainingTicks(state(2), profile, START));
        var blocked = state(4);
        blocked.setWithdrawalBlockedUntilMillis(START + 30_000);
        assertEquals(0, WithdrawalTiming.remainingTicks(blocked, profile, START));
    }

    @Test void managerReportsOnlyActiveUnblockedWithdrawalWithItsRealRemainingTime() throws Exception {
        UUID uuid = UUID.randomUUID();
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        var field = AddictionManager.class.getDeclaredField("addictionData");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var data = (Map<UUID, Map<String, AddictionState>>) field.get(null);
        var state = state(4);
        state.setWithdrawalActive(true);
        data.put(uuid, Map.of("cocaine", state));
        try (var config = mockStatic(AddictionConfigLoader.class)) {
            config.when(AddictionConfigLoader::getSettings).thenReturn(new AddictionSettings());
            config.when(() -> AddictionConfigLoader.getDrugProfile("cocaine")).thenReturn(profile(true, 0.5, 3));
            assertEquals(Map.of("cocaine", 2400), AddictionManager.getWithdrawalTimers(player, START));
            assertEquals(Map.of("cocaine", 1200), AddictionManager.getWithdrawalTimers(player, START + 60_000));
            assertTrue(AddictionManager.getWithdrawalTimers(player, START + 120_001).isEmpty());
            state.setWithdrawalBlockedUntilMillis(START + 30_000);
            assertTrue(AddictionManager.getWithdrawalTimers(player, START).isEmpty());
            state.setWithdrawalBlockedUntilMillis(0);
            state.setWithdrawalActive(false);
            assertTrue(AddictionManager.getWithdrawalTimers(player, START).isEmpty());
        } finally { data.remove(uuid); }
    }
    @Test void realWithdrawalLifecycleStopsOnceAddictionHasDecayed() throws Exception {
        var profile = profile(true, 0.5, 3);
        var state = state(4);
        var player = mock(Player.class);
        Method process = AddictionManager.class.getDeclaredMethod("processWithdrawal", Player.class, AddictionState.class, AddictionDrugProfile.class, long.class);
        Method decay = AddictionManager.class.getDeclaredMethod("applyDecay", AddictionState.class, AddictionDrugProfile.class, long.class);
        process.setAccessible(true); decay.setAccessible(true);
        var instance = DrugsV2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        instance.set(null, null);
        try (var registry = mockStatic(DrugRegistry.class); var warnings = mockStatic(OverdoseWarnings.class)) {
            process.invoke(null, player, state, profile, START);
            assertTrue(state.isWithdrawalActive());
            // Milk reapplication can run without a heartbeat; it must not restart time.
            process.invoke(null, player, state, profile, START + 60_000);
            assertEquals(1200, WithdrawalTiming.remainingTicks(state, profile, START + 60_000));
            verify(player, times(1)).sendMessage(anyString());
            decay.invoke(null, state, profile, START + 120_001);
            process.invoke(null, player, state, profile, START + 120_001);
            assertFalse(state.isWithdrawalActive());
        } finally { instance.set(null, previous); }
    }
}
