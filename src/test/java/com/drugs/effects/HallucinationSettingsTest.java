package com.drugs.effects;

import com.drugs.DrugsV2;
import com.drugs.tolerance.ToleranceConfigLoader;
import com.drugs.tolerance.ToleranceTracker;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HallucinationSettingsTest {
    @TempDir Path folder;
    private org.mockito.MockedStatic<Material> materials;
    private final java.util.Map<String, Material> blocks = new java.util.HashMap<>();
    @BeforeEach void blockRegistry() {
        // Paper's block registry requires a running server; provide material metadata here.
        materials = mockStatic(Material.class, CALLS_REAL_METHODS);
        materials.when(() -> Material.matchMaterial(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            return blocks.computeIfAbsent(name, key -> {
                Material block = mock(Material.class);
                when(block.name()).thenReturn(key);
                when(block.isBlock()).thenReturn(!key.equals("STICK"));
                return block;
            });
        });
    }
    @AfterEach void reset() {
        HallucinationSettings.load(folder.resolve("empty").toFile());
        materials.close();
    }

    @Test void bundledDrugsRetainTheirPreviousHallucinations() {
        HallucinationSettings.load(new File("src/main/resources"));
        assertEquals(Set.of(HallucinationSettings.Type.PSYCHEDELIC, HallucinationSettings.Type.PARANOIA,
                HallucinationSettings.Type.PORTAL, HallucinationSettings.Type.PHANTOM), types("lsd"));
        assertTrue(types("salvia").contains(HallucinationSettings.Type.BLOCK_DISGUISE));
        assertTrue(types("shrooms").contains(HallucinationSettings.Type.PSYCHEDELIC));
        assertTrue(types("meth").contains(HallucinationSettings.Type.PARANOIA));
        assertEquals(Set.of(HallucinationSettings.Type.PHANTOM), types("vape"));
        assertTrue(HallucinationSettings.forDrug("vape").getFirst().nearToleranceLimit());
    }

    private Set<HallucinationSettings.Type> types(String drug) {
        return HallucinationSettings.forDrug(drug).stream().map(HallucinationSettings.Effect::type).collect(Collectors.toSet());
    }

    @Test void parsesOptionsAndEnforcesChanceAndToleranceConditions() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString("type: phantom\nduration-seconds: 5\ndelay-seconds: 2\nchance: 0.25\nnear-tolerance-limit: true\nscale-with-potency: true\nmobs: [ZOMBIE]\nblocks: [GOLD_BLOCK]\nspeed: 0.1\n");
        var effect = HallucinationSettings.read(yaml);
        assertEquals(100, effect.durationTicks());
        assertEquals(40, effect.delayTicks());
        assertEquals(200, effect.duration(2));
        assertEquals(java.util.List.of(EntityType.ZOMBIE), effect.mobs());
        assertEquals("GOLD_BLOCK", effect.blocks().getFirst().name());
        assertFalse(effect.matches(0, 2, 5));
        assertTrue(effect.matches(0.1, 4, 5));
        assertFalse(effect.matches(0.3, 5, 5));
    }

    @Test void invalidTypesNumbersBlocksAndMobsAreRejected() {
        for (var invalid : new Object[][]{{"type", "unknown"}, {"chance", 1.1},
                {"duration-seconds", Double.NaN}, {"delay-seconds", -1}, {"interval-ticks", 0},
                {"speed", "bad"}, {"blocks", java.util.List.of("STICK")},
                {"blocks", "GOLD_BLOCK"}, {"mobs", java.util.List.of("WITHER")},
                {"mobs", java.util.List.of("PLAYER")}}) {
            var yaml = new YamlConfiguration();
            yaml.set("type", "phantom");
            yaml.set((String) invalid[0], invalid[1]);
            assertThrows(IllegalArgumentException.class, () -> HallucinationSettings.read(yaml));
        }
    }

    private void config(String value) throws Exception {
        Files.createDirectories(folder.resolve("drugs"));
        Files.writeString(folder.resolve("drugs/custom.yml"), "drugs:\n  custom:\n    hallucinations:\n" + value);
        HallucinationSettings.load(folder.toFile());
    }

    @Test void customDrugDispatchesConfiguredEffectAndReloadCanDisableIt() throws Exception {
        config("      test:\n        type: portal\n        delay-seconds: 2\n        duration-seconds: 7\n        blocks: [GOLD_BLOCK]\n");
        var plugin = mock(DrugsV2.class);
        var portal = mock(LsdEffectManager.class);
        when(plugin.getLsdEffectManager()).thenReturn(portal);
        var player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        var sensory = new SensoryEffectManager(plugin);
        try (var tolerance = mockStatic(ToleranceTracker.class); var settings = mockStatic(ToleranceConfigLoader.class)) {
            sensory.onDrugUse(player, "custom", 1);
            verify(portal).startEffect(player, "custom", 40, 140, blocks.get("GOLD_BLOCK"));
            config("      test:\n        enabled: false\n        type: portal\n");
            clearInvocations(portal);
            sensory.onDrugUse(player, "custom", 1);
            verifyNoInteractions(portal);
            assertTrue(HallucinationSettings.forDrug("missing").isEmpty());
        }
    }
}
