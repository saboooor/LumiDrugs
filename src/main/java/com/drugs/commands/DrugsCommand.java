package com.drugs.commands;

import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;

import com.drugs.DrugsV2;
import com.drugs.achievements.AchievementSettingsLoader;
import com.drugs.achievements.CustomAchievementLoader;
import com.drugs.addiction.AddictionConfigLoader;
import com.drugs.addiction.AddictionManager;
import com.drugs.addiction.CureRegistry;
import com.drugs.bong.BongConfigLoader;
import com.drugs.bong.BongItemFactory;
import com.drugs.bong.BongRegistry;
import com.drugs.cartel.EnforcerGuide;
import com.drugs.drug.DrugEffectProfile;
import com.drugs.drug.DrugRegistry;
import com.drugs.growing.CannabisPlantRegistry;
import com.drugs.growing.DrugGrowing;
import com.drugs.overdose.OverdoseEffectManager;
import com.drugs.recipes.RecipeUpdates;
import com.drugs.strains.StrainConfigLoader;
import com.drugs.ui.DrugMenuGUI;

import com.drugs.farm.FarmItems;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.TreeSet;

/**
 * Handles /drugs command and subcommands.
 */
public class DrugsCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /drugs
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can open the drug menu.");
                return true;
            }
            DrugMenuGUI.open(player, 0);
            return true;
        }

        if (args[0].equalsIgnoreCase("close")) {
            return true;
        }

        if (args[0].equalsIgnoreCase("farm")) {
            if (sender instanceof Player player && args.length == 2) {
                var controller = DrugsV2.getInstance().getFarmControllerListener();
                if (controller != null) controller.handleDialogAction(player, args[1]);
            }
            return true;
        }

        // /drugs guide <drug> [page]
        if (args[0].equalsIgnoreCase("guide")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can view the enforcer guide.");
                return true;
            }
            if (args.length < 2 || args[1].equalsIgnoreCase("close")) {
                return true;
            }
            String drug = args[1];
            EnforcerGuide.Page page = EnforcerGuide.Page.DIALOGUE;
            if (args.length >= 3) {
                try {
                    page = EnforcerGuide.Page.valueOf(args[2].toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
            EnforcerGuide.openPage(DrugsV2.getInstance(), player, drug, page);
            return true;
        }

        if (args[0].equalsIgnoreCase("status")) {
            if (args.length != 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /drugs status");
                return true;
            }
            return DrugStatusCommand.show(sender);
        }

        // Easter egg: /drugs schizo <player> [seconds|off]
        if (args[0].equalsIgnoreCase("schizo") || args[0].equalsIgnoreCase("schizophrenia") || args[0].equalsIgnoreCase("psychosis")) {
            if (!sender.hasPermission("drugs.admin") && !sender.hasPermission("drugs.give") && !sender.isOp()) {
                sender.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.ITALIC + "The voices do not answer you...");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /drugs schizo <player> [seconds|off]");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }
            var sensory = DrugsV2.getInstance() != null ? DrugsV2.getInstance().getSensoryEffectManager() : null;
            if (sensory == null) return true;
            if (args.length >= 3 && (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("stop") || args[2].equalsIgnoreCase("clear"))) {
                sensory.stopSchizophrenia(target.getUniqueId());
                sender.sendMessage(ChatColor.GREEN + "Cleared the voices from " + target.getName() + "'s head.");
                return true;
            }
            int duration = 0;
            if (args.length >= 3) {
                try {
                    duration = Math.max(5, Integer.parseInt(args[2]));
                } catch (NumberFormatException ignored) {
                }
            }
            boolean enabled = sensory.toggleSchizophrenia(target, duration);
            if (enabled) {
                sender.sendMessage(ChatColor.DARK_PURPLE + "👁 Awakened the voices in " + target.getName() + "'s mind"
                        + (duration > 0 ? " (" + duration + "s)." : " (120s)."));
            } else {
                sender.sendMessage(ChatColor.GREEN + "Cleared the voices from " + target.getName() + "'s head.");
            }
            return true;
        }

        // /drugs achievements
        if (args[0].equalsIgnoreCase("achievements") && args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Only players can view achievements.");
                return true;
            }

            if (!player.hasPermission("drugs.achievements")) {
                player.sendMessage(ChatColor.RED + "You don't have permission to view achievements.");
                return true;
            }

            player.sendMessage(ChatColor.GREEN + "Open Minecraft’s Advancements screen (default key: L) and select the Drugs tab.");
            return true;
        }

        if (args[0].equalsIgnoreCase("recipe") && args.length == 2) {
            if (sender instanceof Player player) com.drugs.ui.DrugRecipeViewer.openRecipe(player, args[1]);
            return true;
        }

        // /drugs growing
        if (args[0].equalsIgnoreCase("growing")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the growing guide.");
                return true;
            }
            String crop = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : null;
            if (crop != null && !DrugGrowing.cropIds().contains(crop)) {
                sender.sendMessage("§cUnknown crop. Use /drugs growing to choose a crop.");
                return true;
            }
            com.drugs.ui.GrowingGuide.open(player, crop, null);
            return true;
        }

        // /drugs help
        if (args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(ChatColor.GOLD + "-----[ LumiDrugs Help ]-----");
            sender.sendMessage(ChatColor.YELLOW + "/drugs" + ChatColor.GRAY + " - Open the drug selection GUI");
            if (sender.hasPermission("drugs.give")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs give <player> <drug|cure|bong|farm-controller|all> [amount]" + ChatColor.GRAY + " - Give drugs/cures to someone");
            }
            if (sender.hasPermission("drugs.tolerance")) {
                sender.sendMessage(ChatColor.YELLOW + "/tolerance" + ChatColor.GRAY + " - View your current drug tolerance");
            }
            if (sender.hasPermission("drugs.strains")) {
                sender.sendMessage(ChatColor.YELLOW + "/strains" + ChatColor.GRAY + " - Open the cannabis strain index");
            }
            if (sender.hasPermission("drugs.admin.purge")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs purge <player>" + ChatColor.GRAY + " - Reset a player's tolerance");
            }
            if (sender.hasPermission("drugs.admin.reload")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs reload" + ChatColor.GRAY + " - Reload all plugin configs");
            }
            if (sender.hasPermission("drugs.admin.list")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs list" + ChatColor.GRAY + " - Show all available drugs");
            }
            if (sender.hasPermission("drugs.status")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugstatus or /drugs status" + ChatColor.GRAY + " - Check whether you are sober");
            }
            if (sender.hasPermission("drugs.achievements")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs achievements" + ChatColor.GRAY + " - View your achievement progress");
            }
            if (sender.hasPermission("drugs.admin.achievements")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs achievements toggle" + ChatColor.GRAY + " - Enable/disable the achievement system");
            }
            if (sender.hasPermission("drugs.admin.overdose")) {
                sender.sendMessage(ChatColor.YELLOW + "/drugs overdose reload" + ChatColor.GRAY + " - Reload overdose configuration");
                sender.sendMessage(ChatColor.YELLOW + "/drugs overdose reset <player>" + ChatColor.GRAY + " - Reset a player's overdose counts");
            }
            sender.sendMessage(ChatColor.YELLOW + "/drugs growing" + ChatColor.GRAY + " - Learn how to grow crafting ingredients");
            sender.sendMessage(ChatColor.YELLOW + "/drugs help" + ChatColor.GRAY + " - Show this help menu");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.DARK_RED + "⚠ Using drugs too often causes tolerance. Max tolerance = no effects!");
            sender.sendMessage(ChatColor.RED + "💀 Use drugs at max tolerance multiple times and you may die of overdose!");
            return true;
        }

        // /drugs give
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("drugs.give")) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /drugs give <player> <drugId|cureId|bong|farm-controller|all> [amount]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            String drugId = args[2];
            int amount = 1;

            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                    if (amount <= 0) amount = 1;
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "Invalid amount, defaulting to 1.");
                }
            }

            if (drugId.equalsIgnoreCase("all")) {
                int total = 0;
                for (String id : DrugRegistry.getRegisteredDrugNames()) {
                    ItemStack drugItem = DrugRegistry.getDrugItem(id, amount);
                    if (drugItem != null) {
                        target.getInventory().addItem(drugItem);
                        total++;
                    }
                }
                for (String id : CureRegistry.getRegisteredCureNames()) {
                    ItemStack cureItem = CureRegistry.getCureItem(id, amount);
                    if (cureItem != null) {
                        target.getInventory().addItem(cureItem);
                        total++;
                    }
                }
                target.getInventory().addItem(BongItemFactory.createBongItem(amount));
                total++;
                target.getInventory().addItem(FarmItems.createControllerItem().asQuantity(amount));
                total++;
                sender.sendMessage(ChatColor.GREEN + "Gave " + total + " item types to " + target.getName());
                target.sendMessage(ChatColor.GOLD + "You received " + total + " item types from " + sender.getName());
                return true;
            }

            ItemStack item = DrugRegistry.getDrugItem(drugId, amount);
            if (item == null && drugId.equalsIgnoreCase("bong")) {
                item = BongItemFactory.createBongItem(amount);
            }
            if (item == null && drugId.equalsIgnoreCase("farm-controller")) {
                item = FarmItems.createControllerItem().asQuantity(amount);
            }
            if (item == null) {
                item = CureRegistry.getCureItem(drugId, amount);
            }
            if (item == null) {
                sender.sendMessage(ChatColor.RED + "Drug or cure '" + drugId + "' not found.");
                return true;
            }

            target.getInventory().addItem(item);
            sender.sendMessage(ChatColor.GREEN + "Gave " + amount + "x " + drugId + " to " + target.getName());
            target.sendMessage(ChatColor.GOLD + "You received " + amount + "x " + drugId + " from " + sender.getName());
            return true;
        }

        // /drugs purge <player>
        if (args[0].equalsIgnoreCase("purge")) {
            if (!sender.hasPermission("drugs.admin.purge")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to purge tolerance.");
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /drugs purge <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online.");
                return true;
            }

            ToleranceTracker.resetAllTolerance(target);
            OverdoseEffectManager.resetOverdoseCounts(target);
            sender.sendMessage(ChatColor.GREEN + "Reset all tolerance and overdose counts for " + target.getName());
            target.sendMessage(ChatColor.YELLOW + "Your drug tolerance levels and overdose counts have been purged by an admin.");
            return true;
        }

        // /drugs reload
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("drugs.admin.reload")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to reload the plugin.");
                return true;
            }

            RecipeUpdates.begin();
            try {
                DrugsV2.getInstance().reloadConfig();
                DrugsV2.getInstance().reloadGrowing();
                DrugsV2.getInstance().saveRecipesConfig();
                DrugsV2.getInstance().saveToleranceConfig();
                DrugsV2.getInstance().saveAchievementSettingsConfig();
                DrugsV2.getInstance().saveAchievementsConfig();
                DrugsV2.getInstance().saveOverdoseConfig();
                DrugsV2.getInstance().saveAddictionConfig();
                DrugsV2.getInstance().saveStrainsConfig();
                DrugsV2.getInstance().saveBongConfig();

                ToleranceConfigLoader.load(DrugsV2.getInstance().getDataFolder());
                AchievementSettingsLoader.load(DrugsV2.getInstance().getDataFolder());
                OverdoseEffectManager.load(DrugsV2.getInstance().getDataFolder());
                AddictionConfigLoader.load(DrugsV2.getInstance());
                StrainConfigLoader.load(DrugsV2.getInstance().getDataFolder());
                BongConfigLoader.load(DrugsV2.getInstance().getDataFolder());
                CannabisPlantRegistry.cleanupInvalidPlants();
                BongRegistry.init(DrugsV2.getInstance().getDataFolder());
                if (DrugsV2.getInstance().getBongListener() != null) {
                    BongRegistry.respawnMissing(DrugsV2.getInstance().getBongListener());
                }
                com.drugs.recipes.FlavorVariants.load(DrugsV2.getInstance().getDataFolder());
                DrugRegistry.init(DrugsV2.getInstance());
                CustomAchievementLoader.load(DrugsV2.getInstance().getDataFolder());
                CureRegistry.init(DrugsV2.getInstance());
                AddictionManager.reload(DrugsV2.getInstance());
            } finally { RecipeUpdates.end(); }

            sender.sendMessage(ChatColor.GREEN + "LumiDrugs configs reloaded successfully.");
            return true;
        }

        // /drugs list
        if (args[0].equalsIgnoreCase("list")) {
            if (!sender.hasPermission("drugs.admin.list")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to view the drug list.");
                return true;
            }

            sender.sendMessage(ChatColor.GOLD + "-----[ Registered Drugs ]-----");

            Set<String> all = new TreeSet<>(DrugRegistry.getRegisteredDrugNames());
            for (String drugId : all) {
                DrugEffectProfile profile = DrugRegistry.getProfileById(drugId);
                if (profile == null) continue;

                sender.sendMessage(ChatColor.YELLOW + "- " + ChatColor.translateAlternateColorCodes('&', profile.getDisplayName()));
                for (String lore : profile.getLore()) {
                    sender.sendMessage(ChatColor.GRAY + "  " + ChatColor.translateAlternateColorCodes('&', lore));
                }
            }
            return true;
        }
        
        // /drugs achievements toggle
        if (args[0].equalsIgnoreCase("achievements") && args.length > 1 && args[1].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("drugs.admin.achievements")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to toggle achievements.");
                return true;
            }
            
            // We'd need to modify the config file directly to toggle achievements
            // For now, just inform the user to edit the config file
            sender.sendMessage(ChatColor.YELLOW + "To enable/disable achievements, edit the achievement_settings.yml file");
            sender.sendMessage(ChatColor.YELLOW + "and set 'enabled' to true or false, then use /drugs reload.");
            return true;
        }
        
        // /drugs overdose reload
        if (args[0].equalsIgnoreCase("overdose")) {
            if (!sender.hasPermission("drugs.admin.overdose")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to manage overdose settings.");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /drugs overdose [reload/reset]");
                return true;
            }
            
            // /drugs overdose reload
            if (args[1].equalsIgnoreCase("reload")) {
                OverdoseEffectManager.load(DrugsV2.getInstance().getDataFolder());
                sender.sendMessage(ChatColor.GREEN + "Overdose configuration reloaded successfully.");
                return true;
            }
            
            // /drugs overdose reset <player>
            if (args[1].equalsIgnoreCase("reset")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatColor.RED + "Usage: /drugs overdose reset <player>");
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Player not found or not online.");
                    return true;
                }
                
                OverdoseEffectManager.resetOverdoseCounts(target);
                sender.sendMessage(ChatColor.GREEN + "Reset all overdose counts for " + target.getName());
                target.sendMessage(ChatColor.YELLOW + "Your overdose counts have been reset by an admin.");
                return true;
            }
            
            sender.sendMessage(ChatColor.RED + "Unknown overdose subcommand. Try reload or reset.");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try /drugs help");
        return true;
    }
}
