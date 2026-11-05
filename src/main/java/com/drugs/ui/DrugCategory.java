package com.drugs.ui;

import org.bukkit.Material;

/** Shared grouping for products, production ingredients and recipe navigation. */
public enum DrugCategory {
    CANNABIS("Cannabis", Material.FERN, "cannabis_plant"),
    NICOTINE("Nicotine", Material.DRIED_KELP, "vape"),
    CAFFEINE("Caffeine", Material.COCOA_BEANS, "caffeine"),
    OPIOIDS("Opioids", Material.POPPY, "heroin"),
    COCAINE("Cocaine", Material.SUGAR, "cocaine"),
    METH("Meth", Material.AMETHYST_SHARD, "meth"),
    MDMA("MDMA", Material.PINK_DYE, "molly"),
    MUSHROOMS("Mushrooms", Material.RED_MUSHROOM, "shrooms"),
    LSD("LSD", Material.PAPER, "lsd"),
    SALVIA("Salvia", Material.SHORT_GRASS, "salvia"),
    INHALANTS("Inhalants", Material.SLIME_BALL, "glue"),
    CURES("Cures", Material.HONEY_BOTTLE, "suboxone"),
    EQUIPMENT("Equipment", Material.BREWING_STAND, "farm_controller"),
    OTHER("Other", Material.CHEST, "farm_work_contract");

    private final String title;
    private final Material icon;
    private final String model;
    DrugCategory(String title, Material icon, String model) { this.title = title; this.icon = icon; this.model = model; }
    public String title() { return title; }
    public Material icon() { return icon; }
    public String model() { return model; }

    public static DrugCategory forItem(String id) {
        id = id.replaceFirst("_(flavor|strain)_.*$", "");
        return switch (id) {
            case "joint", "blunt", "cart", "edible", "cbd", "infused_dough", "cannabis_extract", "bong" -> CANNABIS;
            case "tobacco", "nicotine", "cigarette", "cigar", "chewing_tobacco", "vape", "vape_liquid" -> NICOTINE;
            case "caffeine", "energy_drink" -> CAFFEINE;
            case "heroin", "fent" -> OPIOIDS;
            case "cocaine" -> COCAINE;
            case "meth" -> METH;
            case "molly" -> MDMA;
            case "shrooms" -> MUSHROOMS;
            case "lsd" -> LSD;
            case "salvia" -> SALVIA;
            case "glue" -> INHALANTS;
            case "farm-controller", "farm_controller" -> EQUIPMENT;
            default -> OTHER;
        };
    }

    static DrugCategory fromTitle(String title) {
        for (DrugCategory category : values())
            if (title.startsWith("Drugs Menu: " + category.title() + " (Page ")) return category;
        return null;
    }
}
