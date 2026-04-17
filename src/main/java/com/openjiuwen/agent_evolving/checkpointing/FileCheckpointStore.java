/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.checkpointing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.openjiuwen.core.common.logging.Loggers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal usable checkpoint store: local JSON file.
 *
 * <p>Does not depend on core checkpointer (avoids polluting core lifecycle semantics).
 * Can run in any environment, convenient for debugging and auditing.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.checkpointing.store_file.FileCheckpointStore}.
 */
public class FileCheckpointStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final String baseDir;

    /**
     * Create with base directory.
     *
     * @param baseDir Base directory for checkpoint files
     */
    public FileCheckpointStore(String baseDir) {
        this.baseDir = baseDir;
        ensureDir();
    }

    private void ensureDir() {
        if (baseDir != null) {
            try {
                Files.createDirectories(Paths.get(baseDir));
            } catch (IOException e) {
                Loggers.AGENT.warn("Failed to create checkpoint directory: {}", baseDir);
            }
        }
    }

    /**
     * Save checkpoint to file.
     *
     * @param checkpoint Checkpoint to save
     * @param filename   Target filename
     * @return Path to saved file, or null on failure
     */
    public String saveCheckpoint(EvolveCheckpoint checkpoint, String filename) {
        if (baseDir == null) {
            return null;
        }
        ensureDir();
        Path path = Paths.get(baseDir, filename != null ? filename : "latest.json");
        try {
            Map<String, Object> normalized = normalizeCheckpointRaw(
                    OBJECT_MAPPER.convertValue(checkpoint, new TypeReference<Map<String, Object>>() {})
            );
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(normalized);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            return path.toString();
        } catch (IOException e) {
            Loggers.AGENT.error("Failed to save checkpoint: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Load checkpoint from file.
     *
     * @param path Path to checkpoint file
     * @return Loaded checkpoint, or null on failure
     */
    public EvolveCheckpoint loadCheckpoint(String path) {
        if (baseDir == null) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Map<String, Object> raw = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> normalized = normalizeCheckpointRaw(raw);
            return OBJECT_MAPPER.convertValue(normalized, EvolveCheckpoint.class);
        } catch (IOException e) {
            Loggers.AGENT.error("Failed to load checkpoint: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Deep-learning style inference loader.
     *
     * <p>A single, simple API for inference side that reads `operators_state` from a checkpoint JSON.
     *
     * @param path Path to checkpoint file
     * @return Operators state map, or null on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> loadStateDict(String path) {
        if (baseDir == null) {
            return null;
        }
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Map<String, Object> raw = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
            boolean hasOperatorsState = raw.containsKey("operators_state") || raw.containsKey("operatorsState");
            if (!hasOperatorsState) {
                return null;
            }
            Map<String, Object> normalized = normalizeCheckpointRaw(raw);
            Object operatorsState = normalized.get("operators_state");
            if (operatorsState instanceof Map) {
                return (Map<String, Map<String, Object>>) operatorsState;
            }
            return new HashMap<>();
        } catch (IOException e) {
            Loggers.AGENT.error("Failed to load state dict: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeCheckpointRaw(Map<String, Object> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (raw == null) {
            return normalized;
        }
        normalized.put("version", raw.get("version"));
        normalized.put("run_id", firstPresent(raw, "run_id", "runId"));
        normalized.put("step", asMap(firstPresent(raw, "step")));
        normalized.put("best", normalizeBestMap(asMap(firstPresent(raw, "best"))));
        normalized.put("seed", firstPresent(raw, "seed"));
        normalized.put("operators_state", asNestedMap(firstPresent(raw, "operators_state", "operatorsState")));
        normalized.put("updater_state", asMap(firstPresent(raw, "updater_state", "updaterState")));
        normalized.put("searcher_state", asMap(firstPresent(raw, "searcher_state", "searcherState")));
        normalized.put("last_metrics", normalizeLastMetricsMap(asMap(firstPresent(raw, "last_metrics", "lastMetrics"))));
        return normalized;
    }

    private Object firstPresent(Map<String, Object> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                return raw.get(key);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> asNestedMap(Object value) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            result.put(key, asMap(entry.getValue()));
        }
        return result;
    }

    private Map<String, Object> normalizeBestMap(Map<String, Object> best) {
        Map<String, Object> normalized = new LinkedHashMap<>(best);
        if (normalized.containsKey("bestScore") && !normalized.containsKey("best_score")) {
            normalized.put("best_score", normalized.remove("bestScore"));
        }
        return normalized;
    }

    private Map<String, Object> normalizeLastMetricsMap(Map<String, Object> lastMetrics) {
        Map<String, Object> normalized = new LinkedHashMap<>(lastMetrics);
        if (normalized.containsKey("currentEpochScore") && !normalized.containsKey("current_epoch_score")) {
            normalized.put("current_epoch_score", normalized.remove("currentEpochScore"));
        }
        return normalized;
    }
}
