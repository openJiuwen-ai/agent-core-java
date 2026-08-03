/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Private archive/create helpers for {@code EvolutionStore}.
 *
 * <p>Mirrors Python's {@code StoreArchiveHelper} in
 * {@code openjiuwen/agent_evolving/checkpointing/store_archive.py}.</p>
 */
public class StoreArchiveHelper {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final String EVOLUTION_FILENAME = "evolutions.json";
    private static final Pattern VALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final DateTimeFormatter TS_SUFFIX_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final StoreArchiveStore store;

    public StoreArchiveHelper(StoreArchiveStore store) {
        this.store = store;
    }

    public static Path archiveDir(Path skillDir) {
        Path archive = skillDir.resolve("archive");
        try {
            Files.createDirectories(archive);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create archive directory: " + archive, exception);
        }
        return archive;
    }

    public static String tsSuffix() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(TS_SUFFIX_FORMATTER);
    }

    public CompletionStage<Path> createSkill(
            String name,
            String description,
            String body,
            String frontmatter
    ) {
        if (name == null || name.isEmpty() || !VALID_NAME_PATTERN.matcher(name).matches()) {
            LOGGER.error("[EvolutionStore] create_skill: invalid name {}", name);
            return CompletableFuture.completedFuture(null);
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            LOGGER.error("[EvolutionStore] create_skill: path traversal attempt in name {}", name);
            return CompletableFuture.completedFuture(null);
        }

        Path skillDir = store.resolveSkillDir(name, true);
        if (skillDir == null) {
            LOGGER.error("[EvolutionStore] create_skill: cannot resolve skill dir for {}", name);
            return CompletableFuture.completedFuture(null);
        }

        if (Files.exists(skillDir)) {
            LOGGER.error(
                    "[EvolutionStore] create_skill: skill '{}' already exists at {}; use update operations instead of create",
                    name,
                    skillDir);
            return CompletableFuture.completedFuture(null);
        }

        try {
            Files.createDirectories(skillDir);
            String skillMdContent = buildSkillMdContent(name, description, body, frontmatter);
            Path skillMdPath = skillDir.resolve("SKILL.md");
            EvolutionLog emptyLog = EvolutionLog.empty(name);
            return store.writeFileText(skillMdPath, skillMdContent)
                    .thenCompose(ignored -> store.saveEvolutionLog(name, emptyLog, skillDir))
                    .thenApply(ignored -> {
                        try {
                            Files.createDirectories(skillDir.resolve("evolution"));
                        } catch (IOException exception) {
                            throw new IllegalStateException(
                                    "Failed to create evolution directory for " + skillDir,
                                    exception);
                        }
                        LOGGER.info("[EvolutionStore] created new skill '{}' at {}", name, skillDir);
                        return skillDir;
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create skill directory: " + skillDir, exception);
        }
    }

    public CompletionStage<String> archiveSkillBody(String name) {
        Path skillDir = store.resolveSkillDir(name, false);
        if (skillDir == null) {
            return CompletableFuture.completedFuture(null);
        }
        Path mdPath = store.findSkillMd(skillDir);
        if (mdPath == null) {
            return CompletableFuture.completedFuture(null);
        }
        Path archive = archiveDir(skillDir);
        Path dest = archive.resolve("SKILL.v" + tsSuffix() + ".md");
        return store.readFileText(mdPath)
                .thenCompose(content -> store.writeFileText(dest, content).thenApply(ignored -> content))
                .thenApply(ignored -> {
                    LOGGER.info("[EvolutionStore] archived {} -> {}", mdPath.getFileName(), dest.getFileName());
                    return dest.getFileName().toString();
                });
    }

    public CompletionStage<String> archiveEvolutions(String name) {
        Path skillDir = store.resolveSkillDir(name, false);
        if (skillDir == null) {
            return CompletableFuture.completedFuture(null);
        }
        Path evoPath = skillDir.resolve(EVOLUTION_FILENAME);
        if (!Files.isRegularFile(evoPath)) {
            return CompletableFuture.completedFuture(null);
        }
        Path archive = archiveDir(skillDir);
        Path dest = archive.resolve("evolutions.v" + tsSuffix() + ".json");
        return store.readFileText(evoPath)
                .thenCompose(content -> store.writeFileText(dest, content).thenApply(ignored -> content))
                .thenApply(ignored -> {
                    LOGGER.info("[EvolutionStore] archived evolutions -> {}", dest.getFileName());
                    return dest.getFileName().toString();
                });
    }

    public CompletionStage<Void> clearEvolutions(String name) {
        EvolutionLog emptyLog = EvolutionLog.empty(name);
        return store.saveEvolutionLog(name, emptyLog, null)
                .thenCompose(ignored -> store.renderEvolutionMarkdown(name))
                .thenAccept(ignored -> LOGGER.info("[EvolutionStore] cleared evolutions for skill={}", name));
    }

    public List<String> listArchives(String name) {
        Path skillDir = store.resolveSkillDir(name, false);
        if (skillDir == null) {
            return List.of();
        }
        Path archive = skillDir.resolve("archive");
        if (!Files.isDirectory(archive)) {
            return List.of();
        }
        try (var stream = Files.list(archive)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted(Comparator.reverseOrder())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list archives for " + skillDir, exception);
        }
    }

    private static String buildSkillMdContent(String name, String description, String body, String frontmatter) {
        if (frontmatter != null && !frontmatter.isBlank()) {
            return frontmatter + "\n\n# " + name + "\n\n" + safeBody(body) + "\n";
        }
        return "---\n"
                + "name: " + name + "\n"
                + "description: " + nullToEmpty(description) + "\n"
                + "---\n\n"
                + "# " + name + "\n\n"
                + safeBody(body) + "\n";
    }

    private static String safeBody(String body) {
        return body == null ? "" : body;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * Boundary used by {@link StoreArchiveHelper} to call the surrounding skill store.
     *
     * <p>Mirrors Python's store interaction surface in
     * {@code openjiuwen/agent_evolving/checkpointing/store_archive.py}.</p>
     */
    public interface StoreArchiveStore {

        Path resolveSkillDir(String name, boolean create);

        Path findSkillMd(Path skillDir);

        CompletionStage<String> readFileText(Path path);

        CompletionStage<Void> writeFileText(Path path, String content);

        CompletionStage<Void> saveEvolutionLog(String name, EvolutionLog evolutionLog, Path skillDir);

        CompletionStage<Void> renderEvolutionMarkdown(String name);
    }
}
