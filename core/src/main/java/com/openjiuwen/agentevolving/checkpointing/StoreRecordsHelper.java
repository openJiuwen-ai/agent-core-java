/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.signal.EvolutionTarget;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Private record persistence helpers for {@code EvolutionStore}.
 *
 * <p>Mirrors Python's {@code StoreRecordsHelper} in
 * {@code openjiuwen/agent_evolving/checkpointing/store_records.py}.</p>
 */
public class StoreRecordsHelper {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final String EVOLUTION_FILENAME = "evolutions.json";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Map<String, String> LANGUAGE_TO_EXTENSION = Map.of(
            "python", "py",
            "javascript", "js",
            "typescript", "ts",
            "shell", "sh",
            "bash", "sh"
    );

    private final StoreRecordsStore store;

    public StoreRecordsHelper(StoreRecordsStore store) {
        this.store = store;
    }

    public CompletionStage<Void> persistScript(Path skillDir, EvolutionRecord record) {
        Path scriptsDir = skillDir.resolve("evolution").resolve("scripts");
        createDirectories(scriptsDir);

        EvolutionPatch change = requireChange(record);
        String language = isBlank(change.getScriptLanguage()) ? "py" : change.getScriptLanguage();
        String extension = LANGUAGE_TO_EXTENSION.getOrDefault(language, language);
        String filename = isBlank(change.getScriptFilename())
                ? record.getId() + "_script." + extension
                : change.getScriptFilename();
        Path scriptPath = scriptsDir.resolve(filename);
        String scriptContent = nullToEmpty(change.getContent());

        return store.writeFileText(scriptPath, scriptContent).thenAccept(ignored -> {
            LOGGER.info("[EvolutionStore] persisted script {} for record {}", filename, record.getId());
            change.setScriptFilename(filename);
            change.setContent("Script: " + filename + "\n"
                    + "Language: " + nullToEmpty(change.getScriptLanguage(), "unknown") + "\n"
                    + "Purpose: " + nullToEmpty(change.getScriptPurpose()));
        });
    }

    public CompletionStage<EvolutionLog> loadFullEvolutionLog(String name) {
        Path skillDir = store.resolveSkillDir(name, false);
        if (skillDir == null || !Files.isDirectory(skillDir)) {
            return CompletableFuture.completedFuture(EvolutionLog.empty(name));
        }

        Path evolutionPath = skillDir.resolve(EVOLUTION_FILENAME);
        if (!Files.isRegularFile(evolutionPath)) {
            return CompletableFuture.completedFuture(EvolutionLog.empty(name));
        }

        return store.readFileText(evolutionPath).handle((content, throwable) -> {
            if (throwable != null) {
                LOGGER.warning("[EvolutionStore] read {} failed: {}", evolutionPath.getFileName(), rootMessage(throwable));
                return EvolutionLog.empty(name);
            }
            if (isBlank(content)) {
                return EvolutionLog.empty(name);
            }
            try {
                Map<String, Object> data = OBJECT_MAPPER.readValue(content, MAP_TYPE);
                return EvolutionLog.fromDict(data);
            } catch (Exception exception) {
                LOGGER.warning("[EvolutionStore] parse {} failed: {}", evolutionPath.getFileName(), exception.getMessage());
                return EvolutionLog.empty(name);
            }
        });
    }

    public CompletionStage<Void> saveEvolutionLog(String name, EvolutionLog evolutionLog, Path skillDir) {
        Path targetDir = skillDir != null ? skillDir : store.resolveSkillDir(name, true);
        if (targetDir == null) {
            return CompletableFuture.completedFuture(null);
        }
        createDirectories(targetDir);

        Path evolutionPath = targetDir.resolve(EVOLUTION_FILENAME);
        Map<String, Object> expected = evolutionLog.toDict();
        String content;
        try {
            content = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(expected);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(storeError(
                    "failed to serialize " + evolutionPath,
                    exception
            ));
        }

        return writeFileTextAtomic(evolutionPath, content).thenCompose(ignored ->
                store.readFileText(evolutionPath).thenAccept(readback -> {
                    Map<String, Object> actual;
                    try {
                        actual = OBJECT_MAPPER.readValue(readback, MAP_TYPE);
                    } catch (Exception exception) {
                        throw storeError("failed to read back " + evolutionPath + ": invalid JSON", exception);
                    }
                    if (!actual.equals(expected)) {
                        throw storeError("failed to read back " + evolutionPath + ": content mismatch", null);
                    }
                }));
    }

    public CompletionStage<Void> writeFileTextAtomic(Path path, String content) {
        Path tempPath = path.resolveSibling("." + path.getFileName() + "." + UUID.randomUUID().toString().replace("-", "") + ".tmp");
        return store.writeFileText(tempPath, content).handle((ignored, writeFailure) -> {
            if (writeFailure != null) {
                cleanupTemp(tempPath);
                return CompletableFuture.<Void>failedFuture(storeError(
                        "failed to atomically write " + path + ": " + rootMessage(writeFailure),
                        rootCause(writeFailure)
                ));
            }
            try {
                moveAtomically(tempPath, path);
                return CompletableFuture.<Void>completedFuture(null);
            } catch (Exception exception) {
                cleanupTemp(tempPath);
                return CompletableFuture.<Void>failedFuture(storeError(
                        "failed to atomically write " + path + ": " + exception.getMessage(),
                        exception
                ));
            }
        }).thenCompose(stage -> stage);
    }

    public CompletionStage<EvolutionLog> appendRecordTransactional(
            String name,
            EvolutionRecord record
    ) {
        return appendRecordTransactional(name, record, null);
    }

    public CompletionStage<EvolutionLog> appendRecordTransactional(
            String name,
            EvolutionRecord record,
            Path skillDir
    ) {
        Path targetDir = skillDir != null ? skillDir : store.resolveSkillDir(name, true);
        if (targetDir == null) {
            return CompletableFuture.completedFuture(null);
        }
        createDirectories(targetDir);

        Path evolutionPath = targetDir.resolve(EVOLUTION_FILENAME);
        boolean hadLog = Files.exists(evolutionPath);
        String oldLogContent = readExistingFile(evolutionPath);
        Map<Path, String> projectionBackups = snapshotProjectionFiles(targetDir);
        EvolutionRecord preparedRecord = copyRecord(record);

        CompletionStage<Void> prepareStage = preparedRecord.getChange() != null
                && preparedRecord.getChange().getTarget() == EvolutionTarget.SCRIPT
                ? persistScript(targetDir, preparedRecord)
                : CompletableFuture.completedFuture(null);

        return prepareStage
                .thenCompose(ignored -> loadFullEvolutionLog(name))
                .thenCompose(log -> {
                    appendOrMergeRecord(log, preparedRecord);
                    log.setUpdatedAt(Instant.now().toString());
                    return saveEvolutionLog(name, log, targetDir)
                            .thenCompose(ignored -> store.renderEvolutionMarkdown(name))
                            .thenApply(ignored -> {
                                syncOriginalRecordAfterSuccess(record, preparedRecord);
                                LOGGER.info("[EvolutionStore] atomically wrote record {} for skill={}", record.getId(), name);
                                return log;
                            });
                })
                .handle((result, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    Throwable cause = rootCause(throwable);
                    try {
                        restoreProjectionFiles(targetDir, projectionBackups);
                        restoreTextFile(evolutionPath, hadLog ? oldLogContent : null);
                    } catch (Exception restoreFailure) {
                        cause.addSuppressed(restoreFailure);
                    }
                    return CompletableFuture.<EvolutionLog>failedFuture(cause);
                })
                .thenCompose(stage -> stage);
    }

    public CompletionStage<Integer> updateRecordScores(String name, Map<String, RecordUpdate> updates) {
        if (updates == null || updates.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return loadFullEvolutionLog(name).thenCompose(log -> {
            int updatedCount = 0;
            for (EvolutionRecord record : log.getEntries()) {
                RecordUpdate update = updates.get(record.getId());
                if (update == null) {
                    continue;
                }
                if (update.getScore().isPresent()) {
                    record.setScore(update.getScore().get());
                }
                if (update.getUsageStats() != null) {
                    record.setUsageStats(copyUsageStats(update.getUsageStats()));
                }
                updatedCount++;
            }

            if (updatedCount == 0) {
                return CompletableFuture.completedFuture(0);
            }

            log.setUpdatedAt(Instant.now().toString());
            int finalUpdatedCount = updatedCount;
            return saveEvolutionLog(name, log, null).thenApply(ignored -> {
                LOGGER.info("[EvolutionStore] updated {} record score(s) for skill={}", finalUpdatedCount, name);
                return finalUpdatedCount;
            });
        });
    }

    public CompletionStage<List<EvolutionRecord>> getRecordsByScore(String name, Double minScore) {
        return loadFullEvolutionLog(name).thenApply(log -> {
            List<EvolutionRecord> records = new ArrayList<>();
            for (EvolutionRecord record : log.getEntries()) {
                if (minScore == null || record.getScore() >= minScore) {
                    records.add(record);
                }
            }
            records.sort(Comparator.comparingDouble(EvolutionRecord::getScore).reversed());
            return records;
        });
    }

    public CompletionStage<Integer> deleteRecords(String name, List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return loadFullEvolutionLog(name).thenCompose(log -> {
            Set<String> ids = new LinkedHashSet<>(recordIds);
            int originalCount = log.getEntries().size();
            log.getEntries().removeIf(record -> ids.contains(record.getId()));
            int deletedCount = originalCount - log.getEntries().size();

            if (deletedCount == 0) {
                return CompletableFuture.completedFuture(0);
            }

            log.setUpdatedAt(Instant.now().toString());
            return saveEvolutionLog(name, log, null)
                    .thenCompose(ignored -> store.renderEvolutionMarkdown(name))
                    .thenApply(ignored -> {
                        LOGGER.info("[EvolutionStore] deleted {} record(s) for skill={}", deletedCount, name);
                        return deletedCount;
                    });
        });
    }

    public CompletionStage<Integer> markRecordsApplied(String name, List<String> recordIds) {
        if (recordIds == null || recordIds.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return loadFullEvolutionLog(name).thenCompose(log -> {
            Set<String> ids = new LinkedHashSet<>(recordIds);
            int updatedCount = 0;
            for (EvolutionRecord record : log.getEntries()) {
                if (ids.contains(record.getId()) && !record.isApplied()) {
                    record.setApplied(true);
                    updatedCount++;
                }
            }

            if (updatedCount == 0) {
                return CompletableFuture.completedFuture(0);
            }

            log.setUpdatedAt(Instant.now().toString());
            int finalUpdatedCount = updatedCount;
            return saveEvolutionLog(name, log, null)
                    .thenCompose(ignored -> store.renderEvolutionMarkdown(name))
                    .thenApply(ignored -> {
                        LOGGER.info("[EvolutionStore] marked {} record(s) as applied for skill={}", finalUpdatedCount, name);
                        return finalUpdatedCount;
                    });
        });
    }

    public CompletionStage<EvolutionRecord> mergeRecords(
            String name,
            String primaryId,
            List<String> removeIds,
            String newContent,
            Double newScore
    ) {
        return loadFullEvolutionLog(name).thenCompose(log -> {
            EvolutionRecord primaryRecord = null;
            List<EvolutionRecord> recordsToRemove = new ArrayList<>();
            List<Double> allScores = new ArrayList<>();
            Set<String> removeIdSet = removeIds == null ? Set.of() : new LinkedHashSet<>(removeIds);

            for (EvolutionRecord record : log.getEntries()) {
                if (primaryId.equals(record.getId())) {
                    primaryRecord = record;
                } else if (removeIdSet.contains(record.getId())) {
                    recordsToRemove.add(record);
                    allScores.add(record.getScore());
                }
            }

            if (primaryRecord == null) {
                LOGGER.warning("[EvolutionStore] mergeRecords: primary record {} not found", primaryId);
                return CompletableFuture.completedFuture(null);
            }

            allScores.add(primaryRecord.getScore());
            double finalScore = newScore != null
                    ? newScore
                    : allScores.stream().mapToDouble(Double::doubleValue).max().orElse(primaryRecord.getScore());

            requireChange(primaryRecord).setContent(newContent);
            primaryRecord.setSummary(null);
            primaryRecord.setScore(finalScore);
            primaryRecord.setTimestamp(Instant.now().toString());
            log.getEntries().removeAll(recordsToRemove);
            log.setUpdatedAt(Instant.now().toString());

            EvolutionRecord finalPrimaryRecord = primaryRecord;
            int removedCount = recordsToRemove.size();
            return saveEvolutionLog(name, log, null)
                    .thenCompose(ignored -> store.renderEvolutionMarkdown(name))
                    .thenApply(ignored -> {
                        LOGGER.info("[EvolutionStore] merged {} record(s) into {} for skill={}", removedCount, primaryId, name);
                        return finalPrimaryRecord;
                    });
        });
    }

    public CompletionStage<EvolutionRecord> updateRecordContent(
            String name,
            String recordId,
            String newContent,
            Double newScore
    ) {
        return loadFullEvolutionLog(name).thenCompose(log -> {
            EvolutionRecord targetRecord = null;
            for (EvolutionRecord record : log.getEntries()) {
                if (recordId.equals(record.getId())) {
                    targetRecord = record;
                    break;
                }
            }

            if (targetRecord == null) {
                LOGGER.warning("[EvolutionStore] updateRecordContent: record {} not found", recordId);
                return CompletableFuture.completedFuture(null);
            }

            requireChange(targetRecord).setContent(newContent);
            targetRecord.setSummary(null);
            if (newScore != null) {
                targetRecord.setScore(newScore);
            }
            targetRecord.setTimestamp(Instant.now().toString());
            log.setUpdatedAt(Instant.now().toString());

            EvolutionRecord finalTargetRecord = targetRecord;
            return saveEvolutionLog(name, log, null)
                    .thenCompose(ignored -> store.renderEvolutionMarkdown(name))
                    .thenApply(ignored -> {
                        LOGGER.info("[EvolutionStore] updated record {} for skill={}", recordId, name);
                        return finalTargetRecord;
                    });
        });
    }

    private static EvolutionPatch requireChange(EvolutionRecord record) {
        EvolutionPatch change = record.getChange();
        if (change == null) {
            throw new IllegalArgumentException("evolution record change must not be null");
        }
        return change;
    }

    private static void appendOrMergeRecord(EvolutionLog evolutionLog, EvolutionRecord record) {
        String mergeTarget = requireChange(record).getMergeTarget();
        if (isBlank(mergeTarget)) {
            evolutionLog.getEntries().add(record);
            return;
        }

        for (int index = 0; index < evolutionLog.getEntries().size(); index++) {
            EvolutionRecord existing = evolutionLog.getEntries().get(index);
            if (mergeTarget.equals(existing.getId())) {
                evolutionLog.getEntries().set(index, record);
                LOGGER.info("[EvolutionStore] merged record {} replacing {}", record.getId(), mergeTarget);
                return;
            }
        }
        evolutionLog.getEntries().add(record);
    }

    private static EvolutionRecord copyRecord(EvolutionRecord source) {
        EvolutionRecord copy = new EvolutionRecord();
        copy.setId(source.getId());
        copy.setSource(source.getSource());
        copy.setTimestamp(source.getTimestamp());
        copy.setContext(source.getContext());
        copy.setChange(copyPatch(source.getChange()));
        copy.setApplied(source.isApplied());
        copy.setScore(source.getScore());
        copy.setUsageStats(copyUsageStats(source.getUsageStats()));
        copy.setSkillVersion(source.getSkillVersion());
        copy.setSummary(source.getSummary());
        return copy;
    }

    private static EvolutionPatch copyPatch(EvolutionPatch source) {
        if (source == null) {
            return null;
        }
        EvolutionPatch copy = new EvolutionPatch();
        copy.setSection(source.getSection());
        copy.setAction(source.getAction());
        copy.setContent(source.getContent());
        copy.setTarget(source.getTarget());
        copy.setSkipReason(source.getSkipReason());
        copy.setMergeTarget(source.getMergeTarget());
        copy.setScriptFilename(source.getScriptFilename());
        copy.setScriptLanguage(source.getScriptLanguage());
        copy.setScriptPurpose(source.getScriptPurpose());
        copy.setKeywords(source.getKeywords());
        copy.setSummary(source.getSummary());
        return copy;
    }

    private static UsageStats copyUsageStats(UsageStats source) {
        if (source == null) {
            return null;
        }
        UsageStats copy = new UsageStats();
        copy.setTimesPresented(source.getTimesPresented());
        copy.setTimesUsed(source.getTimesUsed());
        copy.setTimesPositive(source.getTimesPositive());
        copy.setTimesNegative(source.getTimesNegative());
        copy.setLastPresentedAt(source.getLastPresentedAt());
        copy.setLastEvaluatedAt(source.getLastEvaluatedAt());
        return copy;
    }

    private static void syncOriginalRecordAfterSuccess(EvolutionRecord original, EvolutionRecord prepared) {
        if (prepared.getChange() != null && prepared.getChange().getTarget() == EvolutionTarget.SCRIPT) {
            EvolutionPatch originalChange = requireChange(original);
            EvolutionPatch preparedChange = prepared.getChange();
            originalChange.setScriptFilename(preparedChange.getScriptFilename());
            originalChange.setContent(preparedChange.getContent());
        }
    }

    private static Map<Path, String> snapshotProjectionFiles(Path skillDir) {
        Map<Path, String> backups = new LinkedHashMap<>();
        Path evolutionDir = skillDir.resolve("evolution");
        if (Files.isDirectory(evolutionDir)) {
            try (var stream = Files.walk(evolutionDir)) {
                stream.filter(Files::isRegularFile).forEach(path -> backups.put(path, readExistingFile(path)));
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to snapshot projection files under " + evolutionDir, exception);
            }
        }

        Path skillMarkdown = skillDir.resolve("SKILL.md");
        if (Files.isRegularFile(skillMarkdown)) {
            backups.put(skillMarkdown, readExistingFile(skillMarkdown));
        }
        return backups;
    }

    private static void restoreProjectionFiles(Path skillDir, Map<Path, String> backups) {
        Path evolutionDir = skillDir.resolve("evolution");
        removeUnbackedFiles(evolutionDir, backups);
        for (Map.Entry<Path, String> entry : backups.entrySet()) {
            restoreTextFile(entry.getKey(), entry.getValue());
        }
        removeEmptyDirectories(evolutionDir);
    }

    private static void removeUnbackedFiles(Path evolutionDir, Map<Path, String> backups) {
        if (!Files.isDirectory(evolutionDir)) {
            return;
        }
        try (var stream = Files.walk(evolutionDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !backups.containsKey(path))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("Failed to delete rollback file " + path, exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to walk rollback directory " + evolutionDir, exception);
        }
    }

    private static void removeEmptyDirectories(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Python uses best-effort cleanup here as well.
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clean rollback directories under " + root, exception);
        }
    }

    private static void restoreTextFile(Path path, String content) {
        try {
            if (content == null) {
                Files.deleteIfExists(path);
                return;
            }
            createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to restore file " + path, exception);
        }
    }

    private static String readExistingFile(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file " + path, exception);
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void cleanupTemp(Path tempPath) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException ignored) {
            // Best-effort cleanup only.
        }
    }

    private static BaseError storeError(String message, Throwable cause) {
        return ErrorHelper.buildError(
                StatusCode.TOOLCHAIN_EVOLVING_SKILL_STORE_EXECUTION_ERROR,
                null,
                null,
                cause,
                Map.of("error_msg", message)
        );
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cause = rootCause(throwable);
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static void createDirectories(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create directory: " + path, exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToEmpty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Strongly typed score/usage update payload for stored evolution records.
     *
     * <p>Mirrors Python's score update dict entries in
     * {@code openjiuwen/agent_evolving/checkpointing/store_records.py}.</p>
     */
    public static final class RecordUpdate {

        private final Double score;
        private final UsageStats usageStats;

        public RecordUpdate(Double score, UsageStats usageStats) {
            this.score = score;
            this.usageStats = usageStats;
        }

        public static RecordUpdate scoreOnly(double score) {
            return new RecordUpdate(score, null);
        }

        public static RecordUpdate usageOnly(UsageStats usageStats) {
            return new RecordUpdate(null, usageStats);
        }

        public static RecordUpdate of(Double score, UsageStats usageStats) {
            return new RecordUpdate(score, usageStats);
        }

        public Optional<Double> getScore() {
            return Optional.ofNullable(score);
        }

        public UsageStats getUsageStats() {
            return usageStats;
        }
    }

    /**
     * Boundary used by {@link StoreRecordsHelper} to call the surrounding skill store.
     *
     * <p>Mirrors Python's store interaction surface in
     * {@code openjiuwen/agent_evolving/checkpointing/store_records.py}.</p>
     */
    public interface StoreRecordsStore {

        Path resolveSkillDir(String name, boolean create);

        CompletionStage<String> readFileText(Path path);

        CompletionStage<Void> writeFileText(Path path, String content);

        CompletionStage<Void> renderEvolutionMarkdown(String name);
    }
}
