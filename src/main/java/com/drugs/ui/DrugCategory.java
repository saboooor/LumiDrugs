package com.drugs.ui;

import org.bukkit.Material;

/** Shared grouping for products, production ingredients and recipe navigation. */
public enum DrugCategory {
    CANNABIS("Cannabis", Material.FERN),
    NICOTINE("Nicotine", Material.DRIED_KELP),
    CAFFEINE("Caffeine", Material.COCOA_BEANS),
    OPIOIDS("Opioids", Material.POPPY),
    COCAINE("Cocaine", Material.SUGAR),
    METH("Meth", Material.AMETHYST_SHARD),
    MDMA("MDMA", Material.PINK_DYE),
    MUSHROOMS("Mushrooms", Material.RED_MUSHROOM),
    LSD("LSD", Material.PAPER),
    SALVIA("Salvia", Material.SHORT_GRASS),
    INHALANTS("Inhalants", Material.SLIME_BALL),
    CURES("Cures", Material.HONEY_BOTTLE),
    EQUIPMENT("Equipment", Material.BREWING_STAND),
    OTHER("Other", Material.CHEST);

    private final String title;
    private final Material icon;
    DrugCategory(String title, Material icon) { this.title = title; this.icon = icon; }
    public String title() { return title; }
    public Material icon() { return icon; }

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
