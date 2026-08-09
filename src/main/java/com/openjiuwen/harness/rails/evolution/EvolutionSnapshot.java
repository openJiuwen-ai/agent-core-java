/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's {@code EvolutionSnapshot} in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EvolutionSnapshot {

    private final Trajectory trajectory;
    private final List<Map<String, Object>> messages;
    private final String skillName;

    public EvolutionSnapshot(Trajectory trajectory, List<Map<String, Object>> messages, String skillName) {
        this.trajectory = Objects.requireNonNull(trajectory, "trajectory is required");
        this.messages = copyMessages(messages);
        this.skillName = skillName;
    }

    public Map<String, Object> toLegacyDict() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("trajectory", trajectory);
        snapshot.put("messages", copyMessages(messages));
        if (skillName != null) {
            snapshot.put("skill_name", skillName);
        }
        return snapshot;
    }

    public static EvolutionSnapshot fromLegacyDict(Map<String, Object> snapshot) {
        if (snapshot == null || !snapshot.containsKey("trajectory")) {
            throw new IllegalArgumentException("Legacy evolution snapshot requires trajectory");
        }
        Object trajectoryValue = snapshot.get("trajectory");
        if (!(trajectoryValue instanceof Trajectory trajectory)) {
            throw new IllegalArgumentException("Legacy evolution snapshot trajectory must be a Trajectory");
        }
        return new EvolutionSnapshot(
                trajectory,
                normalizeMessages(snapshot.get("messages")),
                stringOrNull(snapshot.get("skill_name"))
        );
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeMessages(Object value) {
        if (!(value instanceof List<?> rawMessages)) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Object item : rawMessages) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    normalized.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                messages.add(normalized);
            } else if (item instanceof Map) {
                messages.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return messages;
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<Map<String, Object>> copyMessages(List<Map<String, Object>> source) {
        if (source == null) {
            return List.of();
        }
        List<Map<String, Object>> copied = new ArrayList<>();
        for (Map<String, Object> message : source) {
            copied.add(message == null ? new LinkedHashMap<>() : new LinkedHashMap<>(message));
        }
        return List.copyOf(copied);
    }

    public Trajectory getTrajectory() {
        return trajectory;
    }

    public List<Map<String, Object>> getMessages() {
        return copyMessages(messages);
    }

    public String getSkillName() {
        return skillName;
    }
}
