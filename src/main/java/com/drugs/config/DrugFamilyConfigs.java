package com.drugs.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Reads family files into configuration views for the runtime subsystems. */
public final class DrugFamilyConfigs {
    private static final List<String> DEFAULT_FAMILIES = List.of(
            "cannabis", "opioids", "stimulants", "psychedelics", "nicotine", "inhalants");

    private DrugFamilyConfigs() {}

    public static void saveDefaults(Plugin plugin) {
        if (!new File(plugin.getDataFolder(), "prices.yml").exists()) plugin.saveResource("prices.yml", false);
        File folder = new File(plugin.getDataFolder(), "drugs");
        // Install on first use; deleted family files stay deleted on subsequent starts.
        if (!folder.exists()) {
            for (String family : DEFAULT_FAMILIES) plugin.saveResource("drugs/" + family + ".yml", false);
        }
    }

    public static YamlConfiguration view(File folder, String kind) {
        var shared = read(new File(folder, "config.yml"));
        var result = new YamlConfiguration();
        switch (kind) {
            case "items", "growing", "strains", "hallucinations" -> { }
            case "recipes" -> copy(shared.getConfigurationSection("recipes"), result, "");
            case "tolerance", "addiction", "overdose" -> copy(shared.getConfigurationSection(kind), result, "");
            case "mechanics" -> copy(shared, result, "");
            case "cartel" -> {
                copy(read(new File(folder, "cartel.yml")), result, "");
                result.set("prices", null);
                result.set("strain-prices", null);
                var prices = read(new File(folder, "prices.yml"));
                copy(prices.getConfigurationSection("prices"), result, "prices");
                copy(prices.getConfigurationSection("strain-prices"), result, "strain-prices");
                return result;
            }
            default -> throw new IllegalArgumentException("Unknown drug configuration view: " + kind);
        }
        File familyFolder = new File(folder, "drugs");
        File[] files = familyFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            if (familyFolder.exists()) throw new IllegalArgumentException("Cannot read drug family folder: " + familyFolder);
            return result;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        Set<String> ids = new HashSet<>();
        for (File file : files) {
            var family = read(file);
            var drugs = family.getConfigurationSection("drugs");
            if (drugs != null) for (String id : drugs.getKeys(false)) {
                if (!id.matches("[a-z0-9_]+") || !ids.add(id))
                    throw new IllegalArgumentException("Invalid or duplicate drug '" + id + "' in " + file);
                var drug = drugs.getConfigurationSection(id);
                if (drug == null) throw new IllegalArgumentException("Drug '" + id + "' must be a section in " + file);
                switch (kind) {
                    case "items" -> copy(drug.getConfigurationSection("item"), result, id);
                    case "hallucinations" -> copy(drug.getConfigurationSection("hallucinations"), result, id);
                    case "recipes" -> copy(drug.getConfigurationSection("recipe"), result, id);
                    case "tolerance" -> copy(drug.getConfigurationSection("tolerance"), result, id);
                    case "addiction" -> copy(drug.getConfigurationSection("addiction"), result, "drugs." + id);
                    case "overdose" -> copy(drug.getConfigurationSection("overdose"), result, "drug_specific." + id);
                    case "mechanics" -> copy(drug.getConfigurationSection("mechanics"), result, id);
                }
            }
            switch (kind) {
                case "growing" -> merge(family.getConfigurationSection("crops"), result, "", file);
                case "strains" -> merge(family.getConfigurationSection("strains"), result, "strains", file);
                case "addiction" -> merge(family.getConfigurationSection("cures"), result, "cures", file);
                case "recipes" -> merge(family.getConfigurationSection("recipes"), result, "", file);
            }
        }
        return result;
    }

    private static void merge(ConfigurationSection source, ConfigurationSection target, String prefix, File file) {
        if (source == null) return;
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            if (target.isSet(path)) throw new IllegalArgumentException("Duplicate configuration '" + path + "' in " + file);
            Object value = source.get(key);
            if (value instanceof ConfigurationSection section) copy(section, target, path);
            else target.set(path, value);
        }
    }

    private static void copy(ConfigurationSection source, ConfigurationSection target, String prefix) {
        if (source == null) return;
        for (String key : source.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection section) copy(section, target, path);
            else target.set(path, value);
        }
    }

    private static YamlConfiguration read(File file) {
        var yaml = new YamlConfiguration();
        if (!file.exists()) return yaml;
        try { yaml.load(file); }
        catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IllegalArgumentException("Cannot load " + file + ": " + ex.getMessage(), ex);
        }
        return yaml;
    }
}
