package com.drugs.cartel;

import com.drugs.DrugsV2;
import com.drugs.commands.DrugsCommand;
import com.drugs.growing.DrugGrowing;
import com.drugs.ui.DialogView;

import com.drugs.ui.DialogView.*;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EnforcerGuideTest {
    private MockedStatic<Bukkit> bukkit;
    private MockedConstruction<ItemStack> items;
    private final List<Inventory> inventories = new ArrayList<>();
    private DrugsV2 plugin;
    private Player player;
    private EnforcerGuide guide;

    @BeforeEach
    void setup() throws Exception {
        bukkit = mockStatic(Bukkit.class);
        items = mockConstruction(ItemStack.class, (item, context) -> {
            when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
        });
        plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("LumiDrugs");
        when(plugin.namespace()).thenReturn("lumidrugs");
        var server = mock(Server.class);
        var scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
                .when(scheduler).runTask(eq(plugin), any(Runnable.class));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), anyString()))
                .thenAnswer(call -> {
                    Inventory inventory = mock(Inventory.class);
                    when(inventory.getHolder()).thenReturn(call.getArgument(0));
                    when(inventory.getSize()).thenReturn(54);
                    inventories.add(inventory);
                    return inventory;
                });
        var field = DrugsV2.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, plugin);
        player = mock(Player.class);
        guide = new EnforcerGuide(plugin);
    }

    @AfterEach
    void cleanup() {
        items.close();
        bukkit.close();
    }

    private InventoryClickEvent click(Inventory inventory, int slot) {
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        guide.click(event);
        verify(event).setCancelled(true);
        return event;
    }

    @Test
    void dialogueOpensRecipeGridAndGrowingPage() {
        ShapedRecipe recipe = mock(ShapedRecipe.class);
        ItemStack paper = mock(ItemStack.class);
        when(paper.getType()).thenReturn(Material.PAPER);
        ItemStack result = mock(ItemStack.class);
        when(result.clone()).thenReturn(result);
        when(recipe.getResult()).thenReturn(result);
        when(recipe.getShape()).thenReturn(new String[]{" P ", "   ", "   "});
        org.bukkit.inventory.RecipeChoice choice = mock(org.bukkit.inventory.RecipeChoice.class);
        when(choice.getItemStack()).thenReturn(paper);
        when(recipe.getChoiceMap()).thenReturn(Map.of('P', choice));
        bukkit.when(() -> Bukkit.getRecipe(new NamespacedKey(plugin, "joint"))).thenReturn(recipe);

        // 1. Dialogue opens as Paper Dialog
        EnforcerGuide.open(plugin, player, "joint");
        ArgumentCaptor<MultiActionDialog> captor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(plugin).showDialog(eq(player), captor.capture());

        MultiActionDialog dialogue = captor.getValue();
        assertEquals("Cartel enforcer", dialogue.getBase().title().toPlainText());
        assertTrue(dialogue.getBase().body().getFirst().contents().toPlainText().contains("Enforcer: I need joint"));
        assertEquals("/drugs guide joint recipe", dialogue.actions().get(0).action().command());
        assertEquals("/drugs guide joint growing", dialogue.actions().get(1).action().command());

        // 2. Recipe page opens as an Inventory GUI with native items
        EnforcerGuide.openPage(plugin, player, "joint", EnforcerGuide.Page.RECIPE);
        assertEquals(1, inventories.size());
        Inventory recipeGui = inventories.getFirst();
        verify(recipeGui).setItem(11, paper);
        verify(recipeGui).setItem(24, result);
        verify(player).openInventory(recipeGui);

        // 3. Clicking "How to grow" (slot 49) in recipe GUI transitions to GROWING dialog
        click(recipeGui, 49);
        verify(player).closeInventory();
        ArgumentCaptor<MultiActionDialog> growingCaptor = ArgumentCaptor.forClass(MultiActionDialog.class);
        verify(plugin, times(2)).showDialog(eq(player), growingCaptor.capture());
        MultiActionDialog growingDialog = growingCaptor.getValue();
        assertEquals("Growing ingredients", growingDialog.getBase().title().toPlainText());
        assertTrue(growingDialog.getBase().body().getFirst().contents().toPlainText().contains("using shears"));

        // 4. Clicking "Back to enforcer" (slot 45) transitions to DIALOGUE dialog
        click(recipeGui, 45);
        verify(plugin, times(3)).showDialog(eq(player), any(MultiActionDialog.class));

        // 5. Clicking close (slot 53) closes inventory
        click(recipeGui, 53);
    }

    @Test
    void bottomInventoryClicksAndDragsCannotTakePreviewItems() {
        EnforcerGuide.openPage(plugin, player, "joint", EnforcerGuide.Page.RECIPE);
        Inventory inventory = inventories.getFirst();
        click(inventory, 60);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        guide.drag(drag);
        verify(drag).setCancelled(true);
    }

    @Test
    void dialogPropertiesAndPaperConversion() {
        MultiActionDialog dialogue = EnforcerGuide.createDialog("joint", EnforcerGuide.Page.DIALOGUE);
        assertTrue(dialogue.getBase().canCloseWithEscape());
        assertEquals(1, dialogue.columns());
        assertEquals(300, dialogue.actions().getFirst().width());

        var provider = mock(DialogInstancesProvider.class);
        var baseBuilder = mock(io.papermc.paper.registry.data.dialog.DialogBase.Builder.class, RETURNS_SELF);
        var buttonBuilder = mock(io.papermc.paper.registry.data.dialog.ActionButton.Builder.class, RETURNS_SELF);
        when(provider.dialogBaseBuilder(any())).thenReturn(baseBuilder);
        when(provider.actionButtonBuilder(any())).thenReturn(buttonBuilder);

        try (var factory = mockStatic(DialogInstancesProvider.class)) {
            factory.when(DialogInstancesProvider::instance).thenReturn(provider);
            DialogView.paperBase(dialogue);
            DialogView.button(dialogue.actions().getFirst());
            verify(provider).dialogBaseBuilder(Component.text("Cartel enforcer"));
            verify(baseBuilder).canCloseWithEscape(true);
            verify(provider).staticAction(ClickEvent.runCommand("/drugs guide joint recipe"));
            verify(buttonBuilder).width(300);
        }
    }

    @Test
    void dialogueOffersExplicitRejectionAndCloseRemainsHarmless() {
        MultiActionDialog dialog = EnforcerGuide.createDialog("joint", EnforcerGuide.Page.DIALOGUE);
        var reject = dialog.actions().stream().filter(button -> button.label().toPlainText().equals("Reject demand"))
                .findFirst().orElseThrow();
        assertEquals("/cartel reject", reject.action().command());
        assertTrue(reject.lore().contains("Counts as a refusal"));
        assertEquals("/drugs guide close", dialog.exit().action().command());
    }

    @Test
    void growingInstructionsUseConfiguredCropAndCannabisMechanics() {
        try (MockedStatic<DrugGrowing> growing = mockStatic(DrugGrowing.class)) {
            growing.when(() -> DrugGrowing.crop("coca")).thenReturn(
                    new DrugGrowing.Crop("coca", "Coca Leaves", Material.VINE, Material.VINE, 123));
            String instructions = String.join(" ", EnforcerGuide.growingSteps("coca"));
            assertTrue(instructions.contains("wheat seeds + vine"));
            assertTrue(instructions.contains("123 seconds"));
            assertTrue(instructions.contains("light at least 9"));
        }
        assertTrue(String.join(" ", EnforcerGuide.growingSteps("cannabis")).contains("using shears"));
    }

    @Test
    void drugsCommandDispatchesGuideSubcommands() {
        DrugsCommand command = new DrugsCommand();
        assertTrue(command.onCommand(player, mock(org.bukkit.command.Command.class), "drugs", new String[]{"close"}));
        assertTrue(command.onCommand(player, mock(org.bukkit.command.Command.class), "drugs", new String[]{"guide", "close"}));
        assertTrue(command.onCommand(player, mock(org.bukkit.command.Command.class), "drugs", new String[]{"guide", "joint", "growing"}));
        verify(plugin).showDialog(eq(player), any(MultiActionDialog.class));
        assertTrue(command.onCommand(player, mock(org.bukkit.command.Command.class), "drugs", new String[]{"guide", "joint", "recipe"}));
        assertEquals(1, inventories.size());
    }
}
