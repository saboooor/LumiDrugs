package com.drugs.bong;

import com.drugs.DrugsV2;
import com.drugs.items.DrugItemMetadata;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class BongConfigLoader {

    private static boolean enabled = true;
    private static String baseDrugId = "blunt";
    private static long cooldownMillis = 1500L;

    private static Material bongItemMaterial = Material.GLASS_BOTTLE;
    private static String bongDisplayName = "&bGlass Bong";
    private static int bongCustomModelData = DrugItemMetadata.customModelData("bong");

    private BongConfigLoader() {
    }

    public static void load(File dataFolder) {
        File file = new File(dataFolder, "config.yml");
        if (!file.exists()) {
            DrugsV2.getInstance().saveResource("config.yml", false);
        }

        var yaml = YamlConfiguration.loadConfiguration(file).getConfigurationSection("bong");
        if (yaml == null) throw new IllegalArgumentException("config.yml requires a bong section");
        enabled = yaml.getBoolean("enabled", true);
        baseDrugId = yaml.getString("base-drug-id", "blunt").toLowerCase();
        cooldownMillis = Math.max(0L, yaml.getLong("cooldown-ms", 1500L));

        Material configured = Material.matchMaterial(yaml.getString("item.material", "GLASS_BOTTLE"));
        bongItemMaterial = DrugItemMetadata.sanitizeMaterial("bong", configured, Material.GLASS_BOTTLE);
        bongDisplayName = yaml.getString("item.display-name", "&bGlass Bong");
        int configuredCmd = yaml.getInt("item.custom-model-data", DrugItemMetadata.customModelData("bong"));
        bongCustomModelData = configuredCmd > 0 ? configuredCmd : DrugItemMetadata.customModelData("bong");
    }

    public static boolean isEnabled() { return enabled; }
    public static String getBaseDrugId() { return baseDrugId; }
    public static long getCooldownMillis() { return cooldownMillis; }
    public static Material getBongItemMaterial() { return bongItemMaterial; }
    public static String getBongDisplayName() { return bongDisplayName; }
    public static int getBongCustomModelData() { return bongCustomModelData; }
}
