package com.drugs.quests;

import com.drugs.DrugsV2;
import com.drugs.cartel.CartelDealer;
import com.drugs.drug.DrugRegistry;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import java.nio.file.Path;
import java.util.*;
import static com.drugs.quests.ProgressQuests.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionJournalTest {
    @TempDir Path folder;
    private DrugsV2 plugin;
    private Player player;
    private CartelDealer dealer;
    private ProgressQuests bridge;
    private ProductionJournal journal;
    private BukkitScheduler scheduler;
    private Runnable tick;
    private MockedStatic<DrugRegistry> registry;
    private MockedStatic<DrugItemMetadata> metadata;
    @BeforeEach void setup() throws Exception {
        registry = mockStatic(DrugRegistry.class);
        registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(Set.of("vape", "joint"));
        metadata = mockStatic(DrugItemMetadata.class);
        plugin = mock(DrugsV2.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L))).thenAnswer(call -> {
            tick = call.getArgument(1); return mock(BukkitTask.class);
        });
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission(anyString())).thenReturn(true);
        doReturn(List.of(player)).when(server).getOnlinePlayers();
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        dealer = mock(CartelDealer.class);
        when(dealer.contractProducts()).thenReturn(List.of("joint"));
        when(dealer.contractsEnabled()).thenReturn(true);
        when(dealer.contractAmount()).thenReturn(16);
        when(dealer.contractHours()).thenReturn(24);
        bridge = mock(ProgressQuests.class);
        when(bridge.begin(player, LEARNING)).thenReturn(true);
        when(bridge.begin(player, SUPPLY)).thenReturn(true);
        when(bridge.finish(eq(player), any())).thenReturn(true);
        journal = new ProductionJournal(plugin, dealer, bridge);
    }
    @AfterEach void cleanup() { metadata.close(); registry.close(); }
    private QuestJournalStore saved() throws Exception { return new QuestJournalStore(folder.resolve("production-journal.yml")); }
    private ItemStack item(String type) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.getAmount()).thenReturn(1); when(item.hasItemMeta()).thenReturn(true); when(item.getItemMeta()).thenReturn(meta);
        metadata.when(() -> DrugItemMetadata.getItemType(meta)).thenReturn(type);
        return item;
    }
    @Test void learningPersistsSkipsOwnedIntermediatesAndCompletesWithoutTakingItems() throws Exception {
        journal.learn(player, "vape");
        assertEquals(0, saved().lessons.get(player.getUniqueId()).completed());
        ItemStack liquid = item("vape_liquid");
        when(player.getInventory().getContents()).thenReturn(new ItemStack[]{liquid});
        tick.run();
        assertEquals(4, saved().lessons.get(player.getUniqueId()).completed());
        journal.shutdown();
        journal = new ProductionJournal(plugin, dealer, bridge);
        when(player.getInventory().getContents()).thenReturn(new ItemStack[0]);
        tick.run();
        assertEquals(4, saved().lessons.get(player.getUniqueId()).completed());
        ItemStack vape = item("vape");
        when(player.getInventory().getContents()).thenReturn(new ItemStack[]{vape});
        tick.run();
        assertFalse(saved().lessons.containsKey(player.getUniqueId()));
        verify(bridge).finish(player, LEARNING);
        verify(liquid, never()).setAmount(anyInt()); verify(vape, never()).setAmount(anyInt());
    }
    @Test void duplicatesAndWrongProductCannotReplaceAContractOrAdvanceIt() throws Exception {
        journal.accept(player, "joint");
        var original = saved().contracts.get(player.getUniqueId());
        journal.accept(player, "joint");
        assertEquals(original, saved().contracts.get(player.getUniqueId()));
        journal.sold(player, "vape", 20);
        assertEquals(original, saved().contracts.get(player.getUniqueId()));
        journal.sold(player, "joint", 5);
        assertEquals(5, saved().contracts.get(player.getUniqueId()).delivered());
        journal.sold(player, "joint", 20);
        assertFalse(saved().contracts.containsKey(player.getUniqueId()));
        verify(bridge).finish(player, SUPPLY);
    }
    @Test void cancellationAndExpiredOfflineContractsDoNotAffectTheOtherQuest() throws Exception {
        journal.learn(player, "vape"); journal.accept(player, "joint");
        journal.externalEnd(player.getUniqueId(), LEARNING);
        assertFalse(saved().lessons.containsKey(player.getUniqueId()));
        assertTrue(saved().contracts.containsKey(player.getUniqueId()));
        journal.shutdown();
        var disk = saved(); var contract = disk.contracts.get(player.getUniqueId());
        disk.contracts.put(player.getUniqueId(), new QuestJournalStore.Contract(contract.product(), contract.amount(), 3, 1)); disk.save();
        journal = new ProductionJournal(plugin, dealer, bridge);
        tick.run();
        assertFalse(saved().contracts.containsKey(player.getUniqueId()));
        verify(bridge).cancel(player, SUPPLY);
        verify(bridge, never()).finish(player, SUPPLY);
    }
    @Test void missingPermissionsOrQuestLimitsCannotCreateProgress() throws Exception {
        when(player.hasPermission("drugs.quests")).thenReturn(false);
        journal.learn(player, "vape"); journal.accept(player, "joint");
        assertTrue(saved().lessons.isEmpty()); assertTrue(saved().contracts.isEmpty());
        when(player.hasPermission("drugs.quests")).thenReturn(true);
        when(bridge.unavailable(player, LEARNING)).thenReturn("Quest limit reached");
        when(bridge.begin(player, SUPPLY)).thenReturn(false);
        journal.learn(player, "vape"); journal.accept(player, "joint");
        assertTrue(saved().lessons.isEmpty()); assertTrue(saved().contracts.isEmpty());
    }
}
