package com.drugs.cartel;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.FileVisitOption;
import java.util.HashSet;
import java.util.Set;

/** Prepares the default quest before BeautyQuests' deferred quest-loading task runs. */
final class CartelQuestSetup {
    static final String FILE_NAME = "lumidrugs-cartel.yml";
    static final String STAGE_TYPE = "lumidrugs-cartel-delivery";

    private static final String FOLLOW = "manager.branches.0.stages.0.options.lumi-follow";

    private CartelQuestSetup() {}

    static int prepare(Path folder) throws IOException, InvalidConfigurationException {
        Files.createDirectories(folder);
        Path destination = folder.resolve(FILE_NAME);
        if (Files.exists(destination)) {
            var saved = read(destination);
            if (!STAGE_TYPE.equals(saved.getString("manager.branches.0.stages.0.stageType"))) {
                throw new IOException(destination + " does not contain a Cartel delivery stage. Use a custom quest ID or fix this file.");
            }
            int existingId = id(saved, destination);
            return existingId;
        }

        Set<Integer> used = new HashSet<>();
        try (var paths = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".yml"))
                    .filter(p -> !p.getFileName().toString().contains("backup")).toList()) {
                used.add(id(read(path), path));
            }
        }
        int id = 9000;
        while (used.contains(id)) {
            if (id == Integer.MAX_VALUE) throw new IOException("No free cartel quest ID available.");
            id++;
        }
        var quest = new YamlConfiguration();
        quest.set("id", id);
        quest.set("name", "Cartel delivery");
        quest.set("repeatable", true);
        quest.set("cancellable", true);
        quest.set("manager.branches.0.stages.0.stageType", STAGE_TYPE);
        quest.createSection("manager.branches.0.endingStages");
        followOptions(quest);
        Files.writeString(destination, quest.saveToString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return id;
    }

    private static void followOptions(YamlConfiguration quest) {
        quest.set(FOLLOW + ".enabled", true);
        quest.set(FOLLOW + ".npc-data", "npc");
        quest.set(FOLLOW + ".open-doors", true);
        quest.set(FOLLOW + ".climbing", true);
        quest.set(FOLLOW + ".max-climb-height", 6);
        quest.set(FOLLOW + ".climb-timeout-ticks", 100);
        quest.set(FOLLOW + ".teleport-recovery", true);
        quest.set(FOLLOW + ".follow-teleports", true);
        quest.set(FOLLOW + ".cross-world", false);
    }

    private static YamlConfiguration read(Path file) throws IOException, InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.load(file.toFile());
        return config;
    }

    private static int id(YamlConfiguration config, Path file) throws IOException {
        if (!config.isInt("id") || config.getInt("id") < 0)
            throw new IOException("Quest file " + file + " needs a valid non-negative integer id.");
        return config.getInt("id");
    }
}
