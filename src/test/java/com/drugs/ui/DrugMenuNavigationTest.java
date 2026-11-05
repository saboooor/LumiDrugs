package com.drugs.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class DrugMenuNavigationTest {
    private InventoryClickEvent click(String title, int slot, String name) {
        var event = mock(InventoryClickEvent.class);
        var inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(36);
        when(event.getInventory()).thenReturn(inventory);
        var view = mock(InventoryView.class);
        when(view.getTitle()).thenReturn(title);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(mock(Player.class));
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(Material.ARROW);
        var meta = mock(ItemMeta.class);
        when(meta.getDisplayName()).thenReturn(name);
        when(item.getItemMeta()).thenReturn(meta);
        when(event.getCurrentItem()).thenReturn(item);
        when(event.getRawSlot()).thenReturn(slot);
        return event;
    }

    @Test void categoryButtonsOpenTheirProductsAndBackReturnsToIndex() {
        try (var menus = mockStatic(DrugMenuGUI.class)) {
            var listener = new DrugMenuListener();
            var root = click("Drugs Menu", DrugCategory.NICOTINE.ordinal(), "Nicotine");
            listener.onMenuClick(root);
            verify(root).setCancelled(true);
            menus.verify(() -> DrugMenuGUI.openCategory((Player) root.getWhoClicked(), DrugCategory.NICOTINE, 0));
            var next = click("Drugs Menu: Nicotine (Page 1)", 35, "Next Page");
            listener.onMenuClick(next);
            menus.verify(() -> DrugMenuGUI.openCategory((Player) next.getWhoClicked(), DrugCategory.NICOTINE, 1));
            var back = click("Drugs Menu: Nicotine (Page 2)", 31, "Back to Categories");
            listener.onMenuClick(back);
            menus.verify(() -> DrugMenuGUI.open((Player) back.getWhoClicked(), 0));
            var flavors = click("Flavors: vape (Page 1)", 31, "Back to Nicotine");
            listener.onMenuClick(flavors);
            menus.verify(() -> DrugMenuGUI.openRecipeCategory((Player) flavors.getWhoClicked(), "vape"));
        }
    }

    @Test void bottomInventoryClicksCannotOpenCategories() {
        try (var menus = mockStatic(DrugMenuGUI.class)) {
            var event = click("Drugs Menu", 37, "Nicotine");
            new DrugMenuListener().onMenuClick(event);
            verify(event).setCancelled(true);
            menus.verifyNoInteractions();
        }
    }
    @Test void recipeIngredientsAndInventoryItemsOpenTheirRecipeWithoutCloseRedirect() {
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                .when(scheduler).runTask(any(), any(Runnable.class));
        try (var bukkit = mockStatic(org.bukkit.Bukkit.class);
             var recipes = mockStatic(DrugRecipeViewer.class)) {
            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
            var listener = new DrugMenuListener();
            for (int slot : new int[]{10, 40}) {
                var event = click("Recipe: edible", slot, "Joint");
                var player = (Player) event.getWhoClicked();
                when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
                recipes.when(() -> DrugRecipeViewer.recipeFor(event.getCurrentItem())).thenReturn("joint");
                var close = mock(org.bukkit.event.inventory.InventoryCloseEvent.class);
                var view = event.getView();
                when(close.getView()).thenReturn(view);
                when(close.getPlayer()).thenReturn(player);
                recipes.when(() -> DrugRecipeViewer.openRecipe(player, "joint")).thenAnswer(call -> {
                    listener.onRecipeClose(close);
                    return null;
                });
                listener.onMenuClick(event);
                verify(event).setCancelled(true);
                recipes.verify(() -> DrugRecipeViewer.openRecipe(player, "joint"));
            }
            verify(scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
        }
    }

    @Test void recipeResultAndStationCannotOpenIngredientRecipes() {
        try (var recipes = mockStatic(DrugRecipeViewer.class)) {
            var listener = new DrugMenuListener();
            listener.onMenuClick(click("Recipe: edible", 13, "Station"));
            listener.onMenuClick(click("Recipe: edible", 15, "Result"));
            recipes.verifyNoInteractions();
        }
    }

}
