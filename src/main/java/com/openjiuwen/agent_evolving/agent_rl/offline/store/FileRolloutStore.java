/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.offline.store;

import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage;
import com.openjiuwen.agent_evolving.agent_rl.schemas.RolloutWithReward;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * File-based implementation of RolloutPersistence.
 * <p>
 * Training and validation rollouts are stored in separate sub-directories.
 * Files are split by step ranges controlled by {@code flushInterval}.
 * <p>
 * Directory layout:
 * <pre>
 * save_path/
 * ├── train/
 * │   └── rollouts/
 * │       ├── steps_000000_000099.jsonl
 * │       └── ...
 * ├── val/
 * │   └── rollouts/
 * │       ├── steps_000000_000099.jsonl
 * │       └── ...
 * └── step_summaries/
 *     └── steps_000000_000099.jsonl
 * </pre>
 * <p>
 * Each {@code .jsonl} file contains one JSON object per line.
 * <p>
 * Mirrors Python's {@code FileRolloutStore} in
 * {@code openjiuwen.agent_evolving.agent_rl.offline.store.file_store}.
 */
public class FileRolloutStore implements RolloutPersistence {

    private static final Logger logger = Logger.getLogger(FileRolloutStore.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
    private final Path savePath;
    private final int flushInterval;
    private final Path trainRolloutDir;
    private final Path valRolloutDir;
    private final Path summaryDir;
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Initialize the file rollout store.
     *
     * @param savePath Root directory for all rollout output files.
     * @param flushInterval Number of steps per file (e.g. 100 means
     *        steps 0-99 go into one file, 100-199 into the next, etc.).
     */
    public FileRolloutStore(String savePath, int flushInterval) {
        this.savePath = Path.of(savePath);
        this.flushInterval = Math.max(1, flushInterval);
        
        this.trainRolloutDir = this.savePath.resolve("train").resolve("rollouts");
        this.valRolloutDir = this.savePath.resolve("val").resolve("rollouts");
        this.summaryDir = this.savePath.resolve("step_summaries");
        
        // Create directories
        try {
            Files.createDirectories(trainRolloutDir);
            Files.createDirectories(valRolloutDir);
            Files.createDirectories(summaryDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directories: " + e.getMessage(), e);
        }
        
        logger.info("FileRolloutStore initialised: path=" + this.savePath + 
            ", flush_interval=" + this.flushInterval);
    }

    /**
     * Return the JSONL file path corresponding to the given step.
     */
    private Path fileForStep(Path baseDir, int step) {
        int lo = (step / flushInterval) * flushInterval;
        int hi = lo + flushInterval - 1;
        return baseDir.resolve(String.format("steps_%06d_%06d.jsonl", lo, hi));
    }

    /**
     * Append a single JSON line to a file (thread-safe).
     */
    private void appendJsonl(Path path, Map<String, Object> obj) throws IOException {
        String line = objectMapper.writeValueAsString(obj) + "\n";
        lock.lock();
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(path, 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND)) {
                writer.write(line);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Return the rollout directory for the given phase (train or val).
     */
    private Path rolloutDirForPhase(String phase) {
        if ("val".equals(phase)) {
            return valRolloutDir;
        }
        return trainRolloutDir;
    }

    // -- RolloutPersistence interface implementation --

    /**
     * Persist a rollout to train/val JSONL file based on step range.
     *
     * @param step Training step number
     * @param taskId Task identifier
     * @param rollout Rollout message to persist
     * @param phase "train" or "val"
     */
    @Override
    public void saveRollout(int step, String taskId, Object rollout, String phase) {
        RolloutMessage rolloutMsg;
        if (rollout instanceof RolloutMessage) {
            rolloutMsg = (RolloutMessage) rollout;
        } else {
            logger.warning("FileRolloutStore: rollout is not a RolloutMessage, skipping");
            return;
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("step", step);
        doc.put("task_id", taskId);
        doc.put("origin_task_id", rolloutMsg.getOriginTaskId());
        doc.put("rollout_id", rolloutMsg.getRolloutId());
        doc.put("turns", serializeRolloutInfo(rolloutMsg.getRolloutInfo()));
        doc.put("reward_list", rolloutMsg.getRewardList());
        doc.put("global_reward", rolloutMsg.getGlobalReward());
        doc.put("turn_count", rolloutMsg.getTurnCount());
        doc.put("round_num", rolloutMsg.getRoundNum());
        doc.put("start_time", rolloutMsg.getStartTime());
        doc.put("end_time", rolloutMsg.getEndTime());
        doc.put("timestamp", Instant.now().toString());
        
        Path rolloutDir = rolloutDirForPhase(phase);
        try {
            appendJsonl(fileForStep(rolloutDir, step), doc);
        } catch (IOException e) {
            logger.warning("FileRolloutStore: failed to save " + phase + 
                " rollout " + taskId + ": " + e.getMessage());
        }
    }

    /**
     * Persist per-step training metrics to step_summaries JSONL file.
     *
     * @param step Training step number
     * @param metrics Metrics map to persist
     */
    @Override
    public void saveStepSummary(int step, Map<String, Object> metrics) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("step", step);
        doc.put("metrics", metrics);
        doc.put("timestamp", Instant.now().toString());
        
        try {
            appendJsonl(fileForStep(summaryDir, step), doc);
        } catch (IOException e) {
            logger.warning("FileRolloutStore: failed to save step summary: " + e.getMessage());
        }
    }

    /**
     * Scan rollout JSONL files (both train and val) and return matching entries.
     *
     * @param filters Filter criteria (key-value pairs to match)
     * @param limit Maximum number of results to return
     * @return List of matching rollout documents
     */
    @Override
    public List<Map<String, Object>> queryRollouts(Map<String, Object> filters, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Path rolloutDir : List.of(trainRolloutDir, valRolloutDir)) {
            if (!Files.exists(rolloutDir)) {
                continue;
            }
            
            try (Stream<Path> files = Files.list(rolloutDir)
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))) {
                
                for (Path fpath : files.toList()) {
                    if (!fpath.getFileName().toString().endsWith(".jsonl")) {
                        continue;
                    }
                    
                    try (BufferedReader reader = Files.newBufferedReader(fpath)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty()) {
                                continue;
                            }
                            
                            try {
                                Map<String, Object> doc = objectMapper.readValue(line, 
                                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                                
                                // Check filters
                                boolean matches = true;
                                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                                    Object docValue = doc.get(entry.getKey());
                                    if (docValue == null || !docValue.equals(entry.getValue())) {
                                        matches = false;
                                        break;
                                    }
                                }
                                
                                if (matches) {
                                    results.add(doc);
                                    if (results.size() >= limit) {
                                        return results;
                                    }
                                }
                            } catch (com.fasterxml.jackson.core.JsonParseException e) {
                                // Skip malformed lines
                                continue;
                            }
                        }
                    } catch (IOException e) {
                        logger.warning("Error reading file " + fpath + ": " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                logger.warning("Error listing directory " + rolloutDir + ": " + e.getMessage());
            }
        }
        
        return results;
    }

    /**
     * Release resources. No-op for file store; logs closure.
     */
    @Override
    public void close() {
        logger.info("FileRolloutStore closed (path=" + savePath + ")");
    }

    /**
     * Serialize rollout info list to a list of maps.
     */
    private List<Map<String, Object>> serializeRolloutInfo(List<?> rolloutInfo) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rolloutInfo == null) {
            return result;
        }
        for (Object item : rolloutInfo) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            } else {
                // Convert POJO to map using Jackson
                try {
                    Map<String, Object> map = objectMapper.convertValue(item, 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    result.add(map);
                } catch (Exception e) {
                    logger.warning("Failed to serialize rollout info: " + e.getMessage());
                }
            }
        }
        return result;
    }

    // -- Getters --

    public Path getSavePath() { return savePath; }
    public int getFlushInterval() { return flushInterval; }
    public Path getTrainRolloutDir() { return trainRolloutDir; }
    public Path getValRolloutDir() { return valRolloutDir; }
    public Path getSummaryDir() { return summaryDir; }
}