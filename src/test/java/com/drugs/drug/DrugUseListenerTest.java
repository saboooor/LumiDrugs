package com.drugs.drug;

import com.drugs.achievements.AchievementManager;
import com.drugs.achievements.NativeAdvancements;
import com.drugs.items.DrugItemMetadata;
import com.drugs.tolerance.ToleranceTracker;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.Mockito.*;

class DrugUseListenerTest {
    @Test void consumesFromEitherHand() {
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            useDrug(hand, "joint", false);
        }
    }

    @Test void removesExhaustedVapeFromOffhand() {
        useDrug(EquipmentSlot.OFF_HAND, "vape", false);
    }

    @Test void migratesLegacyOffhandItemBeforeConsumingIt() {
        useDrug(EquipmentSlot.OFF_HAND, "joint", true);
    }

    @Test void lacedProductsApplyBothProfilesFromEitherHand() {
        for (EquipmentSlot hand : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND})
            useDrug(hand, "joint", false, "meth", 1, 1);
    }

    @Test void lacedVapeExpendsOneDoseAndPreservesItsRemainingPuffs() {
        useDrug(EquipmentSlot.OFF_HAND, "vape", false, "meth", 7, 1);
    }

    @Test void nextVapeInStackRestoresItsOwnAddedDose() {
        useDrug(EquipmentSlot.HAND, "vape", false, "meth", 1, 2);
    }

    @Test void infusionGestureNeverConsumesTheOffhandDrug() {
        var event = mock(PlayerInteractEvent.class);
        var player = mock(Player.class);
        var block = mock(org.bukkit.block.Block.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
        when(event.getClickedBlock()).thenReturn(block);
        when(block.getType()).thenReturn(Material.CAULDRON);
        when(player.isSneaking()).thenReturn(true);
        new DrugUseListener().onDrugUse(event);
        verify(player, never()).getInventory();
    }

    private void useDrug(EquipmentSlot hand, String id, boolean legacy) {
        useDrug(hand, id, legacy, null, 1, 1);
    }

    private void useDrug(EquipmentSlot hand, String id, boolean legacy, String addedId, int durability, int amount) {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        DrugEffectProfile profile;
        try (var construction = mockConstruction(ItemStack.class)) {
            profile = mock(DrugEffectProfile.class);
        }
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.getHand()).thenReturn(hand);
        when(event.getAction()).thenReturn(Action.RIGHT_CLICK_AIR);
        when(event.getPlayer()).thenReturn(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.spigot()).thenReturn(mock(Player.Spigot.class));
        when(inventory.getItem(hand)).thenReturn(item);
        when(item.getType()).thenReturn(mock(Material.class));
        when(item.getAmount()).thenReturn(amount);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);
        when(profile.getId()).thenReturn(id);

        try (var registry = mockStatic(DrugRegistry.class);
             var metadata = mockStatic(DrugItemMetadata.class);
             var achievements = mockStatic(AchievementManager.class);
             var advancements = mockStatic(NativeAdvancements.class);
             var tolerance = mockStatic(ToleranceTracker.class)) {
            registry.when(() -> DrugRegistry.getProfileFromItem(item)).thenReturn(profile);
            metadata.when(() -> DrugItemMetadata.hasDrugId(meta)).thenReturn(!legacy);
            metadata.when(() -> DrugItemMetadata.getCartDurability(meta)).thenReturn(durability);
            ItemStack dose = mock(ItemStack.class);
            DrugEffectProfile addedProfile = mock(DrugEffectProfile.class);
            if (addedId != null) {
                metadata.when(() -> DrugItemMetadata.getLacedDrug(meta)).thenReturn(addedId);
                registry.when(() -> DrugRegistry.getProfileById(addedId)).thenReturn(addedProfile);
                registry.when(() -> DrugRegistry.getDrugItem(addedId, 1, null)).thenReturn(dose);
                registry.when(() -> DrugRegistry.getProfileFromItem(dose)).thenReturn(addedProfile);
                when(addedProfile.getId()).thenReturn(addedId);
            }
            achievements.when(AchievementManager::createContext).thenAnswer(invocation -> new HashMap<>());
            ItemStack consumed = item;
            if (legacy) {
                consumed = mock(ItemStack.class);
                when(consumed.getAmount()).thenReturn(1);
                ItemStack migrated = consumed;
                registry.when(() -> DrugRegistry.getDrugItem(id, 1, null)).thenReturn(migrated);
            }

            new DrugUseListener().onDrugUse(event);

            verify(event).setCancelled(true);
            verify(profile).applyEffects(player, consumed);
            if (addedId != null) verify(addedProfile).applyEffects(player, dose);
            if (legacy) verify(inventory).setItem(hand, consumed);
            boolean reusable = id.equals("vape") || id.equals("cart");
            if (reusable && addedId != null) {
                metadata.verify(() -> DrugItemMetadata.setLacingExpended(meta, true));
                if (durability == 1 && amount > 1)
                    metadata.verify(() -> DrugItemMetadata.setLacingExpended(meta, false));
            }
            if (durability == 1 && amount == 1) verify(inventory).setItem(hand, null);
            else verify(inventory, never()).setItem(eq(hand), isNull());
            verify(inventory, never()).setItem(eq(hand == EquipmentSlot.HAND
                    ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND), any());
        }
    }
}
