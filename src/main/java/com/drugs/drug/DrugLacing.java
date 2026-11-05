package com.drugs.drug;

import com.drugs.items.DrugItemMetadata;
import org.bukkit.inventory.ItemStack;

/** A single additional dose, stored without changing the host item's appearance. */
public final class DrugLacing {
    private DrugLacing() {}

    private static DrugEffectProfile taggedProfile(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = DrugItemMetadata.getDrugId(item.getItemMeta());
        if (id == null) return null;
        var profile = DrugRegistry.getProfileById(id);
        return profile != null && profile.matches(item) ? profile : null;
    }

    public static ItemStack lace(ItemStack base, ItemStack ingredient) {
        var host = taggedProfile(base);
        var additive = taggedProfile(ingredient);
        if (host == null || additive == null || base.getAmount() != 1 || ingredient.getAmount() < 1
                || host.getId().equalsIgnoreCase(additive.getId())
                || DrugItemMetadata.getLacedDrug(base.getItemMeta()) != null
                || DrugItemMetadata.getLacedDrug(ingredient.getItemMeta()) != null) return null;
        ItemStack result = base.clone();
        var meta = result.getItemMeta();
        DrugItemMetadata.setLacedDrug(meta, additive.getId(), DrugItemMetadata.getStrainId(ingredient));
        DrugPurity.storeLacing(meta, ingredient);
        result.setItemMeta(meta);
        return result;
    }

    /** Resolve a fresh, un-laced dose; never recurse through arbitrary item data. */
    public static ItemStack additionalDose(ItemStack source) {
        if (source == null || !source.hasItemMeta()) return null;
        var meta = source.getItemMeta();
        if (DrugItemMetadata.isLacingExpended(meta)) return null;
        String id = DrugItemMetadata.getLacedDrug(meta);
        if (id == null || id.equalsIgnoreCase(DrugItemMetadata.getDrugId(meta))) return null;
        if (DrugRegistry.getProfileById(id) == null) return null;
        var dose = DrugRegistry.getDrugItem(id, 1, DrugItemMetadata.getLacedStrain(meta));
        DrugPurity.restoreLacing(meta, dose);
        return dose;
    }

    /** Multi-use items expend the added dose on their next puff. */
    public static void expendDose(ItemStack source) {
        if (source == null || !source.hasItemMeta()) return;
        var meta = source.getItemMeta();
        if (DrugItemMetadata.getLacedDrug(meta) == null) return;
        DrugItemMetadata.setLacingExpended(meta, true);
        source.setItemMeta(meta);
    }
}
