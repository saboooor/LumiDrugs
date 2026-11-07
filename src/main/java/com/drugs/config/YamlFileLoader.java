package com.drugs.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Utility for loading external YAML config files like a family file’s recipe section
 */
public class YamlFileLoader {

    /**
     * Loads a YAML file from the plugin's data folder.
     *
     * @param filename The name of the file to load (e.g. "a family file’s recipe section")
     * @param plugin   The plugin instance
     * @return The loaded FileConfiguration
     */
    public static FileConfiguration load(String filename, Plugin plugin) {
        File file = new File(plugin.getDataFolder(), filename);
        if (!file.exists()) {
            plugin.saveResource(filename, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }
}
