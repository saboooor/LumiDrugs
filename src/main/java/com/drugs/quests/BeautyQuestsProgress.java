package com.drugs.quests;

import com.drugs.DrugsV2;
import fr.skytasul.quests.api.QuestsAPI;
import fr.skytasul.quests.api.players.PlayerQuester;
import fr.skytasul.quests.api.questers.Quester;
import fr.skytasul.quests.api.quests.Quest;
import fr.skytasul.quests.api.stages.*;
import fr.skytasul.quests.api.stages.creation.StageCreation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.EnumMap;

/** Two persistent personal quests; no encounter NPC or following behavior is attached. */
final class BeautyQuestsProgress implements ProgressQuests {
    private final DrugsV2 plugin;
    private final EnumMap<Kind, Integer> ids = new EnumMap<>(Kind.class);
    BeautyQuestsProgress(DrugsV2 plugin) throws Exception {
        this.plugin = plugin;
        QuestsAPI.getAPI().getStages().register(new StageType<>("lumidrugs-production-learning", LearningStage.class,
                "Drug production learning", (section, controller) -> new LearningStage(controller, this), new ItemStack(Material.BOOK),
                context -> new StageCreation<LearningStage>(context) {
                    @Override protected LearningStage finishStage(StageController controller) { return new LearningStage(controller, BeautyQuestsProgress.this); }
                }));
        QuestsAPI.getAPI().getStages().register(new StageType<>("lumidrugs-supply-contract", SupplyStage.class,
                "Drug supply contract", (section, controller) -> new SupplyStage(controller, this), new ItemStack(Material.EMERALD),
                context -> new StageCreation<SupplyStage>(context) {
                    @Override protected SupplyStage finishStage(StageController controller) { return new SupplyStage(controller, BeautyQuestsProgress.this); }
                }));
        var folder = QuestsAPI.getAPI().getPlugin().getDataFolder().toPath().resolve("quests");
        ids.put(Kind.LEARNING, ProgressQuestSetup.prepare(folder, "lumidrugs-learning.yml", "lumidrugs-production-learning", "Learn drug production"));
        ids.put(Kind.SUPPLY, ProgressQuestSetup.prepare(folder, "lumidrugs-supply.yml", "lumidrugs-supply-contract", "Voluntary supply contract"));
    }
    private Quest quest(Kind kind) {
        return !plugin.getServer().getPluginManager().isPluginEnabled("BeautyQuests") ? null
                : QuestsAPI.getAPI().getQuestsManager().getQuest(ids.get(kind));
    }
    private PlayerQuester quester(Quest quest, Player player) {
        var value = quest.getQuesterStrategy().getPlayerQuester(player).orElse(null);
        return value instanceof PlayerQuester personal && personal.getOfflinePlayer().getUniqueId().equals(player.getUniqueId()) ? personal : null;
    }
    private ProgressStage stage(Quest quest, Kind kind) {
        var branches = quest.getBranchesManager().getBranches();
        if (branches.size() != 1) return null;
        var branch = branches.iterator().next();
        if (branch.getRegularStages().size() != 1 || !branch.getEndingStages().isEmpty()) return null;
        return branch.getRegularStage(0).getStage() instanceof ProgressStage progress && progress.kind() == kind ? progress : null;
    }
    public String unavailable(Player player, Kind kind) {
        var quest = quest(kind);
        if (quest == null || !quest.isValid()) return "Production quests are not loaded. Restart with BeautyQuests enabled and check the server logs.";
        if (!quest.isRepeatable() || !quest.isCancellable() || stage(quest, kind) == null)
            return "The production quest must be repeatable, cancellable, and contain its original single stage.";
        var quester = quester(quest, player);
        if (quester == null) return "Production quests require individual player progression.";
        if (!quest.hasStarted(quester) && !quest.canStart(player, false)) return "BeautyQuests requirements or quest limits prevent starting this quest.";
        return null;
    }
    public boolean begin(Player player, Kind kind) {
        if (unavailable(player, kind) != null) return false;
        var quest = quest(kind); var quester = quester(quest, player);
        if (!quest.hasStarted(quester)) quest.start(quester, false);
        return stage(quest, kind).getController().hasStarted(quester);
    }
    public void describe(Player player, Kind kind, String text) {
        var quest = quest(kind);
        if (quest == null || !quest.isValid()) return;
        var stage = stage(quest, kind); var quester = quester(quest, player);
        if (stage != null && quester != null && stage.getController().hasStarted(quester)) stage.describe(quester, text);
    }
    public boolean finish(Player player, Kind kind) {
        var quest = quest(kind);
        if (quest == null || !quest.isValid()) return false;
        var stage = stage(quest, kind); var quester = quester(quest, player);
        return stage != null && quester != null && stage.getController().hasStarted(quester) && stage.complete(player, quester);
    }
    public void cancel(Player player, Kind kind) {
        var quest = quest(kind);
        if (quest == null) return;
        var quester = quester(quest, player);
        if (quester != null && quest.hasStarted(quester)) quest.cancelQuester(quester);
    }
    public abstract static class ProgressStage extends AbstractStage {
        final BeautyQuestsProgress owner;
        final Kind kind;
        ProgressStage(StageController controller, BeautyQuestsProgress owner, Kind kind) {
            super(controller); this.owner = owner; this.kind = kind;
        }
        Kind kind() { return kind; }
        void describe(Quester quester, String text) { updateObjective(quester, "production", text); }
        boolean complete(Player player, Quester quester) {
            if (!matchesRequirements(player, true)) return false;
            finishStage(quester); return true;
        }
        @Override public String getDefaultDescription(StageDescriptionPlaceholdersContext context) {
            var text = getData(context.getQuester(), "production", String.class);
            return text == null ? "Open /drugs journal to choose a product" : text;
        }
        @Override public void started(Quester quester) {
            owner.plugin.getServer().getScheduler().runTask(owner.plugin, () -> {
                var journal = owner.plugin.getProductionJournal();
                if (journal != null && quester instanceof PlayerQuester personal
                        && !journal.hasProgress(personal.getOfflinePlayer().getUniqueId(), kind)
                        && getController().hasStarted(quester)) {
                    getQuest().cancelQuester(quester);
                    personal.getPlayer().ifPresent(player -> player.sendMessage("§eChoose a product through /drugs journal to start this quest."));
                }
            });
        }
        @Override public void ended(Quester quester) {
            var journal = owner.plugin.getProductionJournal();
            if (journal != null && quester instanceof PlayerQuester personal)
                journal.externalEnd(personal.getOfflinePlayer().getUniqueId(), kind);
        }
        // Persist across disconnects and restarts; the journal re-publishes objectives after startup.
    }
    public static final class LearningStage extends ProgressStage {
        public LearningStage(StageController controller, BeautyQuestsProgress owner) { super(controller, owner, Kind.LEARNING); }
    }
    public static final class SupplyStage extends ProgressStage {
        public SupplyStage(StageController controller, BeautyQuestsProgress owner) { super(controller, owner, Kind.SUPPLY); }
    }
}
