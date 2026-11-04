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

    private void useDrug(EquipmentSlot hand, String id, boolean legacy) {
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
        when(item.getAmount()).thenReturn(1);
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
            metadata.when(() -> DrugItemMetadata.getCartDurability(meta)).thenReturn(1);
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
            if (legacy) verify(inventory).setItem(hand, consumed);
            verify(inventory).setItem(hand, null);
            verify(inventory, never()).setItem(eq(hand == EquipmentSlot.HAND
                    ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND), any());
        }
    }
}
