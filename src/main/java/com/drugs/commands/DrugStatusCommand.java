package com.drugs.commands;

import com.drugs.addiction.AddictionConfigLoader;
import com.drugs.addiction.AddictionDrugProfile;
import com.drugs.addiction.AddictionManager;
import com.drugs.addiction.AddictionState;
import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;
import com.drugs.overdose.OverdoseEffectManager;
import com.drugs.status.DrugStatusTracker;
import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class DrugStatusCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /drugstatus");
            return true;
        }
        return show(sender);
    }

    public static boolean show(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can check their drug status.");
            return true;
        }
        if (!player.hasPermission("drugs.status")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to check your drug status.");
            return true;
        }
        var status = DrugStatusTracker.getStatus(player);
        Set<String> trackedDrugs = new TreeSet<>();
        if (status.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "You are fully sober. No drugs or potion effects are currently affecting you.");
        } else {
            boolean hasDrug = status.keySet().stream().anyMatch(id ->
                    !id.startsWith(DrugStatusTracker.UNKNOWN_PREFIX) && !id.startsWith(DrugStatusTracker.WITHDRAWAL_PREFIX));
            player.sendMessage(ChatColor.GOLD + (hasDrug
                    ? "You are not sober. Current drugs and effects:"
                    : "You have active effects. Current status:"));
            for (var entry : status.entrySet()) {
                String id = entry.getKey();
                boolean unknown = id.startsWith(DrugStatusTracker.UNKNOWN_PREFIX);
                boolean withdrawal = id.startsWith(DrugStatusTracker.WITHDRAWAL_PREFIX);
                String drugId = withdrawal ? id.substring(DrugStatusTracker.WITHDRAWAL_PREFIX.length()) : id;
                if (!unknown && DrugRegistry.isDrugRegistered(drugId)) {
                    trackedDrugs.add(drugId.toLowerCase(Locale.ROOT));
                }
                DrugEffectProfile profile = unknown ? null : DrugRegistry.getProfileById(drugId);
                String name = profile == null ? drugId : profile.getDisplayName();
                if (unknown) name = "Potion effect (source unknown)";
                else if (withdrawal) name = "Withdrawal: " + name;
                var details = entry.getValue();
                String timing = withdrawal && details.remainingTicks() < 0
                        ? "until treated (no natural recovery)"
                        : details.delayTicks() > 0
                        ? "kicks in: " + formatDuration(details.delayTicks()) + "; duration: " + formatDuration(details.remainingTicks())
                        : "remaining: " + formatDuration(details.remainingTicks());
                player.sendMessage(ChatColor.YELLOW + "- " + ChatColor.translateAlternateColorCodes('&', name)
                        + ChatColor.GRAY + " (" + timing + ")");
                if (!details.effects().isEmpty()) {
                    player.sendMessage(ChatColor.GRAY + "  Effects: " + String.join(", ", details.effects()));
                }
            }
        }

        trackedDrugs.addAll(ToleranceTracker.getTrackedDrugs(player));
        trackedDrugs.addAll(AddictionManager.getTrackedDrugs(player));

        if (!trackedDrugs.isEmpty()) {
            int odThreshold = Math.max(1, OverdoseEffectManager.getThreshold());
            player.sendMessage(ChatColor.GOLD + "Overdose Limits, Tolerance & Addiction:");
            long now = System.currentTimeMillis();
            for (String drugId : trackedDrugs) {
                DrugEffectProfile profile = DrugRegistry.getProfileById(drugId);
                String name = profile != null
                        ? ChatColor.translateAlternateColorCodes('&', profile.getDisplayName())
                        : drugId;
                int level = ToleranceTracker.getToleranceLevel(player, drugId);
                int max = ToleranceConfigLoader.getMaxTolerance(drugId);
                int potency = (int) Math.round(ToleranceConfigLoader.getEffectivenessMultiplier(drugId, level) * 100.0);
                int attempts = ToleranceTracker.getOverdoseAttempts(player, drugId);
                int decaySec = ToleranceTracker.getRemainingDecaySeconds(player, drugId);

                String riskBadge;
                if (level >= max) {
                    riskBadge = ChatColor.RED + "AT OVERDOSE LIMIT! " + ChatColor.GRAY
                            + "(OD attempts: " + ChatColor.RED + attempts + "/" + odThreshold + ChatColor.GRAY + ")";
                } else if (level >= max - 1) {
                    riskBadge = ChatColor.GOLD + "NEAR OVERDOSE LIMIT " + ChatColor.GRAY + "(1 dose from limit)";
                } else {
                    riskBadge = ChatColor.GREEN + "Safe " + ChatColor.GRAY + "(" + (max - level) + " doses below limit)";
                }

                String decayInfo = level > 0 && decaySec > 0
                        ? ChatColor.GRAY + ", decay: " + ChatColor.WHITE + formatDuration(decaySec * 20)
                        : "";

                player.sendMessage(ChatColor.YELLOW + "- " + name + ChatColor.GRAY + ": "
                        + ChatColor.WHITE + level + "/" + max + " tolerance "
                        + ChatColor.GRAY + "(" + potency + "% potency" + decayInfo + ") — " + riskBadge);

                AddictionState addiction = AddictionManager.getState(player, drugId);
                AddictionDrugProfile addProfile = AddictionConfigLoader.getDrugProfile(drugId);
                if (addiction != null && addProfile != null && addProfile.isAddictive()
                        && (addiction.getPoints() > 0 || addiction.isWithdrawalActive())) {
                    double pts = addiction.getPoints();
                    double threshold = addProfile.getAddictedAtPoints();
                    boolean isAddicted = pts >= threshold;
                    String stateLabel;
                    if (addiction.isWithdrawalActive()) {
                        stateLabel = ChatColor.RED + "IN WITHDRAWAL";
                    } else if (now < addiction.getWithdrawalBlockedUntilMillis()) {
                        int blockedTicks = (int) (((addiction.getWithdrawalBlockedUntilMillis() - now) + 49L) / 50L);
                        stateLabel = ChatColor.AQUA + "Cure protection (" + formatDuration(blockedTicks) + ")";
                    } else if (isAddicted && addProfile.getWithdrawalAfterSeconds() > 0 && addiction.getLastUseMillis() > 0) {
                        long onsetMillis = addiction.getLastUseMillis() + addProfile.getWithdrawalAfterSeconds() * 1000L;
                        int onsetTicks = (int) Math.max(0L, ((onsetMillis - now) + 49L) / 50L);
                        stateLabel = ChatColor.GOLD + "ADDICTED (withdrawal in " + formatDuration(onsetTicks) + ")";
                    } else if (isAddicted) {
                        stateLabel = ChatColor.GOLD + "ADDICTED";
                    } else {
                        stateLabel = ChatColor.YELLOW + "Developing";
                    }
                    player.sendMessage(ChatColor.GRAY + "  Addiction: " + ChatColor.WHITE
                            + String.format(Locale.ROOT, "%.1f/%.1f", pts, threshold)
                            + ChatColor.GRAY + " — " + stateLabel);
                }
            }
        } else if (player.getUniqueId() != null) {
            player.sendMessage(ChatColor.GRAY + "Overdose & Tolerance: " + ChatColor.GREEN + "0 active tolerances "
                    + ChatColor.GRAY + "(overdose limit is reached at max tolerance; see " + ChatColor.YELLOW + "/tolerance" + ChatColor.GRAY + ").");
        }
        return true;
    }

    static String formatDuration(int ticks) {
        if (ticks < 0) return "indefinite";
        long seconds = (ticks + 19L) / 20;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + seconds % 60 + "s";
        return minutes / 60 + "h " + minutes % 60 + "m " + seconds % 60 + "s";
    }
}
