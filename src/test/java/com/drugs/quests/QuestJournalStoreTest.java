package com.drugs.quests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class QuestJournalStoreTest {
    @TempDir Path folder;
    @Test void reconnectAndRestartPreserveBothQuestsAndTheOriginalDeadline() throws Exception {
        Path file = folder.resolve("journal.yml");
        var store = new QuestJournalStore(file);
        var player = UUID.randomUUID();
        var lesson = new QuestJournalStore.Lesson("vape", 3);
        var contract = new QuestJournalStore.Contract("joint", 16, 4, 123456789L);
        store.lessons.put(player, lesson); store.contracts.put(player, contract); store.save();
        var loaded = new QuestJournalStore(file);
        assertEquals(lesson, loaded.lessons.get(player));
        assertEquals(contract, loaded.contracts.get(player));
        loaded.lessons.remove(player); loaded.save();
        assertFalse(new QuestJournalStore(file).lessons.containsKey(player));
        assertEquals(contract, new QuestJournalStore(file).contracts.get(player));
    }
    @Test void contractsCreditOnlyMatchingSalesBeforeDeadlineAndClampOverdelivery() {
        var contract = new QuestJournalStore.Contract("vape", 16, 3, 1000);
        assertEquals(contract, contract.credit("joint", 9, 500));
        assertEquals(contract, contract.credit("vape", 9, 1000));
        assertEquals(contract, contract.credit("vape", -1, 500));
        assertEquals(7, contract.credit("vape", 4, 500).delivered());
        assertTrue(contract.credit("vape", Integer.MAX_VALUE, 500).complete());
        assertEquals(16, contract.credit("vape", Integer.MAX_VALUE, 500).delivered());
    }
    @Test void malformedDataIsNotSilentlyOverwritten() throws Exception {
        Path file = folder.resolve("journal.yml");
        Files.writeString(file, "broken: [\n");
        assertThrows(org.bukkit.configuration.InvalidConfigurationException.class, () -> new QuestJournalStore(file));
        assertEquals("broken: [\n", Files.readString(file));
    }
}
