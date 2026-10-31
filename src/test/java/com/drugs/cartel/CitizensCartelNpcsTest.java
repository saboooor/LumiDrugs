package com.drugs.cartel;

import com.drugs.DrugsV2;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.npc.MetadataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitizensCartelNpcsTest {
    private MockedStatic<CitizensAPI> citizens;
    private MockedStatic<Bukkit> bukkit;
    private DrugsV2 plugin;
    private CartelDealer dealer;
    private CitizensCartelNpcs service;
    private NPCRegistry persistent;
    private NPCRegistry temporary;
    private NPC npc;
    private MetadataStore metadata;
    private SkinTrait skin;
    private Player entity;
    private YamlConfiguration config;
    private PluginManager plugins;
    private Location location;
    private World world;

    @BeforeEach void setup() {
        citizens = mockStatic(CitizensAPI.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of());
        plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("Citizens")).thenReturn(true);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        dealer = mock(CartelDealer.class);
        config = new YamlConfiguration();
        when(dealer.settings()).thenReturn(config);
        persistent = mock(NPCRegistry.class);
        temporary = mock(NPCRegistry.class);
        citizens.when(CitizensAPI::getNPCRegistry).thenReturn(persistent);
        citizens.when(() -> CitizensAPI.createInMemoryNPCRegistry("lumidrugs-cartel-enforcers")).thenReturn(temporary);
        when(persistent.iterator()).thenAnswer(call -> Collections.emptyIterator());
        npc = mock(NPC.class);
        metadata = mock(MetadataStore.class);
        when(npc.data()).thenReturn(metadata);
        skin = mock(SkinTrait.class);
        when(npc.getOrAddTrait(SkinTrait.class)).thenReturn(skin);
        entity = mock(Player.class);
        when(entity.isValid()).thenReturn(true);
        when(npc.getEntity()).thenReturn(entity);
        when(npc.isSpawned()).thenReturn(true);
        when(npc.spawn(any(Location.class))).thenReturn(true);
        when(npc.getOwningRegistry()).thenReturn(persistent);
        when(persistent.createNPC(eq(EntityType.PLAYER), anyString())).thenReturn(npc);
        when(temporary.createNPC(eq(EntityType.PLAYER), anyString())).thenReturn(npc);
        world = mock(World.class);
        location = new Location(world, 1, 64, 1);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(location);
        service = new CitizensCartelNpcs(plugin, dealer);
    }

    @AfterEach void cleanup() { citizens.close(); bukkit.close(); }

    @Test void buyersAreProtectedPlayerNpcsWithBundledSignedSkinAndPersistence() {
        CartelNpcs.Character buyer = service.create(location, "buyer");
        assertNotNull(buyer);
        assertSame(entity, buyer.entity());
        assertTrue(buyer.valid());
        verify(persistent).createNPC(EntityType.PLAYER, "Pablo");
        verify(metadata).setPersistent(CitizensCartelNpcs.ROLE, "buyer");
        verify(npc).setProtected(true);
        verify(persistent).saveToStore();
        ArgumentCaptor<String> signature = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> texture = ArgumentCaptor.forClass(String.class);
        verify(skin).setSkinPersistent(eq("cartel-default"), signature.capture(), texture.capture());
        assertFalse(signature.getValue().isBlank());
        String decoded = new String(Base64.getDecoder().decode(texture.getValue()), StandardCharsets.UTF_8);
        assertTrue(decoded.contains("textures.minecraft.net/texture/"));
        verify(skin, never()).setSkinName(anyString());
    }

    @Test void configuredBuyerNameAppliesOnCreationAndReload() {
        config.set("npc.name", "Diego");
        assertNotNull(service.create(location, "buyer"));
        verify(persistent).createNPC(EntityType.PLAYER, "Diego");
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        config.set("npc.name", "Miguel");
        service.reload();
        verify(npc).setName("Miguel");
    }

    @Test void reloadRestoresSavedBuyerThatDidNotSpawnDuringStartup() {
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        when(npc.isSpawned()).thenReturn(false);
        when(npc.getStoredLocation()).thenReturn(location);
        service.reload();
        verify(npc).spawn(location);
        verify(persistent, never()).createNPC(any(), anyString());
        verify(npc, never()).destroy();
    }

    @Test void reloadLeavesBuyersInUnloadedChunksForChunkLoadRecovery() {
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        when(npc.isSpawned()).thenReturn(false);
        when(npc.getStoredLocation()).thenReturn(location);
        when(world.isChunkLoaded(0, 0)).thenReturn(false);
        service.reload();
        verify(npc, never()).spawn(any(Location.class));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        verify(npc, never()).destroy();
    }

    @Test void reloadDoesNotRespawnAnExistingBuyerOrUnrelatedNpc() {
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        service.reload();
        when(npc.isSpawned()).thenReturn(false);
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("");
        service.reload();
        verify(npc, never()).spawn(any(Location.class));
    }

    @Test void chunkLoadRestoresOnlyBuyersStoredInThatChunk() {
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        when(npc.isSpawned()).thenReturn(false);
        when(npc.getStoredLocation()).thenReturn(location);
        Chunk chunk = mock(Chunk.class);
        when(chunk.getWorld()).thenReturn(world);
        when(chunk.getEntities()).thenReturn(new Entity[0]);
        when(chunk.getX()).thenReturn(1);
        service.onChunkLoad(new org.bukkit.event.world.ChunkLoadEvent(chunk, false));
        verify(npc, never()).spawn(any(Location.class));
        when(chunk.getX()).thenReturn(0);
        service.onChunkLoad(new org.bukkit.event.world.ChunkLoadEvent(chunk, false));
        verify(npc).spawn(location);
    }

    @Test void failedRecoveryKeepsSavedBuyerForAnotherAttempt() {
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        when(npc.isSpawned()).thenReturn(false);
        when(npc.getStoredLocation()).thenReturn(location);
        when(npc.spawn(location)).thenReturn(false, true);
        service.reload();
        service.reload();
        verify(npc, times(2)).spawn(location);
        verify(npc, never()).destroy();
    }

    @Test void enforcersUseMemoryOnlyRegistryAndUsernameOverride() {
        config.set("npc.skin-name", "MyCartelSkin");
        assertNotNull(service.create(location, "enforcer"));
        verify(temporary).createNPC(EntityType.PLAYER, "Cartel Enforcer");
        verify(metadata).setPersistent("pathfinder-open-doors", true);
        verify(metadata).setPersistent("activation-range", 64);
        verify(persistent, never()).createNPC(any(), anyString());
        verify(skin).clearTexture();
        verify(skin).setSkinName("MyCartelSkin");
        verify(skin, never()).setSkinPersistent(anyString(), anyString(), anyString());
    }

    @Test void failedSpawnDestroysRegistryEntry() {
        when(npc.spawn(location)).thenReturn(false);
        assertNull(service.create(location, "buyer"));
        verify(npc).destroy();
    }

    @Test void clicksForwardOnceToTheCorrectCartelHandler() {
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("enforcer");
        Player clicker = mock(Player.class);
        NPCRightClickEvent event = mock(NPCRightClickEvent.class);
        when(event.getNPC()).thenReturn(npc);
        when(event.getClicker()).thenReturn(clicker);
        service.onRightClick(event);
        verify(event).setCancelled(true);
        verify(dealer, times(1)).interactEnforcer(clicker, entity);
        verify(dealer, never()).interact(any(), any());
    }

    @Test void leftClickEnforcerForwardsToTheSameInteractionHandler() {
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("enforcer");
        Player clicker = mock(Player.class);
        NPCLeftClickEvent event = mock(NPCLeftClickEvent.class);
        when(event.getNPC()).thenReturn(npc);
        when(event.getClicker()).thenReturn(clicker);
        service.onLeftClick(event);
        verify(event).setCancelled(true);
        verify(dealer).interactEnforcer(clicker, entity);
        verify(dealer, never()).interact(any(), any());
    }

    @Test void leftClickOtherNpcsIsIgnored() {
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        NPCLeftClickEvent event = mock(NPCLeftClickEvent.class);
        when(event.getNPC()).thenReturn(npc);
        service.onLeftClick(event);
        verify(event, never()).setCancelled(true);
        verify(dealer, never()).interactEnforcer(any(), any());
    }

    @Test void unrelatedCitizensNpcsAreIgnored() {
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("");
        NPCRightClickEvent event = mock(NPCRightClickEvent.class);
        when(event.getNPC()).thenReturn(npc);
        service.onRightClick(event);
        verify(event, never()).setCancelled(true);
        verify(dealer, never()).interactEnforcer(any(), any());
        verify(dealer, never()).interact(any(), any());
    }

    @Test void enforcersNavigateWithCitizensAndTeleportWhenStuck() {
        Navigator navigator = mock(Navigator.class);
        NavigatorParameters parameters = mock(NavigatorParameters.class, RETURNS_SELF);
        when(npc.getNavigator()).thenReturn(navigator);
        when(navigator.getDefaultParameters()).thenReturn(parameters);
        Player target = mock(Player.class);
        when(target.getLocation()).thenReturn(location);
        when(target.isOnline()).thenReturn(true);
        when(navigator.canNavigateTo(location)).thenReturn(true);
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("enforcer");
        CartelNpcs.Character enforcer = service.create(location, "enforcer");
        assertTrue(enforcer.approach(target));
        verify(navigator).setTarget(target, false);
        ArgumentCaptor<net.citizensnpcs.api.ai.StuckAction> stuck = ArgumentCaptor.forClass(net.citizensnpcs.api.ai.StuckAction.class);
        verify(parameters).stuckAction(stuck.capture());
        verify(parameters).useNewPathfinder(true);
        verify(parameters).updatePathRate(10);
        verify(parameters).examiner(any(net.citizensnpcs.api.astar.pathfinder.DoorExaminer.class));

        // When stuckAction fires, enforcer teleports closer to target
        try (MockedStatic<io.github.saboooor.lumifollow.SafeLocations> safe = mockStatic(io.github.saboooor.lumifollow.SafeLocations.class)) {
            safe.when(() -> io.github.saboooor.lumifollow.SafeLocations.findNear(location, 4, 8)).thenReturn(location);
            assertTrue(stuck.getValue().run(npc, navigator));
        }
        verify(npc).teleport(any(Location.class), eq(org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN));

        enforcer.stop();
        verify(navigator, atLeastOnce()).cancelNavigation();
        // Navigation is attempted even if a preliminary path check would return false.
        verify(navigator, never()).canNavigateTo(any(Location.class));
        when(npc.isSpawned()).thenReturn(false);
        assertFalse(enforcer.approach(target));
    }

    @Test void legacyBuyersAreReplacedOnlyAfterSuccessfulPlayerSpawn() {
        World world = mock(World.class);
        Villager old = mock(Villager.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(old.getPersistentDataContainer()).thenReturn(data);
        when(data.has(new NamespacedKey(plugin, "cartel_dealer"), PersistentDataType.BYTE)).thenReturn(true);
        when(old.getLocation()).thenReturn(location);
        when(world.getEntitiesByClass(Villager.class)).thenReturn(List.of(old));
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
        when(npc.spawn(location)).thenReturn(false);
        service.reload();
        verify(old, never()).remove();
        when(npc.spawn(location)).thenReturn(true);
        service.reload();
        verify(old).remove();
    }

    @Test void shutdownDestroysTemporaryRegistryAndKeepsBuyers() {
        service.shutdown();
        service.shutdown();
        verify(temporary, times(1)).deregisterAll();
        verify(persistent, never()).deregisterAll();
        verify(persistent, never()).saveToStore();
        citizens.verify(() -> CitizensAPI.removeNamedNPCRegistry("lumidrugs-cartel-enforcers"));
    }

    @Test void missingCitizensLeavesTheRestOfThePluginUsable() {
        when(plugins.isPluginEnabled("Citizens")).thenReturn(false);
        CartelNpcs disabled = CartelNpcs.create(plugin, dealer);
        assertNull(disabled.create(location, "buyer"));
        assertNull(disabled.find(entity, "buyer"));
        disabled.reload();
        disabled.shutdown();
    }
}
