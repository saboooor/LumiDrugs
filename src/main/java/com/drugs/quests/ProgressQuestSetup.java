package com.drugs.quests;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.IOException;
import java.util.HashSet;

final class ProgressQuestSetup {
    private ProgressQuestSetup() {}
    static int prepare(Path folder, String fileName, String stage, String title) throws Exception {
        Files.createDirectories(folder);
        Path file = folder.resolve(fileName);
        if (Files.exists(file)) {
            var existing = new YamlConfiguration(); existing.load(file.toFile());
            if (!stage.equals(existing.getString("manager.branches.0.stages.0.stageType"))
                    || !existing.isInt("id") || existing.getInt("id") < 0)
                throw new IOException("Invalid production quest: " + file);
            return existing.getInt("id");
        }
        var used = new HashSet<Integer>();
        try (var paths = Files.walk(folder)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".yml")).toList()) {
                var yaml = new YamlConfiguration(); yaml.load(path.toFile());
                if (yaml.isInt("id")) used.add(yaml.getInt("id"));
            }
        }
        int id = 9100;
        while (used.contains(id)) id = Math.incrementExact(id);
        var yaml = new YamlConfiguration();
        yaml.set("id", id); yaml.set("name", title);
        yaml.set("repeatable", true); yaml.set("cancellable", true);
        yaml.set("manager.branches.0.stages.0.stageType", stage);
        yaml.createSection("manager.branches.0.endingStages");
        Files.writeString(file, yaml.saveToString(), StandardOpenOption.CREATE_NEW);
        return id;
    }
}
