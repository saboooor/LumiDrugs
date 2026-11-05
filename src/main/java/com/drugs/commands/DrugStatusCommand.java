package com.drugs.commands;

import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;
import com.drugs.status.DrugStatusTracker;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
