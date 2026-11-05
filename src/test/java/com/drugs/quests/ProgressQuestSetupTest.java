package com.drugs.quests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProgressQuestSetupTest {
    @TempDir Path folder;
    @Test void allocatesDistinctIdsAndPreservesAdminEditsAcrossRestarts() throws Exception {
        Files.writeString(folder.resolve("existing.yml"), "id: 9100\n");
        int learning = ProgressQuestSetup.prepare(folder, "learning.yml", "learning-stage", "Learning");
        int supply = ProgressQuestSetup.prepare(folder, "supply.yml", "supply-stage", "Supply");
        assertEquals(9101, learning); assertEquals(9102, supply);
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(folder.resolve("learning.yml").toFile());
        assertTrue(yaml.getBoolean("repeatable")); assertTrue(yaml.getBoolean("cancellable"));
        String original = Files.readString(folder.resolve("learning.yml")) + "# Admin settings stay intact\n";
        Files.writeString(folder.resolve("learning.yml"), original);
        assertEquals(learning, ProgressQuestSetup.prepare(folder, "learning.yml", "learning-stage", "Changed"));
        assertEquals(original, Files.readString(folder.resolve("learning.yml")));
    }
    @Test void refusesToReplaceAnUnrelatedQuest() throws Exception {
        Files.writeString(folder.resolve("learning.yml"), "id: 5\nname: unrelated\n");
        assertThrows(java.io.IOException.class, () -> ProgressQuestSetup.prepare(folder, "learning.yml", "learning-stage", "Learning"));
    }
}
