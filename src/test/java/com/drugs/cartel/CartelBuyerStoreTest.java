package com.drugs.cartel;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartelBuyerStoreTest {
    @TempDir Path directory;
    private Plugin plugin() {
        var plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return plugin;
    }
    private World world() {
        var world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("world");
        return world;
    }

    @Test void locationsSurviveRestartAndRemovalsRemainRemoved() {
        var plugin = plugin();
        var world = world();
        UUID buyer = UUID.randomUUID();
        var store = new CartelBuyerStore(plugin);
        store.remember(buyer, 12, new Location(world, -0.5, 65, 32.5, 90, 15));
        var restarted = new CartelBuyerStore(plugin);
        var saved = restarted.all().getFirst();
        assertEquals(buyer, saved.uuid());
        assertEquals(12, saved.npcId());
        assertEquals(world.getUID(), saved.worldId());
        assertEquals(-0.5, saved.x());
        assertEquals(90, saved.yaw());
        assertTrue(saved.inChunk(world.getUID(), -1, 2));
        assertFalse(saved.inChunk(world.getUID(), 0, 2));
        restarted.forget(buyer);
        assertTrue(new CartelBuyerStore(plugin).all().isEmpty());
    }

    @Test void malformedFilesAreNotOverwritten() throws Exception {
        var file = directory.resolve("cartel-buyers.yml");
        String broken = "buyers: [not valid yaml\n";
        Files.writeString(file, broken);
        var store = new CartelBuyerStore(plugin());
        store.remember(UUID.randomUUID(), 1, new Location(world(), 1, 64, 1));
        assertEquals(broken, Files.readString(file));
    }
}
