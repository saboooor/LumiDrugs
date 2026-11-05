package com.drugs.quests;

import com.drugs.DrugsV2;
import com.drugs.cartel.CartelDealer;
import com.drugs.drug.DrugRegistry;
import com.drugs.addiction.CureRegistry;
import com.drugs.recipes.ProductionRecipes;
import com.drugs.ui.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import static com.drugs.quests.ProgressQuests.Kind.*;

/** Main-thread learning milestones and paid-sale contracts, independent of enforcer deadlines. */
public final class ProductionJournal implements org.bukkit.event.Listener {
    private final DrugsV2 plugin;
    private final CartelDealer dealer;
    private final ProgressQuests quests;
    private final QuestJournalStore store;
    private final BukkitTask task;
    private final Map<UUID, String> publishedLessons = new HashMap<>(), publishedContracts = new HashMap<>();
    public ProductionJournal(DrugsV2 plugin, CartelDealer dealer, ProgressQuests quests) throws Exception {
        this.plugin = plugin; this.dealer = dealer; this.quests = quests;
        store = new QuestJournalStore(plugin.getDataFolder().toPath().resolve("production-journal.yml"));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }
    public List<String> products() {
        var ids = new TreeSet<>(DrugRegistry.getRegisteredDrugNames());
        ids.addAll(ProductionRecipes.intermediates());
        for (String cure : CureRegistry.getRegisteredCureNames()) {
            var profile = CureRegistry.getProfileById(cure);
            if (profile != null && profile.isEnabled() && profile.isItemEnabled()) ids.add(cure);
        }
        return List.copyOf(ids);
    }
    private boolean allowed(Player player) {
        if (player.hasPermission("drugs.quests")) return true;
        player.sendMessage("§cYou cannot use production quests."); return false;
    }
    private boolean available(Player player, ProgressQuests.Kind kind) {
        String reason = quests.unavailable(player, kind);
        if (reason == null) return true;
        player.sendMessage("§e" + reason); return false;
    }
    public void learn(Player player, String product) {
        if (!allowed(player)) return;
        product = product.toLowerCase(Locale.ROOT);
        if (!products().contains(product)) { player.sendMessage("§eChoose a product through /drugs journal."); return; }
        UUID id = player.getUniqueId();
        if (store.lessons.containsKey(id)) {
            player.sendMessage("§eYou already have a learning quest. Resume or abandon it in /drugs journal."); open(player); return;
        }
        if (!available(player, LEARNING)) return;
        store.lessons.put(id, new QuestJournalStore.Lesson(product, 0));
        if (!quests.begin(player, LEARNING)) { store.lessons.remove(id); player.sendMessage("§eThe learning quest could not start."); return; }
        save();
        player.sendMessage("§aLearning quest started: " + ProductionPlan.name(product) + ". No deadline; open /drugs journal to resume.");
        updateLesson(player); open(player);
    }
    public void accept(Player player, String product) {
        if (!allowed(player) || !player.hasPermission("drugs.cartel.sell")) return;
        UUID id = player.getUniqueId();
        expire(player);
        if (store.contracts.containsKey(id)) { player.sendMessage("§eFinish or abandon your current supply contract first."); open(player); return; }
        if (!dealer.contractProducts().contains(product)) { player.sendMessage("§eThat product has no available supply contract."); return; }
        if (!dealer.contractsEnabled()) { player.sendMessage("§eSupply contracts are disabled."); return; }
        if (!available(player, SUPPLY)) return;
        int amount = dealer.contractAmount();
        long deadline = System.currentTimeMillis() + dealer.contractHours() * 3_600_000L;
        store.contracts.put(id, new QuestJournalStore.Contract(product, amount, 0, deadline));
        if (!quests.begin(player, SUPPLY)) { store.contracts.remove(id); player.sendMessage("§eThe supply contract could not start."); return; }
        save();
        player.sendMessage("§aSupply contract accepted. Sell " + amount + " " + ProductionPlan.name(product)
                + " to stationary buyers within " + dealer.contractHours() + " hours. Normal sale prices apply.");
        publishContract(player); open(player);
    }
    public void sold(Player player, String product, int count) {
        UUID id = player.getUniqueId();
        expire(player);
        var contract = store.contracts.get(id);
        if (contract == null) return;
        var next = contract.credit(product, count, System.currentTimeMillis());
        if (next.equals(contract)) return;
        store.contracts.put(id, next); save();
        if (!completeContract(player, next)) {
            publishContract(player);
            player.sendMessage("§aSupply contract: " + next.delivered() + "/" + next.amount() + " " + ProductionPlan.name(product));
        }
    }
    private boolean completeContract(Player player, QuestJournalStore.Contract contract) {
        if (!contract.complete() || !quests.begin(player, SUPPLY) || !quests.finish(player, SUPPLY)) return false;
        store.contracts.remove(player.getUniqueId()); publishedContracts.remove(player.getUniqueId()); save();
        player.sendMessage("§aSupply contract complete! Your deliveries were paid at the normal buyer prices.");
        com.drugs.achievements.AchievementManager.processTrigger(player, "supply_contract_complete");
        return true;
    }

    private void expire(Player player) {
        var contract = store.contracts.get(player.getUniqueId());
        if (contract != null && contract.expired(System.currentTimeMillis())) {
            store.contracts.remove(player.getUniqueId()); publishedContracts.remove(player.getUniqueId()); save();
            quests.cancel(player, SUPPLY);
            player.sendMessage("§eYour supply contract expired. No refusal penalty; previous sales stay paid.");
        }
    }
    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) continue;
            updateLesson(player);
            expire(player);
            var contract = store.contracts.get(player.getUniqueId());
            if (contract != null && !completeContract(player, contract)) publishContract(player);
        }
    }
    private void updateLesson(Player player) {
        UUID id = player.getUniqueId();
        var lesson = store.lessons.get(id);
        if (lesson == null || !quests.begin(player, LEARNING)) return;
        var plan = ProductionPlan.forProduct(lesson.product());
        int completed = Math.clamp(lesson.completed(), 0, plan.size());
        int next = ProductionPlan.advance(plan, completed, player.getInventory().getContents());
        if (next >= plan.size()) {
            if (!quests.finish(player, LEARNING)) return;
            store.lessons.remove(id); publishedLessons.remove(id); save();
            player.sendMessage("§aLearning quest complete: " + ProductionPlan.name(lesson.product()) + "! Your items are yours to keep."); return;
        }
        if (next != lesson.completed()) {
            store.lessons.put(id, new QuestJournalStore.Lesson(lesson.product(), next)); save();
            player.sendMessage("§aNext production step: " + plan.get(next).label() + ". Open /drugs journal for instructions.");
        }
        String description = ProductionPlan.name(lesson.product()) + ": " + plan.get(next).label() + " (" + next + "/" + plan.size() + ")";
        if (!description.equals(publishedLessons.get(id))) {
            quests.describe(player, LEARNING, description); publishedLessons.put(id, description);
        }
    }
    private void publishContract(Player player) {
        var contract = store.contracts.get(player.getUniqueId());
        if (contract == null) return;
        long minutes = Math.max(0, (contract.deadline() - System.currentTimeMillis() + 59_999) / 60_000);
        String description = "Sell " + ProductionPlan.name(contract.product()) + " to stationary buyers: " + contract.delivered()
                + "/" + contract.amount() + " (" + minutes + " min left)";
        if (!description.equals(publishedContracts.get(player.getUniqueId())) && quests.begin(player, SUPPLY)) {
            quests.describe(player, SUPPLY, description); publishedContracts.put(player.getUniqueId(), description);
        }
    }
    public void abandon(Player player, ProgressQuests.Kind kind) {
        if (!allowed(player)) return;
        externalEnd(player.getUniqueId(), kind); quests.cancel(player, kind);
        player.sendMessage("§eQuest abandoned. No refusal penalty; previous sales stay paid."); open(player);
    }
    public void externalEnd(UUID id, ProgressQuests.Kind kind) {
        boolean changed = kind == LEARNING ? store.lessons.remove(id) != null : store.contracts.remove(id) != null;
        if (kind == LEARNING) publishedLessons.remove(id); else publishedContracts.remove(id);
        if (changed) save();
    }
    public void open(Player player) {
        if (!allowed(player)) return;
        expire(player);
        var actions = new ArrayList<DialogView.ActionButton>();
        var lines = new ArrayList<String>();
        var lesson = store.lessons.get(player.getUniqueId());
        if (lesson == null) {
            lines.add("Learning quests have no deadline. Acquire the listed items to advance; existing supplies skip earlier steps. Items are never consumed by the tutorial.");
            for (String product : products()) actions.add(Dialogs.button("Learn: " + ProductionPlan.name(product), "/drugs learn " + product, false, DialogView.Icon.BOOK));
        } else {
            var steps = ProductionPlan.forProduct(lesson.product());
            int next = Math.clamp(lesson.completed(), 0, steps.size() - 1);
            lines.add("Learning: " + ProductionPlan.name(lesson.product()) + " — " + lesson.completed() + "/" + steps.size() + " steps complete. No deadline.");
            for (int i = 0; i < steps.size(); i++) lines.add((i < next ? "Complete: " : i == next ? "Next: " : "Later: ") + steps.get(i).label());
            var step = steps.get(next);
            if (step.recipe() != null) actions.add(Dialogs.button("View next recipe", "/drugs recipe " + step.recipe(), false, DialogView.Icon.CRAFTING_TABLE));
            if (step.crop() != null) actions.add(Dialogs.button("Growing instructions", "/drugs growing " + step.crop(), false, DialogView.Icon.SEEDS));
            actions.add(Dialogs.button("Abandon learning quest", "/drugs journal abandon-learning", false, DialogView.Icon.CLOSE));
        }
        var contract = store.contracts.get(player.getUniqueId());
        if (contract != null) {
            long minutes = Math.max(0, (contract.deadline() - System.currentTimeMillis() + 59_999) / 60_000);
            lines.add("Supply: " + ProductionPlan.name(contract.product()) + " — " + contract.delivered() + "/" + contract.amount() + "; " + minutes + " minutes left. Sell to any stationary buyer. Normal prices apply; enforcer deliveries do not count.");
            actions.add(Dialogs.button("Learn this contract's product", "/drugs learn " + contract.product(), false, DialogView.Icon.BOOK));
            actions.add(Dialogs.button("Abandon supply contract", "/drugs journal abandon-supply", false, DialogView.Icon.CLOSE));
        } else actions.add(Dialogs.button("Choose a supply contract", "/drugs contracts", false, DialogView.Icon.SHARED));
        plugin.showDialog(player, Dialogs.menu(GrowingGuide.formattedBase("Production journal", lines), actions));
    }
    public void contracts(Player player) {
        if (!allowed(player) || !player.hasPermission("drugs.cartel.sell")) return;
        if (!dealer.contractsEnabled()) { player.sendMessage("§eSupply contracts are disabled."); return; }
        var actions = new ArrayList<DialogView.ActionButton>();
        for (String product : dealer.contractProducts()) actions.add(Dialogs.button(ProductionPlan.name(product), "/drugs contracts " + product, false, DialogView.Icon.SHARED));
        actions.add(Dialogs.button("Quest journal", "/drugs journal", false, DialogView.Icon.BOOK));
        plugin.showDialog(player, Dialogs.menu(Dialogs.base("Supply contracts", "Choose a product to accept a " + dealer.contractAmount()
                + "-item contract lasting " + dealer.contractHours() + " hours. Sell to any stationary buyer; normal prices apply."
                + "\nOnly successful sales after acceptance count. Extra items are sold normally. Expiry or abandonment has no refusal penalty."), actions));
    }
    private void save() {
        try { store.save(); }
        catch (java.io.IOException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not save production journal", error); }
    }
    @org.bukkit.event.EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        publishedLessons.remove(event.getPlayer().getUniqueId());
        publishedContracts.remove(event.getPlayer().getUniqueId());
    }
    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        publishedLessons.remove(event.getPlayer().getUniqueId());
        publishedContracts.remove(event.getPlayer().getUniqueId());
    }
    public boolean hasProgress(UUID id, ProgressQuests.Kind kind) {
        return kind == LEARNING ? store.lessons.containsKey(id) : store.contracts.containsKey(id);
    }
    public void shutdown() { task.cancel(); save(); }
}
