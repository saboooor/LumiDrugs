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
}
