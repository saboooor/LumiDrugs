package com.drugs.effects;

import com.drugs.config.DrugFamilyConfigs;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.util.*;

/** Immutable, validated per-drug hallucination definitions. Missing entries have no effects. */
public final class HallucinationSettings {
    public enum Type { PSYCHEDELIC, PARANOIA, PHANTOM, PORTAL, BLOCK_DISGUISE }
    public record Effect(Type type, int durationTicks, int delayTicks, int intervalTicks,
                         double chance, boolean nearToleranceLimit, boolean scaleWithPotency,
                         List<Material> blocks, List<EntityType> mobs, int mobLifetimeTicks, double speed,
                         double shadowChance, double phantomChance) {
        public boolean matches(double roll, int tolerance, int maximum) {
            return roll < chance && (!nearToleranceLimit || tolerance >= Math.max(1, maximum - 1));
        }
        public int duration(double potency) {
            return scaleWithPotency ? (int) Math.clamp(Math.round(durationTicks * potency), 100L, 72000L) : durationTicks;
        }
    }
    private static Map<String, List<Effect>> profiles = Map.of();
    private HallucinationSettings() {}

    public static void load(File folder) {
        var yaml = DrugFamilyConfigs.view(folder, "hallucinations");
        Map<String, List<Effect>> loaded = new HashMap<>();
        for (String drug : yaml.getKeys(false)) {
            var section = yaml.getConfigurationSection(drug);
            List<Effect> effects = new ArrayList<>();
            for (String name : section.getKeys(false)) {
                try {
                    var effect = section.getConfigurationSection(name);
                    if (effect == null) throw new IllegalArgumentException("must be a section");
                    if (effect.getBoolean("enabled", true)) effects.add(read(effect));
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("Invalid hallucination '" + drug + "." + name + "': " + ex.getMessage(), ex);
                }
            }
            loaded.put(drug, List.copyOf(effects));
        }
        profiles = Map.copyOf(loaded);
    }

    public static List<Effect> forDrug(String drug) {
        return profiles.getOrDefault(drug.toLowerCase(Locale.ROOT), List.of());
    }

    static Effect read(ConfigurationSection section) {
        Type type = Type.valueOf(section.getString("type", "").toUpperCase(Locale.ROOT).replace('-', '_'));
        List<Material> blocks = new ArrayList<>();
        for (String name : names(section, "blocks")) {
            Material block = Material.matchMaterial(name);
            if (block == null || !block.isBlock() || block.isAir()) throw new IllegalArgumentException("Invalid block: " + name);
            blocks.add(block);
        }
        if (blocks.isEmpty()) blocks.addAll(switch (type) {
            case PORTAL -> List.of(Material.NETHER_PORTAL);
            case BLOCK_DISGUISE -> List.of(Material.DIRT);
            default -> List.of(Material.MAGENTA_GLAZED_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA,
                    Material.PURPLE_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
                    Material.AMETHYST_BLOCK, Material.SHROOMLIGHT, Material.SEA_LANTERN,
                    Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM);
        });
        List<EntityType> mobs = new ArrayList<>();
        for (String name : names(section, "mobs")) {
            EntityType mob = EntityType.valueOf(name.toUpperCase(Locale.ROOT));
            if (!mob.isAlive() || !mob.isSpawnable() || mob.getEntityClass() == null
                    || !org.bukkit.entity.Mob.class.isAssignableFrom(mob.getEntityClass())
                    || mob == EntityType.WITHER || mob == EntityType.ENDER_DRAGON) throw new IllegalArgumentException("Invalid hallucination mob: " + name);
            mobs.add(mob);
        }
        if (mobs.isEmpty()) mobs.addAll(List.of(EntityType.CREEPER, EntityType.ENDERMAN,
                EntityType.CAVE_SPIDER, EntityType.PHANTOM, EntityType.WITHER_SKELETON));
        return new Effect(type, ticks(section, "duration-seconds", 40, 1, 3600),
                ticks(section, "delay-seconds", 0, 0, 3600),
                (int) number(section, "interval-ticks", type == Type.PARANOIA ? 60 : 20, 1, 1200),
                number(section, "chance", 1, 0, 1), section.getBoolean("near-tolerance-limit", false),
                section.getBoolean("scale-with-potency", false), List.copyOf(blocks), List.copyOf(mobs), ticks(section, "mob-lifetime-seconds", 4, 1, 60),
                number(section, "speed", 0.32, 0, 2), number(section, "shadow-chance", 0.65, 0, 1),
                number(section, "phantom-chance", 0.55, 0, 1));
    }

    private static List<String> names(ConfigurationSection section, String key) {
        if (section.isSet(key) && !section.isList(key)) throw new IllegalArgumentException(key + " must be a list");
        return section.getStringList(key);
    }

    private static int ticks(ConfigurationSection section, String key, double fallback, double min, double max) {
        return (int) Math.round(number(section, key, fallback, min, max) * 20);
    }
    private static double number(ConfigurationSection section, String key, double fallback, double min, double max) {
        if (section.isSet(key) && !(section.get(key) instanceof Number)) throw new IllegalArgumentException(key + " must be a number");
        double value = section.getDouble(key, fallback);
        if (!Double.isFinite(value) || value < min || value > max) throw new IllegalArgumentException(key + " must be between " + min + " and " + max);
        return value;
    }
}
