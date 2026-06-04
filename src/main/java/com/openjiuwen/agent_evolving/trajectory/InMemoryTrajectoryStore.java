/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * In-memory trajectory store for testing and development.
 * <p>
 * Mirrors Python's {@code InMemoryTrajectoryStore} in
 * {@code openjiuwen.agent_evolving.trajectory.store}.
 * </p>
 */
public class InMemoryTrajectoryStore implements TrajectoryStore {

    private static final String DEFAULT_VERSION = "default";

    private final Map<String, Map<String, Trajectory>> data = new LinkedHashMap<>();

    @Override
    public synchronized void save(Trajectory trajectory, String version) {
        String resolvedVersion = resolveVersion(version);
        data.computeIfAbsent(resolvedVersion, ignored -> new LinkedHashMap<>())
                .put(trajectory.getExecutionId(), trajectory);
    }

    @Override
    public synchronized Trajectory load(String executionId, String version) {
        return data.getOrDefault(resolveVersion(version), Map.of()).get(executionId);
    }

    @Override
    public synchronized List<Trajectory> queryBySessionId(String sessionId) {
        List<Trajectory> results = new ArrayList<>();
        for (Map<String, Trajectory> versionData : data.values()) {
            for (Trajectory trajectory : versionData.values()) {
                if (sessionId == null || sessionId.equals(trajectory.getSessionId())) {
                    results.add(trajectory);
                }
            }
        }
        return results;
    }

    @Override
    public synchronized List<Trajectory> query(String sessionId, String executionId, String version) {
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
    public synchronized List<Trajectory> query(String version, Map<String, Object> filters) {
        return data.getOrDefault(resolveVersion(version), Map.of())
                .values()
                .stream()
                .filter(trajectory -> matchesFilters(trajectory, filters))
                .collect(Collectors.toList());
    }

    private static String resolveVersion(String version) {
        return version == null || version.isBlank() ? DEFAULT_VERSION : version;
    }

    private static boolean matchesFilters(Trajectory trajectory, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (!java.util.Objects.equals(fieldValue(trajectory, entry.getKey()), entry.getValue())) {
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
}
