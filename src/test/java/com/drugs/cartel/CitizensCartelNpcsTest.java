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
import java.util.UUID;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CitizensCartelNpcsTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
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
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        plugins = mock(PluginManager.class);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("Citizens")).thenReturn(true);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        dealer = mock(CartelDealer.class);
        config = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/cartel.yml"));
        when(dealer.settings()).thenReturn(config);
        persistent = mock(NPCRegistry.class);
        temporary = mock(NPCRegistry.class);
        citizens.when(CitizensAPI::getNPCRegistry).thenReturn(persistent);
        citizens.when(() -> CitizensAPI.createInMemoryNPCRegistry("lumidrugs-cartel-enforcers")).thenReturn(temporary);
        when(persistent.iterator()).thenAnswer(call -> Collections.emptyIterator());
        npc = mock(NPC.class);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        when(npc.getId()).thenReturn(7);
        when(persistent.getByUniqueId(npc.getUniqueId())).thenReturn(npc);
        when(persistent.createNPC(eq(EntityType.PLAYER), any(UUID.class), anyInt(), anyString())).thenReturn(npc);
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
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("world");
        bukkit.when(() -> Bukkit.getWorld(world.getUID())).thenReturn(world);
        bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(world);
        location = new Location(world, 1, 64, 1);
        when(world.isChunkLoaded(0, 0)).thenReturn(true);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getLocation()).thenReturn(location);
        service = new CitizensCartelNpcs(plugin, dealer);
    }

    @AfterEach void cleanup() { citizens.close(); bukkit.close(); }

























    @Test void enforcersUseMemoryOnlyRegistryAndUsernameOverride() {
        config.set("npc.skin-name", "MyCartelSkin");
        assertNotNull(service.create(location, "enforcer"));
        verify(temporary).createNPC(EntityType.PLAYER, "Cartel Enforcer");
        verify(metadata, never()).setPersistent(eq("pathfinder-open-doors"), any());
        verify(metadata).setPersistent("activation-range", 64);
        verify(persistent, never()).createNPC(any(), anyString());
        verify(skin).clearTexture();
        verify(skin).setSkinName("MyCartelSkin");
        verify(skin, never()).setSkinPersistent(anyString(), anyString(), anyString());
    }

    @Test void signedEnforcerSkinComesFromCartelConfig() {
        assertNotNull(service.create(location, "enforcer"));
        verify(skin).setSkinPersistent(config.getString("npc.skin.name"),
                config.getString("npc.skin.signature"), config.getString("npc.skin.texture"));
        String texture = new String(Base64.getDecoder().decode(config.getString("npc.skin.texture")),
                StandardCharsets.UTF_8);
        assertTrue(texture.contains("textures.minecraft.net/texture/"));
    }

    @Test void failedSpawnDestroysRegistryEntry() {
        when(npc.spawn(location)).thenReturn(false);
        assertNull(service.create(location, "enforcer"));
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

    @Test void enforcerSpawnDoesNotStartNavigation() {
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("enforcer");
        CartelNpcs.Character enforcer = service.create(location, "enforcer");
        assertNotNull(enforcer);
        assertTrue(enforcer.valid());
        verify(npc, never()).getNavigator();
        enforcer.destroy();
        verify(npc).destroy();
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
    @Test void linkPersistsRoleWithoutChangingCitizensNpc() {
        when(persistent.getById(7)).thenReturn(npc);
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("");
        assertTrue(service.linkBuyer(7));
        verify(metadata).setPersistent(CitizensCartelNpcs.ROLE, "buyer");
        verify(persistent).saveToStore();
        verify(npc, never()).spawn(any());
        verify(npc, never()).setName(anyString());
        verify(npc, never()).setProtected(anyBoolean());
        verifyNoInteractions(skin);
    }

    @Test void unlinkRemovesOnlyBuyerRoleAndNeverDeletesNpc() {
        when(persistent.getById(7)).thenReturn(npc);
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        assertTrue(service.unlinkBuyer(7));
        verify(metadata).remove(CitizensCartelNpcs.ROLE);
        verify(persistent).saveToStore();
        verify(npc, never()).destroy();
    }

    @Test void missingOrOtherRoleCannotBeUnlinkedOrOverwritten() {
        assertFalse(service.linkBuyer(99));
        assertFalse(service.unlinkBuyer(99));
        when(persistent.getById(7)).thenReturn(npc);
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("enforcer");
        assertFalse(service.linkBuyer(7));
        assertFalse(service.unlinkBuyer(7));
        verify(persistent, never()).saveToStore();
    }

    @Test void reloadAndShutdownLeaveExistingBuyersAndTheirAppearanceAlone() {
        when(persistent.iterator()).thenAnswer(call -> List.of(npc).iterator());
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        when(npc.isSpawned()).thenReturn(false);
        service.reload();
        service.shutdown();
        verify(npc, never()).spawn(any());
        verify(npc, never()).destroy();
        verify(npc, never()).setName(anyString());
        verifyNoInteractions(skin);
        verify(persistent, never()).createNPC(any(), anyString());
    }

    @Test void buyerCreationIsDisabledAndLegacyBuyerHandlesOnlyUnlink() {
        assertNull(service.create(location, "buyer"));
        when(persistent.getNPC(entity)).thenReturn(npc);
        when(persistent.getById(7)).thenReturn(npc);
        when(metadata.get(CitizensCartelNpcs.ROLE, "")).thenReturn("buyer");
        service.find(entity, "buyer").destroy();
        verify(metadata).remove(CitizensCartelNpcs.ROLE);
        verify(npc, never()).destroy();
        verify(persistent, never()).createNPC(any(), anyString());
    }

}
