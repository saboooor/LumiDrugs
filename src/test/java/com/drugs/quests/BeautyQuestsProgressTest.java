package com.drugs.quests;

import com.drugs.DrugsV2;
import fr.skytasul.quests.api.*;
import fr.skytasul.quests.api.players.PlayerQuester;
import fr.skytasul.quests.api.quests.*;
import fr.skytasul.quests.api.quests.branches.*;
import fr.skytasul.quests.api.quests.quester.QuestQuesterStrategy;
import fr.skytasul.quests.api.stages.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import java.nio.file.*;
import java.util.*;
import static com.drugs.quests.ProgressQuests.Kind.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BeautyQuestsProgressTest {
    @TempDir Path folder;
    private MockedStatic<QuestsAPI> apiMock;
    private DrugsV2 plugin;
    private BeautyQuestsProgress bridge;
    private Quest quest;
    private Player player;
    private PlayerQuester quester;
    private StageController controller;
    private BeautyQuestsProgress.LearningStage stage;
    @BeforeEach void setup() throws Exception {
        var api = mock(QuestsAPI.class); apiMock = mockStatic(QuestsAPI.class);
        apiMock.when(QuestsAPI::getAPI).thenReturn(api);
        var types = mock(StageTypeRegistry.class); when(api.getStages()).thenReturn(types);
        var beauty = mock(QuestsPlugin.class); when(api.getPlugin()).thenReturn(beauty);
        when(beauty.getDataFolder()).thenReturn(folder.toFile());
        var manager = mock(QuestsManager.class); when(api.getQuestsManager()).thenReturn(manager);
        plugin = mock(DrugsV2.class);
        var server = mock(Server.class); when(plugin.getServer()).thenReturn(server);
        var plugins = mock(PluginManager.class); when(server.getPluginManager()).thenReturn(plugins);
        when(plugins.isPluginEnabled("BeautyQuests")).thenReturn(true);
        try (var items = mockConstruction(org.bukkit.inventory.ItemStack.class)) { bridge = new BeautyQuestsProgress(plugin); }
        verify(types, times(2)).register(any());
        quest = mock(Quest.class); when(manager.getQuest(9100)).thenReturn(quest);
        when(quest.isValid()).thenReturn(true); when(quest.isRepeatable()).thenReturn(true); when(quest.isCancellable()).thenReturn(true);
        player = mock(Player.class); var uuid = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(uuid);
        quester = mock(PlayerQuester.class); var offline = mock(OfflinePlayer.class);
        when(quester.getOfflinePlayer()).thenReturn(offline); when(offline.getUniqueId()).thenReturn(uuid);
        var strategy = mock(QuestQuesterStrategy.class); when(quest.getQuesterStrategy()).thenReturn(strategy);
        doReturn(Optional.of(quester)).when(strategy).getPlayerQuester(player);
        when(quest.canStart(player, false)).thenReturn(true);
        var branches = mock(QuestBranchesManager.class); when(quest.getBranchesManager()).thenReturn(branches);
        var branch = mock(QuestBranch.class); when(branches.getBranches()).thenReturn(List.of(branch));
        controller = mock(StageController.class);
        when(branch.getRegularStages()).thenReturn(List.of(controller)); when(branch.getRegularStage(0)).thenReturn(controller);
        when(branch.getEndingStages()).thenReturn(List.of());
        stage = mock(BeautyQuestsProgress.LearningStage.class); when(controller.getStage()).thenReturn(stage);
        when(stage.getController()).thenReturn(controller);
        when(stage.kind()).thenReturn(LEARNING);
        when(controller.hasStarted(quester)).thenReturn(true);
    }
    @AfterEach void cleanup() { apiMock.close(); }
    @Test void createsSeparatePersistentQuestFilesBeforeDeferredLoading() throws Exception {
        assertTrue(Files.readString(folder.resolve("quests/lumidrugs-learning.yml")).contains("lumidrugs-production-learning"));
        assertTrue(Files.readString(folder.resolve("quests/lumidrugs-supply.yml")).contains("lumidrugs-supply-contract"));
    }
    @Test void startsPersonalQuestAndPublishesItsCurrentMilestone() {
        assertNull(bridge.unavailable(player, LEARNING));
        assertTrue(bridge.begin(player, LEARNING));
        verify(quest).start(quester, false);
        bridge.describe(player, LEARNING, "Vape: brew liquid (3/5)");
        verify(stage).describe(quester, "Vape: brew liquid (3/5)");
    }
    @Test void resumesExistingQuestWithoutStartingItAgain() {
        when(quest.hasStarted(quester)).thenReturn(true);
        assertTrue(bridge.begin(player, LEARNING));
        verify(quest, never()).start(any(), anyBoolean());
    }
    @Test void respectsQuestLimitsAndCompletionRequirements() {
        when(quest.canStart(player, false)).thenReturn(false);
        assertFalse(bridge.begin(player, LEARNING));
        verify(quest, never()).start(any(), anyBoolean());
        when(stage.complete(player, quester)).thenReturn(false);
        assertFalse(bridge.finish(player, LEARNING));
        when(stage.complete(player, quester)).thenReturn(true);
        assertTrue(bridge.finish(player, LEARNING));
    }
    @Test void cancellationTargetsOnlyTheSelectedQuest() {
        when(quest.hasStarted(quester)).thenReturn(true);
        bridge.cancel(player, LEARNING);
        verify(quest).cancelQuester(quester);
        bridge.cancel(player, SUPPLY);
        verify(quest, times(1)).cancelQuester(quester);
    }

}
