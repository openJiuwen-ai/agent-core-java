/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.offline.store;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.schemas.RolloutMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * File-based implementation of rollout persistence.
 *
 * <p>Mirrors Python's {@code FileRolloutStore} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/store/file_store.py}.</p>
 */
public class FileRolloutStore implements RolloutPersistence {

    private static final Logger LOGGER = Logger.getLogger(FileRolloutStore.class.getName());
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Path savePath;
    private final int flushInterval;
    private final Path trainRolloutDir;
    private final Path valRolloutDir;
    private final Path summaryDir;
    private final ReentrantLock lock = new ReentrantLock();

    public FileRolloutStore(String savePath, int flushInterval) {
        this.savePath = Path.of(savePath);
        this.flushInterval = Math.max(1, flushInterval);
        this.trainRolloutDir = this.savePath.resolve("train").resolve("rollouts");
        this.valRolloutDir = this.savePath.resolve("val").resolve("rollouts");
        this.summaryDir = this.savePath.resolve("step_summaries");
        ensureDirectories();
        LOGGER.info("FileRolloutStore initialised: path=" + this.savePath + ", flush_interval=" + this.flushInterval);
    }

    @Override
    public void saveRollout(int step, String taskId, RolloutMessage rollout, String phase) {
        if (rollout == null) {
            LOGGER.warning("FileRolloutStore: rollout is null, skipping");
            return;
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("step", step);
        doc.put("task_id", taskId);
        doc.put("origin_task_id", rollout.getOriginTaskId());
        doc.put("rollout_id", rollout.getRolloutId());
        doc.put("turns", serializeRolloutInfo(rollout.getRolloutInfo()));
        doc.put("reward_list", rollout.getRewardList());
        doc.put("global_reward", rollout.getGlobalReward());
        doc.put("turn_count", rollout.getTurnCount());
        doc.put("round_num", rollout.getRoundNum());
        doc.put("start_time", rollout.getStartTime());
        doc.put("end_time", rollout.getEndTime());
        doc.put("timestamp", Instant.now().toString());

        try {
            appendJsonl(fileForStep(rolloutDirForPhase(phase), step), doc);
        } catch (IOException exception) {
            LOGGER.warning("FileRolloutStore: failed to save " + phase + " rollout " + taskId + ": " + exception.getMessage());
        }
    }

    @Override
    public void saveStepSummary(int step, Map<String, Object> metrics) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("step", step);
        doc.put("metrics", metrics);
        doc.put("timestamp", Instant.now().toString());
        try {
            appendJsonl(fileForStep(summaryDir, step), doc);
        } catch (IOException exception) {
            LOGGER.warning("FileRolloutStore: failed to save step summary: " + exception.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Path rolloutDir : List.of(trainRolloutDir, valRolloutDir)) {
            if (!Files.exists(rolloutDir)) {
                continue;
            }
            try (Stream<Path> files = Files.list(rolloutDir)
                    .sorted((left, right) -> right.getFileName().toString().compareTo(left.getFileName().toString()))) {
                for (Path filePath : files.toList()) {
                    if (!filePath.getFileName().toString().endsWith(".jsonl")) {
                        continue;
                    }
                    try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmed = line.trim();
                            if (trimmed.isEmpty()) {
                                continue;
                            }
                            try {
                                Map<String, Object> doc = OBJECT_MAPPER.readValue(trimmed, MAP_TYPE);
                                if (matchesFilters(doc, filters)) {
                                    results.add(doc);
                                    if (results.size() >= limit) {
                                        return results;
                                    }
                                }
                            } catch (JsonParseException ignored) {
                                // Skip malformed lines to mirror the Python store's best-effort scan.
                            }
                        }
                    } catch (IOException exception) {
                        LOGGER.warning("Error reading file " + filePath + ": " + exception.getMessage());
                    }
                }
            } catch (IOException exception) {
                LOGGER.warning("Error listing directory " + rolloutDir + ": " + exception.getMessage());
            }
        }

        return results;
    }

    @Override
    public void close() {
        LOGGER.info("FileRolloutStore closed (path=" + savePath + ")");
    }

    public Path getSavePath() {
        return savePath;
    }

    public int getFlushInterval() {
        return flushInterval;
    }

    public Path getTrainRolloutDir() {
        return trainRolloutDir;
    }

    public Path getValRolloutDir() {
        return valRolloutDir;
    }

    public Path getSummaryDir() {
        return summaryDir;
    }

    private void ensureDirectories() {
        try {
            Files.createDirectories(trainRolloutDir);
            Files.createDirectories(valRolloutDir);
            Files.createDirectories(summaryDir);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create directories: " + exception.getMessage(), exception);
        }
    }

    private Path fileForStep(Path baseDir, int step) {
        int lo = (step / flushInterval) * flushInterval;
        int hi = lo + flushInterval - 1;
        return baseDir.resolve(String.format("steps_%06d_%06d.jsonl", lo, hi));
    }

    private void appendJsonl(Path path, Map<String, Object> obj) throws IOException {
        String line = OBJECT_MAPPER.writeValueAsString(obj) + "\n";
        lock.lock();
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        )) {
            writer.write(line);
        } finally {
            lock.unlock();
        }
    }

    private Path rolloutDirForPhase(String phase) {
        return "val".equals(phase) ? valRolloutDir : trainRolloutDir;
    }

    private static boolean matchesFilters(Map<String, Object> doc, Map<String, Object> filters) {
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object docValue = doc.get(entry.getKey());
            if (docValue == null || !docValue.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private List<Map<String, Object>> serializeRolloutInfo(List<?> rolloutInfo) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rolloutInfo == null) {
            return result;
        }
        for (Object item : rolloutInfo) {
            try {
                result.add(OBJECT_MAPPER.convertValue(item, MAP_TYPE));
            } catch (IllegalArgumentException exception) {
                LOGGER.warning("Failed to serialize rollout info: " + exception.getMessage());
            }
        }
        return result;
    }
}
