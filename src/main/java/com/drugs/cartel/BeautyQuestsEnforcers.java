package com.drugs.cartel;

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

/** BeautyQuests 2.0 API; loaded only when the optional dependency is enabled. */
final class BeautyQuestsEnforcers implements EnforcerQuests {
    private final DrugsV2 plugin;
    private final CartelDealer dealer;
    private CartelEnforcer enforcer;
    private int automaticQuestId = -1;
    private String setupError;
    private final java.util.Map<Quester, Session> sessions = new java.util.HashMap<>();

    @Override public void bind(CartelEnforcer enforcer) { this.enforcer = enforcer; }

    BeautyQuestsEnforcers(DrugsV2 plugin, CartelDealer dealer) {
        this.plugin = plugin;
        this.dealer = dealer;
        // BeautyQuests enables before us, but loads saved quests on a later server tick.
        QuestsAPI.getAPI().getStages().register(new StageType<>(CartelQuestSetup.STAGE_TYPE,
                DeliveryStage.class, "Cartel delivery", (section, controller) -> new DeliveryStage(controller, BeautyQuestsEnforcers.this),
                new ItemStack(Material.EMERALD), context -> new StageCreation<DeliveryStage>(context) {
                    @Override protected DeliveryStage finishStage(StageController controller) {
                        return new DeliveryStage(controller, BeautyQuestsEnforcers.this);
                    }
                }));
        if (dealer.settings().getInt("enforcer.beautyquests-quest-id", -1) < 0) {
            try {
                var folder = QuestsAPI.getAPI().getPlugin().getDataFolder().toPath().resolve("quests");
                automaticQuestId = CartelQuestSetup.prepare(folder);
                plugin.getLogger().info("Cartel enforcers use BeautyQuests quest " + automaticQuestId
                        + " (" + folder.resolve(CartelQuestSetup.FILE_NAME) + ").");
            } catch (java.io.IOException | org.bukkit.configuration.InvalidConfigurationException exception) {
                setupError = "Automatic cartel quest setup failed: " + exception.getMessage();
                plugin.getLogger().log(java.util.logging.Level.SEVERE, setupError, exception);
            }
        }
    }

    private boolean enabled() {
        return plugin.getServer().getPluginManager().isPluginEnabled("BeautyQuests");
    }

    private Quest quest() {
        int configured = dealer.settings().getInt("enforcer.beautyquests-quest-id", -1);
        int id = configured < 0 ? automaticQuestId : configured;
        return id < 0 ? null : QuestsAPI.getAPI().getQuestsManager().getQuest(id);
    }

    private DeliveryStage stage(Quest quest) {
        if (quest.getBranchesManager().getBranches().size() != 1) return null;
        var branch = quest.getBranchesManager().getBranches().iterator().next();
        if (branch.getRegularStages().size() != 1 || !branch.getEndingStages().isEmpty()) return null;
        return branch.getRegularStage(0).getStage() instanceof DeliveryStage stage ? stage : null;
    }

    private Quester personalQuester(Quest quest, Player player) {
        var quester = quest.getQuesterStrategy().getPlayerQuester(player).orElse(null);
        return quester instanceof PlayerQuester personal
                && personal.getOfflinePlayer().getUniqueId().equals(player.getUniqueId()) ? quester : null;
    }

    @Override public String unavailableReason(Player player) {
        if (!enabled()) return "BeautyQuests is not enabled. Restart with BeautyQuests installed.";
        Quest quest = quest();
        if (quest == null || !quest.isValid()) {
            if (dealer.settings().getInt("enforcer.beautyquests-quest-id", -1) < 0)
                return setupError != null ? setupError : "The automatic cartel quest is not loaded. Restart the server and check BeautyQuests startup logs.";
            return "The custom cartel quest ID is invalid. Set beautyquests-quest-id to -1 and restart for automatic setup, or choose a valid quest ID.";
        }
        String prefix = "Cartel quest " + quest.getId() + ": ";
        if (!quest.isRepeatable()) return prefix + "BeautyQuests reports repeatable=false. Enable Repeatable in /quests edit " + quest.getId() + ".";
        if (!quest.isCancellable()) return prefix + "BeautyQuests reports cancellable=false. Enable Cancellable in /quests edit " + quest.getId() + ".";
        var branches = quest.getBranchesManager().getBranches();
        if (branches.size() != 1) return prefix + "expected one branch, loaded " + branches.size() + ".";
        var branch = branches.iterator().next();
        if (branch.getRegularStages().size() != 1)
            return prefix + "expected one regular Cartel delivery stage, loaded " + branch.getRegularStages().size() + ".";
        if (!branch.getEndingStages().isEmpty())
            return prefix + "expected no ending stages, loaded " + branch.getEndingStages().size() + ".";
        if (stage(quest) == null) {
            var loaded = branch.getRegularStage(0).getStage();
            return prefix + "expected a LumiDrugs Cartel delivery stage, loaded "
                    + (loaded == null ? "null" : loaded.getClass().getName())
                    + ". Fully restart the server with LumiDrugs enabled; avoid plugin hot reloads.";
        }
        if (personalQuester(quest, player) == null) return "The cartel quest must use individual player progression.";
        if (!quest.hasStarted(personalQuester(quest, player)) && !quest.canStart(player, false)) return "The target cannot start the cartel quest: check BeautyQuests requirements, quest limits and cooldowns.";
        return null;
    }

    @Override public Session begin(Player player, String drug, int amount) {
        if (unavailableReason(player) != null) return null;
        Quest quest = quest();
        Quester quester = personalQuester(quest, player);
        DeliveryStage stage = stage(quest);
        try {
            if (sessions.containsKey(quester)) return null;
            if (!quest.hasStarted(quester)) quest.start(quester, false);
            if (!stage.getController().hasStarted(quester)) {
                quest.cancelQuester(quester);
                return null;
            }
            Session session = new Session() {
                private boolean closed;
                public boolean active() {
                    return !closed && sessions.get(quester) == this && enabled() && quest() == quest && quest.isValid()
                            && stage.getController().hasStarted(quester);
                }
                public void npc(java.util.UUID entityId) {
                    if (active()) stage.npc(quester, entityId);
                }
                public void progress(int delivered) {
                    if (active()) stage.progress(quester, drug, amount, delivered);
                }
                public boolean complete() {
                    if (!active() || !stage.complete(player, quester)) return false;
                    closed = true;
                    sessions.remove(quester, this);
                    return true;
                }
                public void cancel() {
                    if (closed) return;
                    closed = true;
                    boolean owned = sessions.remove(quester, this);
                    if (owned && enabled() && QuestsAPI.getAPI().getQuestsManager().getQuest(quest.getId()) == quest
                            && quest.hasStarted(quester)) quest.cancelQuester(quester);
                }
            };
            sessions.put(quester, session);
            session.progress(0);
            return session;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not start cartel BeautyQuests quest", exception);
            if (quest.hasStarted(quester)) quest.cancelQuester(quester);
            return null;
        }
    }

    public static final class DeliveryStage extends AbstractStage {
        private final BeautyQuestsEnforcers owner;
        public DeliveryStage(StageController controller, BeautyQuestsEnforcers owner) {
            super(controller);
            this.owner = owner;
        }

        @Override public void started(Quester quester) {
            // Defer until BeautyQuests finishes installing stage data and begin() records its session.
            owner.plugin.getServer().getScheduler().runTask(owner.plugin, () -> {
                if (!getController().hasStarted(quester) || owner.sessions.containsKey(quester)) return;
                Player player = quester instanceof PlayerQuester personal ? personal.getPlayer().orElse(null) : null;
                String error = !owner.enabled() || getQuest() != owner.quest() || player == null || owner.enforcer == null ? "No online individual player."
                        : owner.enforcer.startQuestEncounter(player);
                if (error != null) {
                    getQuest().cancelQuester(quester);
                    if (player != null) player.sendMessage(org.bukkit.ChatColor.GRAY + "Cartel quest called off: " + error);
                }
            });
        }

        @Override public String getDefaultDescription(StageDescriptionPlaceholdersContext context) {
            String delivery = getData(context.getQuester(), "delivery", String.class);
            return delivery == null ? "Complete a cartel enforcer delivery" : delivery;
        }

        void npc(Quester quester, java.util.UUID entityId) {
            updateObjective(quester, "npc", entityId.toString());
        }

        void progress(Quester quester, String drug, int amount, int delivered) {
            updateObjective(quester, "delivery", "Deliver " + drug + " to your enforcer: " + delivered + "/" + amount);
        }

        boolean complete(Player player, Quester quester) {
            if (!matchesRequirements(player, true)) return false;
            finishStage(quester);
            return true;
        }

        @Override public void ended(Quester quester) {
            // A cancelled/restarted quest must not inherit an old NPC's paid deliveries.
            owner.sessions.remove(quester);
        }

        @Override public void joined(Player player, Quester quester) {
            // Temporary NPCs do not survive a restart. Clear an abandoned saved demand on login.
            if (getController().hasStarted(quester)) getQuest().cancelQuester(quester);
        }
    }
}
