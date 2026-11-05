package com.drugs.achievements;

import org.bukkit.Material;

/**
 * Represents a customizable achievement that can be loaded from config
 */
public class CustomAchievement {
    
    private final String id;
    private final String defaultTitle;
    private final String defaultDescription;
    private final String trigger;
    private final Material icon;
    private final Material completedIcon;
    private final String drugId;
    private final String cropId;
    private final int count;
    
    /**
     * Creates a new custom achievement
     * 
     * @param id The unique identifier for this achievement
     * @param defaultTitle The default title (can be overridden in settings)
     * @param defaultDescription The default description (can be overridden in settings)
     * @param trigger The trigger type that grants this achievement
     * @param icon The icon to display in the GUI when locked
     * @param completedIcon The icon to display when unlocked
     */
    public CustomAchievement(String id, String defaultTitle, String defaultDescription, 
                            String trigger, Material icon, Material completedIcon) {
        this(id, defaultTitle, defaultDescription, trigger, icon, completedIcon, null, null, 0);
    }

    public CustomAchievement(String id, String defaultTitle, String defaultDescription,
                            String trigger, Material icon, Material completedIcon,
                            String drugId, String cropId, int count) {
        this.id = id;
        this.defaultTitle = defaultTitle;
        this.defaultDescription = defaultDescription;
        this.trigger = trigger;
        this.icon = icon;
        this.completedIcon = completedIcon;
        this.drugId = drugId;
        this.cropId = cropId;
        this.count = count;
    }
    
    /**
     * Gets the unique ID of this achievement
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets the title from settings, or default if not configured
     */
    public String getTitle() {
        return AchievementSettingsLoader.getAchievementTitle(id, defaultTitle);
    }
    
    /**
     * Gets the description from settings, or default if not configured
     */
    public String getDescription() {
        return AchievementSettingsLoader.getAchievementDescription(id, defaultDescription);
    }
    
    /**
     * Gets the trigger type for this achievement
     */
    public String getTrigger() {
        return trigger;
    }
    
    /**
     * Gets the icon to display in GUI when locked
     */
    public Material getIcon() {
        return icon;
    }
    
    /**
     * Gets the icon to display in GUI when unlocked
     */
    public Material getCompletedIcon() {
        return completedIcon;
    }

    public String getDrugId() {
        return drugId;
    }

    public String getCropId() {
        return cropId;
    }

    public int getCount() {
        return count;
    }
    
    /**
     * Checks if this achievement is enabled in settings
     */
    public boolean isEnabled() {
        return AchievementSettingsLoader.isAchievementEnabled(id);
    }
} 