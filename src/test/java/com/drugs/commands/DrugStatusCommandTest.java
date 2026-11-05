package com.drugs.commands;

import com.drugs.status.DrugStatusTracker;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugStatusCommandTest {
    @Test void soberMessageIsExplicit() {
        Player player = mock(Player.class);
        when(player.hasPermission("drugs.status")).thenReturn(true);
        try (var tracker = mockStatic(DrugStatusTracker.class)) {
            tracker.when(() -> DrugStatusTracker.getStatus(player)).thenReturn(Map.of());
            DrugStatusCommand.show(player);
            verify(player).sendMessage(contains("You are fully sober."));
        }
    }

    @Test void activeDrugIsNamed() {
        Player player = mock(Player.class);
        when(player.hasPermission("drugs.status")).thenReturn(true);
        try (var tracker = mockStatic(DrugStatusTracker.class)) {
            tracker.when(() -> DrugStatusTracker.getStatus(player)).thenReturn(Map.of("cocaine", new DrugStatusTracker.Status(1800, 0, List.of("speed 2"))));
            DrugStatusCommand.show(player);
            verify(player).sendMessage(contains("You are not sober."));
            verify(player).sendMessage(contains("cocaine"));
            verify(player).sendMessage(contains("remaining: 1m 30s"));
            verify(player).sendMessage(contains("speed 2"));
        }
    }

    @Test void pendingDrugShowsDelayAndDuration() {
        Player player = mock(Player.class);
        when(player.hasPermission("drugs.status")).thenReturn(true);
        try (var tracker = mockStatic(DrugStatusTracker.class)) {
            tracker.when(() -> DrugStatusTracker.getStatus(player)).thenReturn(
                    Map.of("salvia", new DrugStatusTracker.Status(900, 60, List.of("block disguise pending"))));
            DrugStatusCommand.show(player);
            verify(player).sendMessage(contains("kicks in: 3s; duration: 45s"));
        }
    }

    @Test void durationFormattingRoundsUpAndSupportsInfiniteEffects() {
        assertEquals("1s", DrugStatusCommand.formatDuration(1));
        assertEquals("2s", DrugStatusCommand.formatDuration(21));
        assertEquals("1m 0s", DrugStatusCommand.formatDuration(1200));
        assertEquals("1h 1m 1s", DrugStatusCommand.formatDuration(73220));
        assertEquals("indefinite", DrugStatusCommand.formatDuration(-1));
    }

    @Test void untrackedWeaknessDoesNotShowFullySober() {
        Player player = mock(Player.class);
        when(player.hasPermission("drugs.status")).thenReturn(true);
        try (var tracker = mockStatic(DrugStatusTracker.class)) {
            tracker.when(() -> DrugStatusTracker.getStatus(player)).thenReturn(
                    Map.of("effect/minecraft/weakness", new DrugStatusTracker.Status(600, 0, List.of("weakness 1"))));
            DrugStatusCommand.show(player);
            verify(player, never()).sendMessage(contains("fully sober"));
            verify(player).sendMessage(contains("source unknown"));
            verify(player).sendMessage(contains("remaining: 30s"));
            verify(player).sendMessage(contains("weakness 1"));
        }
    }

    @Test void withdrawalIsLabeledSeparatelyFromDrugUse() {
        Player player = mock(Player.class);
        when(player.hasPermission("drugs.status")).thenReturn(true);
        try (var tracker = mockStatic(DrugStatusTracker.class)) {
            tracker.when(() -> DrugStatusTracker.getStatus(player)).thenReturn(
                    Map.of("withdrawal/cocaine", new DrugStatusTracker.Status(-1, 0, List.of("weakness 1"))));
            DrugStatusCommand.show(player);
            verify(player).sendMessage(contains("Withdrawal: cocaine"));
            verify(player).sendMessage(contains("until treated (no natural recovery)"));
            verify(player, never()).sendMessage(contains("fully sober"));
        }
    }

    @Test void consoleGetsPlayerOnlyMessage() {
        CommandSender console = mock(CommandSender.class);
        DrugStatusCommand.show(console);
        verify(console).sendMessage(contains("Only players"));
    }

    @Test void permissionIsRequired() {
        Player player = mock(Player.class);
        try (var tracker = mockStatic(DrugStatusTracker.class)) {
            DrugStatusCommand.show(player);
            verify(player).sendMessage(contains("permission"));
            tracker.verifyNoInteractions();
        }
    }

    @Test void displaysOverdoseLimitsToleranceAttemptsAndAddiction() {
        Player player = mock(Player.class);
        when(player.hasPermission("drugs.status")).thenReturn(true);
        try (var tracker = mockStatic(DrugStatusTracker.class);
             var tolerance = mockStatic(com.drugs.tolerance.ToleranceTracker.class);
             var tolConfig = mockStatic(com.drugs.tolerance.ToleranceConfigLoader.class);
             var addiction = mockStatic(com.drugs.addiction.AddictionManager.class);
             var addConfig = mockStatic(com.drugs.addiction.AddictionConfigLoader.class);
             var overdose = mockStatic(com.drugs.overdose.OverdoseEffectManager.class)) {
            tracker.when(() -> DrugStatusTracker.getStatus(player)).thenReturn(Map.of());
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getTrackedDrugs(player))
                    .thenReturn( java.util.Set.of("cocaine", "heroin"));
            addiction.when(() -> com.drugs.addiction.AddictionManager.getTrackedDrugs(player))
                    .thenReturn(java.util.Set.of("cocaine"));
            overdose.when(com.drugs.overdose.OverdoseEffectManager::getThreshold).thenReturn(3);

            // Cocaine: at overdose limit (5/5), 2/3 OD attempts, addicted (12/10)
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getToleranceLevel(player, "cocaine")).thenReturn(5);
            tolConfig.when(() -> com.drugs.tolerance.ToleranceConfigLoader.getMaxTolerance("cocaine")).thenReturn(5);
            tolConfig.when(() -> com.drugs.tolerance.ToleranceConfigLoader.getEffectivenessMultiplier("cocaine", 5)).thenReturn(0.0);
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getOverdoseAttempts(player, "cocaine")).thenReturn(2);
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getRemainingDecaySeconds(player, "cocaine")).thenReturn(120);

            var state = new com.drugs.addiction.AddictionState(12.0, 0L, 0L, 0L);
            var profile = mock(com.drugs.addiction.AddictionDrugProfile.class);
            when(profile.isAddictive()).thenReturn(true);
            when(profile.getAddictedAtPoints()).thenReturn(10.0);
            addiction.when(() -> com.drugs.addiction.AddictionManager.getState(player, "cocaine")).thenReturn(state);
            addConfig.when(() -> com.drugs.addiction.AddictionConfigLoader.getDrugProfile("cocaine")).thenReturn(profile);

            // Heroin: near overdose limit (4/5)
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getToleranceLevel(player, "heroin")).thenReturn(4);
            tolConfig.when(() -> com.drugs.tolerance.ToleranceConfigLoader.getMaxTolerance("heroin")).thenReturn(5);
            tolConfig.when(() -> com.drugs.tolerance.ToleranceConfigLoader.getEffectivenessMultiplier("heroin", 4)).thenReturn(0.25);
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getOverdoseAttempts(player, "heroin")).thenReturn(0);
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getRemainingDecaySeconds(player, "heroin")).thenReturn(60);

            DrugStatusCommand.show(player);
            verify(player).sendMessage(contains("Overdose Limits, Tolerance & Addiction:"));
            verify(player).sendMessage(contains("5/5 tolerance"));
            verify(player).sendMessage(contains("AT OVERDOSE LIMIT!"));
            verify(player).sendMessage(contains("OD attempts:"));
            verify(player).sendMessage(contains("2/3"));
            verify(player).sendMessage(contains("12.0/10.0"));
            verify(player).sendMessage(contains("ADDICTED"));
            verify(player).sendMessage(contains("4/5 tolerance"));
            verify(player).sendMessage(contains("NEAR OVERDOSE LIMIT"));
        }
    }
}
