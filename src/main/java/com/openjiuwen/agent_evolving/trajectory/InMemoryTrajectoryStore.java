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
        return data.getOrDefault(resolveVersion(version), Map.of())
                .values()
                .stream()
                .filter(trajectory -> sessionId == null || sessionId.equals(trajectory.getSessionId()))
                .filter(trajectory -> executionId == null || executionId.equals(trajectory.getExecutionId()))
                .collect(Collectors.toList());
    }

    private static String resolveVersion(String version) {
        return version == null || version.isBlank() ? DEFAULT_VERSION : version;
    }
}
