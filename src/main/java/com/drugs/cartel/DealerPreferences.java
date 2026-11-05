package com.drugs.cartel;

import com.drugs.drug.DrugRegistry;
import com.drugs.items.DrugItemMetadata;
import com.drugs.strains.StrainConfigLoader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Deterministic rotations retain each buyer's offer across restarts without a timer. */
final class DealerPreferences {
    record Offer(String drug, String strain, double multiplier, long changesAt) {
        boolean matches(ItemStack item) {
            if (item == null || !item.hasItemMeta()) return false;
            var meta = item.getItemMeta();
            return drug.equals(DrugItemMetadata.getDrugId(meta))
                    && (strain == null || strain.equals(DrugItemMetadata.getStrainId(meta)));
        }
    }

    static Offer current(FileConfiguration config, UUID buyer, long now) {
        if (buyer == null || !config.getBoolean("preferences.enabled", true)) return null;
        List<String> drugs = DrugRegistry.getRegisteredDrugNames().stream().filter(id -> {
            double price = config.getDouble("prices." + id, 0);
            return Double.isFinite(price) && price > 0;
        }).sorted().toList();
        if (drugs.isEmpty()) return null;
        long hours = Math.clamp(config.getLong("preferences.rotation-hours", 24), 1, 8760);
        long interval = hours * 3_600_000L;
        long rotation = Math.floorDiv(Math.max(0, now), interval);
        int offset = Math.floorMod(buyer.hashCode(), drugs.size());
        String drug = drugs.get((int) ((offset + rotation % drugs.size()) % drugs.size()));
        List<String> strains = config.getStringList("preferences.strains." + drug).stream()
                .map(id -> id.toLowerCase(Locale.ROOT)).distinct()
                .filter(id -> StrainConfigLoader.isCannabisDrug(drug) && StrainConfigLoader.getStrain(id) != null)
                .filter(id -> {
                    double price = config.getDouble("strain-prices." + drug + "." + id, config.getDouble("prices." + drug));
                    return Double.isFinite(price) && price > 0;
                }).sorted().toList();
        String strain = strains.isEmpty() ? null : strains.get((int) Math.floorMod(rotation / drugs.size() + buyer.hashCode(), strains.size()));
        double multiplier = config.getDouble("preferences.price-multiplier", 1.5);
        if (!Double.isFinite(multiplier) || multiplier < 1) multiplier = 1.5;
        return new Offer(drug, strain, multiplier, (rotation + 1) * interval);
    }
}
