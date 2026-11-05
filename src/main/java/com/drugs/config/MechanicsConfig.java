package com.drugs.config;

import com.drugs.DrugsV2;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public final class MechanicsConfig {

    private static int cannabisGrowthSeconds = 600;
    private static int cartDurabilityUses = 16;
    private static int vapeDurabilityUses = 32;
    private static int bongDurabilityUses = 24;
    private static int workerTickInterval = 15;
    private static int maxHarvestPerCycle = 8;
    private static int maxPlantPerCycle = 8;
    private static volatile boolean chatEffectsEnabled = true;
    private static volatile boolean hudEnabled = true;
    private static boolean purityEnabled = true;
    private static boolean overdoseWarningsEnabled = true;
    private static int overdoseWarningSeconds = 60;
    private static int purityRefiningPoints = 5;
    private static org.bukkit.Material purityRefiningIngredient = org.bukkit.Material.QUARTZ;

    private MechanicsConfig() {
    }

    public static void load(File dataFolder) {
        File file = new File(dataFolder, "mechanics.yml");
        if (!file.exists()) {
            DrugsV2.getInstance().saveResource("mechanics.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        chatEffectsEnabled = yaml.getBoolean("chat-effects.enabled", true);
        hudEnabled = yaml.getBoolean("hud.enabled", true);
        overdoseWarningsEnabled = yaml.getBoolean("overdose-warnings.enabled", true);
        overdoseWarningSeconds = Math.clamp(yaml.getInt("overdose-warnings.reminder-seconds", 60), 5, 3600);
        purityEnabled = yaml.getBoolean("purity.enabled", true);
        purityRefiningPoints = Math.clamp(yaml.getInt("purity.refining-points", 5), 1, 25);
        var ingredient = org.bukkit.Material.matchMaterial(yaml.getString("purity.refining-ingredient", "QUARTZ"));
        purityRefiningIngredient = ingredient != null && (ingredient == org.bukkit.Material.QUARTZ || ingredient.isItem())
                && !com.drugs.items.DrugItemMetadata.isUnobtainableMaterial(ingredient) ? ingredient : org.bukkit.Material.QUARTZ;
        com.drugs.recipes.FlavorVariants.load(dataFolder);
        cannabisGrowthSeconds = Math.max(1, yaml.getInt("cannabis.growth-seconds", 600));
        vapeDurabilityUses = Math.max(1, yaml.getInt("vape.durability-uses", 32));
        cartDurabilityUses = Math.max(1, yaml.getInt("cart.durability-uses", 16));
        bongDurabilityUses = Math.max(1, yaml.getInt("bong.durability-uses", 24));
        String farmSection = yaml.isConfigurationSection("farm") ? "farm." : "weed-farm.";
        workerTickInterval = Math.max(10, yaml.getInt(farmSection + "worker-tick-interval", 15));
        maxHarvestPerCycle = Math.max(1, yaml.getInt(farmSection + "max-harvest-per-cycle", 8));
        maxPlantPerCycle = Math.max(1, yaml.getInt(farmSection + "max-plant-per-cycle", 8));
    }

    public static int getCannabisGrowthSeconds() {
        return cannabisGrowthSeconds;
    }

    public static boolean isChatEffectsEnabled() { return chatEffectsEnabled; }
    public static boolean isHudEnabled() { return hudEnabled; }
    public static boolean isPurityEnabled() { return purityEnabled; }
    public static boolean isOverdoseWarningsEnabled() { return overdoseWarningsEnabled; }
    public static int getOverdoseWarningSeconds() { return overdoseWarningSeconds; }
    public static int getPurityRefiningPoints() { return purityRefiningPoints; }
    public static org.bukkit.Material getPurityRefiningIngredient() { return purityRefiningIngredient; }

    public static int getCartDurabilityUses() {
        return cartDurabilityUses;
    }

    public static int getVapeDurabilityUses() {
        return vapeDurabilityUses;
    }

    public static int getBongDurabilityUses() {
        return bongDurabilityUses;
    }

    public static int getWorkerTickInterval() {
        return workerTickInterval;
    }

    public static int getMaxHarvestPerCycle() {
        return maxHarvestPerCycle;
    }

    public static int getMaxPlantPerCycle() {
        return maxPlantPerCycle;
    }
}
