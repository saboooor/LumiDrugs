package com.drugs.cartel;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CartelQuestSetupTest {
    @TempDir Path folder;

    @Test void createsReadyToLoadQuestWithFreeId() throws Exception {
        Path quests = folder.resolve("quests");
        assertEquals(9000, CartelQuestSetup.prepare(quests));
        var saved = YamlConfiguration.loadConfiguration(quests.resolve(CartelQuestSetup.FILE_NAME).toFile());
        assertEquals(9000, saved.getInt("id"));
        assertTrue(saved.getBoolean("repeatable"));
        assertTrue(saved.getBoolean("cancellable"));
        assertEquals(CartelQuestSetup.STAGE_TYPE, saved.getString("manager.branches.0.stages.0.stageType"));
    }

    @Test void avoidsIdsInsideNestedQuestFilesRegardlessOfFilename() throws Exception {
        Path nested = Files.createDirectories(folder.resolve("custom"));
        Files.writeString(folder.resolve("some-quest.yml"), "id: 9000\n");
        Files.writeString(nested.resolve("another.YML"), "id: 9001\n");
        assertEquals(9002, CartelQuestSetup.prepare(folder));
        assertEquals("id: 9000\n", Files.readString(folder.resolve("some-quest.yml")));
    }

    @Test void restartsPreserveAdminEditsAndReuseQuest() throws Exception {
        int id = CartelQuestSetup.prepare(folder);
        Path file = folder.resolve(CartelQuestSetup.FILE_NAME);
        String edited = Files.readString(file).replace("Cartel delivery", "Pablo's errands") + "description: My custom quest\n";
        Files.writeString(file, edited);
        assertEquals(id, CartelQuestSetup.prepare(folder));
        assertEquals(edited, Files.readString(file));
        try (var files = Files.list(folder)) { assertEquals(1, files.count()); }
    }

    @Test void unrelatedFileAtManagedPathIsNotOverwritten() throws Exception {
        Path file = folder.resolve(CartelQuestSetup.FILE_NAME);
        Files.writeString(file, "id: 9000\nname: An unrelated quest\n");
        assertThrows(IOException.class, () -> CartelQuestSetup.prepare(folder));
        assertEquals("id: 9000\nname: An unrelated quest\n", Files.readString(file));
    }

    @Test void invalidExistingQuestDoesNotRiskAnIdCollision() throws Exception {
        Files.writeString(folder.resolve("broken.yml"), "name: Missing ID\n");
        assertThrows(IOException.class, () -> CartelQuestSetup.prepare(folder));
        assertFalse(Files.exists(folder.resolve(CartelQuestSetup.FILE_NAME)));
    }
}
