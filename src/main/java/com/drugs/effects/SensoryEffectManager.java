package com.drugs.effects;

import com.drugs.DrugsV2;
import com.drugs.addiction.AddictionManager;
import com.drugs.config.MechanicsConfig;
import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;
import com.drugs.status.DrugStatusTracker;
import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages non-potion sensory, physical, audio, visual, withdrawal, and easter-egg effects.
 */
public class SensoryEffectManager implements Listener {

    private static final List<Material> SCHIZO_GLITCH_BLOCKS = List.of(
            Material.CRYING_OBSIDIAN,
            Material.SCULK,
            Material.SOUL_SAND,
            Material.NETHER_WART_BLOCK,
            Material.COAL_BLOCK
    );

    private static final List<String> SCHIZO_WHISPERS = List.of(
            "§8§oDid you hear that?",
            "§8§oSomeone is standing right behind you...",
            "§8§oDon't turn around.",
            "§8§oThey are watching you through the blocks.",
            "§7§oYou feel cold eyes on the back of your neck.",
            "§8§oWhy are they following you?",
            "§8§oRun.",
            "§8§oIt's in the walls.",
            "§7§o[Server] A unseen entity has entered your chunk.",
            "§8§oYou aren't alone down here."
    );

    private static Sound[] paranoiaSounds() {
        return new Sound[]{
                Sound.ENTITY_CREEPER_PRIMED,
                Sound.ENTITY_ENDERMAN_STARE,
                Sound.ENTITY_PHANTOM_SWOOP,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
                Sound.BLOCK_WOODEN_DOOR_OPEN,
                Sound.BLOCK_CHEST_OPEN,
                Sound.ENTITY_WARDEN_NEARBY_CLOSER,
                Sound.AMBIENT_CAVE
        };
    }

    private static final EntityType[] PHANTOM_MOB_TYPES = {
            EntityType.CREEPER,
            EntityType.ENDERMAN,
            EntityType.CAVE_SPIDER,
            EntityType.PHANTOM,
            EntityType.WITHER_SKELETON
    };

    private static final NamespacedKey HUD_DISABLED_KEY = new NamespacedKey("lumidrugs", "hud_disabled");
    private static final NamespacedKey HALLUCINATION_KEY = new NamespacedKey("lumidrugs", "phantom_hallucination");

    private final DrugsV2 plugin;
    private final Map<UUID, List<BukkitTask>> drugTasks = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> schizoTasks = new HashMap<>();
    private final Map<UUID, Integer> schizoEndTicks = new HashMap<>();
    private final Map<UUID, BukkitTask> edibleDelayTasks = new HashMap<>();
    private final Map<UUID, Integer> edibleActivationTicks = new HashMap<>();
    private final Map<UUID, Set<Location>> morphedBlocks = new HashMap<>();
    private final Map<UUID, Set<Entity>> activeHallucinations = new HashMap<>();
    private final Map<UUID, UUID> hallucinationOwners = new HashMap<>();
    private final Set<UUID> activeVignettes = new HashSet<>();
    private final Map<UUID, Integer> caffeineDoses = new HashMap<>();
    private final Map<UUID, Long> lastCaffeineMillis = new HashMap<>();
    private BukkitTask hudTask;

    public SensoryEffectManager(DrugsV2 plugin) {
        this.plugin = plugin;
    }

    public void startHudTask() {
        if (hudTask != null) hudTask.cancel();
        hudTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickHud, 20L, 20L);
    }

    /**
     * Triggers custom non-potion sensory effects for a drug use.
     */
    public void onDrugUse(Player player, String drugId, double multiplier) {
        if (player == null || !player.isOnline()) return;
        String id = drugId.toLowerCase(Locale.ROOT);

        // 1. Smoke inhale/exhale & coughing mechanics
        if (Set.of("blunt", "joint", "cigarette", "cigar", "tobacco", "cart", "vape").contains(id)) {
            triggerSmokeAndCough(player, id);
        }

        // 2. Drug-specific ongoing sensory effects
        int durationTicks = Math.max(100, (int) Math.round(400 * multiplier));
        switch (id) {
            case "meth" -> {
                startHeartRacing(player, durationTicks, 1.6f);
            }
            case "cocaine" -> startHeartRacing(player, 200, 1.8f);
            case "caffeine", "energy_drink" -> handleCaffeineUse(player, id, durationTicks);
            case "heroin", "fent" -> startOpioidNodding(player, id.equals("fent") ? 600 : 500);
            case "glue" -> startInhalantStumble(player, 360);
            case "molly" -> {
                startHeartRacing(player, 600, 1.4f);
                startMollyEuphoria(player, 800);
            }
            case "nicotine", "chewing_tobacco" -> {
                player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1.6f);
                if (id.equals("chewing_tobacco")) {
                    scheduleDrugTask(player.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.playSound(player.getLocation(), Sound.ENTITY_LLAMA_SPIT, 0.7f, 0.8f);
                            Location mouth = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.5));
                            player.getWorld().spawnParticle(Particle.DRIPPING_WATER, mouth, 8, 0.1, 0.1, 0.1, 0.02);
                        }
                    }, 40L));
                }
            }
            default -> {}
        }

        applyHallucinations(player, id, multiplier);

    }

    private void applyHallucinations(Player player, String drugId, double potency) {
        List<HallucinationSettings.Effect> definitions = HallucinationSettings.forDrug(drugId);
        if (definitions.isEmpty()) return;
        int tolerance = ToleranceTracker.getToleranceLevel(player, drugId);
        int maximum = ToleranceConfigLoader.getMaxTolerance(drugId);
        for (var effect : definitions) {
            if (!effect.matches(ThreadLocalRandom.current().nextDouble(), tolerance, maximum)) continue;
            Runnable start = () -> {
                if (!player.isOnline() || player.isDead()) return;
                int duration = effect.duration(potency);
                switch (effect.type()) {
                    case PSYCHEDELIC -> startPsychedelicVisuals(player, duration, effect);
                    case PARANOIA -> startParanoia(player, duration, effect);
                    case PHANTOM -> spawnPhantomMobHallucination(player, effect, duration);
                    case PORTAL -> {
                        if (plugin.getLsdEffectManager() != null)
                            plugin.getLsdEffectManager().startEffect(player, drugId, effect.delayTicks(), duration, effect.blocks().getFirst());
                    }
                    case BLOCK_DISGUISE -> {
                        if (plugin.getSalviaEffectManager() != null)
                            plugin.getSalviaEffectManager().startEffect(player, effect.delayTicks(), duration, effect.blocks().getFirst());
                    }
                }
            };
            if (effect.delayTicks() == 0 || effect.type() == HallucinationSettings.Type.PORTAL
                    || effect.type() == HallucinationSettings.Type.BLOCK_DISGUISE) start.run();
            else scheduleDrugTask(player.getUniqueId(), plugin.getServer().getScheduler()
                    .runTaskLater(plugin, start, effect.delayTicks()));
        }
    }

    /**
     * Schedules delayed edible kick-in so edibles hit after a realistic delay.
     */
    public void scheduleEdibleOnset(Player player, Runnable applyEffectsCallback) {
        UUID uuid = player.getUniqueId();
        BukkitTask existing = edibleDelayTasks.remove(uuid);
        if (existing != null) existing.cancel();

        int delaySeconds = Math.max(1, com.drugs.drug.DrugRegistry.setting("edible.delay", 10));
        long delayTicks = delaySeconds * 20L;
        edibleActivationTicks.put(uuid, Bukkit.getCurrentTick() + (int) delayTicks);

        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.9f, 1.0f);
        player.sendMessage("§7§oYou eat the edible... it takes a moment to kick in.");

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            edibleDelayTasks.remove(uuid);
            edibleActivationTicks.remove(uuid);
            Player online = plugin.getServer().getPlayer(uuid);
            if (online == null || !online.isOnline() || online.isDead()) return;

            online.sendMessage("§a§oThe edible hits you all at once!");
            online.playSound(online.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 0.75f);
            applyEffectsCallback.run();
            startMollyEuphoria(online, 300);
        }, delayTicks);
        edibleDelayTasks.put(uuid, task);
    }

    public boolean hasPendingEdible(Player player) {
        return edibleDelayTasks.containsKey(player.getUniqueId());
    }

    public int getEdibleDelayTicks(Player player) {
        if (!hasPendingEdible(player)) return 0;
        return Math.max(0, edibleActivationTicks.getOrDefault(player.getUniqueId(), Bukkit.getCurrentTick()) - Bukkit.getCurrentTick());
    }

    private void triggerSmokeAndCough(Player player, String id) {
        boolean vapor = id.equals("cart") || id.equals("vape");
        if (vapor) {
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.8f);
        } else {
            player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 0.7f, 1.2f);
            player.playSound(player.getLocation(), Sound.BLOCK_CAMPFIRE_CRACKLE, 0.6f, 1.4f);
        }

        // Directional exhale plume 1.25s after inhale
        scheduleDrugTask(player.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            Location eye = player.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            Particle particle = vapor ? Particle.CLOUD : Particle.CAMPFIRE_COSY_SMOKE;
            for (int i = 1; i <= 5; i++) {
                Location point = eye.clone().add(dir.clone().multiply(0.35 * i)).subtract(0, 0.1, 0);
                player.getWorld().spawnParticle(particle, point, vapor ? 4 : 3, 0.08, 0.06, 0.08, 0.01);
            }
            player.playSound(eye, Sound.ENTITY_PLAYER_BREATH, 0.45f, vapor ? 1.3f : 0.9f);

            // 25% chance of a coughing fit on heavy smoke
            if (!vapor && ThreadLocalRandom.current().nextDouble() < 0.25) {
                triggerCoughingFit(player);
            }
        }, 25L));
    }

    private void triggerCoughingFit(Player player) {
        UUID uuid = player.getUniqueId();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§7§o*cough* *cough*"));
        for (int i = 0; i < 2; i++) {
            scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                player.playSound(player.getLocation(), Sound.ENTITY_PANDA_SNEEZE, 0.8f, 0.6f);
                Location mouth = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.4));
                player.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mouth, 6, 0.12, 0.12, 0.12, 0.02);
                nudgePlayer(player, 0.08);
            }, 10L + i * 12L));
        }
    }

    private void startPsychedelicVisuals(Player player, int durationTicks, HallucinationSettings.Effect effect) {
        UUID uuid = player.getUniqueId();
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || elapsed[0] >= durationTicks) {
                resetVisuals(player);
                return;
            }
            elapsed[0] += effect.intervalTicks();

            // Smooth client-side sky time shift
            long shiftedTime = (player.getWorld().getTime() + (elapsed[0] * 180L)) % 24000L;
            player.setPlayerTime(shiftedTime, false);

            // Client-side psychedelic block morphing around the player
            Location base = player.getLocation();
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < 4; i++) {
                Block block = base.clone().add(rng.nextInt(-5, 6), rng.nextInt(-2, 3), rng.nextInt(-5, 6)).getBlock();
                if (block.getType().isSolid() && block.getType() != Material.BEDROCK && block.getType() != Material.BARRIER) {
                    Location loc = block.getLocation();
                    BlockData fake = effect.blocks().get(rng.nextInt(effect.blocks().size())).createBlockData();
                    player.sendBlockChange(loc, fake);
                    morphedBlocks.computeIfAbsent(uuid, k -> new HashSet<>()).add(loc);
                    scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendBlockChange(loc, loc.getBlock().getBlockData());
                        }
                    }, 60L));
                }
            }
            player.spawnParticle(Particle.END_ROD, player.getEyeLocation(), 6, 2.5, 1.5, 2.5, 0.02);
        }, 20L, effect.intervalTicks());
        scheduleDrugTask(uuid, task);
        scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            task.cancel();
            resetVisuals(player);
        }, durationTicks + 5L));
    }

    private void startHeartRacing(Player player, int durationTicks, float pitch) {
        UUID uuid = player.getUniqueId();
        applyRedVignette(player, true);
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || elapsed[0] >= durationTicks) {
                applyRedVignette(player, false);
                return;
            }
            elapsed[0] += 15;
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.85f, pitch);
        }, 5L, 15L);
        scheduleDrugTask(uuid, task);
        scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            task.cancel();
            applyRedVignette(player, false);
        }, durationTicks + 5L));
    }

    private void handleCaffeineUse(Player player, String id, int durationTicks) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastCaffeineMillis.getOrDefault(uuid, 0L);
        int doses = (now - last > 120_000L) ? 0 : caffeineDoses.getOrDefault(uuid, 0);
        doses++;
        caffeineDoses.put(uuid, doses);
        lastCaffeineMillis.put(uuid, now);

        if (id.equals("energy_drink")) {
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.15f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.5f, 1.3f);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.4f);
        }

        if (doses >= 4) {
            triggerHeartAttack(player, id);
            return;
        }

        if (doses == 3) {
            player.sendMessage("§c§l⚠ HEART PALPITATIONS! §7Your chest feels dangerously tight—one more dose will cause a heart attack!");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§4§l❤ 210 BPM §8- §cYour heart is pounding out of your chest!"));
            DrugStatusTracker.applyEffect(player, id, new PotionEffect(PotionEffectType.NAUSEA, 160, 0, true, false, true));
            startHeartRacing(player, durationTicks, 2.0f);
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§e⚡ Caffeine Rush §7(" + (110 + doses * 25) + " BPM)"));
            startHeartRacing(player, Math.min(240, durationTicks), 1.5f + (doses * 0.15f));
        }
    }

    /**
     * Triggers a multi-stage fatal heart attack from excessive caffeine or stimulant overdose.
     */
    public void triggerHeartAttack(Player player, String drugId) {
        if (player == null || !player.isOnline() || player.isDead()) return;
        UUID uuid = player.getUniqueId();
        caffeineDoses.remove(uuid);
        lastCaffeineMillis.remove(uuid);

        applyRedVignette(player, true);
        player.sendMessage("§4§l💔 HEART ATTACK! §8» §cYour heart is seizing up from taking too much "
                + (drugId.equalsIgnoreCase("energy_drink") ? "Energy Drink" : drugId) + "!");
        player.sendTitle("§4§l💔 HEART ATTACK", "§cYour chest seizes in agony!", 5, 50, 15);

        DrugStatusTracker.applyEffect(player, drugId, new PotionEffect(PotionEffectType.SLOWNESS, 100, 4, true, false, true));
        DrugStatusTracker.applyEffect(player, drugId, new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, true, false, true));
        DrugStatusTracker.applyEffect(player, drugId, new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false, true));
        DrugStatusTracker.applyEffect(player, drugId, new PotionEffect(PotionEffectType.NAUSEA, 100, 1, true, false, true));

        // Force player to clutch chest / collapse downward
        player.setSneaking(true);
        Location collapsed = player.getLocation().clone();
        collapsed.setPitch(85f);
        player.teleport(collapsed);

        // Rapid fibrillating heartbeats + chest pain damage ticks followed by flatline
        for (int i = 1; i <= 4; i++) {
            final int beat = i;
            scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 2.0f);
                player.spawnParticle(Particle.DAMAGE_INDICATOR, player.getEyeLocation(), 5, 0.3, 0.3, 0.3, 0.1);
                if (beat < 4) {
                    player.damage(4.0);
                } else {
                    // Flatline tone & cardiac arrest
                    player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.0f, 2.0f);
                    Bukkit.broadcastMessage("§4" + player.getName() + " suffered a fatal heart attack from too much "
                            + (drugId.equalsIgnoreCase("energy_drink") ? "Energy Drink" : "Caffeine") + "!");
                    player.setHealth(0.0);
                }
            }, i * 10L));
        }
    }

    private void startParanoia(Player player, int durationTicks, HallucinationSettings.Effect effect) {
        UUID uuid = player.getUniqueId();
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || elapsed[0] >= durationTicks) return;
            elapsed[0] += effect.intervalTicks();
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            if (rng.nextDouble() < effect.shadowChance()) {
                spawnShadowFigureBehind(player);
            }
            if (rng.nextDouble() < effect.phantomChance()) {
                spawnPhantomMobHallucination(player, effect, effect.mobLifetimeTicks());
            }
        }, 40L, effect.intervalTicks());
        scheduleDrugTask(uuid, task);
        scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, task::cancel, durationTicks + 5L));
    }

    private void startOpioidNodding(Player player, int durationTicks) {
        UUID uuid = player.getUniqueId();
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || elapsed[0] >= durationTicks) return;
            elapsed[0] += 50;
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.7f, 0.55f);
            player.setSneaking(true);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText("§8§oYour eyelids feel impossibly heavy..."));
            Location drooped = player.getLocation().clone();
            drooped.setPitch(Math.min(90f, drooped.getPitch() + 28f));
            player.teleport(drooped);
            scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) player.setSneaking(false);
            }, 25L));
        }, 40L, 50L);
        scheduleDrugTask(uuid, task);
        scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, task::cancel, durationTicks + 5L));
    }

    private void startInhalantStumble(Player player, int durationTicks) {
        UUID uuid = player.getUniqueId();
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 1.8f);
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || elapsed[0] >= durationTicks) return;
            elapsed[0] += 20;
            nudgePlayer(player, 0.22);
            player.spawnParticle(Particle.BUBBLE_POP, player.getEyeLocation(), 8, 0.4, 0.4, 0.4, 0.02);
        }, 10L, 20L);
        scheduleDrugTask(uuid, task);
        scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, task::cancel, durationTicks + 5L));
    }

    private void startMollyEuphoria(Player player, int durationTicks) {
        UUID uuid = player.getUniqueId();
        final int[] elapsed = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead() || elapsed[0] >= durationTicks) return;
            elapsed[0] += 30;
            Location loc = player.getLocation().add(0, 1.2, 0);
            player.spawnParticle(Particle.HEART, loc, 3, 0.6, 0.5, 0.6, 0.01);
            player.spawnParticle(Particle.NOTE, loc, 4, 0.8, 0.6, 0.8, 0.5);
            player.playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.5f, 1.5f);
        }, 15L, 30L);
        scheduleDrugTask(uuid, task);
        scheduleDrugTask(uuid, plugin.getServer().getScheduler().runTaskLater(plugin, task::cancel, durationTicks + 5L));
    }

    /**
     * Visceral withdrawal symptoms (tremors, red vignette, and occasional vomiting on severe withdrawal).
     */
    public void onWithdrawalTick(Player player, String drugId) {
        if (player == null || !player.isOnline() || player.isDead()) return;
        applyRedVignette(player, true);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // Shivers / tremors
        if (rng.nextDouble() < 0.20) {
            nudgePlayer(player, 0.12);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 0.45f, 1.2f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText("§c§oYou are shaking from " + drugId + " withdrawal..."));
        }

        // Vomiting on severe withdrawal (opioids, glue, meth, cocaine)
        if (Set.of("heroin", "fent", "glue", "cocaine", "meth").contains(drugId.toLowerCase(Locale.ROOT))
                && rng.nextDouble() < 0.08) {
            triggerVomit(player);
        }
    }

    public void clearWithdrawalSensory(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!hasSchizophrenia(player)) {
            applyRedVignette(player, false);
        }
    }

    public void triggerVomit(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return;
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.9f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.4f, 1.8f);
        Location mouth = player.getEyeLocation().add(player.getEyeLocation().getDirection().multiply(0.45)).subtract(0, 0.15, 0);
        player.getWorld().spawnParticle(Particle.ITEM_SLIME, mouth, 18, 0.15, 0.12, 0.15, 0.04);
        player.getWorld().spawnParticle(Particle.SNEEZE, mouth, 6, 0.12, 0.1, 0.12, 0.02);
    }

    /**
     * Easter Egg: Toggles or applies a full Schizophrenia / Psychosis episode on the target player.
     *
     * @return true if enabled, false if toggled off
     */
    public boolean toggleSchizophrenia(Player target, int durationSeconds) {
        UUID uuid = target.getUniqueId();
        if (hasSchizophrenia(target) && durationSeconds <= 0) {
            stopSchizophrenia(uuid);
            return false;
        }
        stopSchizophrenia(uuid);
        int seconds = durationSeconds > 0 ? durationSeconds : 120;
        int durationTicks = seconds * 20;
        schizoEndTicks.put(uuid, Bukkit.getCurrentTick() + durationTicks);

        applyRedVignette(target, true);
        target.playSound(target.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.5f);
        DrugStatusTracker.applyEffect(target, "schizophrenia",
                new PotionEffect(PotionEffectType.DARKNESS, durationTicks, 0, true, false, false));

        List<BukkitTask> tasks = new ArrayList<>();
        BukkitTask loop = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = plugin.getServer().getPlayer(uuid);
            if (online == null || !online.isOnline() || online.isDead()
                    || Bukkit.getCurrentTick() >= schizoEndTicks.getOrDefault(uuid, 0)) {
                stopSchizophrenia(uuid);
                return;
            }

            ThreadLocalRandom rng = ThreadLocalRandom.current();

            // 1. Eerie whispers in chat or action bar
            if (rng.nextDouble() < 0.55) {
                String whisper = SCHIZO_WHISPERS.get(rng.nextInt(SCHIZO_WHISPERS.size()));
                if (rng.nextBoolean()) {
                    online.sendMessage(whisper);
                } else {
                    online.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(whisper));
                }
            }

            // 2. Shadow figure & paranoid footsteps/sounds right behind the player
            spawnShadowFigureBehind(online);
            if (rng.nextDouble() < 0.60) {
                spawnPhantomMobHallucination(online);
            }

            // 3. Sudden head-turn twitch or camera jolt
            if (rng.nextDouble() < 0.35) {
                nudgePlayer(online, 0.15);
            }

            // 4. Client-side cursed block flicker
            Location base = online.getLocation();
            for (int i = 0; i < 3; i++) {
                Block block = base.clone().add(rng.nextInt(-4, 5), rng.nextInt(-1, 3), rng.nextInt(-4, 5)).getBlock();
                if (block.getType().isSolid() && block.getType() != Material.BEDROCK) {
                    Location loc = block.getLocation();
                    online.sendBlockChange(loc, SCHIZO_GLITCH_BLOCKS.get(rng.nextInt(SCHIZO_GLITCH_BLOCKS.size())).createBlockData());
                    morphedBlocks.computeIfAbsent(uuid, k -> new HashSet<>()).add(loc);
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (online.isOnline()) online.sendBlockChange(loc, loc.getBlock().getBlockData());
                    }, 30L);
                }
            }
        }, 20L, 50L);

        BukkitTask endTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> stopSchizophrenia(uuid), durationTicks);
        tasks.add(loop);
        tasks.add(endTask);
        schizoTasks.put(uuid, tasks);
        return true;
    }

    public boolean hasSchizophrenia(Player player) {
        return schizoTasks.containsKey(player.getUniqueId());
    }

    public int getSchizophreniaRemainingTicks(Player player) {
        if (!hasSchizophrenia(player)) return 0;
        return Math.max(0, schizoEndTicks.getOrDefault(player.getUniqueId(), Bukkit.getCurrentTick()) - Bukkit.getCurrentTick());
    }

    public void stopSchizophrenia(UUID uuid) {
        schizoEndTicks.remove(uuid);
        List<BukkitTask> tasks = schizoTasks.remove(uuid);
        if (tasks != null) {
            for (BukkitTask task : tasks) task.cancel();
        }
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
            resetVisuals(player);
            applyRedVignette(player, false);
        }
    }

    private void spawnShadowFigureBehind(Player player) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location eye = player.getEyeLocation();
        Vector behind = eye.getDirection().normalize().multiply(-rng.nextDouble(2.2, 4.0));
        behind.rotateAroundY(rng.nextDouble(-0.6, 0.6));
        Location shadowLoc = player.getLocation().add(behind);

        // Play directional paranoia sound from the shadow figure's position
        Sound[] sounds = paranoiaSounds();
        Sound sound = sounds[rng.nextInt(sounds.length)];
        player.playSound(shadowLoc, sound, 0.85f, rng.nextFloat(0.6f, 1.3f));

        // Play rapid footsteps approaching from behind
        for (int step = 0; step < 3; step++) {
            Location stepLoc = shadowLoc.clone().add(eye.getDirection().normalize().multiply(step * 0.5));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.playSound(stepLoc, Sound.BLOCK_STONE_STEP, 0.9f, 0.8f);
                }
            }, step * 5L);
        }

        // Render a client-only human-shaped dark shadow silhouette
        for (double y = 0.2; y <= 1.8; y += 0.4) {
            player.spawnParticle(Particle.LARGE_SMOKE, shadowLoc.clone().add(0, y, 0), 4, 0.15, 0.15, 0.15, 0.0);
            player.spawnParticle(Particle.SQUID_INK, shadowLoc.clone().add(0, y, 0), 3, 0.12, 0.12, 0.12, 0.0);
        }
        player.spawnParticle(Particle.SOUL, shadowLoc.clone().add(0, 1.6, 0), 2, 0.1, 0.1, 0.1, 0.01);
    }

    /**
     * Spawns a client-visible-only phantom mob hallucination that stalks toward the player
     * and vanishes into smoke and souls when attacked, clicked, or approached.
     */
    public Entity spawnPhantomMobHallucination(Player player) {
        return spawnPhantomMobHallucination(player, null, 80);
    }

    private Entity spawnPhantomMobHallucination(Player player, HallucinationSettings.Effect effect, int lifetimeTicks) {
        if (player == null || !player.isOnline() || player.isDead() || player.getWorld() == null) return null;
        UUID uuid = player.getUniqueId();
        Set<Entity> currentSet = activeHallucinations.computeIfAbsent(uuid, k -> new HashSet<>());
        currentSet.removeIf(e -> e == null || !e.isValid());
        if (currentSet.size() >= 2) return null;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Location eye = player.getEyeLocation();
        Vector offset = eye.getDirection().setY(0);
        if (offset.lengthSquared() < 0.01) offset = new Vector(1, 0, 0);
        offset.normalize().multiply(rng.nextDouble(3.2, 5.5));
        offset.rotateAroundY(rng.nextDouble(-0.9, 0.9));
        Location spawnLoc = player.getLocation().add(offset);
        Vector toPlayer = player.getLocation().toVector().subtract(spawnLoc.toVector()).setY(0);
        if (toPlayer.lengthSquared() > 0.01) spawnLoc.setDirection(toPlayer);

        EntityType type = effect == null ? PHANTOM_MOB_TYPES[rng.nextInt(PHANTOM_MOB_TYPES.length)]
                : effect.mobs().get(rng.nextInt(effect.mobs().size()));
        Entity spawned;
        try {
            spawned = player.getWorld().spawnEntity(spawnLoc, type);
        } catch (Throwable ignored) {
            return null;
        }
        if (spawned == null) return null;

        try {
            spawned.setVisibleByDefault(false);
            player.showEntity(plugin, spawned);
        } catch (Throwable ignored) {
        }
        spawned.setSilent(true);
        spawned.setPersistent(false);
        if (spawned instanceof Mob mob) {
            mob.setAI(false);
            mob.setAware(false);
            mob.setCanPickupItems(false);
            mob.setCollidable(false);
            mob.setRemoveWhenFarAway(true);
        }
        if (spawned.getPersistentDataContainer() != null && uuid != null) {
            spawned.getPersistentDataContainer().set(HALLUCINATION_KEY, PersistentDataType.STRING, uuid.toString());
        }
        currentSet.add(spawned);
        if (spawned.getUniqueId() != null && uuid != null) {
            hallucinationOwners.put(spawned.getUniqueId(), uuid);
        }

        try {
            Sound cue = switch (type) {
                case CREEPER -> Sound.ENTITY_CREEPER_PRIMED;
                case ENDERMAN -> Sound.ENTITY_ENDERMAN_STARE;
                case PHANTOM -> Sound.ENTITY_PHANTOM_SWOOP;
                default -> Sound.ENTITY_WARDEN_NEARBY_CLOSER;
            };
            player.playSound(spawnLoc, cue, 0.85f, rng.nextFloat(0.65f, 1.25f));
            player.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.clone().add(0, 1.0, 0), 8, 0.25, 0.4, 0.25, 0.01);
        } catch (Throwable ignored) {
        }

        final int[] ticks = {0};
        final BukkitTask[] stalkTask = new BukkitTask[1];
        stalkTask[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ticks[0] += 4;
            if (!player.isOnline() || player.isDead() || !spawned.isValid() || ticks[0] >= lifetimeTicks) {
                if (stalkTask[0] != null) stalkTask[0].cancel();
                vanishHallucination(player, spawned, false);
                return;
            }
            if (!player.getWorld().equals(spawned.getWorld())) {
                if (stalkTask[0] != null) stalkTask[0].cancel();
                vanishHallucination(player, spawned, false);
                return;
            }
            Location mobLoc = spawned.getLocation();
            Location targetLoc = player.getLocation();
            if (mobLoc.distanceSquared(targetLoc) <= 2.56) {
                if (stalkTask[0] != null) stalkTask[0].cancel();
                vanishHallucination(player, spawned, true);
                return;
            }
            Vector step = targetLoc.toVector().subtract(mobLoc.toVector());
            if (step.lengthSquared() > 0.01) {
                Vector dir = step.clone().normalize();
                Location next = mobLoc.clone().add(dir.clone().multiply(effect == null ? 0.32 : effect.speed()));
                next.setDirection(dir.setY(0));
                spawned.teleport(next);
            }
            player.spawnParticle(Particle.SMOKE, spawned.getLocation().add(0, 1.0, 0), 2, 0.15, 0.3, 0.15, 0.0);
        }, 4L, 4L);
        if (stalkTask[0] != null && uuid != null) {
            scheduleDrugTask(uuid, stalkTask[0]);
        }
        return spawned;
    }

    public boolean isHallucination(Entity entity) {
        if (entity == null) return false;
        if (entity.getUniqueId() != null && hallucinationOwners.containsKey(entity.getUniqueId())) return true;
        return entity.getPersistentDataContainer() != null
                && entity.getPersistentDataContainer().has(HALLUCINATION_KEY, PersistentDataType.STRING);
    }

    public void vanishHallucination(Player player, Entity entity, boolean whisper) {
        if (entity == null) return;
        UUID entityId = entity.getUniqueId();
        UUID ownerId = entityId != null ? hallucinationOwners.remove(entityId) : null;
        if (ownerId == null && player != null) ownerId = player.getUniqueId();
        if (ownerId != null) {
            Set<Entity> set = activeHallucinations.get(ownerId);
            if (set != null) set.remove(entity);
        }
        Location loc = entity.getLocation();
        if (entity.isValid()) {
            entity.remove();
        }
        if (player != null && player.isOnline() && loc != null) {
            try {
                player.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 1.0, 0), 16, 0.3, 0.5, 0.3, 0.03);
                player.spawnParticle(Particle.SOUL, loc.clone().add(0, 1.0, 0), 6, 0.25, 0.4, 0.25, 0.02);
                player.playSound(loc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.85f, 0.7f);
            } catch (Throwable ignored) {
            }
            if (whisper) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText("§8§oThe hallucination dissolves into smoke... it wasn't real."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHallucinationDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (isHallucination(entity)) {
            event.setCancelled(true);
            Player attacker = event.getDamager() instanceof Player p ? p : null;
            vanishHallucination(attacker, entity, true);
            return;
        }
        if (isHallucination(event.getDamager())) {
            event.setCancelled(true);
            vanishHallucination(null, event.getDamager(), false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHallucinationInteract(PlayerInteractEntityEvent event) {
        if (isHallucination(event.getRightClicked())) {
            event.setCancelled(true);
            vanishHallucination(event.getPlayer(), event.getRightClicked(), true);
        }
    }

    /**
     * Toggles the player's personal Actionbar HUD pulse on or off.
     *
     * @return true if HUD is now enabled for the player, false if disabled
     */
    public static boolean toggleHud(Player player) {
        if (player == null || player.getPersistentDataContainer() == null) return false;
        var pdc = player.getPersistentDataContainer();
        if (pdc.has(HUD_DISABLED_KEY, PersistentDataType.BYTE)) {
            pdc.remove(HUD_DISABLED_KEY);
            return true;
        }
        pdc.set(HUD_DISABLED_KEY, PersistentDataType.BYTE, (byte) 1);
        return false;
    }

    public static boolean isHudEnabledFor(Player player) {
        if (player == null || !MechanicsConfig.isHudEnabled()) return false;
        var pdc = player.getPersistentDataContainer();
        return pdc == null || !pdc.has(HUD_DISABLED_KEY, PersistentDataType.BYTE);
    }

    void tickHud() {
        if (!MechanicsConfig.isHudEnabled()) return;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || !isHudEnabledFor(player)) continue;
            String hud = buildHudActionbar(player);
            if (hud != null && !hud.isEmpty()) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(hud));
            }
        }
    }

    public String buildHudActionbar(Player player) {
        if (player == null) return null;
        Map<String, DrugStatusTracker.Status> status = DrugStatusTracker.getStatus(player);
        List<String> activeHighs = new ArrayList<>();
        List<String> withdrawals = new ArrayList<>();
        Set<String> activeDrugIds = new LinkedHashSet<>();

        for (var entry : status.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(DrugStatusTracker.UNKNOWN_PREFIX)) continue;
            var details = entry.getValue();
            if (key.startsWith(DrugStatusTracker.WITHDRAWAL_PREFIX)) {
                String drugId = key.substring(DrugStatusTracker.WITHDRAWAL_PREFIX.length());
                DrugEffectProfile profile = DrugRegistry.getProfileById(drugId);
                String name = profile != null ? ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', profile.getDisplayName())) : drugId;
                String timer = details.remainingTicks() < 0 ? "active" : formatCompactTicks(details.remainingTicks());
                withdrawals.add("§c☠ Withdrawal: " + name + " §7(" + timer + ")");
            } else {
                activeDrugIds.add(key.toLowerCase(Locale.ROOT));
                DrugEffectProfile profile = DrugRegistry.getProfileById(key);
                String name = profile != null ? ChatColor.translateAlternateColorCodes('&', profile.getDisplayName()) : "§e" + key;
                String timer = details.delayTicks() > 0
                        ? "onset " + formatCompactTicks(details.delayTicks())
                        : formatCompactTicks(details.remainingTicks());
                activeHighs.add("§d✦ " + name + " §7(" + timer + ")");
            }
        }

        if (activeHighs.isEmpty() && withdrawals.isEmpty()) {
            return null;
        }

        int bpm = computeHeartRateBpm(player, activeDrugIds, !withdrawals.isEmpty());
        String bpmColor = bpm >= 165 ? "§4§l" : bpm >= 125 ? "§c" : bpm <= 58 ? "§9" : "§a";
        String odBadge = "";
        for (String drugId : activeDrugIds) {
            if (!DrugRegistry.isDrugRegistered(drugId)) continue;
            int level = ToleranceTracker.getToleranceLevel(player, drugId);
            int max = ToleranceConfigLoader.getMaxTolerance(drugId);
            if (level >= max) {
                odBadge = " §4§l[⚠ OD LIMIT]";
                break;
            } else if (level >= Math.max(1, max - 1) && odBadge.isEmpty()) {
                odBadge = " §6[Near OD]";
            }
        }

        List<String> segments = new ArrayList<>();
        segments.add(bpmColor + "❤ " + bpm + " BPM" + odBadge);
        if (!activeHighs.isEmpty()) {
            segments.add(String.join(" §8| ", activeHighs.subList(0, Math.min(2, activeHighs.size()))));
        }
        if (!withdrawals.isEmpty()) {
            segments.add(withdrawals.getFirst());
        }
        return String.join(" §8• ", segments);
    }

    private int computeHeartRateBpm(Player player, Set<String> activeDrugs, boolean inWithdrawal) {
        int bpm = 72;
        for (String drug : activeDrugs) {
            switch (drug) {
                case "cocaine", "meth" -> bpm += 55;
                case "molly", "caffeine", "energy_drink" -> bpm += 38;
                case "nicotine", "vape", "cigarette", "cigar", "chewing_tobacco", "tobacco" -> bpm += 16;
                case "lsd", "shrooms", "dmt", "salvia", "schizophrenia" -> bpm += 24;
                case "heroin", "fent" -> bpm -= 22;
                case "joint", "blunt", "edible", "cart" -> bpm += 10;
                default -> bpm += 8;
            }
        }
        if (inWithdrawal) bpm += 22;
        UUID uuid = player.getUniqueId();
        if (uuid != null && caffeineDoses.containsKey(uuid)) {
            bpm += caffeineDoses.get(uuid) * 15;
        }
        return Math.clamp(bpm, 42, 220);
    }

    private static String formatCompactTicks(int ticks) {
        int seconds = Math.max(0, (ticks + 19) / 20);
        int mins = seconds / 60;
        int secs = seconds % 60;
        return mins > 0 ? mins + "m " + secs + "s" : secs + "s";
    }

    private void nudgePlayer(Player player, double strength) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Vector current = player.getVelocity();
        Vector nudge = new Vector(rng.nextDouble(-strength, strength), 0, rng.nextDouble(-strength, strength));
        player.setVelocity(current.add(nudge));
    }

    private void applyRedVignette(Player player, boolean enable) {
        try {
            if (enable) {
                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(player.getLocation());
                border.setSize(20_000_000);
                border.setWarningDistance(20_000_000);
                player.setWorldBorder(border);
                activeVignettes.add(player.getUniqueId());
            } else if (activeVignettes.remove(player.getUniqueId())) {
                player.setWorldBorder(null);
            }
        } catch (Throwable ignored) {
            // Safe fallback on mocked test environments
        }
    }

    private void resetVisuals(Player player) {
        if (player == null || !player.isOnline()) return;
        player.resetPlayerTime();
        Set<Location> locations = morphedBlocks.remove(player.getUniqueId());
        if (locations != null) {
            for (Location loc : locations) {
                if (loc.getWorld() != null && loc.getWorld().equals(player.getWorld())
                        && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                }
            }
        }
    }

    private void scheduleDrugTask(UUID uuid, BukkitTask task) {
        drugTasks.computeIfAbsent(uuid, k -> new ArrayList<>()).add(task);
    }

    public void cleanupPlayer(UUID uuid) {
        List<BukkitTask> tasks = drugTasks.remove(uuid);
        if (tasks != null) {
            for (BukkitTask task : tasks) task.cancel();
        }
        BukkitTask edibleTask = edibleDelayTasks.remove(uuid);
        if (edibleTask != null) edibleTask.cancel();
        edibleActivationTicks.remove(uuid);
        caffeineDoses.remove(uuid);
        lastCaffeineMillis.remove(uuid);
        Set<Entity> hallucinations = activeHallucinations.remove(uuid);
        if (hallucinations != null) {
            for (Entity entity : hallucinations) {
                if (entity != null) {
                    if (entity.getUniqueId() != null) hallucinationOwners.remove(entity.getUniqueId());
                    if (entity.isValid()) entity.remove();
                }
            }
        }
        stopSchizophrenia(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null && player.isOnline()) {
            resetVisuals(player);
            applyRedVignette(player, false);
        } else {
            morphedBlocks.remove(uuid);
            activeVignettes.remove(uuid);
        }
    }

    public void cleanupAll() {
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        Set<UUID> all = new HashSet<>();
        all.addAll(drugTasks.keySet());
        all.addAll(schizoTasks.keySet());
        all.addAll(edibleDelayTasks.keySet());
        all.addAll(morphedBlocks.keySet());
        all.addAll(activeHallucinations.keySet());
        all.addAll(activeVignettes);
        for (UUID uuid : all) {
            cleanupPlayer(uuid);
        }
        hallucinationOwners.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity().getUniqueId());
    }
}

