/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Public class SessionSpawnExecutor used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SessionSpawnExecutor {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn";

    private final Function<Map<String, Object>, Object> subagentInvoker;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SessionSpawnExecutor(Function<Map<String, Object>, Object> subagentInvoker) {
        this.subagentInvoker = subagentInvoker;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> execute(String taskId, Map<String, Object> metadata) {
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("subagent_type", safeMetadata.getOrDefault("subagent_type", "general-purpose"));
        request.put("task_description", safeMetadata.getOrDefault("task_description", ""));
        request.put("sub_session_id", safeMetadata.getOrDefault("sub_session_id", ""));
        request.put("task_id", taskId);
        try {
            Object output = subagentInvoker == null ? request.get("task_description") : subagentInvoker.apply(request);
            return Map.of(
                    "type", "TASK_COMPLETION",
                    "task_id", taskId,
                    "task_type", SESSION_SPAWN_TASK_TYPE,
                    "data", Map.of("output", output == null ? "" : output)
            );
        } catch (RuntimeException ex) {
            return Map.of(
                    "type", "TASK_FAILED",
                    "task_id", taskId,
                    "task_type", SESSION_SPAWN_TASK_TYPE,
                    "error", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()
            );
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean canPause() {
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean canCancel() {
        return true;
    }
}
