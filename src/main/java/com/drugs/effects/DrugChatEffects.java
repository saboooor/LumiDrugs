package com.drugs.effects;

import com.drugs.config.MechanicsConfig;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Main-thread effect updates publish immutable state for asynchronous Paper chat. */
public final class DrugChatEffects implements Listener {
    enum Style { STUTTER, DRAWL, PAUSES }
    private record Active(Style style, long expiresAt) {}
    private static final Map<UUID, Map<String, Active>> ACTIVE = new ConcurrentHashMap<>();
    private static final Pattern WORD = Pattern.compile("[\\p{L}]{2,}");

    static Style style(String drug) {
        return switch (drug.toLowerCase(Locale.ROOT)) {
            case "meth", "cocaine", "molly", "caffeine", "energy_drink" -> Style.STUTTER;
            case "weed", "joint", "blunt", "cart", "edible", "heroin", "fent", "glue" -> Style.DRAWL;
            case "lsd", "shrooms", "salvia" -> Style.PAUSES;
            default -> null;
        };
    }

    public static void track(Player player, String drug, PotionEffect effect) {
        Style style = style(drug);
        String key = effect.getType().getKey().toString();
        if (style == null) {
            remove(player.getUniqueId(), key);
            return;
        }
        long expiry = effect.getDuration() < 0 ? Long.MAX_VALUE
                : System.nanoTime() + effect.getDuration() * 50_000_000L;
        ACTIVE.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(key, new Active(style, expiry));
    }

    public static void trackSalvia(Player player, long ticks) {
        ACTIVE.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put("salvia", new Active(Style.PAUSES, System.nanoTime() + ticks * 50_000_000L));
    }

    public static void clearSalvia(UUID uuid) { remove(uuid, "salvia"); }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            var key = new org.bukkit.NamespacedKey("lumidrugs", "drug_status/"
                    + effect.getType().getKey().getNamespace() + "/" + effect.getType().getKey().getKey());
            String drug = player.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);
            if (drug != null) track(player, drug, effect);
        }
    }

    static String transformWord(String word, Style style) {
        int first = word.offsetByCodePoints(0, 1);
        return switch (style) {
            case STUTTER -> word.substring(0, first) + "-" + word;
            case DRAWL -> word + word.substring(word.offsetByCodePoints(word.length(), -1)).repeat(2);
            case PAUSES -> word + "...";
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!MechanicsConfig.isChatEffectsEnabled()) return;
        var effects = ACTIVE.get(event.getPlayer().getUniqueId());
        if (effects == null) return;
        long now = System.nanoTime();
        // Stable precedence when multiple drug types are active.
        Style style = effects.values().stream().filter(active -> active.expiresAt() > now)
                .map(Active::style).min(java.util.Comparator.naturalOrder()).orElse(null);
        if (style == null) return;
        // Preserve component styling, links, recipients and other plugins' renderers.
        // Affect the first two words so every ordinary message has a visible effect.
        event.message(event.message().replaceText(TextReplacementConfig.builder()
                .match(WORD).times(2)
                .replacement((match, builder) -> builder.content(transformWord(match.group(), style)))
                .build()));
    }

    private static void remove(UUID uuid, String key) {
        ACTIVE.computeIfPresent(uuid, (ignored, effects) -> {
            effects.remove(key);
            return effects.isEmpty() ? null : effects;
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getOldEffect() == null) return;
        if (event.getAction() == EntityPotionEffectEvent.Action.CHANGED && !event.isOverride()) return;
        remove(player.getUniqueId(), event.getOldEffect().getType().getKey().toString());
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) { ACTIVE.remove(event.getEntity().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { ACTIVE.remove(event.getPlayer().getUniqueId()); }
    public static void cleanupAll() { ACTIVE.clear(); }
}
