package com.drugs;

import com.drugs.achievements.AchievementSettingsLoader;
import com.drugs.achievements.CustomAchievementLoader;
import com.drugs.achievements.NativeAdvancements;
import com.drugs.addiction.AddictionConfigLoader;
import com.drugs.addiction.AddictionListener;
import com.drugs.addiction.AddictionManager;
import com.drugs.addiction.CureRegistry;
import com.drugs.bong.BongConfigLoader;
import com.drugs.bong.BongItemFactory;
import com.drugs.bong.BongListener;
import com.drugs.bong.BongRegistry;
import com.drugs.bootstrap.PerformanceOptimizer;
import com.drugs.bootstrap.PluginStartup;
import com.drugs.cartel.CartelDealer;
import com.drugs.cartel.CartelDroppedSales;
import com.drugs.cartel.CartelEnforcer;
import com.drugs.cartel.CartelNpcs;
import com.drugs.cartel.EnforcerGuide;
import com.drugs.commands.DrugStatusCommand;
import com.drugs.commands.DrugsCommand;
import com.drugs.commands.DrugsTabCompleter;
import com.drugs.commands.StrainsCommand;
import com.drugs.commands.ToleranceCommand;
import com.drugs.config.MechanicsConfig;
import com.drugs.drug.DrugRegistry;
import com.drugs.drug.DrugUseListener;
import com.drugs.effects.LsdEffectManager;
import com.drugs.effects.SalviaEffectManager;
import com.drugs.effects.SensoryEffectManager;
import com.drugs.growing.CannabisPlantListener;
import com.drugs.growing.CannabisPlantRegistry;
import com.drugs.growing.DrugGrowing;
import com.drugs.integration.DrugsPlaceholderExpansion;
import com.drugs.overdose.OverdoseEffectManager;
import com.drugs.recipes.DrugRecipeHelper;
import com.drugs.recipes.HarvestCraftingListener;
import com.drugs.recipes.RecipeUpdates;
import com.drugs.status.DrugStatusTracker;
import com.drugs.strains.StrainConfigLoader;
import com.drugs.strains.StrainCraftingListener;
import com.drugs.strains.StrainsMenuListener;
import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;
import com.drugs.ui.BedrockForms;
import com.drugs.ui.DialogView;
import com.drugs.ui.DrugMenuListener;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import com.drugs.farm.FarmControllerListener;
import com.drugs.farm.FarmItems;
import com.drugs.farm.FarmManager;
import com.drugs.farm.FarmWorkerService;

import java.io.File;
import java.util.List;
import java.util.logging.Level;

/**
 * Main plugin class for DrugsV2.
 */
public class DrugsV2 extends JavaPlugin {

    private static DrugsV2 instance;
    private FileConfiguration recipesConfig;
    private File recipesFile;
    private BongListener bongListener;
    private DrugGrowing drugGrowing;
    private FarmManager farmManager;
    private FarmControllerListener farmControllerListener;

    public FarmControllerListener getFarmControllerListener() {
        return farmControllerListener;
    }
    private LsdEffectManager lsdEffectManager;
    private SalviaEffectManager salviaEffectManager;
    private SensoryEffectManager sensoryEffectManager;
    private PluginStartup startup;
    private DrugsPlaceholderExpansion placeholderExpansion;
    private boolean cannabisLoaded;
    private boolean bongsLoaded;
    private boolean farmsLoaded;
    private boolean addictionLoaded;
    private CartelEnforcer cartelEnforcer;
    private CartelNpcs cartelNpcs;
    private CartelDroppedSales cartelDroppedSales;
    private BedrockForms bedrockForms;

    public static DrugsV2 getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // PlugMan calls this on the server thread. Defer heavy initialization so ticks
        // continue between phases, and keep commands safe until all services are ready.
        for (String name : List.of("drugs", "drugstatus", "tolerance", "strains", "cartel")) {
            getCommand(name).setExecutor((sender, command, label, args) -> {
                sender.sendMessage("§eLumiDrugs is initializing. Please try again shortly.");
                return true;
            });
            getCommand(name).setTabCompleter((sender, command, label, args) -> List.of());
        }
        startup = new PluginStartup(this, List.of(
                new PluginStartup.Step("default configuration", () -> {
                    saveDefaultConfig();
                    saveRecipesConfig();
                    saveToleranceConfig();
                    saveAchievementSettingsConfig();
                    saveAchievementsConfig();
                    saveOverdoseConfig();
                    saveAddictionConfig();
                    saveStrainsConfig();
                    saveBongConfig();
                    saveMechanicsConfig();
                }),
                new PluginStartup.Step("tolerance configuration", () -> ToleranceConfigLoader.load(getDataFolder())),
                new PluginStartup.Step("achievement settings", () -> AchievementSettingsLoader.load(getDataFolder())),
                new PluginStartup.Step("overdose configuration", () -> OverdoseEffectManager.load(getDataFolder())),
                new PluginStartup.Step("addiction configuration", () -> AddictionConfigLoader.load(this)),
                new PluginStartup.Step("strain configuration", () -> StrainConfigLoader.load(getDataFolder())),
                new PluginStartup.Step("bong and mechanics configuration", () -> {
                    BongConfigLoader.load(getDataFolder());
                    MechanicsConfig.load(getDataFolder());
                }),
                new PluginStartup.Step("saved cannabis plants", () -> {
                    CannabisPlantRegistry.init(getDataFolder());
                    cannabisLoaded = true;
                }),
                new PluginStartup.Step("saved bongs", () -> {
                    BongRegistry.init(getDataFolder());
                    bongsLoaded = true;
                }),
                new PluginStartup.Step("saved farms", () -> {
                    farmManager = new FarmManager(getDataFolder());
                    farmManager.load();
                    farmsLoaded = true;
                }),
                new PluginStartup.Step("begin recipe batch", RecipeUpdates::begin),
                new PluginStartup.Step("ingredient crops and recipes", () -> drugGrowing = new DrugGrowing(this)),
                PluginStartup.Step.expand("drug recipes", () -> DrugRegistry.startupSteps(this)),
                new PluginStartup.Step("cure recipes", () -> CureRegistry.init(this)),
                new PluginStartup.Step("production recipes", com.drugs.recipes.ProductionRecipes::register),
                new PluginStartup.Step("achievement definitions", () -> CustomAchievementLoader.load(getDataFolder())),
                new PluginStartup.Step("bong and farm recipes", () -> {
                    registerBongRecipe();
                    registerFarmControllerRecipe();
                }),
                new PluginStartup.Step("publish recipe batch", RecipeUpdates::end),
                new PluginStartup.Step("saved addiction data", () -> {
                    AddictionManager.init(this);
                    addictionLoaded = true;
                }),
                new PluginStartup.Step("gameplay listeners and services", this::startGameplay)
        ), () -> getLogger().info("LumiDrugs is ready!"), exception -> {
            getLogger().log(Level.SEVERE, "Cannot initialize LumiDrugs", exception);
            getServer().getPluginManager().disablePlugin(this);
        });
        startup.start();
        getLogger().info("LumiDrugs initialization scheduled across server ticks.");
    }

    private void startGameplay() {
        PerformanceOptimizer.initialize();
        ToleranceTracker.startDecayTask();
        var manager = getServer().getPluginManager();
        manager.registerEvents(drugGrowing, this);
        manager.registerEvents(new DrugStatusTracker(), this);
        manager.registerEvents(new DrugMenuListener(), this);
        manager.registerEvents(new DrugUseListener(), this);
        manager.registerEvents(new com.drugs.recipes.FlavorInfusionListener(), this);
        manager.registerEvents(new AddictionListener(), this);
        manager.registerEvents(new CannabisPlantListener(drugGrowing), this);
        manager.registerEvents(new StrainCraftingListener(), this);
        manager.registerEvents(new HarvestCraftingListener(), this);
        manager.registerEvents(new StrainsMenuListener(), this);
        manager.registerEvents(new EnforcerGuide(this), this);
        bongListener = new BongListener();
        manager.registerEvents(bongListener, this);
        lsdEffectManager = new LsdEffectManager(this);
        manager.registerEvents(lsdEffectManager, this);
        salviaEffectManager = new SalviaEffectManager(this);
        manager.registerEvents(salviaEffectManager, this);
        sensoryEffectManager = new SensoryEffectManager(this);
        manager.registerEvents(sensoryEffectManager, this);
        manager.registerEvents(new NativeAdvancements(), this);
        farmControllerListener = new FarmControllerListener(farmManager);
        manager.registerEvents(farmControllerListener, this);
        getServer().getScheduler().runTaskTimer(this, new FarmWorkerService(farmManager, drugGrowing),
                MechanicsConfig.getWorkerTickInterval(), MechanicsConfig.getWorkerTickInterval());
        getServer().getScheduler().runTask(this, () -> BongRegistry.respawnMissing(bongListener));

        // Register commands
        CartelDealer cartelDealer = new CartelDealer(this);
        getServer().getPluginManager().registerEvents(cartelDealer, this);
        getCommand("cartel").setExecutor(cartelDealer);
        getCommand("cartel").setTabCompleter(cartelDealer);
        cartelNpcs = CartelNpcs.create(this, cartelDealer);
        cartelDealer.setNpcs(cartelNpcs);
        cartelEnforcer = new CartelEnforcer(this, cartelDealer);
        cartelDealer.setEnforcer(cartelEnforcer);
        getServer().getPluginManager().registerEvents(cartelEnforcer, this);
        cartelEnforcer.start();
        cartelDroppedSales = new CartelDroppedSales(this, cartelDealer, cartelEnforcer);
        getServer().getPluginManager().registerEvents(cartelDroppedSales, this);
        cartelDroppedSales.start();
        getCommand("drugs").setExecutor(new DrugsCommand());
        getCommand("drugs").setTabCompleter(new DrugsTabCompleter());
        getCommand("drugstatus").setExecutor(new DrugStatusCommand());
        getCommand("tolerance").setExecutor(new ToleranceCommand());
        StrainsCommand strainsCommand = new StrainsCommand();
        getCommand("strains").setExecutor(strainsCommand);
        getCommand("strains").setTabCompleter(strainsCommand);
        if (manager.getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new DrugsPlaceholderExpansion(this);
            placeholderExpansion.register();
        }
        bedrockForms = new BedrockForms(this);
        bedrockForms.refresh();
    }

    public void showDialog(org.bukkit.entity.Player player, DialogView.MultiActionDialog dialog) {
        if (bedrockForms != null && bedrockForms.isBedrock(player)) {
            bedrockForms.show(player, dialog);
        } else {
            player.showDialog(DialogView.paper(dialog));
        }
    }

    public void executeUiCommand(org.bukkit.entity.Player player, String command) {
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        player.performCommand(cmd);
    }

    @Override
    public String namespace() {
        String name = getName();
        return name != null ? name.toLowerCase(java.util.Locale.ROOT) : "lumidrugs";
    }

    @Override
    public void onDisable() {
        if (startup != null) startup.cancel();
        // Discard an unfinished startup batch before publishing shutdown removals.
        RecipeUpdates.reset();
        savePhase("removing production recipes", com.drugs.recipes.ProductionRecipes::clear);
        if (cartelDroppedSales != null) savePhase("stopping cartel sales", cartelDroppedSales::shutdown);
        if (cartelEnforcer != null) savePhase("stopping cartel encounters", cartelEnforcer::shutdown);
        if (cartelNpcs != null) savePhase("stopping Citizens integration", cartelNpcs::shutdown);
        savePhase("stopping advancements", NativeAdvancements::shutdown);
        if (placeholderExpansion != null) savePhase("unregistering placeholders", placeholderExpansion::unregister);
        if (lsdEffectManager != null) savePhase("clearing LSD effects", lsdEffectManager::cleanupAll);
        if (salviaEffectManager != null) savePhase("clearing salvia effects", salviaEffectManager::cleanupAll);
        if (sensoryEffectManager != null) savePhase("clearing sensory effects", sensoryEffectManager::cleanupAll);
        // Save only successfully loaded stores; unloading during initialization must
        // not replace saved player data with an empty, partly initialized registry.
        if (cannabisLoaded) savePhase("saving cannabis plants", CannabisPlantRegistry::save);
        if (drugGrowing != null) savePhase("saving ingredient crops", drugGrowing::save);
        if (bongsLoaded) savePhase("saving bongs", BongRegistry::save);
        if (farmsLoaded) savePhase("saving farms", farmManager::save);
        if (addictionLoaded) savePhase("saving addiction data", AddictionManager::shutdown);
        getLogger().info("LumiDrugs disabled.");
    }

    private void savePhase(String name, Runnable action) {
        getLogger().info("Shutdown phase: " + name);
        long started = System.nanoTime();
        try {
            action.run();
        } catch (RuntimeException exception) {
            getLogger().log(Level.SEVERE, "Shutdown failed during " + name + "; continuing cleanup", exception);
        } finally {
            PluginStartup.reportDuration(this, name, started);
        }
    }

    public void saveRecipesConfig() {
        if (recipesFile == null) {
            recipesFile = new File(getDataFolder(), "recipes.yml");
        }
        if (!recipesFile.exists()) {
            saveResource("recipes.yml", false);
        }
        recipesConfig = YamlConfiguration.loadConfiguration(recipesFile);
        if (recipesConfig.getInt("processing-version", 0) < 1) {
            for (String id : java.util.List.of("tobacco", "nicotine", "salvia")) {
                if (recipesConfig.contains(id + ".type")) continue;
                recipesConfig.set(id + ".type", id.equals("tobacco") ? "smoker" : id.equals("nicotine") ? "furnace" : "campfire");
                recipesConfig.set(id + ".input-crop", id.equals("salvia") ? "salvia" : "tobacco");
                recipesConfig.set(id + ".cooking-time", id.equals("tobacco") ? 200 : id.equals("nicotine") ? 400 : 600);
            }
            recipesConfig.set("vape.ingredients.S", "SUGAR");
            recipesConfig.set("processing-version", 1);
            try { recipesConfig.save(recipesFile); }
            catch (java.io.IOException exception) { getLogger().warning("Could not save recipe migration: " + exception.getMessage()); }
        }
        if (recipesConfig.getInt("processing-version", 0) < 2) {
            recipesConfig.set("glue.type", "furnace");
            recipesConfig.set("glue.input-crop", "resin");
            recipesConfig.set("glue.cooking-time", 200);
            for (String id : java.util.List.of("edible", "cbd", "cart", "vape", "cigarette", "cigar")) {
                recipesConfig.set(id + ".type", "production");
                recipesConfig.set(id + ".shape", null);
                recipesConfig.set(id + ".ingredients", null);
            }
            recipesConfig.set("processing-version", 2);
            try { recipesConfig.save(recipesFile); }
            catch (java.io.IOException exception) { getLogger().warning("Could not save production recipe migration: " + exception.getMessage()); }
        }
        if (!recipesConfig.contains("caffeine") || !recipesConfig.contains("energy_drink")) {
            recipesConfig.set("caffeine.type", "furnace");
            recipesConfig.set("caffeine.input-crop", "coffee");
            recipesConfig.set("caffeine.cooking-time", 200);
            recipesConfig.set("caffeine.experience", 0.1);
            recipesConfig.set("energy_drink.type", "production");
            try { recipesConfig.save(recipesFile); }
            catch (java.io.IOException exception) { getLogger().warning("Could not save caffeine recipe migration: " + exception.getMessage()); }
        }

    }

    public void saveToleranceConfig() {
        File file = new File(getDataFolder(), "tolerance.yml");
        if (!file.exists()) {
            saveResource("tolerance.yml", false);
        }
    }
    
    public void saveAchievementSettingsConfig() {
        File file = new File(getDataFolder(), "achievement_settings.yml");
        if (!file.exists()) {
            saveResource("achievement_settings.yml", false);
        }
    }
    
    public void saveAchievementsConfig() {
        File file = new File(getDataFolder(), "achievements.yml");
        if (!file.exists()) {
            saveResource("achievements.yml", false);
        }
    }

    /**
     * Saves the overdose config file
     */
    public void saveOverdoseConfig() {
        File file = new File(getDataFolder(), "overdose.yml");
        if (!file.exists()) {
            saveResource("overdose.yml", false);
        }
    }

    public void saveAddictionConfig() {
        File file = new File(getDataFolder(), "addiction.yml");
        if (!file.exists()) {
            saveResource("addiction.yml", false);
        }
    }

    public void saveStrainsConfig() {
        File file = new File(getDataFolder(), "strains.yml");
        if (!file.exists()) {
            saveResource("strains.yml", false);
        }
    }

    public void saveBongConfig() {
        File file = new File(getDataFolder(), "bong.yml");
        if (!file.exists()) {
            saveResource("bong.yml", false);
        }
    }

    public void saveMechanicsConfig() {
        File file = new File(getDataFolder(), "mechanics.yml");
        if (!file.exists()) {
            saveResource("mechanics.yml", false);
        }
    }

    private void registerBongRecipe() {
        if (recipesConfig == null) return;

        var recipeSection = recipesConfig.getConfigurationSection("bong");
        if (recipeSection == null) return;

        DrugRecipeHelper.registerItemRecipe("bong", recipeSection, BongItemFactory.createBongItem(1), this);
    }



    private void registerFarmControllerRecipe() {
        if (recipesConfig == null) return;

        var recipeSection = recipesConfig.getConfigurationSection("farm-controller");
        if (recipeSection == null) return;

        DrugRecipeHelper.registerItemRecipe("farm_controller", recipeSection, FarmItems.createControllerItem(), this);
    }
    public FileConfiguration getRecipesConfig() {
        return recipesConfig;
    }

    public void reloadGrowing() {
        if (drugGrowing != null) drugGrowing.reloadConfiguration();
    }

    public BongListener getBongListener() {
        return bongListener;
    }

    public LsdEffectManager getLsdEffectManager() {
        return lsdEffectManager;
    }

    public SalviaEffectManager getSalviaEffectManager() {
        return salviaEffectManager;
    }

    public SensoryEffectManager getSensoryEffectManager() {
        return sensoryEffectManager;
    }
}
