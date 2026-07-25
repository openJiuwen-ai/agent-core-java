/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * File-based JSONL trajectory store.
 * <p>
 * Mirrors Python's {@code FileTrajectoryStore} in
 * {@code openjiuwen/agent_evolving/trajectory/store.py}.
 * </p>
 */
public class FileTrajectoryStore implements TrajectoryStore {

    private static final String DEFAULT_VERSION = "default";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private static final ObjectMapper LEGACY_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final Path baseDir;

    /**
     * Create a file-backed trajectory store.
     *
     * @param baseDir directory containing trajectory JSONL files
     */
    public FileTrajectoryStore(Path baseDir) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir must not be null");
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create trajectory store directory: " + baseDir, e);
        }
    }

    @Override
    public void save(Trajectory trajectory, String version) {
        Path filePath = getFilePath(version);
        try {
            Files.createDirectories(baseDir);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    filePath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                writer.write(MAPPER.writeValueAsString(trajectory));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save trajectory: " + trajectory.getExecutionId(), e);
        }
    }

    @Override
    public Trajectory load(String executionId, String version) {
        for (Trajectory trajectory : readTrajectories(version)) {
            if (Objects.equals(executionId, trajectory.getExecutionId())) {
                return trajectory;
            }
        }
        return null;
    }

    @Override
    public List<Trajectory> queryBySessionId(String sessionId) {
        return query(sessionId, null, null);
    }

    @Override
    public List<Trajectory> query(String sessionId, String executionId, String version) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (sessionId != null) {
            filters.put("session_id", sessionId);
        }
        if (executionId != null) {
            filters.put("execution_id", executionId);
        }
        return query(version, filters);
    }

    @Override
    public List<Trajectory> query(String version, Map<String, Object> filters) {
        List<Trajectory> results = new ArrayList<>();
        for (Trajectory trajectory : readTrajectories(version)) {
            if (!matchesFilters(trajectory, filters)) {
                continue;
            }
            results.add(trajectory);
        }
        return results;
    }

    private Path getFilePath(String version) {
        String resolvedVersion = version == null || version.isBlank() ? DEFAULT_VERSION : version;
        return baseDir.resolve("trajectories_" + resolvedVersion + ".jsonl");
    }

    private List<Trajectory> readTrajectories(String version) {
        Path filePath = getFilePath(version);
        if (!Files.exists(filePath)) {
            return List.of();
        }

        List<Trajectory> trajectories = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                Trajectory trajectory = parseTrajectory(line);
                if (trajectory != null) {
                    trajectories.add(trajectory);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read trajectory store: " + filePath, e);
        }
        return trajectories;
    }

    private static Trajectory parseTrajectory(String line) {
        try {
            Trajectory trajectory = MAPPER.readValue(line, Trajectory.class);
            if (trajectory.getExecutionId() == null && line.contains("\"executionId\"")) {
                trajectory = LEGACY_MAPPER.readValue(line, Trajectory.class);
            }
            if (trajectory.getExecutionId() == null) {
                return null;
            }
            normalizeStepDetails(trajectory);
            return trajectory;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static boolean matchesFilters(Trajectory trajectory, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (!Objects.equals(fieldValue(trajectory, entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static Object fieldValue(Trajectory trajectory, String key) {
        return switch (key) {
            case "execution_id", "executionId" -> trajectory.getExecutionId();
            case "session_id", "sessionId" -> trajectory.getSessionId();
            case "case_id", "caseId" -> trajectory.getCaseId();
            case "source" -> trajectory.getSource();
            default -> trajectory.getMeta() != null ? trajectory.getMeta().get(key) : null;
        };
    }

    private static void normalizeStepDetails(Trajectory trajectory) {
        if (trajectory.getSteps() == null) {
            return;
        }
        for (TrajectoryStep step : trajectory.getSteps()) {
            Object detail = step.getDetail();
            if (detail instanceof Map<?, ?> detailMap) {
                if (hasKey(detailMap, "toolName") || hasKey(detailMap, "tool_name")) {
                    step.setDetail(toToolCallDetail(detailMap));
                } else if (hasKey(detailMap, "model") || hasKey(detailMap, "messages")) {
                    step.setDetail(toLlmCallDetail(detailMap));
                }
            }
        }
    }

    private static ToolCallDetail toToolCallDetail(Map<?, ?> detailMap) {
        return new ToolCallDetail(
                stringValue(first(detailMap, "toolName", "tool_name")),
                first(detailMap, "callArgs", "call_args"),
                first(detailMap, "callResult", "call_result"),
                stringValue(first(detailMap, "toolDescription", "tool_description")),
                stringObjectMap(first(detailMap, "toolSchema", "tool_schema")),
                stringValue(first(detailMap, "toolCallId", "tool_call_id"))
        );
    }

    private static LLMCallDetail toLlmCallDetail(Map<?, ?> detailMap) {
        return new LLMCallDetail(
                stringValue(first(detailMap, "model")),
                listOfObjects(first(detailMap, "messages")),
                stringObjectMap(first(detailMap, "response")),
                toToolList(first(detailMap, "tools")),
                stringObjectMap(first(detailMap, "usage")),
                stringObjectMap(first(detailMap, "meta"))
        );
    }

    private static boolean hasKey(Map<?, ?> map, String key) {
        return map.containsKey(key);
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> input)) {
            return null;
        }
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            output.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return output;
    }

    private static List<Map<String, Object>> listOfStringObjectMaps(Object value) {
        if (!(value instanceof List<?> input)) {
            return null;
        }
        List<Map<String, Object>> output = new ArrayList<>();
        for (Object item : input) {
            Map<String, Object> mapped = stringObjectMap(item);
            if (mapped != null) {
                output.add(mapped);
            }
        }
        return output;
    }

    private static List<Object> listOfObjects(Object value) {
        if (!(value instanceof List<?> input)) {
            return null;
        }
        return new ArrayList<>(input);
    }

    private static List<Object> toToolList(Object value) {
        if (!(value instanceof List<?> input)) {
            return null;
        }
        List<Object> output = new ArrayList<>();
        for (Object item : input) {
            if (item instanceof Map<?, ?>) {
                output.add(stringObjectMap(item));
            } else {
                output.add(item);
            }
        }
        return output;
    }
}
