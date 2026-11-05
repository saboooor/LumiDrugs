package com.drugs.overdose;

import com.drugs.addiction.AddictionManager;
import com.drugs.config.MechanicsConfig;
import com.drugs.drug.DrugRegistry;
import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OverdoseWarningsTest {
    @AfterEach void cleanup() { OverdoseWarnings.reset(); }
    private Player player() {
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
    @Test void riskStatesFollowActualToleranceLimitsAndCallOutWithdrawal() {
        assertEquals(OverdoseWarnings.Risk.NONE, OverdoseWarnings.assess(2, 10, false));
        assertEquals(OverdoseWarnings.Risk.NEAR_LIMIT, OverdoseWarnings.assess(9, 10, false));
        assertEquals(OverdoseWarnings.Risk.HIGH, OverdoseWarnings.assess(10, 10, false));
        assertEquals(OverdoseWarnings.Risk.WITHDRAWAL, OverdoseWarnings.assess(2, 10, true));
        assertEquals(OverdoseWarnings.Risk.WITHDRAWAL_NEAR, OverdoseWarnings.assess(9, 10, true));
        assertEquals(OverdoseWarnings.Risk.WITHDRAWAL_HIGH, OverdoseWarnings.assess(10, 10, true));
    }
    @Test void remindersAreThrottledButEscalationAndDeliberateRiskWarnSooner() {
        var player = player();
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL, false, 10_000);
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL, false, 11_000);
        verify(player, times(1)).sendMessage(anyString());
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL_HIGH, false, 11_000);
        verify(player, times(2)).sendMessage(anyString());
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL_HIGH, true, 11_100);
        verify(player, times(2)).sendMessage(anyString());
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL_HIGH, true, 12_000);
        verify(player, times(3)).sendMessage(anyString());
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL_HIGH, false, 72_000);
        verify(player, times(4)).sendMessage(anyString());
    }
    @Test void periodicChecksWarnForHiddenDrugRiskWithoutNamingItsIdentity() {
        var player = player();
        try (var registry = mockStatic(DrugRegistry.class);
             var addiction = mockStatic(AddictionManager.class);
             var tolerance = mockStatic(ToleranceTracker.class);
             var config = mockStatic(ToleranceConfigLoader.class)) {
            registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("joint", "fent"));
            addiction.when(() -> AddictionManager.hasActiveWithdrawal(player)).thenReturn(true);
            config.when(() -> ToleranceConfigLoader.getMaxTolerance(anyString())).thenReturn(10);
            tolerance.when(() -> ToleranceTracker.getToleranceLevel(player, "fent")).thenReturn(10);
            OverdoseWarnings.check(player);
            verify(player).sendMessage(argThat((String text) -> text.contains("OVERDOSE RISK") && text.contains("withdrawal")
                    && !text.contains("fent") && !text.contains("joint") && !text.contains("laced")));
            OverdoseWarnings.reset();
            clearInvocations(player);
            OverdoseWarnings.beforeUse(player, "fent");
            verify(player, times(1)).sendMessage(anyString());
        }
    }
    @Test void resolvedRiskAndDisconnectClearReminderState() {
        var player = player();
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.HIGH, false, 10_000);
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.NONE, false, 10_100);
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.HIGH, false, 10_200);
        verify(player, times(2)).sendMessage(anyString());
        var quit = mock(org.bukkit.event.player.PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        // Constructor-free instance exercises event cleanup without scheduling a server task.
        var listener = mock(OverdoseWarnings.class, CALLS_REAL_METHODS);
        listener.quit(quit);
        OverdoseWarnings.send(player, OverdoseWarnings.Risk.HIGH, false, 10_300);
        verify(player, times(3)).sendMessage(anyString());
    }
    @Test void disabledWarningsDoNotMessagePlayers() {
        try (var config = mockStatic(MechanicsConfig.class)) {
            config.when(MechanicsConfig::isOverdoseWarningsEnabled).thenReturn(false);
            var player = player();
            OverdoseWarnings.send(player, OverdoseWarnings.Risk.WITHDRAWAL_HIGH, true, 0);
            verify(player, never()).sendMessage(anyString());
        }
    }
}
