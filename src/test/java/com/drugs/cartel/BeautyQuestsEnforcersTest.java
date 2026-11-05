package com.drugs.cartel;

import com.drugs.DrugsV2;
import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.api.players.PlayerQuester;
import fr.skytasul.quests.api.quests.Quest;
import fr.skytasul.quests.api.quests.QuestsManager;
import fr.skytasul.quests.api.quests.branches.QuestBranch;
import fr.skytasul.quests.api.quests.branches.QuestBranchesManager;
import fr.skytasul.quests.api.quests.quester.QuestQuesterStrategy;
import fr.skytasul.quests.api.stages.StageController;
import fr.skytasul.quests.api.stages.StageTypeRegistry;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BeautyQuestsEnforcersTest {
    private MockedStatic<QuestsAPI> apiMock;
    private QuestsAPI api;
    private DrugsV2 plugin;
    private CartelDealer dealer;
    private YamlConfiguration config;
    private QuestsManager manager;
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path folder;
    private BeautyQuestsEnforcers integration;
    private Quest quest;
    private Player player;
    private PlayerQuester quester;
    private StageController controller;
    private BeautyQuestsEnforcers.DeliveryStage stage;
    private PluginManager plugins;

    @BeforeEach void setup() {
        api = mock(QuestsAPI.class);
        apiMock = mockStatic(QuestsAPI.class);
        apiMock.when(QuestsAPI::getAPI).thenReturn(api);
        when(api.getStages()).thenReturn(mock(StageTypeRegistry.class));
        manager = mock(QuestsManager.class);
        when(api.getQuestsManager()).thenReturn(manager);
        plugin = mock(DrugsV2.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        Server server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("BeautyQuests")).thenReturn(true);
        dealer = mock(CartelDealer.class);
        config = new YamlConfiguration();
        config.set("enforcer.beautyquests-quest-id", 42);
        when(dealer.settings()).thenReturn(config);
        quest = mock(Quest.class);
        when(manager.getQuest(42)).thenReturn(quest);
        when(quest.getId()).thenReturn(42);
        when(quest.isValid()).thenReturn(true);
        when(quest.isRepeatable()).thenReturn(true);
        when(quest.isCancellable()).thenReturn(true);
        player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        quester = mock(PlayerQuester.class);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(id);
        when(quester.getOfflinePlayer()).thenReturn(offline);
        QuestQuesterStrategy strategy = mock(QuestQuesterStrategy.class);
        when(quest.getQuesterStrategy()).thenReturn(strategy);
        doReturn(Optional.of(quester)).when(strategy).getPlayerQuester(player);
        when(quest.canStart(player, false)).thenReturn(true);
        QuestBranchesManager branches = mock(QuestBranchesManager.class);
        when(quest.getBranchesManager()).thenReturn(branches);
        QuestBranch branch = mock(QuestBranch.class);
        when(branches.getBranches()).thenReturn(List.of(branch));
        controller = mock(StageController.class);
        when(branch.getRegularStages()).thenReturn(List.of(controller));
        when(branch.getRegularStage(0)).thenReturn(controller);
        when(branch.getEndingStages()).thenReturn(List.of());
        stage = mock(BeautyQuestsEnforcers.DeliveryStage.class);
        when(controller.getStage()).thenReturn(stage);
        when(stage.getController()).thenReturn(controller);
        when(controller.hasStarted(quester)).thenReturn(true);
        try (var items = mockConstruction(org.bukkit.inventory.ItemStack.class)) {
            integration = new BeautyQuestsEnforcers(plugin, dealer);
        }
    }

    @AfterEach void cleanup() { apiMock.close(); }

    @Test void identifiesLoadedQuestAndFailedRepeatableFlag() {
        when(quest.isRepeatable()).thenReturn(false);
        String reason = integration.unavailableReason(player);
        assertTrue(reason.contains("quest 42"));
        assertTrue(reason.contains("repeatable=false"));
        assertNull(integration.begin(player, "joint", 16));
        verify(quest, never()).start(quester, false);
    }

    @Test void identifiesFailedCancellableFlag() {
        when(quest.isCancellable()).thenReturn(false);
        assertTrue(integration.unavailableReason(player).contains("cancellable=false"));
    }

    @Test void identifiesMissingLoadedStage() {
        when(controller.getStage()).thenReturn(null);
        assertTrue(integration.unavailableReason(player).contains("loaded null"));
    }

    @Test void publishesNpcIdentityThroughQuestDataWithoutMovementDependency() {
        var session = integration.begin(player, "joint", 16);
        assertNotNull(session);
        UUID npc = UUID.randomUUID();
        session.npc(npc);
        verify(stage).npc(quester, npc);
    }

    @Test void unmetRequirementsDoNotStartQuest() {
        when(quest.canStart(player, false)).thenReturn(false);
        assertNotNull(integration.unavailableReason(player));
        assertNull(integration.begin(player, "joint", 16));
        verify(quest, never()).start(quester, false);
    }

    @Test void successfulDeliveryFinishesStageOnceAndDoesNotCancelCompletedQuest() {
        var session = integration.begin(player, "joint", 16);
        assertNotNull(session);
        verify(quest).start(quester, false);
        verify(stage).progress(quester, "joint", 16, 0);
        session.progress(6);
        verify(stage).progress(quester, "joint", 16, 6);
        when(stage.complete(player, quester)).thenReturn(true);
        assertTrue(session.complete());
        assertFalse(session.complete());
        session.cancel();
        verify(stage).complete(player, quester);
        verify(quest, never()).cancelQuester(quester);
    }

    @Test void interruptedEncounterCancelsOnlyItsOwnQuestOnce() {
        var session = integration.begin(player, "joint", 16);
        when(quest.hasStarted(quester)).thenReturn(true);
        session.cancel();
        session.cancel();
        verify(quest).cancelQuester(quester);
        verify(stage, never()).complete(player, quester);
        assertFalse(session.active());
    }

    @Test void questCancellationAndDependencyDisableInvalidateSession() {
        var session = integration.begin(player, "joint", 16);
        when(controller.hasStarted(quester)).thenReturn(false);
        assertFalse(session.active());
        assertFalse(session.complete());
        when(controller.hasStarted(quester)).thenReturn(true);
        when(plugins.isPluginEnabled("BeautyQuests")).thenReturn(false);
        assertFalse(session.active());
        assertFalse(session.complete());
    }

    @Test void menuStartedQuestIsAttachedWithoutStartingItAgain() {
        when(quest.hasStarted(quester)).thenReturn(true);
        assertNotNull(integration.begin(player, "joint", 16));
        verify(quest, never()).start(quester, false);
        assertNull(integration.begin(player, "joint", 16));
    }

    @Test void stageRequirementsCannotBeBypassedByDelivery() {
        var session = integration.begin(player, "joint", 16);
        when(stage.complete(player, quester)).thenReturn(false);
        assertFalse(session.complete());
        assertTrue(session.active());
    }
    @Test void defaultConfigurationCreatesAndSelectsQuestAutomatically() {
        config.set("enforcer.beautyquests-quest-id", -1);
        var beautyQuests = mock(fr.skytasul.quests.api.QuestsPlugin.class);
        when(api.getPlugin()).thenReturn(beautyQuests);
        when(beautyQuests.getDataFolder()).thenReturn(folder.toFile());
        when(manager.getQuest(9000)).thenReturn(quest);
        try (var items = mockConstruction(org.bukkit.inventory.ItemStack.class)) {
            var automatic = new BeautyQuestsEnforcers(plugin, dealer);
            assertNull(automatic.unavailableReason(player));
            assertTrue(java.nio.file.Files.exists(folder.resolve("quests/lumidrugs-cartel.yml")));
            verify(manager).getQuest(9000);
        }
    }

    @Test void setupFailureReportsUsefulErrorWithoutOverwritingQuest() throws Exception {
        config.set("enforcer.beautyquests-quest-id", -1);
        var beautyQuests = mock(fr.skytasul.quests.api.QuestsPlugin.class);
        when(api.getPlugin()).thenReturn(beautyQuests);
        when(beautyQuests.getDataFolder()).thenReturn(folder.toFile());
        java.nio.file.Files.createDirectories(folder.resolve("quests"));
        var existing = folder.resolve("quests/lumidrugs-cartel.yml");
        java.nio.file.Files.writeString(existing, "id: 123\n");
        try (var items = mockConstruction(org.bukkit.inventory.ItemStack.class)) {
            var automatic = new BeautyQuestsEnforcers(plugin, dealer);
            assertTrue(automatic.unavailableReason(player).contains("Automatic cartel quest setup failed"));
            assertEquals("id: 123\n", java.nio.file.Files.readString(existing));
        }
    }

}
