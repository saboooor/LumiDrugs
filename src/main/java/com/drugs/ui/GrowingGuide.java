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

    private record StepStyle(String label, DialogView.Icon icon, NamedTextColor color) {}

    private static StepStyle stepStyle(String instruction) {
        String text = instruction.toLowerCase(java.util.Locale.ROOT);
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

    public static DialogView.MultiActionDialog createDialog(String crop, String recipe) {
        if (crop == null) {
            var actions = new ArrayList<DialogView.ActionButton>();
            for (String id : DrugGrowing.cropIds()) {
                DrugGrowing.Crop definition = DrugGrowing.crop(id);
                actions.add(Dialogs.button(definition.name(), "/drugs growing " + id, false,
                        DialogView.Icon.SEEDS, "Planting, growing conditions, harvests, and replanting."));
            }
            return Dialogs.menu(formattedBase("How to grow ingredients", List.of(
                    "Choose a crop for planting and harvesting instructions.",
                    "Click harvested ingredients in a crafting recipe to open their growing guide.",
                    "Recipes need custom harvested ingredients, not ordinary materials or planting stock.")), actions);
        }
        List<String> steps = EnforcerGuide.growingSteps(crop);
        var actions = new ArrayList<DialogView.ActionButton>();
        if (recipe != null) actions.add(Dialogs.button("Back to recipe", "/drugs recipe " + recipe, false, DialogView.Icon.CRAFTING_TABLE));
        actions.add(Dialogs.button("All crops", "/drugs growing", false, DialogView.Icon.SEEDS));
        var definition = DrugGrowing.crop(crop);
        String name = definition == null ? crop : definition.name();
        return Dialogs.menu(formattedBase("How to grow: " + name, steps), actions);
    }
}
