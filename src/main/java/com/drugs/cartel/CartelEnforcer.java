package com.drugs.cartel;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;
import com.drugs.items.DrugItemMetadata;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.random.RandomGenerator;

/** Temporary, player-specific encounters. Navigation and sales always run on the main thread. */
public final class CartelEnforcer implements Listener {
    private final DrugsV2 plugin;
    private final CartelDealer dealer;
    private final EnforcerQuests quests;
    private final NamespacedKey key;
    private final NamespacedKey refusalsKey;
    private final LongSupplier clock;
    private final Map<UUID, Encounter> encounters = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private BukkitTask task;
    private long nextCheck;

    public CartelEnforcer(DrugsV2 plugin, CartelDealer dealer) {
        this(plugin, dealer, System::currentTimeMillis, EnforcerQuests.create(plugin, dealer));
    }

    CartelEnforcer(DrugsV2 plugin, CartelDealer dealer, LongSupplier clock, EnforcerQuests quests) {
        this.plugin = plugin;
        this.dealer = dealer;
        this.quests = quests;
        quests.bind(this);
        this.clock = clock;
        key = new NamespacedKey(plugin, "cartel_enforcer");
        refusalsKey = new NamespacedKey(plugin, "cartel_refusal_streak");
    }

    public void start() {
        // Recover from an interrupted previous run without keeping abandoned NPCs.
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) villager.remove();
            }
        }
        nextCheck = clock.getAsLong() + seconds("check-interval-seconds", 300, 10, 86400) * 1000L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID player : new ArrayList<>(encounters.keySet())) finish(player);
        cooldowns.clear();
    }

    public void reload() {
        // Cancel existing demands rather than changing their price or rules halfway through.
        for (UUID player : new ArrayList<>(encounters.keySet())) {
            Player online = Bukkit.getPlayer(player);
            if (online != null) online.sendMessage(ChatColor.GRAY + "The cartel has called off its demand.");
            finish(player);
        }
        nextCheck = clock.getAsLong() + seconds("check-interval-seconds", 300, 10, 86400) * 1000L;
    }

    private FileConfiguration config() { return dealer.settings(); }

    private int seconds(String setting, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, config().getInt("enforcer." + setting, fallback)));
    }

    private boolean hasBypass(Player player) {
        return player.isPermissionSet("drugs.cartel.enforcer.bypass")
                && player.hasPermission("drugs.cartel.enforcer.bypass");
    }

    private boolean eligible(Player player) {
        return player.isOnline() && !player.isDead() && !player.hasMetadata("NPC")
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
                && player.hasPermission("drugs.cartel.sell")
                && !hasBypass(player);
    }

    private boolean isEnforcer(Entity entity) {
        return dealer.npcs() != null && dealer.npcs().find(entity, "enforcer") != null;
    }

    boolean onCommand(CommandSender sender, String[] args) {
        if (args.length != 1 && args.length != 4) {
            sender.sendMessage("Usage: /cartel enforcer [player drug amount]");
            return true;
        }
        Player target = args.length == 1 ? (sender instanceof Player player ? player : null)
                : Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Choose an online player: /cartel enforcer <player> <drug> <amount>");
            return true;
        }
        Demand demand;
        try {
            demand = args.length == 1 ? randomDemand(ThreadLocalRandom.current())
                    : new Demand(args[2].toLowerCase(Locale.ROOT), Integer.parseInt(args[3]));
        } catch (NumberFormatException exception) {
            sender.sendMessage(ChatColor.RED + "Amount must be a whole number between 1 and 2304.");
            return true;
        }
        String error = demand == null ? "No registered drugs have a positive, finite base price in cartel.yml."
                : startEncounter(target, demand.drug(), demand.amount(), true);
        if (error != null) sender.sendMessage(ChatColor.RED + "Could not start encounter: " + error);
        else sender.sendMessage(ChatColor.GREEN + "Cartel enforcer sent to " + target.getName() + ".");
        return true;
    }

    record Demand(String drug, int amount) {}

    Demand randomDemand(RandomGenerator random) {
        List<String> drugs = DrugRegistry.getRegisteredDrugNames().stream().filter(drug -> {
            double price = config().getDouble("prices." + drug, 0);
            return Double.isFinite(price) && price > 0;
        }).sorted().toList();
        if (drugs.isEmpty()) return null;
        int min = seconds("min-amount", 8, 1, 2304);
        int max = Math.max(min, seconds("max-amount", 32, 1, 2304));
        return new Demand(drugs.get(random.nextInt(drugs.size())), random.nextInt(min, max + 1));
    }

    boolean spawn(Player player, String drug, int amount, boolean manual) {
        return startEncounter(player, drug, amount, manual) == null;
    }

    /** Returns a specific failure message, or null after starting the encounter. */
    private String startEncounter(Player player, String drug, int amount, boolean manual) {
        long now = clock.getAsLong();
        if (!player.isOnline() || player.isDead()) return "The target must be online and alive.";
        if (player.hasMetadata("NPC")) return "The target is an NPC, not a real player.";
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
            return "The target must be in Survival or Adventure mode.";
        if (!player.hasPermission("drugs.cartel.sell")) return "The target lacks drugs.cartel.sell permission.";
        if (hasBypass(player))
            return "The target has drugs.cartel.enforcer.bypass. Remove that permission to allow encounters.";
        if (encounters.containsKey(player.getUniqueId()))
            return "The target already has an active encounter. Finish it or use /cartel reload to call it off.";
        if (drug == null || !DrugRegistry.isDrugRegistered(drug)) return "Unknown drug ID: " + drug + ". Use /drugs list.";
        if (amount < 1 || amount > 2304) return "Amount must be between 1 and 2304.";
        if (encounters.size() >= seconds("max-active", 8, 1, 100)) return "The server's enforcer.max-active limit has been reached.";
        if (!manual && cooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return "The target is on an encounter cooldown.";
        double price = config().getDouble("prices." + drug, 0);
        if (!Double.isFinite(price) || price <= 0)
            return "prices." + drug + " must be positive and finite in cartel.yml. Run /cartel reload after editing.";
        if (dealer.payments() == null) return "Vault has no enabled economy provider. Check Vault and EssentialsX startup logs.";
        CartelNpcs npcs = dealer.npcs();
        if (npcs == null) return "Cartel NPC support is not initialized. Check the startup log.";
        String unavailable = npcs.unavailableReason();
        if (unavailable != null) return unavailable;
        String questError = quests.unavailableReason(player);
        if (questError != null) return questError;
        Location location = findSpawn(player);
        if (location == null) return "No safe nearby ground found in loaded chunks. Try an open, flat area.";
        CartelNpcs.Character npc = npcs.create(location, "enforcer");
        if (npc == null) return "Citizens could not spawn the player NPC. Check the server console for errors or spawn protection.";
        if (!npc.valid()) {
            npc.destroy();
            return "The player NPC did not become valid. Check Citizens compatibility and the server console.";
        }
        EnforcerQuests.Session quest = quests.begin(player, drug, amount);
        if (quest == null) {
            npc.destroy();
            return "BeautyQuests could not start the cartel quest. Check its requirements and the server log.";
        }
        quest.npc(npc.entity().getUniqueId());
        Encounter encounter = new Encounter(npc, drug, amount, now, quest);
        encounters.put(player.getUniqueId(), encounter);
        say(player, encounter, "arrival", "&8Someone from the cartel is heading your way.", 0);
        return null;
    }

    String startQuestEncounter(Player player) {
        Demand demand = randomDemand(ThreadLocalRandom.current());
        return demand == null ? "No drugs have a positive cartel price."
                : startEncounter(player, demand.drug(), demand.amount(), false);
    }

    private Location findSpawn(Player player) {
        int minRadius = seconds("spawn-min-distance", 8, 4, 24);
        int maxRadius = Math.max(minRadius, seconds("spawn-max-distance", 12, 4, 32));
        return CartelSpawnLocations.findNear(player.getLocation(), minRadius, maxRadius);
    }

    void tick() {
        long now = clock.getAsLong();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
        boolean economyAvailable = encounters.isEmpty() || dealer.payments() != null;
        for (UUID id : new ArrayList<>(encounters.keySet())) {
            Encounter encounter = encounters.get(id);
            Player player = Bukkit.getPlayer(id);
            if (player == null || !eligible(player) || !encounter.npc.valid() || !encounter.quest.active()) {
                finish(id);
                continue;
            }
            if (!economyAvailable) {
                player.sendMessage(ChatColor.GRAY + "The cartel can't pay right now. The demand has been called off.");
                finish(id);
                continue;
            }
            double distance = player.getWorld().equals(encounter.npc.entity().getWorld())
                    ? player.getLocation().distanceSquared(encounter.npc.entity().getLocation()) : Double.MAX_VALUE;
            if (encounter.deadline == 0) {
                if (distance <= 12.25 && encounter.npc.entity().hasLineOfSight(player)) beginDemand(player, encounter, now);
                else if (now - encounter.spawnedAt >= seconds("approach-timeout-seconds", 60, 10, 300) * 1000L) {
                    finish(id);
                    continue;
                }
            }
            if (encounter.deadline > 0) {
                if (now >= encounter.deadline) {
                    threaten(player, encounter);
                    finish(id);
                    continue;
                }
                if (now >= encounter.nextWarning) {
                    say(player, encounter, "warning",
                            "&cThe cartel doesn't like waiting. Throw {remaining} more {drug} near me. You have {seconds}s left.",
                            remainingSeconds(encounter, now));
                    encounter.nextWarning = now + seconds("warning-interval-seconds", 15, 5, 300) * 1000L;
                }
            }
        }
        if (now >= nextCheck) {
            nextCheck = now + seconds("check-interval-seconds", 300, 10, 86400) * 1000L;
            if (!config().getBoolean("enforcer.enabled", true) || dealer.payments() == null) return;
            double chance = config().getDouble("enforcer.spawn-chance", 0.25);
            if (!Double.isFinite(chance)) return;
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            Collections.shuffle(players);
            for (Player player : players) {
                if (encounters.size() >= seconds("max-active", 8, 1, 100)) break;
                if (ThreadLocalRandom.current().nextDouble() < Math.max(0, Math.min(1, chance))) {
                    Demand demand = randomDemand(ThreadLocalRandom.current());
                    if (demand != null) spawn(player, demand.drug(), demand.amount(), false);
                }
            }
        }
    }

    private void beginDemand(Player player, Encounter encounter, long now) {
        int duration = seconds("deadline-seconds", 90, 10, 3600);
        int tier = angerTier(player);
        if (tier > 0) duration = Math.min(duration, seconds("aggravation." + (tier == 2 ? "furious" : "angry") + "-deadline-seconds", tier == 2 ? 30 : 45, 10, 3600));
        encounter.deadline = now + duration * 1000L;
        encounter.nextWarning = now + seconds("warning-interval-seconds", 15, 5, 300) * 1000L;
        say(player, encounter, "demand",
                "&4Cartel: &cSell me {amount} {drug} within {seconds}s, or you'll answer to the cartel. "
                        + "Throw the drugs near me. Smaller deliveries count, and you'll be paid.", duration);
    }

    private int remainingSeconds(Encounter encounter, long now) {
        return (int) Math.max(0, (encounter.deadline - now + 999) / 1000);
    }

    private void say(Player player, Encounter encounter, String message, String fallback, int seconds) {
        int tier = angerTier(player);
        String aggravated = aggravatedMessage(message, tier);
        if (aggravated != null) {
            message = (tier == 2 ? "furious-" : "angry-") + message;
            fallback = aggravated;
        }
        String text = config().getString("enforcer.messages." + message, fallback)
                .replace("{amount}", Integer.toString(encounter.amount))
                .replace("{drug}", encounter.drug).replace("{seconds}", Integer.toString(seconds))
                .replace("{remaining}", Integer.toString(encounter.amount - encounter.delivered))
                .replace("{refusals}", Integer.toString(refusalStreak(player)));
        // Update the previous default text in existing configs as well.
        text = text.replace("Right-click me with the full amount in your inventory.", "Throw the drugs near me. Smaller deliveries count.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', text));
    }

    int refusalStreak(Player player) {
        return Math.max(0, player.getPersistentDataContainer().getOrDefault(refusalsKey, PersistentDataType.INTEGER, 0));
    }

    private int angerTier(Player player) {
        int angry = seconds("aggravation.angry-after-refusals", 15, 1, 1000000);
        int furious = Math.max(angry, seconds("aggravation.furious-after-refusals", 20, 1, 1000000));
        int streak = refusalStreak(player);
        return streak >= furious ? 2 : streak >= angry ? 1 : 0;
    }

    private String aggravatedMessage(String message, int tier) {
        if (tier == 0) return null;
        return switch (message) {
            case "arrival" -> tier == 2
                    ? "&4The cartel enforcer is back. His patience with you is gone."
                    : "&cThe cartel enforcer is back, and he looks angry.";
            case "demand" -> tier == 2
                    ? "&4Cartel: &c&lEnough! &r&cYou've refused us {refusals} times in a row. Sell me {amount} {drug} in {seconds}s. Throw it near me. You'll be paid. No more excuses."
                    : "&4Cartel: &cYou've refused us {refusals} times in a row. My patience is running out. Sell me {amount} {drug} in {seconds}s. Throw it near me. You'll be paid.";
            case "warning" -> tier == 2
                    ? "&4Cartel: &c&lI'm done waiting! &r&c{remaining} more {drug}. {seconds}s left. Move!"
                    : "&4Cartel: &cDon't test me again. {remaining} more {drug}, and only {seconds}s left.";
            case "failure" -> tier == 2
                    ? "&4Cartel: &c&lAgain?! &r&c{refusals} refusals in a row. You've made an enemy of the cartel. This isn't over."
                    : "&4Cartel: &cThat's {refusals} refusals in a row. You're pushing your luck. Next time, come prepared.";
            default -> null;
        };
    }

    private void threaten(Player player, Encounter encounter) {
        int streak = refusalStreak(player);
        player.getPersistentDataContainer().set(refusalsKey, PersistentDataType.INTEGER,
                streak == Integer.MAX_VALUE ? streak : streak + 1);
        say(player, encounter, "failure", "&4Cartel: &cYou refused our deal. We'll remember this. Watch your back.", 0);
        // Dialogue only by default. Optional damage is capped and cannot deliberately kill the player.
        double damage = config().getDouble("enforcer.failure-damage", 0);
        if (Double.isFinite(damage) && damage > 0 && player.getWorld() == encounter.npc.entity().getWorld()
                && player.getLocation().distanceSquared(encounter.npc.entity().getLocation()) <= 36) {
            double nonlethal = Math.min(Math.min(20, damage), Math.max(0, player.getHealth() - 1));
            if (nonlethal > 0) player.damage(nonlethal, encounter.npc.entity());
        }
    }

    private void finish(UUID id) {
        Encounter removed = encounters.remove(id);
        if (removed == null) return;
        removed.npc.destroy();
        removed.quest.cancel();
        cooldowns.put(id, clock.getAsLong() + seconds("cooldown-seconds", 1800, 10, 86400) * 1000L);
    }

    void interact(Player player, Entity entity) {
        if (!isEnforcer(entity)) return;
        Encounter encounter = encounters.get(player.getUniqueId());
        if (encounter == null || !encounter.npc.valid() || !encounter.npc.entity().getUniqueId().equals(entity.getUniqueId())) {
            player.sendMessage(ChatColor.GRAY + "The enforcer is here for someone else.");
            return;
        }
        if (!encounter.quest.active()) {
            finish(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + "That cartel quest has already ended.");
            return;
        }
        if (!eligible(player) || player.getWorld() != encounter.npc.entity().getWorld()
                || player.getLocation().distanceSquared(encounter.npc.entity().getLocation()) > 36
                || !encounter.npc.entity().hasLineOfSight(player)) return;
        long now = clock.getAsLong();
        if (encounter.deadline == 0) {
            beginDemand(player, encounter, now);
            EnforcerGuide.open(plugin, player, encounter.drug);
            return; // First interaction explains the deal before any items are sold.
        }
        if (now >= encounter.deadline) {
            threaten(player, encounter);
            finish(player.getUniqueId());
            return;
        }
        EnforcerGuide.open(plugin, player, encounter.drug);
    }

    /** Reject only the caller's current encounter, using the same consequences as expiry. */
    void reject(Player player) {
        Encounter encounter = encounters.get(player.getUniqueId());
        if (encounter == null) {
            player.sendMessage(ChatColor.GRAY + "You have no active cartel demand to reject.");
            return;
        }
        if (!encounter.npc.valid() || !encounter.quest.active()) {
            finish(player.getUniqueId());
            player.sendMessage(ChatColor.GRAY + "That cartel encounter has already ended.");
            return;
        }
        threaten(player, encounter);
        finish(player.getUniqueId());
        player.sendMessage(ChatColor.GRAY + "You rejected the cartel's demand. The enforcer has left.");
    }

    boolean acceptDrop(Player player, Item item) {
        Encounter encounter = encounters.get(player.getUniqueId());
        if (encounter == null || !encounter.quest.active() || encounter.deadline == 0 || !eligible(player) || !item.isValid()
                || !encounter.npc.valid() || clock.getAsLong() >= encounter.deadline) return false;
        LivingEntity npc = encounter.npc.entity();
        if (item.getWorld() != npc.getWorld() || npc.getLocation().distanceSquared(item.getLocation()) > 4
                || !npc.hasLineOfSight(item)) return false;
        if (CartelDealer.price(item.getItemStack(), config()) == 0
                || !encounter.drug.equals(DrugItemMetadata.getDrugId(item.getItemStack().getItemMeta()))) return false;
        CartelDealer.Payments payments = dealer.payments();
        if (payments == null) {
            player.sendMessage(ChatColor.GRAY + "The cartel can't pay right now. The demand has been called off.");
            finish(player.getUniqueId());
            return true;
        }
        int sold = dealer.sellDropped(player, item, encounter.amount - encounter.delivered, payments);
        encounter.delivered += sold;
        if (sold > 0) encounter.quest.progress(encounter.delivered);
        if (encounter.delivered >= encounter.amount) {
            if (!encounter.quest.complete()) {
                player.sendMessage(ChatColor.GRAY + "Delivery paid, but the quest could not advance. Contact an administrator.");
                finish(player.getUniqueId());
                return true;
            }
            player.getPersistentDataContainer().remove(refusalsKey);
            say(player, encounter, "success", "&2Cartel: &aGood. We can do business. You're clear.", 0);
            com.drugs.achievements.AchievementManager.processTrigger(player, "cartel_enforcer_complete");
            finish(player.getUniqueId());
        } else if (sold > 0) {
            player.sendMessage(ChatColor.GREEN + "Delivered " + encounter.delivered + "/" + encounter.amount + " "
                    + encounter.drug + ". Throw " + (encounter.amount - encounter.delivered) + " more near me.");
        }
        return true;
    }

    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (isEnforcer(event.getEntity())) event.setCancelled(true);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) { finish(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { finish(event.getEntity().getUniqueId()); }
    @EventHandler public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isEnforcer(entity) && encounters.values().stream()
                    .noneMatch(encounter -> encounter.npc.valid() && encounter.npc.entity().getUniqueId().equals(entity.getUniqueId()))) {
                dealer.npcs().find(entity, "enforcer").destroy();
            }
        }
    }

    private static final class Encounter {
        final CartelNpcs.Character npc;
        final String drug;
        final int amount;
        final long spawnedAt;
        final EnforcerQuests.Session quest;
        int delivered;
        long deadline;
        long nextWarning;
        Encounter(CartelNpcs.Character npc, String drug, int amount, long spawnedAt, EnforcerQuests.Session quest) {
            this.npc = npc;
            this.quest = quest;
            this.drug = drug;
            this.amount = amount;
            this.spawnedAt = spawnedAt;
        }
    }
}
