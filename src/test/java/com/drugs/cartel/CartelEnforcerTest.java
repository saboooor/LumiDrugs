package com.drugs.cartel;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.items.DrugItemMetadata;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CartelEnforcerTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<DrugRegistry> registry;
    private DrugsV2 plugin;
    private Player player;
    private Player npc;
    private CartelNpcs npcs;
    private CartelNpcs.Character character;
    private CartelDealer dealer;
    private CartelEnforcer enforcer;
    private EnforcerQuests quests;
    private EnforcerQuests.Session quest;
    private YamlConfiguration config;
    private AtomicLong time;
    private World world;

    @BeforeEach
    void setup() {
        bukkit = mockStatic(Bukkit.class);
        registry = mockStatic(DrugRegistry.class);
        registry.when(() -> DrugRegistry.isDrugRegistered("joint")).thenReturn(true);
        plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("DrugsV2");
        when(plugin.namespace()).thenReturn("drugsv2");
        Server server = mock(Server.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            ((Runnable) call.getArgument(1)).run();
            return null;
        });
        dealer = mock(CartelDealer.class);
        config = new YamlConfiguration();
        config.set("enforcer.enabled", false);
        config.set("enforcer.deadline-seconds", 10);
        config.set("enforcer.warning-interval-seconds", 5);
        config.set("prices.joint", 15.0);
        when(dealer.settings()).thenReturn(config);
        when(dealer.payments()).thenReturn(mock(CartelDealer.Payments.class));
        time = new AtomicLong(1000);
        world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        WorldBorder border = mock(WorldBorder.class);
        when(world.getWorldBorder()).thenReturn(border);
        when(border.isInside(any())).thenReturn(true);
        Block floor = mock(Block.class);
        when(floor.getType()).thenReturn(Material.STONE);
        when(floor.isSolid()).thenReturn(true);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                (int) call.getArgument(1) == 63 ? floor : air);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var data = mock(org.bukkit.persistence.PersistentDataContainer.class);
        var stored = new java.util.HashMap<NamespacedKey, Integer>();
        when(player.getPersistentDataContainer()).thenReturn(data);
        when(data.getOrDefault(any(NamespacedKey.class), eq(org.bukkit.persistence.PersistentDataType.INTEGER), anyInt()))
                .thenAnswer(call -> stored.getOrDefault(call.getArgument(0), call.getArgument(2)));
        doAnswer(call -> { stored.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(data).set(any(NamespacedKey.class), eq(org.bukkit.persistence.PersistentDataType.INTEGER), anyInt());
        doAnswer(call -> { stored.remove(call.getArgument(0)); return null; }).when(data).remove(any(NamespacedKey.class));
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.hasPermission("drugs.cartel.sell")).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        npc = mock(Player.class);
        when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
        when(npc.isValid()).thenReturn(true);
        when(npc.getWorld()).thenReturn(world);
        when(npc.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        when(npc.hasLineOfSight(player)).thenReturn(true);
        npcs = mock(CartelNpcs.class);
        character = mock(CartelNpcs.Character.class);
        when(dealer.npcs()).thenReturn(npcs);
        when(npcs.create(any(Location.class), eq("enforcer"))).thenReturn(character);
        when(npcs.find(npc, "enforcer")).thenReturn(character);
        when(character.entity()).thenReturn(npc);
        when(character.valid()).thenReturn(true);
        quests = mock(EnforcerQuests.class);
        quest = mock(EnforcerQuests.Session.class);
        when(quests.begin(eq(player), anyString(), anyInt())).thenReturn(quest);
        when(quest.active()).thenReturn(true);
        when(quest.solicitation()).thenReturn("READY");
        when(quest.complete()).thenReturn(true);
        enforcer = new CartelEnforcer(plugin, dealer, time::get, quests);
    }

    @AfterEach void cleanup() { registry.close(); bukkit.close(); }

    private void spawnAndApproach() {
        assertTrue(enforcer.spawn(player, "joint", 16, true));
        enforcer.tick();
    }

    private void interact(Player who) { enforcer.interact(who, npc); }

    private void refuse() {
        spawnAndApproach();
        time.addAndGet(10000);
        enforcer.tick();
    }

    @Test void escalatesAtFifteenAndTwentyConsecutiveRefusals() {
        for (int i = 0; i < 14; i++) refuse();
        assertEquals(14, enforcer.refusalStreak(player));
        verify(player, never()).sendMessage(contains("pushing your luck"));
        refuse();
        verify(player).sendMessage(contains("15 refusals in a row"));
        verify(player).sendMessage(contains("pushing your luck"));
        for (int i = 15; i < 20; i++) refuse();
        assertEquals(20, enforcer.refusalStreak(player));
        verify(player).sendMessage(contains("20 refusals in a row"));
        verify(player).sendMessage(contains("made an enemy"));
        verify(player, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
    }

    @Test void angryAndFuriousDemandsHaveShorterDeadlines() {
        for (int i = 0; i < 15; i++) refuse();
        config.set("enforcer.deadline-seconds", 90);
        spawnAndApproach();
        verify(player).sendMessage(contains("16 joint in 45s"));
        time.addAndGet(44999);
        enforcer.tick();
        assertEquals(15, enforcer.refusalStreak(player));
        time.incrementAndGet();
        enforcer.tick();
        assertEquals(16, enforcer.refusalStreak(player));
        config.set("enforcer.deadline-seconds", 10);
        for (int i = 16; i < 20; i++) refuse();
        config.set("enforcer.deadline-seconds", 90);
        spawnAndApproach();
        verify(player).sendMessage(contains("16 joint in 30s"));
        time.addAndGet(30000);
        enforcer.tick();
        assertEquals(21, enforcer.refusalStreak(player));
    }

    @Test void calledOffEncountersDoNotCountOrClearPreviousRefusals() {
        refuse();
        spawnAndApproach();
        enforcer.reload();
        assertEquals(1, enforcer.refusalStreak(player));
        spawnAndApproach();
        when(dealer.payments()).thenReturn(null);
        time.addAndGet(10000);
        enforcer.tick();
        assertEquals(1, enforcer.refusalStreak(player));
    }

    @Test void approachesWarnsAndThreatensAtDeadlineWithNoDefaultDamage() {
        spawnAndApproach();
        verify(player).sendMessage(contains("16 joint within 10s"));
        time.addAndGet(5000);
        enforcer.tick();
        verify(player).sendMessage(contains("5s left"));
        time.addAndGet(5000);
        enforcer.tick();
        verify(player).sendMessage(contains("Watch your back"));
        verify(character).destroy();
        verify(player, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
    }

    @Test void immediateRejectionCountsOnceAndStartsNormalCooldown() {
        spawnAndApproach();
        enforcer.reject(player);
        assertEquals(1, enforcer.refusalStreak(player));
        verify(character).destroy();
        verify(player).sendMessage(contains("enforcer has left"));
        enforcer.reject(player);
        time.addAndGet(15000);
        enforcer.tick();
        assertEquals(1, enforcer.refusalStreak(player));
        verify(character, times(1)).destroy();
        assertFalse(enforcer.spawn(player, "joint", 16, false));
    }

    @Test void canRejectWhileTheEnforcerIsStillApproaching() {
        when(npc.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        assertTrue(enforcer.spawn(player, "joint", 16, true));
        enforcer.reject(player);
        assertEquals(1, enforcer.refusalStreak(player));
        verify(character).destroy();
    }

    @Test void anotherPlayerCannotRejectYourEncounter() {
        spawnAndApproach();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        enforcer.reject(other);
        assertEquals(0, enforcer.refusalStreak(player));
        verify(character, never()).destroy();
        verify(other).sendMessage(contains("no active cartel demand"));
    }

    @Test void rejectionKeepsPartialSalesAndUsesConfiguredFailureConsequences() {
        spawnAndApproach();
        Item item = delivery("joint");
        try (MockedStatic<CartelDealer> prices = mockStatic(CartelDealer.class);
             MockedStatic<DrugItemMetadata> metadata = mockStatic(DrugItemMetadata.class)) {
            prices.when(() -> CartelDealer.price(any(), any())).thenReturn(15.0);
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("joint");
            when(dealer.sellDropped(eq(player), eq(item), eq(16), any())).thenReturn(6);
            assertTrue(enforcer.acceptDrop(player, item));
            config.set("enforcer.failure-damage", 2);
            when(player.getHealth()).thenReturn(20.0);
            enforcer.reject(player);
            assertEquals(1, enforcer.refusalStreak(player));
            verify(player).damage(2.0, npc);
            verify(character).destroy();
            verify(dealer, times(1)).sellDropped(eq(player), eq(item), eq(16), any());
            assertFalse(enforcer.acceptDrop(player, item));
            verify(item, never()).remove();
        }
    }

    @Test void solicitationTimeoutCancelsWithoutPenalizingPlayer() {
        when(quest.solicitation()).thenReturn("TIMED_OUT");
        assertTrue(enforcer.spawn(player, "joint", 16, true));
        enforcer.tick();
        verify(character).destroy();
        verify(quest).cancel();
        assertEquals(0, enforcer.refusalStreak(player));
        verify(player, never()).sendMessage(contains("within 10s"));
    }

    @Test void deadlineDoesNotStartUntilLumiFollowReportsReady() {
        when(quest.solicitation()).thenReturn("APPROACHING");
        when(npc.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        assertTrue(enforcer.spawn(player, "joint", 16, true));
        time.addAndGet(15000);
        enforcer.tick();
        verify(player, never()).sendMessage(contains("within 10s"));
        verify(player, never()).sendMessage(contains("Watch your back"));
        when(npc.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        enforcer.tick();
        verify(player, never()).sendMessage(contains("16 joint within 10s"));
        when(quest.solicitation()).thenReturn("READY");
        enforcer.tick();
        verify(player).sendMessage(contains("16 joint within 10s"));
        time.addAndGet(9000);
        enforcer.tick();
        verify(character, never()).destroy();
    }


    private Item delivery(String drug) {
        Item item = mock(Item.class);
        when(item.isValid()).thenReturn(true);
        when(item.getWorld()).thenReturn(world);
        when(item.getLocation()).thenReturn(new Location(world, 1, 64, 0));
        when(npc.hasLineOfSight(item)).thenReturn(true);
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getItemStack()).thenReturn(stack);
        when(stack.getItemMeta()).thenReturn(meta);
        // Item validation is covered by CartelDealerTest; isolate encounter progress here.
        return item;
    }

    @Test void droppedDeliveriesAccumulateAndStopAtRequiredQuantity() {
        refuse();
        assertEquals(1, enforcer.refusalStreak(player));
        clearInvocations(character);
        spawnAndApproach();
        Item first = delivery("joint");
        Item second = delivery("joint");
        try (MockedStatic<CartelDealer> prices = mockStatic(CartelDealer.class);
             MockedStatic<DrugItemMetadata> metadata = mockStatic(DrugItemMetadata.class)) {
            prices.when(() -> CartelDealer.price(any(), any())).thenReturn(15.0);
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("joint");
            when(dealer.sellDropped(eq(player), eq(first), eq(16), any())).thenReturn(6);
            assertTrue(enforcer.acceptDrop(player, first));
            verify(character, never()).destroy();
            verify(player).sendMessage(contains("Delivered 6/16"));
            verify(quest).progress(6);
            verify(quest, never()).complete();
            when(dealer.sellDropped(eq(player), eq(second), eq(10), any())).thenReturn(10);
            assertTrue(enforcer.acceptDrop(player, second));
            assertFalse(enforcer.acceptDrop(player, second));
            verify(character).destroy();
            verify(player).sendMessage(contains("You're clear"));
            assertEquals(0, enforcer.refusalStreak(player));
            verify(quest).progress(16);
            verify(quest).complete();
        }
    }

    @Test void otherPlayersCannotFulfillSomeonesDemand() {
        spawnAndApproach();
        Player other = mock(Player.class);
        when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(enforcer.acceptDrop(other, delivery("joint")));
        verify(dealer, never()).sellDropped(any(), any(), anyInt(), any());
        verify(character, never()).destroy();
    }

    @Test void rightClickStartsDemandAndNeverOpensLumiDrugsDialogOrSellsInventory() {
        assertTrue(enforcer.spawn(player, "joint", 16, true));
        interact(player);
        interact(player);
        verify(player).sendMessage(contains("16 joint within 10s"));
        verify(plugin, never()).showDialog(any(), any());
        verify(dealer, never()).sellDropped(any(), any(), anyInt(), any());
        verify(player, never()).getInventory();
    }

    @Test void wrongDrugOrFailedSaleDoesNotCompleteDemand() {
        spawnAndApproach();
        Item item = delivery("meth");
        try (MockedStatic<CartelDealer> prices = mockStatic(CartelDealer.class);
             MockedStatic<DrugItemMetadata> metadata = mockStatic(DrugItemMetadata.class)) {
            prices.when(() -> CartelDealer.price(any(), any())).thenReturn(15.0);
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("meth");
            assertFalse(enforcer.acceptDrop(player, item));
            verify(dealer, never()).sellDropped(any(), any(), anyInt(), any());
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("joint");
            assertTrue(enforcer.acceptDrop(player, item));
            verify(dealer).sellDropped(eq(player), eq(item), eq(16), any());
            verify(character, never()).destroy();
        }
    }

    @Test void quitRemovesNpcAndAppliesCooldown() {
        spawnAndApproach();
        PlayerQuitEvent event = mock(PlayerQuitEvent.class);
        when(event.getPlayer()).thenReturn(player);
        enforcer.onQuit(event);
        verify(character).destroy();
        assertFalse(enforcer.spawn(player, "joint", 16, false));
    }

    @Test void operatorWithoutExplicitBypassCanHaveAnEncounter() {
        when(player.isOp()).thenReturn(true);
        when(player.hasPermission("drugs.cartel.enforcer.bypass")).thenReturn(true);
        when(player.isPermissionSet("drugs.cartel.enforcer.bypass")).thenReturn(false);
        assertTrue(enforcer.spawn(player, "joint", 16, true));
    }

    @Test void rejectsBypassedPlayersAndInvalidOrUnpricedDemands() {
        assertFalse(enforcer.spawn(player, "joint", 0, true));
        assertFalse(enforcer.spawn(player, "joint", 2305, true));
        assertFalse(enforcer.spawn(player, "unknown", 16, true));
        config.set("prices.joint", 0);
        assertFalse(enforcer.spawn(player, "joint", 16, true));
        config.set("prices.joint", 15);
        when(player.isPermissionSet("drugs.cartel.enforcer.bypass")).thenReturn(true);
        when(player.hasPermission("drugs.cartel.enforcer.bypass")).thenReturn(true);
        assertFalse(enforcer.spawn(player, "joint", 16, true));
    }

    @Test void invalidNpcIsRemovedWithoutIssuingADemand() {
        when(character.valid()).thenReturn(false);
        assertFalse(enforcer.spawn(player, "joint", 16, true));
        verify(character).destroy();
        verify(player, never()).sendMessage(anyString());
    }

    @Test void vanishedEconomyCallsOffDemandWithoutSelling() {
        spawnAndApproach();
        when(dealer.payments()).thenReturn(null);
        Item item = delivery("joint");
        try (MockedStatic<CartelDealer> prices = mockStatic(CartelDealer.class);
             MockedStatic<DrugItemMetadata> metadata = mockStatic(DrugItemMetadata.class)) {
            prices.when(() -> CartelDealer.price(any(), any())).thenReturn(15.0);
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("joint");
            assertTrue(enforcer.acceptDrop(player, item));
        }
        verify(character).destroy();
        verify(dealer, never()).sellDropped(any(), any(), anyInt(), any());
        verify(player).sendMessage(contains("called off"));
    }
    @Test void optionalDamageIsLimitedByCurrentHealth() {
        config.set("enforcer.failure-damage", 20);
        when(player.getHealth()).thenReturn(3.0);
        spawnAndApproach();
        time.addAndGet(10000);
        enforcer.tick();
        verify(player).damage(2.0, npc);
        verify(character).destroy();
    }

    @Test void unloadedGroundIsSkippedWithoutGeneratingChunks() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertFalse(enforcer.spawn(player, "joint", 16, true));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        verify(npcs, never()).create(any(Location.class), anyString());
    }

    @Test void reloadCallsOffActiveDemandsWithoutThreats() {
        spawnAndApproach();
        enforcer.reload();
        time.addAndGet(20000);
        enforcer.tick();
        verify(character).destroy();
        verify(player).sendMessage(contains("called off"));
        verify(player, never()).sendMessage(contains("Watch your back"));
    }

    @Test void unavailableEconomyCancelsAtDeadlineInsteadOfThreatening() {
        spawnAndApproach();
        when(dealer.payments()).thenReturn(null);
        time.addAndGet(10000);
        enforcer.tick();
        verify(character).destroy();
        verify(player).sendMessage(contains("called off"));
        verify(player, never()).sendMessage(contains("Watch your back"));
    }

    @Test void randomDemandsVaryDrugAndQuantityWithinConfiguredBounds() {
        registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("joint", "meth", "glue", "lsd", "heroin", "molly"));
        config.set("prices.meth", 75);
        config.set("prices.glue", 0);
        config.set("prices.lsd", -1);
        config.set("prices.heroin", Double.NaN);
        config.set("prices.molly", Double.POSITIVE_INFINITY);
        config.set("enforcer.min-amount", 3);
        config.set("enforcer.max-amount", 5);
        Random random = new Random(42);
        Set<String> chosenDrugs = new HashSet<>();
        Set<Integer> chosenAmounts = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            CartelEnforcer.Demand demand = enforcer.randomDemand(random);
            assertNotNull(demand);
            assertTrue(Set.of("joint", "meth").contains(demand.drug()));
            assertTrue(demand.amount() >= 3 && demand.amount() <= 5);
            chosenDrugs.add(demand.drug());
            chosenAmounts.add(demand.amount());
        }
        assertEquals(Set.of("joint", "meth"), chosenDrugs);
        assertEquals(Set.of(3, 4, 5), chosenAmounts);
    }

    @Test void randomDemandRequiresAtLeastOnePricedRegisteredDrug() {
        registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("unknown"));
        assertNull(enforcer.randomDemand(new Random(42)));
    }

    @Test void randomQuantityLimitsAreClampedAndLegacyFixedSettingsIgnored() {
        registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("joint"));
        config.set("enforcer.drug", "unknown");
        config.set("enforcer.amount", 16);
        config.set("enforcer.min-amount", 9999);
        config.set("enforcer.max-amount", -10);
        CartelEnforcer.Demand demand = enforcer.randomDemand(new Random(42));
        assertEquals("joint", demand.drug());
        assertEquals(2304, demand.amount());
        config.set("enforcer.min-amount", -5);
        config.set("enforcer.max-amount", -1);
        assertEquals(1, enforcer.randomDemand(new Random(42)).amount());
    }

    @Test void citizensPlayerNpcsCannotBeTargetedAsRealPlayers() {
        when(player.hasMetadata("NPC")).thenReturn(true);
        assertFalse(enforcer.spawn(player, "joint", 16, true));
        verify(npcs, never()).create(any(Location.class), anyString());
    }

    private void runExplicitCommand() {
        bukkit.when(() -> Bukkit.getPlayerExact("Tester")).thenReturn(player);
        enforcer.onCommand(player, new String[]{"enforcer", "Tester", "joint", "16"});
    }

    @Test void commandReportsMissingEconomyInsteadOfGenericFailure() {
        when(dealer.payments()).thenReturn(null);
        runExplicitCommand();
        verify(player).sendMessage(contains("Vault has no enabled economy provider"));
        verify(npcs, never()).create(any(), anyString());
    }

    @Test void commandReportsBypassPermissionAndGameMode() {
        when(player.isPermissionSet("drugs.cartel.enforcer.bypass")).thenReturn(true);
        when(player.hasPermission("drugs.cartel.enforcer.bypass")).thenReturn(true);
        runExplicitCommand();
        verify(player).sendMessage(contains("has drugs.cartel.enforcer.bypass"));
        when(player.hasPermission("drugs.cartel.enforcer.bypass")).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        runExplicitCommand();
        verify(player).sendMessage(contains("must be in Survival or Adventure mode"));
    }

    @Test void commandReportsUnavailableNpcProviderBeforeSearchingForGround() {
        when(npcs.unavailableReason()).thenReturn("Citizens is not enabled.");
        runExplicitCommand();
        verify(player).sendMessage(contains("Citizens is not enabled"));
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    }

    @Test void commandDistinguishesMissingGroundFromRejectedNpcSpawn() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        runExplicitCommand();
        verify(player).sendMessage(contains("No safe nearby ground"));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(npcs.create(any(), eq("enforcer"))).thenReturn(null);
        runExplicitCommand();
        verify(player).sendMessage(contains("Citizens could not spawn the player NPC"));
    }

    @Test void commandReportsExistingEncounterAndMissingPricedDrugs() {
        spawnAndApproach();
        runExplicitCommand();
        verify(player).sendMessage(contains("already has an active encounter"));
        registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of());
        enforcer.onCommand(player, new String[]{"enforcer"});
        verify(player).sendMessage(contains("No registered drugs have a positive, finite base price"));
    }

    @Test void encounterDoesNotOwnDistanceRecovery() {
        spawnAndApproach();
        when(player.getLocation()).thenReturn(new Location(world, 500, 64, 500));
        enforcer.tick();
        verify(character, never()).destroy();
        // Movement is owned by the quest option; the encounter only publishes the NPC identity.
        verify(quest).npc(character.entity().getUniqueId());
    }
    @Test void missingBeautyQuestsPreventsNpcSpawn() {
        when(quests.unavailableReason(player)).thenReturn("Install BeautyQuests");
        assertFalse(enforcer.spawn(player, "joint", 16, true));
        verify(npcs, never()).create(any(), anyString());
        verify(quests, never()).begin(any(), anyString(), anyInt());
    }

    @Test void failedQuestStartRemovesNpcWithoutIssuingDemand() {
        when(quests.begin(player, "joint", 16)).thenReturn(null);
        assertFalse(enforcer.spawn(player, "joint", 16, true));
        verify(character).destroy();
        verify(player, never()).sendMessage(contains("heading your way"));
    }

    @Test void cancelledQuestCallsOffEncounterWithoutRefusalOrSale() {
        spawnAndApproach();
        when(quest.active()).thenReturn(false);
        assertFalse(enforcer.acceptDrop(player, delivery("joint")));
        enforcer.tick();
        verify(character).destroy();
        verify(quest).cancel();
        verify(quest, never()).complete();
        assertEquals(0, enforcer.refusalStreak(player));
    }

    @Test void expiredEncounterCancelsQuestWithoutCompletingIt() {
        spawnAndApproach();
        time.addAndGet(11000);
        enforcer.tick();
        verify(quest).cancel();
        verify(quest, never()).complete();
    }

    @Test void failedPaymentDoesNotAdvanceBeautyQuests() {
        spawnAndApproach();
        Item item = delivery("joint");
        try (MockedStatic<CartelDealer> prices = mockStatic(CartelDealer.class);
             MockedStatic<DrugItemMetadata> metadata = mockStatic(DrugItemMetadata.class)) {
            prices.when(() -> CartelDealer.price(any(), any())).thenReturn(15.0);
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("joint");
            when(dealer.sellDropped(eq(player), eq(item), anyInt(), any())).thenReturn(0);
            assertTrue(enforcer.acceptDrop(player, item));
            verify(quest, never()).progress(anyInt());
            verify(quest, never()).complete();
        }
    }

    @Test void rejectingAfterQuestCancellationDoesNotCountARefusal() {
        spawnAndApproach();
        when(quest.active()).thenReturn(false);
        enforcer.reject(player);
        verify(character).destroy();
        assertEquals(0, enforcer.refusalStreak(player));
    }

    @Test void directHideoutDeliveryCutsOutMiddlemanForThreeTimesPayoutAndPrefersCaveOrShade() {
        Block ceiling = mock(Block.class);
        when(ceiling.getType()).thenReturn(Material.STONE);
        when(ceiling.isSolid()).thenReturn(true);
        Block floor = mock(Block.class);
        when(floor.getType()).thenReturn(Material.STONE);
        when(floor.isSolid()).thenReturn(true);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        when(air.isPassable()).thenReturn(true);
        // Underground cave at y=52 (floor at 51, roof at 55) and open surface at y=64 (floor at 63)
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int y = call.getArgument(1);
            if (y == 51 || y == 63) return floor;
            if (y == 55) return ceiling;
            return air;
        });

        spawnAndApproach();
        Location hideout = enforcer.hideoutLocation(player);
        assertNotNull(hideout);
        // Should prefer the underground cave elevation (y=52) because it has a stone ceiling at y=55
        assertEquals(52, hideout.getBlockY());

        enforcer.locateHideout(player);
        verify(player).setCompassTarget(hideout);
        verify(player, atLeastOnce()).sendMessage(contains("3x the money"));

        Item item = delivery("joint");
        when(item.getLocation()).thenReturn(hideout.clone());
        try (MockedStatic<CartelDealer> prices = mockStatic(CartelDealer.class);
             MockedStatic<DrugItemMetadata> metadata = mockStatic(DrugItemMetadata.class)) {
            prices.when(() -> CartelDealer.price(any(), any())).thenReturn(15.0);
            metadata.when(() -> DrugItemMetadata.getDrugId(any(ItemMeta.class))).thenReturn("joint");
            when(dealer.sellDropped(eq(player), eq(item), eq(16), any(), eq(3.0))).thenReturn(16);

            assertTrue(enforcer.acceptDrop(player, item));
            verify(dealer).sellDropped(eq(player), eq(item), eq(16), any(), eq(3.0));
            verify(dealer, never()).sellDropped(eq(player), eq(item), anyInt(), any());
            verify(quest).progress(16);
            verify(quest).complete();
            verify(character).destroy();
            verify(player).sendMessage(contains("cutting out the middleman"));
        }
    }

}
