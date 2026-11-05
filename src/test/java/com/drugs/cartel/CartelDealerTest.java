package com.drugs.cartel;

import com.drugs.DrugsV2;
import com.drugs.drug.DrugRegistry;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import java.util.UUID;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import org.mockito.MockedStatic;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartelDealerTest {
    @TempDir Path directory;
    private DrugsV2 plugin;
    private YamlConfiguration prices;
    private MockedStatic<DrugRegistry> registry;

    @BeforeEach
    void setup() throws Exception {
        plugin = mock(DrugsV2.class);
        when(plugin.getName()).thenReturn("DrugsV2");
        when(plugin.namespace()).thenReturn("drugsv2");
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var instance = DrugsV2.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, plugin);
        registry = mockStatic(DrugRegistry.class);
        registry.when(() -> DrugRegistry.isDrugRegistered("joint")).thenReturn(true);
        prices = new YamlConfiguration();
        prices.set("prices.joint", 15.0);
        prices.save(directory.resolve("cartel.yml").toFile());
    }

    @AfterEach void cleanup() { registry.close(); }

    @Test void ordinaryPlayersCanRejectButCannotManageCartelNpcs() {
        CartelDealer dealer = new CartelDealer(plugin);
        CartelEnforcer enforcer = mock(CartelEnforcer.class);
        dealer.setEnforcer(enforcer);
        Player player = mock(Player.class);
        org.bukkit.command.Command command = mock(org.bukkit.command.Command.class);
        assertTrue(dealer.onCommand(player, command, "cartel", new String[]{"reject"}));
        verify(enforcer).reject(player);
        CartelNpcs npcs = mock(CartelNpcs.class);
        dealer.setNpcs(npcs);
        for (String action : java.util.List.of("create", "remove", "reload", "enforcer")) {
            assertTrue(dealer.onCommand(player, command, "cartel", new String[]{action}));
        }
        verifyNoInteractions(npcs);
        verify(enforcer, never()).onCommand(any(), any());
        assertEquals(java.util.List.of("reject"), dealer.onTabComplete(player, command, "cartel", new String[]{"r"}));
    }

    @Test void malformedOrConsoleRejectionDoesNotTouchEncounters() {
        CartelDealer dealer = new CartelDealer(plugin);
        CartelEnforcer enforcer = mock(CartelEnforcer.class);
        dealer.setEnforcer(enforcer);
        org.bukkit.command.Command command = mock(org.bukkit.command.Command.class);
        dealer.onCommand(mock(Player.class), command, "cartel", new String[]{"reject", "OtherPlayer"});
        dealer.onCommand(mock(org.bukkit.command.ConsoleCommandSender.class), command, "cartel", new String[]{"reject"});
        verifyNoInteractions(enforcer);
    }

    private ItemStack drug(String id, String type, String strain) {
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getAmount()).thenReturn(3);
        when(item.clone()).thenReturn(item);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(new NamespacedKey(plugin, "drug_id"), PersistentDataType.STRING)).thenReturn(id);
        when(data.get(new NamespacedKey(plugin, "item_type"), PersistentDataType.STRING)).thenReturn(type);
        when(data.get(new NamespacedKey(plugin, "strain_id"), PersistentDataType.STRING)).thenReturn(strain);
        return item;
    }

    @Test void rejectsVanillaUnknownAndPlantingItems() {
        assertEquals(0, CartelDealer.price(mock(ItemStack.class), prices));
        assertEquals(0, CartelDealer.price(drug("unknown", "unknown", null), prices));
        assertEquals(0, CartelDealer.price(drug("joint", "cannabis_plant", null), prices));
    }

    @Test void strainOverrideAndInvalidPrices() {
        ItemStack joint = drug("joint", "joint", "reggie");
        assertEquals(15, CartelDealer.price(joint, prices));
        prices.set("strain-prices.joint.reggie", 9.0);
        assertEquals(9, CartelDealer.price(joint, prices));
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            prices.set("strain-prices.joint.reggie", invalid);
            assertEquals(0, CartelDealer.price(joint, prices));
        }
    }


    private Item dropped(ItemStack stack) {
        Item item = mock(Item.class);
        when(item.isValid()).thenReturn(true);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());
        when(item.getItemStack()).thenReturn(stack);
        when(item.getPickupDelay()).thenReturn(10);
        return item;
    }

    @Test void successfulDropPaysAndRemovesFullStack() {
        Player player = mock(Player.class);
        Item item = dropped(drug("joint", "joint", null));
        CartelDealer.Payments payments = mock(CartelDealer.Payments.class);
        when(payments.deposit(player, 45)).thenReturn(true);
        assertEquals(3, new CartelDealer(plugin).sellDropped(player, item, 16, payments));
        verify(payments).deposit(player, 45);
        verify(item).remove();
        verify(player, never()).getInventory();
    }

    @Test void excessDroppedItemsRemainOnGround() {
        Player player = mock(Player.class);
        ItemStack joint = drug("joint", "joint", null);
        ItemStack leftover = mock(ItemStack.class);
        when(joint.clone()).thenReturn(leftover);
        Item item = dropped(joint);
        CartelDealer.Payments payments = mock(CartelDealer.Payments.class);
        when(payments.deposit(player, 30)).thenReturn(true);
        assertEquals(2, new CartelDealer(plugin).sellDropped(player, item, 2, payments));
        verify(leftover).setAmount(1);
        verify(item).setItemStack(leftover);
        verify(item, never()).remove();
        verify(item).setPickupDelay(10);
    }

    @Test void preferredProductReceivesBonusAndOtherProductsKeepTheirPrice() {
        var dealer = new CartelDealer(plugin);
        var player = mock(Player.class);
        var matching = new DealerPreferences.Offer("joint", null, 1.5, Long.MAX_VALUE);
        var payment = mock(CartelDealer.Payments.class);
        when(payment.deposit(player, 67.5)).thenReturn(true);
        assertEquals(3, dealer.sellDropped(player, dropped(drug("joint", "joint", null)), 3, payment, matching));
        verify(payment).deposit(player, 67.5);
        var otherOffer = new DealerPreferences.Offer("meth", null, 1.5, Long.MAX_VALUE);
        when(payment.deposit(player, 45)).thenReturn(true);
        assertEquals(3, dealer.sellDropped(player, dropped(drug("joint", "joint", null)), 3, payment, otherOffer));
        verify(payment).deposit(player, 45);
    }

    @Test void purityScalesStrainPriceBeforeDealerPreferenceBonus() {
        var joint = drug("joint", "joint", "reggie");
        when(joint.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "purity"), PersistentDataType.INTEGER)).thenReturn(100);
        prices.set("strain-prices.joint.reggie", 18.0);
        assertEquals(20.0, CartelDealer.price(joint, prices), 0.00001);
        var player = mock(Player.class);
        var payment = mock(CartelDealer.Payments.class);
        when(payment.deposit(player, 75)).thenReturn(true);
        var offer = new DealerPreferences.Offer("joint", null, 1.5, Long.MAX_VALUE);
        assertEquals(3, new CartelDealer(plugin).sellDropped(player, dropped(joint), 3, payment, offer));
        verify(payment).deposit(player, 75);
    }

    @Test void nearbyBuyerUsesItsOwnPreferenceWhenCollectingDrops() {
        var dealer = spy(new CartelDealer(plugin));
        var payment = mock(CartelDealer.Payments.class);
        doReturn(payment).when(dealer).payments();
        var player = mock(Player.class);
        when(player.hasPermission("drugs.cartel.sell")).thenReturn(true);
        var item = dropped(drug("joint", "joint", null));
        var buyer = mock(org.bukkit.entity.LivingEntity.class);
        var world = mock(org.bukkit.World.class);
        var location = new org.bukkit.Location(world, 1, 64, 1);
        when(item.getLocation()).thenReturn(location);
        when(buyer.getLocation()).thenReturn(location);
        when(buyer.hasLineOfSight(item)).thenReturn(true);
        when(item.getNearbyEntities(2, 2, 2)).thenReturn(java.util.List.of(buyer));
        var npcs = mock(CartelNpcs.class);
        var character = mock(CartelNpcs.Character.class);
        when(character.id()).thenReturn(UUID.randomUUID());
        when(npcs.find(buyer, "buyer")).thenReturn(character);
        dealer.setNpcs(npcs);
        registry.when(DrugRegistry::getRegisteredDrugNames).thenReturn(java.util.Set.of("joint"));
        when(payment.deposit(player, 67.5)).thenReturn(true);
        assertTrue(dealer.acceptDrop(player, item));
        verify(payment).deposit(player, 67.5);
        verify(item).remove();
    }

    @Test void preferredPaymentFailureKeepsItemsAndDisabledProductsCannotEarnBonuses() throws Exception {
        prices.set("prices.joint", 0);
        prices.save(directory.resolve("cartel.yml").toFile());
        var offer = new DealerPreferences.Offer("joint", null, 1.5, Long.MAX_VALUE);
        var payment = mock(CartelDealer.Payments.class);
        var item = dropped(drug("joint", "joint", null));
        var dealer = new CartelDealer(plugin);
        assertEquals(0, dealer.sellDropped(mock(Player.class), item, 3, payment, offer));
        verifyNoInteractions(payment);
        verify(item, never()).remove();
        prices.set("prices.joint", 15);
        prices.save(directory.resolve("cartel.yml").toFile());
        assertEquals(0, new CartelDealer(plugin).sellDropped(mock(Player.class), item, 3, payment, offer));
        verify(item, never()).remove();
        verify(item, never()).setItemStack(any());
    }

    @Test void failedAndThrowingDepositsLeaveDropUntouched() {
        for (boolean throwing : new boolean[]{false, true}) {
            Player player = mock(Player.class);
            Item item = dropped(drug("joint", "joint", null));
            CartelDealer.Payments payments = mock(CartelDealer.Payments.class);
            if (throwing) when(payments.deposit(player, 45)).thenThrow(new IllegalStateException("offline"));
            assertEquals(0, new CartelDealer(plugin).sellDropped(player, item, 16, payments));
            verify(item, never()).remove();
            verify(item, never()).setItemStack(any());
            verify(item).setPickupDelay(10);
        }
    }

    @Test void nestedPaymentCallbackCannotSellSameDropTwice() {
        Player player = mock(Player.class);
        Item item = dropped(drug("joint", "joint", null));
        CartelDealer.Payments payments = mock(CartelDealer.Payments.class);
        CartelDealer dealer = new CartelDealer(plugin);
        when(payments.deposit(player, 45)).thenAnswer(call -> {
            assertEquals(0, dealer.sellDropped(player, item, 16, payments));
            return true;
        });
        assertEquals(3, dealer.sellDropped(player, item, 16, payments));
        verify(payments, times(1)).deposit(player, 45);
    }

    @Test void invalidOrUnpricedDropsNeverDepositMoney() {
        Player player = mock(Player.class);
        Item item = dropped(mock(ItemStack.class));
        CartelDealer.Payments payments = mock(CartelDealer.Payments.class);
        assertEquals(0, new CartelDealer(plugin).sellDropped(player, item, 16, payments));
        verifyNoInteractions(payments);
    }
    @Test void stationaryBuyerCreditsContractOnlyAfterASuccessfulSale() {
        CartelDealer dealer = spy(new CartelDealer(plugin));
        CartelNpcs npcs = mock(CartelNpcs.class);
        dealer.setNpcs(npcs);
        var buyer = mock(org.bukkit.entity.LivingEntity.class);
        var character = mock(CartelNpcs.Character.class);
        when(npcs.find(buyer, "buyer")).thenReturn(character);
        var player = mock(Player.class);
        when(player.hasPermission("drugs.cartel.sell")).thenReturn(true);
        var stack = drug("joint", "joint", null);
        var item = dropped(stack);
        when(item.getNearbyEntities(2, 2, 2)).thenReturn(java.util.List.of(buyer));
        when(buyer.hasLineOfSight(item)).thenReturn(true);
        var world = mock(org.bukkit.World.class);
        when(buyer.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));
        when(item.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 0, 0));
        var payments = mock(CartelDealer.Payments.class);
        doReturn(payments).when(dealer).payments();
        var journal = mock(com.drugs.quests.ProductionJournal.class);
        when(plugin.getProductionJournal()).thenReturn(journal);
        when(payments.deposit(player, 45)).thenReturn(false);
        assertTrue(dealer.acceptDrop(player, item));
        verifyNoInteractions(journal);
        when(payments.deposit(player, 45)).thenReturn(true);
        assertTrue(dealer.acceptDrop(player, item));
        verify(journal).sold(player, "joint", 3);
    }

}
