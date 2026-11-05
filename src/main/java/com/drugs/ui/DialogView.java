package com.drugs.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.event.ClickEvent;
import java.util.*;

/** Shared menu description for Paper dialogs and Floodgate forms. */
public final class DialogView {
    public record TextComponent(String toPlainText, Integer xpLevels, Component paperText) {
        public TextComponent(String text) { this(text, null, null); }
        public TextComponent(String text, Integer xpLevels) { this(text, xpLevels, null); }
    }
    public record PlainMessageBody(TextComponent contents, int width) {}
    public record Action(String command, boolean dynamic) {}
    public enum Icon {
        OWNER("item/name_tag"), TRAVEL("item/ender_pearl"), SAVE("item/feather"), RETRIEVE("item/iron_pickaxe"),
        SHARED("item/emerald"), PRIVATE("item/iron_door"), CLOSE("item/barrier"), INFO("item/paper"), DIAMOND("item/diamond"), HOME("item/red_bed"), NATURE("item/oak_sapling"), STAR("item/nether_star"),
        COMPASS("item/compass_16"), MAP("item/map"), SPYGLASS("item/spyglass"),
        ENDER_EYE("item/ender_eye"), ELYTRA("item/elytra"), FIREWORK("item/firework_rocket"),
        IRON("item/iron_ingot"), GOLD("item/gold_ingot"), COPPER("item/copper_ingot"),
        NETHERITE("item/netherite_ingot"), AMETHYST("item/amethyst_shard"), QUARTZ("item/quartz"),
        COAL("item/coal"), REDSTONE("item/redstone"), LAPIS("item/lapis_lazuli"),
        SWORD("item/diamond_sword"), AXE("item/diamond_axe"), BOW("item/bow"),
        TRIDENT("item/trident"), HELMET("item/diamond_helmet"),
        APPLE("item/apple"), GOLDEN_APPLE("item/golden_apple"), BREAD("item/bread"),
        CARROT("item/carrot"), WHEAT("item/wheat"), FISHING_ROD("item/fishing_rod"),
        BUCKET("item/bucket"), WATER_BUCKET("item/water_bucket"), LAVA_BUCKET("item/lava_bucket"),
        BOOK("item/book"), ENCHANTED_BOOK("item/enchanted_book"), BLAZE_ROD("item/blaze_rod"),
        SLIME_BALL("item/slime_ball"), BONE("item/bone"), LEATHER("item/leather"),
        BRICK("item/brick"), HONEY_BOTTLE("item/honey_bottle"), SNOWBALL("item/snowball"),
        SHEARS("item/shears"), TORCH("item/glowstone_dust"), CLOCK("item/clock_00"),
        FURNACE("item/blaze_powder"), BREWING_STAND("item/brewing_stand"),
        CRAFTING_TABLE("item/crafting_table"), SEEDS("item/wheat_seeds"), ARROW("item/arrow");
        final String sprite;
        Icon(String sprite) { this.sprite = sprite; }
        public String sprite() { return sprite; }
    }
    public record ActionButton(TextComponent label, Action action, int width, Icon icon, String lore, UUID owner, String ownerName) {
        public ActionButton(TextComponent label, Action action, int width, Icon icon, String lore) { this(label, action, width, icon, lore, null, null); }
        public ActionButton(TextComponent label, Action action, int width, Icon icon) { this(label, action, width, icon, ""); }
    }
    public record MultiActionDialog(DialogBase getBase, List<ActionButton> actions, int columns, ActionButton exit) {}
    public static final class DialogBase {
        private final TextComponent title;
        private List<PlainMessageBody> body = List.of();
        private List<Input> inputs = List.of();
        private boolean canCloseWithEscape = true;
        public DialogBase(TextComponent title) { this.title = title; }
        public DialogBase body(List<PlainMessageBody> value) { body = List.copyOf(value); return this; }
        public DialogBase inputs(List<Input> value) { inputs = List.copyOf(value); return this; }
        public DialogBase canCloseWithEscape(boolean value) { canCloseWithEscape = value; return this; }
        public boolean canCloseWithEscape() { return canCloseWithEscape; }
        public TextComponent title() { return title; }
        public List<PlainMessageBody> body() { return body; }
        public List<Input> inputs() { return inputs; }
    }
    public interface Input { String key(); }
    public static final class TextInput implements Input {
        private final String key; private final TextComponent label;
        private String initial = ""; private int maxLength = 32;
        public TextInput(String key, TextComponent label) { this.key = key; this.label = label; }
        public String key() { return key; } public TextComponent label() { return label; }
        public String initial() { return initial; } public int maxLength() { return maxLength; }
        public TextInput initial(String value) { initial = value; return this; }
        public TextInput maxLength(int value) { maxLength = value; return this; }
    }
    public static final class NumberRangeInput implements Input {
        private final String key; private final TextComponent label; private final float start, end;
        private Float initial, step;
        public NumberRangeInput(String key, TextComponent label, float start, float end) { this.key = key; this.label = label; this.start = start; this.end = end; }
        public String key() { return key; } public TextComponent label() { return label; }
        public float start() { return start; } public float end() { return end; } public Float initial() { return initial; } public Float step() { return step; }
        public NumberRangeInput initial(Float value) { initial = value; return this; }
        public NumberRangeInput step(Float value) { step = value; return this; }
    }
    public static final class BooleanInput implements Input {
        private final String key; private final TextComponent label; private boolean initial;
        public BooleanInput(String key, TextComponent label) { this.key = key; this.label = label; }
        public String key() { return key; } public TextComponent label() { return label; } public boolean initial() { return initial; }
        public BooleanInput initial(boolean value) { initial = value; return this; }
    }
    public static Dialog paper(MultiActionDialog view) {
        return Dialog.create(factory -> factory.empty().base(paperBase(view)).type(DialogType.multiAction(
                view.actions().stream().map(DialogView::button).toList(), button(view.exit()), view.columns())));
    }
    public static io.papermc.paper.registry.data.dialog.DialogBase paperBase(MultiActionDialog view) {
        return io.papermc.paper.registry.data.dialog.DialogBase.builder(text(view.getBase().title()))
                .pause(false).canCloseWithEscape(view.getBase().canCloseWithEscape()).body(view.getBase().body().stream().map(body -> DialogBody.plainMessage(text(body.contents()), body.width())).toList())
                .inputs(view.getBase().inputs().stream().map(DialogView::input).toList()).build();
    }
    public static Component text(TextComponent value) {
        return value.paperText() == null ? Component.text(value.toPlainText()) : value.paperText();
    }

    public static Component sprite(Icon icon) {
        return Component.object(ObjectContents.sprite(spriteAtlas(), Key.key("minecraft:" + icon.sprite())));
    }

    private static DialogInput input(Input value) {
        if (value instanceof TextInput text) return DialogInput.text(text.key(), Component.text(text.label().toPlainText())).initial(text.initial()).maxLength(text.maxLength()).build();
        if (value instanceof NumberRangeInput number) return DialogInput.numberRange(number.key(), Component.text(number.label().toPlainText()), number.start(), number.end()).initial(number.initial()).step(number.step()).build();
        if (value instanceof BooleanInput bool) return DialogInput.bool(bool.key(), Component.text(bool.label().toPlainText()), bool.initial(), "true", "false");
        throw new IllegalArgumentException("Unsupported dialog input");
    }
    public static Key spriteAtlas() {
        String version = org.bukkit.Bukkit.getServer() == null ? "1.21.10" : org.bukkit.Bukkit.getMinecraftVersion();
        return spriteAtlas(version);
    }
    public static Key spriteAtlas(String version) {
        String[] parts = version.split("\\.");
        boolean separate = Integer.parseInt(parts[0]) > 1 || parts.length > 2 && Integer.parseInt(parts[1]) == 21 && Integer.parseInt(parts[2]) >= 11
                || Integer.parseInt(parts[1]) > 21;
        return Key.key(separate ? "minecraft:items" : "minecraft:blocks");
    }
    public static Component buttonLabel(ActionButton value) {
        Component text = value.label().paperText() == null ? Component.text(value.label().toPlainText()) : value.label().paperText();
        if (value.label().xpLevels() != null && value.label().xpLevels() > 0) {
            text = text.append(Component.object(ObjectContents.sprite(spriteAtlas(), Key.key("minecraft:item/experience_bottle"))))
                    .append(Component.text(" " + value.label().xpLevels()));
        }
        if (value.owner() != null) {
            text = text.append(Component.text(" · ")).append(Component.object(ObjectContents.playerHead(value.owner()))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Owner: " + value.ownerName()))));
        }
        if (value.icon() == null) return text;
        Component icon = Component.object(ObjectContents.sprite(spriteAtlas(), Key.key("minecraft:" + value.icon().sprite)));
        if (value.label().toPlainText().isEmpty() && value.label().paperText() == null) return icon;
        return icon.append(Component.text(" ")).append(text);
    }
    public static io.papermc.paper.registry.data.dialog.ActionButton button(ActionButton value) {
        var action = value.action().dynamic() ? DialogAction.commandTemplate(value.action().command()) : DialogAction.staticAction(ClickEvent.runCommand(value.action().command()));
        return io.papermc.paper.registry.data.dialog.ActionButton.create(buttonLabel(value), Component.text(value.lore()), value.width(), action);
    }
}

