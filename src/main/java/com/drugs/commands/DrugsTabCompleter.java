package com.drugs.commands;

import com.drugs.addiction.CureRegistry;
import com.drugs.drug.DrugRegistry;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DrugsTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            List<String> options = new ArrayList<>();

            options.add("give");
            options.add("help");
            if (sender.hasPermission("drugs.status")) options.add("status");
            options.add("growing");
            options.add("purity");
            options.add("hud");
            options.add("recipe");
            if (sender.hasPermission("drugs.quests")) options.addAll(List.of("learn", "journal", "contracts"));
            options.add("list");
            options.add("purge");
            options.add("reload");
            options.add("achievements"); // ✅ added this!
            
            if (sender.hasPermission("drugs.admin.overdose")) {
                options.add("overdose");
            }

            return partialMatch(args[0], options);
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("achievements")) {
            if (sender.hasPermission("drugs.admin.achievements")) {
                return partialMatch(args[1], Collections.singletonList("toggle"));
            }
            return Collections.emptyList();
        }
        
        if (args.length == 2 && args[0].equalsIgnoreCase("overdose")) {
            if (sender.hasPermission("drugs.admin.overdose")) {
                return partialMatch(args[1], Arrays.asList("reload", "reset"));
            }
            return Collections.emptyList();
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("overdose") && args[1].equalsIgnoreCase("reset")) {
            if (sender.hasPermission("drugs.admin.overdose")) {
                List<String> playerNames = new ArrayList<>();
                for (Player online : sender.getServer().getOnlinePlayers()) {
                    playerNames.add(online.getName());
                }
                return partialMatch(args[2], playerNames);
            }
            return Collections.emptyList();
        }

        if (args.length == 2 && sender.hasPermission("drugs.quests")) {
            if (args[0].equalsIgnoreCase("journal")) return partialMatch(args[1], List.of("abandon-learning", "abandon-supply"));
            var journal = com.drugs.DrugsV2.getInstance().getProductionJournal();
            if (journal != null && (args[0].equalsIgnoreCase("learn") || args[0].equalsIgnoreCase("contracts")))
                return partialMatch(args[1], journal.products());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("recipe")) {
            List<String> recipeIds = new ArrayList<>(DrugRegistry.getRegisteredDrugNames());
            recipeIds.addAll(CureRegistry.getRegisteredCureNames());
            recipeIds.addAll(com.drugs.recipes.ProductionRecipes.intermediates());
            recipeIds.add("bong");
            recipeIds.add("farm-controller");
            return partialMatch(args[1], recipeIds);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> playerNames = new ArrayList<>();
            for (Player online : sender.getServer().getOnlinePlayers()) {
                playerNames.add(online.getName());
            }
            return partialMatch(args[1], playerNames);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> itemIds = new ArrayList<>(DrugRegistry.getRegisteredDrugNames());
            itemIds.addAll(CureRegistry.getRegisteredCureNames());
            itemIds.add("bong");
            itemIds.add("farm-controller");
            itemIds.add("all");
            return partialMatch(args[2], itemIds);
        }

        return Collections.emptyList();
    }

    private List<String> partialMatch(String input, List<String> options) {
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(option);
            }
        }
        return result;
    }
}
