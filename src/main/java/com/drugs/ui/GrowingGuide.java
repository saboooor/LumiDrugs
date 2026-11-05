package com.drugs.ui;

import com.drugs.DrugsV2;
import com.drugs.cartel.EnforcerGuide;
import com.drugs.growing.DrugGrowing;
import com.drugs.items.DrugItemMetadata;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.ArrayList;
import java.util.List;

/** Player growing index and crop-specific dialogs, shared by recipes and commands. */
public final class GrowingGuide {
    private GrowingGuide() {}

    public static String cropFromIngredient(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String type = DrugItemMetadata.getItemType(item.getItemMeta());
        if (type == null || !type.startsWith("harvest_")) return null;
        String crop = type.substring("harvest_".length());
        return DrugGrowing.cropIds().contains(crop) ? crop : null;
    }

    public static void open(Player player, String crop, String recipe) {
        DrugsV2.getInstance().showDialog(player, createDialog(crop, recipe));
    }

    public static void openPurity(Player player) {
        DrugsV2.getInstance().showDialog(player, createPurityDialog());
    }

    private record StepStyle(String label, DialogView.Icon icon, NamedTextColor color) {}

    private static StepStyle stepStyle(String instruction) {
        String text = instruction.toLowerCase(java.util.Locale.ROOT);
        if (text.startsWith("purity") || text.startsWith("maximize purity") || text.startsWith("refine"))
            return new StepStyle("Purity & Refining", DialogView.Icon.BREWING_STAND, NamedTextColor.AQUA);
        if (text.startsWith("bone meal") || text.startsWith("if harvesting"))
            return new StepStyle("Remember", DialogView.Icon.INFO, NamedTextColor.YELLOW);
        if (text.startsWith("wait")) return new StepStyle("Growth time", DialogView.Icon.CLOCK, NamedTextColor.AQUA);
        if (text.startsWith("replant")) return new StepStyle("Replant", DialogView.Icon.SEEDS, NamedTextColor.GREEN);
        if (text.startsWith("break")) return new StepStyle("Harvest", DialogView.Icon.SHEARS, NamedTextColor.GOLD);
        if (text.startsWith("collect") || text.startsWith("mature"))
            return new StepStyle("Your harvest", DialogView.Icon.WHEAT, NamedTextColor.GOLD);
        if (text.startsWith("keep") || text.startsWith("leave"))
            return new StepStyle("Growing conditions", DialogView.Icon.TORCH, NamedTextColor.AQUA);
        if (text.startsWith("plant") || text.startsWith("right-click"))
            return new StepStyle("Planting", DialogView.Icon.WATER_BUCKET, NamedTextColor.GREEN);
        if (text.startsWith("brew")) return new StepStyle("Brewing", DialogView.Icon.BREWING_STAND, NamedTextColor.LIGHT_PURPLE);
        if (text.startsWith("process") || text.startsWith("roast") || text.startsWith("smoke") || text.startsWith("bake"))
            return new StepStyle("Processing", DialogView.Icon.FURNACE, NamedTextColor.LIGHT_PURPLE);
        if (text.startsWith("craft") || text.startsWith("assemble"))
            return new StepStyle("Crafting", DialogView.Icon.BOOK, NamedTextColor.GREEN);
        return new StepStyle("Guide", DialogView.Icon.BOOK, NamedTextColor.GREEN);
    }

    /** Rich Paper text with a readable plain-text fallback for Bedrock forms. */
    public static DialogView.DialogBase formattedBase(String title, List<String> steps) {
        Component rich = Component.empty();
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            var style = stepStyle(steps.get(i));
            String heading = (i + 1) + ". " + style.label();
            if (i > 0) rich = rich.append(Component.text("\n\n"));
            rich = rich.append(DialogView.sprite(style.icon()))
                    .append(Component.text(" " + heading, style.color(), TextDecoration.BOLD))
                    .append(Component.text("\n" + steps.get(i), NamedTextColor.WHITE));
            plain.append(heading).append("\n").append(steps.get(i)).append("\n\n");
        }
        Component richTitle = DialogView.sprite(DialogView.Icon.SEEDS)
                .append(Component.text(" " + title, NamedTextColor.GREEN, TextDecoration.BOLD));
        return new DialogView.DialogBase(new DialogView.TextComponent(title, null, richTitle))
                .body(List.of(new DialogView.PlainMessageBody(
                        new DialogView.TextComponent(plain.toString(), null, rich), 350)));
    }

    public static DialogView.MultiActionDialog createPurityDialog() {
        String refiningItem = com.drugs.config.MechanicsConfig.getPurityRefiningIngredient()
                .name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        int points = com.drugs.config.MechanicsConfig.getPurityRefiningPoints();
        List<String> steps = List.of(
                "Purity (65%–100%) scales drug effect duration and cartel sale value (90% is baseline).",
                "Plant on fully hydrated farmland (moisture 7/7, water within 4 blocks) for +5% purity (+2% at moisture 4–6).",
                "Keep light level at 14–15 for +10% purity (+5% at light level 12–13).",
                "Keep an overhead grow lamp (Sea Lantern, Glowstone, Shroomlight, Froglight, Lantern, Redstone Lamp, End Rod, or Beacon) 1–4 blocks above the crop for +5% purity.",
                "Place a Composter, Moss Block, Mud, or Podzol within 2 blocks horizontally for +5% purity, and leave at least 2 cardinal sides open to avoid overcrowding (-5%).",
                "Right-click any planted crop with an empty hand to see its live Purity % and tips on what condition to improve next.",
                "Refine finished drugs at a Cauldron: hold 1 drug in your main hand and " + refiningItem + " in your offhand, then sneak-right-click a Cauldron for +" + points + "% purity per step (up to 100%).");
        var actions = List.of(
                Dialogs.button("All crops", "/drugs growing", false, DialogView.Icon.SEEDS));
        return Dialogs.menu(formattedBase("How to increase purity", steps), actions);
    }

    public static DialogView.MultiActionDialog createDialog(String crop, String recipe) {
        if ("purity".equalsIgnoreCase(crop)) {
            return createPurityDialog();
        }
        if (crop == null) {
            var actions = new ArrayList<DialogView.ActionButton>();
            for (String id : DrugGrowing.cropIds()) {
                DrugGrowing.Crop definition = DrugGrowing.crop(id);
                actions.add(Dialogs.button(definition.name(), "/drugs growing " + id, false,
                        DialogView.Icon.SEEDS, "Planting, growing conditions, harvests, and replanting."));
            }
            actions.add(Dialogs.button("How to increase purity", "/drugs purity", false,
                    DialogView.Icon.BREWING_STAND, "Learn how growing conditions and cauldron refining increase purity."));
            return Dialogs.menu(formattedBase("How to grow ingredients", List.of(
                    "Choose a crop for planting and harvesting instructions.",
                    "Click harvested ingredients in a crafting recipe to open their growing guide.",
                    "Recipes need custom harvested ingredients, not ordinary materials or planting stock.",
                    "Purity depends on soil hydration, light level, overhead grow lamps, nearby Composter/Moss, plant spacing, and cauldron refining.")), actions);
        }
        List<String> steps = EnforcerGuide.growingSteps(crop);
        var actions = new ArrayList<DialogView.ActionButton>();
        if (recipe != null) actions.add(Dialogs.button("Back to recipe", "/drugs recipe " + recipe, false, DialogView.Icon.CRAFTING_TABLE));
        actions.add(Dialogs.button("All crops", "/drugs growing", false, DialogView.Icon.SEEDS));
        actions.add(Dialogs.button("How to increase purity", "/drugs purity", false, DialogView.Icon.BREWING_STAND));
        var definition = DrugGrowing.crop(crop);
        String name = definition == null ? crop : definition.name();
        return Dialogs.menu(formattedBase("How to grow: " + name, steps), actions);
    }
}
