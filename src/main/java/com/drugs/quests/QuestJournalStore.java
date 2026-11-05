package com.drugs.quests;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Independent of transient enforcer NPCs and BeautyQuests' loaded quest instances. */
public final class QuestJournalStore {
    public record Lesson(String product, int completed) {}
    public record Contract(String product, int amount, int delivered, long deadline) {
        public Contract credit(String soldProduct, int count, long now) {
            return expired(now) || !product.equals(soldProduct) || count <= 0 ? this
                    : new Contract(product, amount, (int) Math.min(amount, (long) delivered + count), deadline);
        }
        public boolean expired(long now) { return now >= deadline; }
        public boolean complete() { return delivered >= amount; }
    }
    final Map<UUID, Lesson> lessons = new HashMap<>();
    final Map<UUID, Contract> contracts = new HashMap<>();
    private final Path file;
    public QuestJournalStore(Path file) throws IOException, org.bukkit.configuration.InvalidConfigurationException {
        this.file = file;
        if (!Files.exists(file)) return;
        var yaml = new YamlConfiguration();
        yaml.load(file.toFile());
        for (String key : yaml.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException ignored) { continue; }
            String product = yaml.getString(key + ".lesson.product");
            if (valid(product)) lessons.put(uuid, new Lesson(product, Math.max(0, yaml.getInt(key + ".lesson.completed"))));
            product = yaml.getString(key + ".contract.product");
            int amount = yaml.getInt(key + ".contract.amount");
            if (valid(product) && amount > 0 && amount <= 2304)
                contracts.put(uuid, new Contract(product, amount, Math.clamp(yaml.getInt(key + ".contract.delivered"), 0, amount),
                        yaml.getLong(key + ".contract.deadline")));
        }
    }
    private static boolean valid(String id) { return id != null && id.matches("[a-z0-9_]+") && id.length() <= 80; }
    public void save() throws IOException {
        var yaml = new YamlConfiguration();
        lessons.forEach((uuid, lesson) -> {
            yaml.set(uuid + ".lesson.product", lesson.product());
            yaml.set(uuid + ".lesson.completed", lesson.completed());
        });
        contracts.forEach((uuid, contract) -> {
            yaml.set(uuid + ".contract.product", contract.product());
            yaml.set(uuid + ".contract.amount", contract.amount());
            yaml.set(uuid + ".contract.delivered", contract.delivered());
            yaml.set(uuid + ".contract.deadline", contract.deadline());
        });
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "quest-journal-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
