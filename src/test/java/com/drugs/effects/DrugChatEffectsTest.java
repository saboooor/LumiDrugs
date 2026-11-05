package com.drugs.effects;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DrugChatEffectsTest {
    @Test void drugFamiliesProduceDistinctSpeechAndExcludeWithdrawal() {
        assertEquals("h-hello", DrugChatEffects.transformWord("hello", DrugChatEffects.style("cocaine")));
        assertEquals("hellooo", DrugChatEffects.transformWord("hello", DrugChatEffects.style("joint")));
        assertEquals("hello...", DrugChatEffects.transformWord("hello", DrugChatEffects.style("lsd")));
        assertNull(DrugChatEffects.style("withdrawal/cocaine"));
        assertNull(DrugChatEffects.style("vape"));
    }

    @Test void chatPreservesFormattingAndStopsAfterCleanup() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        Component original = Component.text("Hello, friend! Third.", net.kyori.adventure.text.format.NamedTextColor.GOLD);
        when(event.message()).thenReturn(original);
        var listener = new DrugChatEffects();
        DrugChatEffects.trackSalvia(player, 200);
        listener.onChat(event);
        var changed = org.mockito.ArgumentCaptor.forClass(Component.class);
        verify(event).message(changed.capture());
        assertEquals(Component.text("Hello..., friend...! Third.", net.kyori.adventure.text.format.NamedTextColor.GOLD), changed.getValue().compact());
        clearInvocations(event);
        listener.onQuit(new PlayerQuitEvent(player, Component.empty(), org.bukkit.event.player.PlayerQuitEvent.QuitReason.DISCONNECTED));
        listener.onChat(event);
        verify(event, never()).message(any(Component.class));
    }

    @Test void phantomMobHallucinationIsClientOnlyAndVanishesOnAttack() {
        com.drugs.DrugsV2 plugin = mock(com.drugs.DrugsV2.class);
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);

        SensoryEffectManager sensory = new SensoryEffectManager(plugin);
        Player player = mock(Player.class);
        UUID playerUuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerUuid);
        when(player.isOnline()).thenReturn(true);
        org.bukkit.entity.Player.Spigot spigot = mock(org.bukkit.entity.Player.Spigot.class);
        when(player.spigot()).thenReturn(spigot);

        org.bukkit.World world = mock(org.bukkit.World.class);
        org.bukkit.Location loc = new org.bukkit.Location(world, 10, 64, 10);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(loc);
        when(player.getEyeLocation()).thenReturn(loc.clone().add(0, 1.6, 0));

        org.bukkit.entity.Creeper phantom = mock(org.bukkit.entity.Creeper.class);
        UUID phantomUuid = UUID.randomUUID();
        when(phantom.getUniqueId()).thenReturn(phantomUuid);
        when(phantom.isValid()).thenReturn(true);
        when(phantom.getLocation()).thenReturn(new org.bukkit.Location(world, 14, 64, 10));
        when(phantom.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
        when(world.spawnEntity(any(org.bukkit.Location.class), any(org.bukkit.entity.EntityType.class))).thenReturn(phantom);

        org.bukkit.entity.Entity spawned = sensory.spawnPhantomMobHallucination(player);
        assertSame(phantom, spawned);
        verify(phantom).setVisibleByDefault(false);
        verify(player).showEntity(plugin, phantom);
        verify(phantom).setAI(false);
        assertTrue(sensory.isHallucination(phantom));

        org.bukkit.event.entity.EntityDamageByEntityEvent attack = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(attack.getEntity()).thenReturn(phantom);
        when(attack.getDamager()).thenReturn(player);
        sensory.onHallucinationDamage(attack);
        verify(attack).setCancelled(true);
        verify(phantom).remove();
        assertFalse(sensory.isHallucination(phantom));
    }

    @Test void hudActionbarFormatsBpmOverdoseRiskAndActiveTimers() {
        com.drugs.DrugsV2 plugin = mock(com.drugs.DrugsV2.class);
        SensoryEffectManager sensory = new SensoryEffectManager(plugin);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        try (org.mockito.MockedStatic<com.drugs.status.DrugStatusTracker> statusTracker = mockStatic(com.drugs.status.DrugStatusTracker.class);
             org.mockito.MockedStatic<com.drugs.drug.DrugRegistry> registry = mockStatic(com.drugs.drug.DrugRegistry.class);
             org.mockito.MockedStatic<com.drugs.tolerance.ToleranceTracker> tolerance = mockStatic(com.drugs.tolerance.ToleranceTracker.class);
             org.mockito.MockedStatic<com.drugs.tolerance.ToleranceConfigLoader> toleranceCfg = mockStatic(com.drugs.tolerance.ToleranceConfigLoader.class)) {
            statusTracker.when(() -> com.drugs.status.DrugStatusTracker.getStatus(player)).thenReturn(java.util.Map.of(
                    "cocaine", new com.drugs.status.DrugStatusTracker.Status(900, 0, java.util.List.of("speed 2")),
                    "withdrawal/meth", new com.drugs.status.DrugStatusTracker.Status(600, 0, java.util.List.of("slowness 1"))
            ));
            registry.when(() -> com.drugs.drug.DrugRegistry.isDrugRegistered("cocaine")).thenReturn(true);
            tolerance.when(() -> com.drugs.tolerance.ToleranceTracker.getToleranceLevel(player, "cocaine")).thenReturn(5);
            toleranceCfg.when(() -> com.drugs.tolerance.ToleranceConfigLoader.getMaxTolerance("cocaine")).thenReturn(5);

            String hud = sensory.buildHudActionbar(player);
            assertNotNull(hud);
            assertTrue(hud.contains("BPM"));
            assertTrue(hud.contains("OD LIMIT"));
            assertTrue(hud.contains("45s"));
            assertTrue(hud.contains("Withdrawal: meth"));
        }
    }
}
