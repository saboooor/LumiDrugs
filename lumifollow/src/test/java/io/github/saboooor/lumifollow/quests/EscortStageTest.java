package io.github.saboooor.lumifollow.quests;

import fr.skytasul.quests.api.players.PlayerQuester;
import fr.skytasul.quests.api.stages.StageController;
import io.github.saboooor.lumifollow.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EscortStageTest {
    LumiFollowPlugin plugin;
    StageController controller;
    PlayerQuester quester;
    Player player;
    NPC npc;
    EscortProfile profile;
    NpcLeases leases;
    @BeforeEach void setup() throws Exception {
        plugin = mock(LumiFollowPlugin.class);
        var stages = LumiFollowPlugin.class.getDeclaredField("stages");
        stages.setAccessible(true); stages.set(plugin, new HashSet<>());
        var field = LumiFollowPlugin.class.getDeclaredField("leases");
        field.setAccessible(true); leases = new NpcLeases(); field.set(plugin, leases);
        plugin.following = mock(LumiFollow.class);
        controller = mock(StageController.class, RETURNS_DEEP_STUBS);
        when(controller.getStageType().getOptionsRegistry().getCreators()).thenReturn(List.of());
        quester = mock(PlayerQuester.class);
        player = mock(Player.class);
        when(quester.getPlayer()).thenReturn(Optional.of(player));
        when(player.isOnline()).thenReturn(true);
        when(controller.hasStarted(quester)).thenReturn(true);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        profile = new EscortProfile(7, "world", 10, 64, 0, 4, FollowOptions.defaults());
        when(plugin.profile("route")).thenReturn(profile);
        npc = mock(NPC.class);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.getEntity()).thenReturn(mock(Player.class));
        when(npc.getEntity().getLocation()).thenReturn(new Location(world, 10, 64, 0));
    }
    @Test void followsOnlyDuringStageAndCancellationReleasesNpc() {
        var registry = mock(NPCRegistry.class);
        when(registry.getById(7)).thenReturn(npc);
        try (var citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            var stage = new EscortStage(controller, plugin, "route");
            stage.started(quester);
            stage.tick();
            verify(plugin.following).follow(npc, player, profile.options());
            verify(controller, never()).finishStage(any());
            when(controller.hasStarted(quester)).thenReturn(false);
            stage.tick();
            verify(plugin.following).release(npc);
            assertTrue(leases.acquire(npc, new Object()));
        }
    }
    @Test void completesOnlyWhenPlayerAndNpcArriveAndReleasesBeforeAdvancing() {
        var registry = mock(NPCRegistry.class);
        when(registry.getById(7)).thenReturn(npc);
        when(player.getLocation()).thenReturn(npc.getEntity().getLocation());
        try (var citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            var stage = new EscortStage(controller, plugin, "route");
            stage.started(quester);
            stage.tick();
            var order = inOrder(plugin.following, controller);
            order.verify(plugin.following).release(npc);
            order.verify(controller).finishStage(quester);
            stage.tick();
            verify(controller, times(1)).finishStage(quester);
        }
    }
    @Test void busyNpcIsNeverStolenAndWaitingPlayerCanRetry() {
        var registry = mock(NPCRegistry.class);
        when(registry.getById(7)).thenReturn(npc);
        Object otherOwner = new Object();
        assertTrue(leases.acquire(npc, otherOwner));
        try (var citizens = mockStatic(CitizensAPI.class)) {
            citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
            var stage = new EscortStage(controller, plugin, "route");
            stage.started(quester);
            stage.tick();
            verifyNoInteractions(plugin.following);
            stage.ended(quester);
            assertFalse(leases.acquire(npc, new Object()));
            assertTrue(leases.release(npc, otherOwner));
            stage.joined(player, quester);
            stage.tick();
            verify(plugin.following).follow(npc, player, profile.options());
            stage.left(player, quester);
            verify(plugin.following).release(npc);
        }
    }
}
