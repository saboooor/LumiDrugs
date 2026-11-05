package com.drugs.cartel;

import com.drugs.DrugsV2;
import com.drugs.config.MechanicsConfig;
import com.drugs.growing.DrugGrowing;
import com.drugs.ui.DialogView;
import com.drugs.ui.Dialogs;
import com.drugs.ui.GrowingGuide;

import com.drugs.ui.DialogView.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

/** Enforcer dialogue actions and guides; recipes use a read-only inventory preview. */
public final class EnforcerGuide implements Listener {
    public enum Page { DIALOGUE, RECIPE, GROWING }

    static final class RecipeMenu implements InventoryHolder {
        final String drug;
        final Inventory inventory;

        RecipeMenu(String drug) {
            this.drug = drug;
            this.inventory = Bukkit.createInventory(this, 54, "Crafting: " + drug);
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DrugsV2 plugin;
    public EnforcerGuide(DrugsV2 plugin) { this.plugin = plugin; }

    public static void open(DrugsV2 plugin, Player player, String drug) {
        openPage(plugin, player, drug, Page.DIALOGUE);
    }

    public static void openPage(DrugsV2 plugin, Player player, String drug, Page page) {
        if (page == Page.RECIPE) {
            openRecipeInventory(plugin, player, drug);
        } else {
            MultiActionDialog dialog = createDialog(drug, page);
            plugin.showDialog(player, dialog);
        }
    }

    public static MultiActionDialog createDialog(String drug, Page page) {
        return switch (page) {
            case DIALOGUE -> dialogueDialog(drug);
            case GROWING -> growingDialog(drug);
            case RECIPE -> throw new IllegalArgumentException("Recipe page uses an inventory GUI");
        };
    }

    private static MultiActionDialog dialogueDialog(String drug) {
        String message = "Enforcer: I need " + drug + ".\n"
                + "Throw the goods near me to deliver them.\n"
                + "Need help? Choose a guide below.\n"
                + "Your delivery deadline keeps running. Rejecting counts as a refusal.";
        DialogBase base = Dialogs.base("Cartel enforcer", message);
        List<ActionButton> actions = List.of(
                Dialogs.button("Crafting recipe", "/drugs guide " + drug + " recipe", false, Icon.CRAFTING_TABLE, "View the crafting recipe for " + drug + "."),
                Dialogs.button("How to grow", "/drugs guide " + drug + " growing", false, Icon.SEEDS, "Learn how to grow the required ingredient."),
                Dialogs.button("Learn to make this", "/drugs learn " + drug, false, Icon.BOOK,
                        "Start a learning quest without a deadline. The enforcer's delivery timer keeps running separately."),
                Dialogs.button("Production journal", "/drugs journal", false, Icon.BOOK),
                Dialogs.button("Reject demand", "/cartel reject", false, Icon.CLOSE,
                        "End this encounter now. Counts as a refusal, with the same consequences as letting time run out.")
        );
        return Dialogs.menu(base, actions, Dialogs.button("Close", "/drugs guide close", false, Icon.CLOSE, "Close this guide."));
    }

    private static MultiActionDialog growingDialog(String drug) {
        String crop = switch (drug) {
            case "vape", "vape_liquid" -> "tobacco";
            case "infused_dough", "cannabis_extract" -> "cannabis";
            default -> DrugGrowing.requirement(drug);
        };
        List<String> instructions = new java.util.ArrayList<>(growingSteps(crop));
        switch (drug) {
            case "vape" -> {
                instructions.add("Process harvested tobacco leaves in a furnace for 20 seconds to make nicotine.");
                instructions.add("Brew a water bottle with nicotine to make vape liquid, then assemble the vape with that liquid.");
            }
            case "vape_liquid" -> {
                instructions.add("Process harvested tobacco leaves in a furnace for 20 seconds to make nicotine.");
                instructions.add("Brew a water bottle with nicotine in a brewing stand to make vape liquid.");
            }
            case "edible" -> {
                instructions.add("Craft Infused Dough (shapeless: wheat + sugar + harvested cannabis buds).");
                instructions.add("Bake the Infused Dough in a smoker for 10 seconds to make an Edible.");
            }
            case "infused_dough" -> instructions.add("Craft Infused Dough shapelessly using wheat, sugar, and harvested cannabis buds.");
            case "cart" -> {
                instructions.add("Brew an awkward potion with harvested cannabis buds to make Cannabis Extract.");
                instructions.add("Assemble the Cart in a crafting table using glass, redstone, stone button, copper ingot, and Cannabis Extract.");
            }
            case "cannabis_extract" -> instructions.add("Brew an awkward potion with harvested cannabis buds in a brewing stand to make Cannabis Extract.");
            case "cbd" -> instructions.add("Brew a water bottle with harvested cannabis buds in a brewing stand to make CBD.");
            case "cigarette" -> {
                instructions.add("Smoke harvested tobacco leaves in a smoker for 10 seconds to make Tobacco.");
                instructions.add("Craft a Cigarette using paper and processed Tobacco.");
            }
            case "cigar" -> {
                instructions.add("Smoke harvested tobacco leaves in a smoker for 10 seconds to make Tobacco.");
                instructions.add("Craft a Cigar using 3 processed Tobacco and 3 leaf litter.");
            }
            case "caffeine" -> instructions.add("Roast harvested Coffee Beans in a furnace for 10 seconds to refine Pure Caffeine.");
            case "energy_drink" -> {
                instructions.add("Roast harvested Coffee Beans in a furnace for 10 seconds to refine Pure Caffeine.");
                instructions.add("Brew an awkward potion with Pure Caffeine in a brewing stand to make an Energy Drink.");
            }
            default -> {}
        }
        DialogBase base = GrowingGuide.formattedBase("Growing ingredients", instructions);
        List<ActionButton> actions = List.of(
                Dialogs.button("Crafting recipe", "/drugs guide " + drug + " recipe", false, Icon.CRAFTING_TABLE, "View the crafting recipe for " + drug + "."),
                Dialogs.button("Back to enforcer", "/drugs guide " + drug + " dialogue", false, Icon.ARROW, "Return to cartel enforcer instructions.")
        );
        return Dialogs.menu(base, actions, Dialogs.button("Close", "/drugs guide close", false, Icon.CLOSE, "Close this guide."));
    }

    private static void openRecipeInventory(DrugsV2 plugin, Player player, String drug) {
        RecipeMenu menu = new RecipeMenu(drug);
        Inventory inventory = menu.inventory;
        Recipe recipe = Bukkit.getRecipe(new NamespacedKey(plugin, drug));
        var brewing = com.drugs.recipes.ProductionRecipes.brewingPreview(drug);
        if (brewing != null) {
            inventory.setItem(11, com.drugs.ui.DrugRecipeViewer.decorateIngredient(brewing.base()));
            inventory.setItem(12, com.drugs.ui.DrugRecipeViewer.decorateIngredient(brewing.ingredient()));
            inventory.setItem(24, brewing.result());
            inventory.setItem(16, icon(Material.BREWING_STAND, "§eBrew ingredient", "§7Base below, ingredient above; fuel with blaze powder.", "§7Brewing time: 20 seconds."));
        } else if (recipe instanceof ShapedRecipe shaped) {
            String crop = DrugGrowing.requirement(drug);
            String[] shape = shaped.getShape();
            Map<Character, RecipeChoice> ingredients = shaped.getChoiceMap();
            for (int row = 0; row < shape.length; row++) {
                for (int column = 0; column < shape[row].length(); column++) {
                    RecipeChoice choice = ingredients.get(shape[row].charAt(column));
                    ItemStack ingredient = com.drugs.recipes.ProductionRecipes.previewIngredient(drug, choice);
                    if (choice instanceof RecipeChoice.MaterialChoice && ingredient != null && crop != null && DrugGrowing.material(crop) == ingredient.getType()) {
                        ingredient = DrugGrowing.harvest(crop, null);
                    }
                    inventory.setItem(10 + row * 9 + column, com.drugs.ui.DrugRecipeViewer.decorateIngredient(ingredient));
                }
            }
            inventory.setItem(24, shaped.getResult().clone());
            inventory.setItem(16, icon(Material.CRAFTING_TABLE, "§eRecipe preview", "§7Copy the grid into a crafting table."));
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            int slot = 10;
            for (RecipeChoice choice : shapeless.getChoiceList()) {
                inventory.setItem(slot++, com.drugs.ui.DrugRecipeViewer.decorateIngredient(choice.getItemStack()));
            }
            inventory.setItem(24, shapeless.getResult().clone());
            inventory.setItem(16, icon(Material.CRAFTING_TABLE, "§eShapeless recipe", "§7Place ingredients anywhere in a crafting grid."));
        } else if (recipe instanceof CookingRecipe<?> cooking) {
            inventory.setItem(11, com.drugs.ui.DrugRecipeViewer.decorateIngredient(cooking.getInputChoice().getItemStack()));
            inventory.setItem(24, cooking.getResult());
            Material station = cooking instanceof SmokingRecipe ? Material.SMOKER
                    : cooking instanceof BlastingRecipe ? Material.BLAST_FURNACE
                    : cooking instanceof CampfireRecipe ? Material.CAMPFIRE : Material.FURNACE;
            inventory.setItem(16, icon(station, "§eProcess ingredient", "§7Time: " + cooking.getCookingTime() / 20.0 + " seconds."));
        } else if (recipe instanceof StonecuttingRecipe cutting) {
            inventory.setItem(11, com.drugs.ui.DrugRecipeViewer.decorateIngredient(cutting.getInputChoice().getItemStack()));
            inventory.setItem(24, cutting.getResult());
            inventory.setItem(16, icon(Material.STONECUTTER, "§eCut ingredient"));
        } else {
            inventory.setItem(22, icon(Material.BARRIER, "§cNo recipe available"));
        }
        inventory.setItem(45, icon(Material.ARROW, "§eBack to enforcer"));
        inventory.setItem(49, icon(Material.WHEAT_SEEDS, "§aHow to grow the ingredient"));
        inventory.setItem(53, icon(Material.BARRIER, "§cClose"));
        player.openInventory(inventory);
    }

    public static List<String> growingSteps(String crop) {
        if (crop == null) return List.of("This recipe does not require a grown ingredient.");
        if (crop.equals("cannabis")) return List.of(
                "Craft a cannabis plant: 1 wheat seeds + 1 fern (shapeless).",
                "Plant the cannabis plant on hydrated farmland. Keep it lit (light level 9+).",
                "Leave air above it and keep the chunk loaded.",
                "Wait for it to become a large fern (" + DrugGrowing.growthSeconds("cannabis") + "s of active growth). Right-click empty-handed to check progress.",
                "Bone meal cannot speed up cannabis growth.",
                "Break the mature large fern using shears.",
                "Collect 2 Cannabis Buds and a cannabis plant. Mature plants have a 10% chance of an extra plant.",
                "Replant the cannabis plant; its strain may mutate. Use buds in recipes.",
                "Maximize purity (up to 100%) with full water hydration (7/7), bright light (14–15), an overhead grow lamp, a nearby Composter or Moss Block, and open spacing — or refine finished drugs at a cauldron with Quartz.",
                "If harvesting early, use shears to recover planting stock. Without shears, no buds or planting stock are returned.");
        DrugGrowing.Crop definition = DrugGrowing.crop(crop);
        if (definition == null) return List.of("Growing information is currently unavailable.");
        String starter = definition.starter().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return List.of(
                "Craft seeds: wheat seeds + " + starter + " (shapeless).",
                "Right-click the top of hydrated farmland with those seeds.",
                "Keep water nearby, light at least 9, and the chunk loaded.",
                "Wait " + definition.seconds() + " seconds of active growth. Right-click empty-handed to check progress.",
                "Bone meal cannot speed up this crop.",
                "Break the mature crop in Survival to get 2 " + definition.name() + " and a seed.",
                "Mature crops have a 10% chance of one extra seed. Breaking early returns only the seed.",
                "Replant the seed. Use the custom harvested " + definition.name() + ", not ordinary materials or seeds, in the recipe.",
                "Maximize purity (up to 100%) with full water hydration (7/7), bright light (14–15), an overhead grow lamp, a nearby Composter or Moss Block, and open spacing — or refine finished drugs at a cauldron with Quartz.");
    }

    private static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RecipeMenu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0
                || event.getRawSlot() >= menu.inventory.getSize()) return;
        int slot = event.getRawSlot();
        if (slot == 53) {
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            return;
        }
        if (slot == 45) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                openPage(plugin, player, menu.drug, Page.DIALOGUE);
            });
            return;
        }
        if (slot >= 10 && slot <= 30 && slot % 9 >= 1 && slot % 9 <= 3) {
            String ingredientRecipe = com.drugs.recipes.ProductionRecipes.ingredientRecipe(event.getCurrentItem());
            if (ingredientRecipe != null) {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> com.drugs.ui.DrugRecipeViewer.openRecipe(player, ingredientRecipe));
                return;
            }
        }
        if (slot == 49 || (slot >= 10 && slot <= 30 && slot % 9 >= 1 && slot % 9 <= 3
                && GrowingGuide.cropFromIngredient(event.getCurrentItem()) != null)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                openPage(plugin, player, menu.drug, Page.GROWING);
            });
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RecipeMenu) {
            event.setCancelled(true);
        }
    }
}
