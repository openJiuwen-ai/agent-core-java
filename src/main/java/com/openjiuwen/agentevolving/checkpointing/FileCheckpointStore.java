/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.checkpointing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal usable checkpoint store backed by a local JSON file.
 *
 * <p>Mirrors Python's {@code FileCheckpointStore} in
 * {@code openjiuwen/agent_evolving/checkpointing/store_file.py}.
 */
public class FileCheckpointStore {

    private static final String DEFAULT_FILENAME = "latest.json";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private final String baseDir;

    public FileCheckpointStore(String baseDir) {
        this.baseDir = baseDir;
        ensureDir();
    }

    public String saveCheckpoint(EvolveCheckpoint checkpoint) {
        return saveCheckpoint(checkpoint, DEFAULT_FILENAME);
    }

    public String saveCheckpoint(EvolveCheckpoint checkpoint, String filename) {
        if (baseDir == null) {
            return null;
        }
        ensureDir();
        Path path = Path.of(baseDir).resolve(filename == null || filename.isBlank() ? DEFAULT_FILENAME : filename);
        try {
            Map<String, Object> serialized = toJsonCompatible(checkpoint);
            String json = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(serialized);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            return path.toString();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save checkpoint: " + path, exception);
        }
    }

    public EvolveCheckpoint loadCheckpoint(String path) {
        if (baseDir == null || path == null || !Files.exists(Path.of(path))) {
            return null;
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(Files.readString(Path.of(path), StandardCharsets.UTF_8), MAP_TYPE);
            return OBJECT_MAPPER.convertValue(raw, EvolveCheckpoint.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load checkpoint: " + path, exception);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> loadStateDict(String path) {
        if (baseDir == null || path == null || !Files.exists(Path.of(path))) {
            return null;
        }
        try {
            Map<String, Object> raw = OBJECT_MAPPER.readValue(Files.readString(Path.of(path), StandardCharsets.UTF_8), MAP_TYPE);
            if (!raw.containsKey("operators_state")) {
                return null;
            }
            Object operatorsState = raw.get("operators_state");
            if (!(operatorsState instanceof Map<?, ?> input)) {
                return Map.of();
            }
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : input.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Map<?, ?> nested) {
                    Map<String, Object> nestedMap = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                        nestedMap.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
                    }
                    result.put(String.valueOf(entry.getKey()), nestedMap);
                } else {
                    result.put(String.valueOf(entry.getKey()), Map.of());
                }
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load checkpoint state dict: " + path, exception);
        }
    }

    private void ensureDir() {
        if (baseDir == null) {
            return;
        }
        try {
            Files.createDirectories(Path.of(baseDir));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create checkpoint directory: " + baseDir, exception);
        }
    }

    private static Map<String, Object> toJsonCompatible(Object value) {
        Object normalized = normalize(value);
        if (normalized instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return OBJECT_MAPPER.convertValue(normalized, MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private static Object normalize(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalize(item));
            }
            return normalized;
        }
        if (value instanceof Object[] array) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : array) {
                normalized.add(normalize(item));
            }
            return normalized;
        }
        return normalize(OBJECT_MAPPER.convertValue(value, MAP_TYPE));
    }
}
