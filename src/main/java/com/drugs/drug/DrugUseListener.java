package com.drugs.drug;

import com.drugs.tolerance.ToleranceTracker;

import com.drugs.DrugsV2;
import com.drugs.achievements.AchievementManager;
import com.drugs.achievements.NativeAdvancements;
import com.drugs.config.MechanicsConfig;
import com.drugs.items.DrugItemMetadata;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class DrugUseListener implements Listener {

    // Track drug use count per player
    private static final Map<String, Integer> drugUseCount = new HashMap<>();

    @EventHandler
    public void onDrugUse(PlayerInteractEvent event) {
        // Only handle right-click interactions for drug use
        EquipmentSlot hand = event.getHand();
        if (hand == null || !event.getAction().isRightClick()) return;

        if (com.drugs.recipes.FlavorInfusionListener.isInfusionInteraction(event)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(hand);

        // Skip if item is null or air
        if (item == null || item.getType().isAir()) return;

        DrugEffectProfile profile = DrugRegistry.getProfileFromItem(item);
        if (profile == null) return;

        String drugId = profile.getId();

        // Only cancel the event if we've confirmed this is a valid drug use
        event.setCancelled(true);

        // ----------------------------------------
        // Track drug use count for achievements
        // ----------------------------------------
        String playerKey = player.getUniqueId().toString();
        int useCount = drugUseCount.getOrDefault(playerKey, 0) + 1;
        drugUseCount.put(playerKey, useCount);

        // ----------------------------------------
        // Process achievement triggers
        // ----------------------------------------
        
        // First drug use achievement
        Map<String, Object> firstUseContext = AchievementManager.createContext();
        AchievementManager.processTrigger(player, "first_drug_use", firstUseContext);
        
        // Specific drug use achievement
        Map<String, Object> specificDrugContext = AchievementManager.createContext();
        specificDrugContext.put("drug_id", drugId);
        AchievementManager.processTrigger(player, "use_specific_drug", specificDrugContext);
        
        // Use count achievement
        Map<String, Object> countContext = AchievementManager.createContext();
        countContext.put("count", useCount);
        AchievementManager.processTrigger(player, "use_count", countContext);
        
        // Track for connoisseur achievement (all drugs used)
        trackDrugUsedForConnoisseur(player, drugId);
        
        // Max tolerance achievement
        if (ToleranceTracker.isAtMaxTolerance(player.getUniqueId(), drugId)) {
            Map<String, Object> maxToleranceContext = AchievementManager.createContext();
            AchievementManager.processTrigger(player, "use_at_max", maxToleranceContext);
        }

        // ----------------------------------------
        // 🧪 Apply effects
        // ----------------------------------------
        applyDrugEffects(profile, player, item);
        ItemStack addedDose = DrugLacing.additionalDose(item);
        if (addedDose != null) {
            DrugEffectProfile addedProfile = DrugRegistry.getProfileFromItem(addedDose);
            if (addedProfile != null) applyDrugEffects(addedProfile, player, addedDose);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            if (drugId.equalsIgnoreCase("cart") || drugId.equalsIgnoreCase("vape")) {
                // One ingredient supplies one dose, rather than one dose per remaining puff.
                DrugLacing.expendDose(item);
                handleCartDurability(player, item, drugId, hand);
            } else {
                int newAmount = item.getAmount() - 1;
                if (newAmount <= 0) {
                    player.getInventory().setItem(hand, null);
                } else {
                    item.setAmount(newAmount);
                }
            }
        }
    }

    private void applyDrugEffects(DrugEffectProfile profile, Player player, ItemStack item) {
        profile.applyEffects(player, item);
    }
    

    private void handleCartDurability(Player player, ItemStack item, String drugId, EquipmentSlot hand) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        var meta = item.getItemMeta();
        int maxDurability = drugId.equalsIgnoreCase("vape")
                ? MechanicsConfig.getVapeDurabilityUses() : MechanicsConfig.getCartDurabilityUses();
        Integer currentDurability = DrugItemMetadata.getCartDurability(meta);

        if (currentDurability == null || currentDurability <= 0 || currentDurability > maxDurability) {
            currentDurability = maxDurability;
        }

        int nextDurability = currentDurability - 1;
        if (nextDurability <= 0) {
            sendCartDurabilityActionBar(player, 0, maxDurability, drugId);
            int newAmount = item.getAmount() - 1;
            if (newAmount <= 0) {
                player.getInventory().setItem(hand, null);
            } else {
                item.setAmount(newAmount);
                var refreshedMeta = item.getItemMeta();
                if (refreshedMeta != null) {
                    DrugItemMetadata.setLacingExpended(refreshedMeta, false);
                    DrugItemMetadata.setCartDurability(refreshedMeta, maxDurability);
                    DrugItemMetadata.applyCartDurabilityLore(refreshedMeta, maxDurability, maxDurability);
                    item.setItemMeta(refreshedMeta);
                }
            }
            return;
        }

        DrugItemMetadata.setCartDurability(meta, nextDurability);
        DrugItemMetadata.applyCartDurabilityLore(meta, nextDurability, maxDurability);
        item.setItemMeta(meta);
        sendCartDurabilityActionBar(player, nextDurability, maxDurability, drugId);
    }

    private void sendCartDurabilityActionBar(Player player, int currentDurability, int maxDurability, String drugId) {
        String message = ChatColor.DARK_GREEN + (drugId.equalsIgnoreCase("vape") ? "Vape Puffs: " : "Cart Durability: ")
                + ChatColor.GREEN + Math.max(0, currentDurability)
                + ChatColor.DARK_GRAY + "/"
                + ChatColor.GREEN + Math.max(1, maxDurability);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }

    /**
     * Tracks drug usage for the connoisseur achievement
     */
    private void trackDrugUsedForConnoisseur(Player player, String drugId) {
        NativeAdvancements.trackDrug(player, drugId);
    }
}
