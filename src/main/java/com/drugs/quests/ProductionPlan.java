package com.drugs.quests;

import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Acquisition milestones: existing supplies skip earlier steps; tutorials never consume items. */
public final class ProductionPlan {
    public record Step(String label, String type, String recipe, String crop) {
        public boolean matches(ItemStack item) {
            if (item == null || item.getAmount() < 1 || !item.hasItemMeta()) return false;
            return type.equals(DrugItemMetadata.getItemType(item.getItemMeta()));
        }
    }
    private ProductionPlan() {}
    public static List<Step> forProduct(String product) {
        String crop = switch (product) {
            case "vape", "vape_liquid" -> "tobacco";
            case "infused_dough", "cannabis_extract", "cbd" -> "cannabis";
            default -> DrugGrowing.requirement(product);
        };
        List<Step> steps = new ArrayList<>();
        if (crop != null) {
            steps.add(new Step("Prepare planting stock", crop.equals("cannabis") ? "cannabis_plant" : "seed_" + crop,
                    "seed_" + crop, crop));
            steps.add(new Step("Grow and collect the harvest", "harvest_" + crop, null, crop));
        }
        switch (product) {
            case "vape", "vape_liquid" -> add(steps, "Process leaves into Nicotine", "nicotine");
            case "cigarette", "cigar" -> add(steps, "Smoke leaves into Tobacco", "tobacco");
            case "energy_drink" -> add(steps, "Roast beans into Pure Caffeine", "caffeine");
            case "glue" -> add(steps, "Process resin clumps at a cauldron", "processed_resin");
            case "edible" -> add(steps, "Craft Infused Dough", "infused_dough");
            case "cart" -> add(steps, "Brew Cannabis Extract", "cannabis_extract");
            default -> {}
        }
        if (product.equals("vape")) add(steps, "Brew Nicotine Vape Liquid", "vape_liquid");
        add(steps, "Obtain " + name(product), product);
        return List.copyOf(steps);
    }
    private static void add(List<Step> steps, String label, String type) { steps.add(new Step(label, type, type, null)); }
    public static String name(String id) {
        return Arrays.stream(id.replace('_', ' ').split(" ")).map(s -> s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
    public static int advance(List<Step> steps, int completed, ItemStack[] inventory) {
        int next = completed;
        for (int i = completed; i < steps.size(); i++)
            for (ItemStack item : inventory) if (steps.get(i).matches(item)) { next = i + 1; break; }
        return next;
    }
}
