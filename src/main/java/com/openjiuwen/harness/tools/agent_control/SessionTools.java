/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.agent_control;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session tools for async subagent spawning.
 *
 * <p>Mirrors Python's {@code session_tools} module in
 * {@code openjiuwen.harness.tools.agent_control.session_tools}.
 */
public final class SessionTools {

    private static final Logger LOG = LoggerFactory.getLogger(SessionTools.class);

    /** Task type for session spawn. */
    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn_task";

    /**
     * Session task row (business view).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionTaskRow {
        private String taskId;
        private String subSessionId;
        private String description;
        private String status;
        @Builder.Default
        private String result = "";
        @Builder.Default
        private String error = "";
    }

    /**
     * Session task registry.
     */
    public static class SessionToolkit {
        private final Map<String, SessionTaskRow> rows = new ConcurrentHashMap<>();

        /**
         * Insert or update task as running.
         */
        public void upsertRunning(String taskId, String subSessionId, String description) {
            SessionTaskRow row = SessionTaskRow.builder()
                    .taskId(taskId)
                    .subSessionId(subSessionId)
                    .description(description)
                    .status("running")
                    .build();
            rows.put(taskId, row);
            LOG.debug("[SessionToolkit] upsert_running task_id={}", taskId);
        }

        /**
         * Mark task as completed with result.
         */
        public void markCompleted(String taskId, String result) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("completed");
                row.setResult(result != null ? result : "");
                LOG.debug("[SessionToolkit] mark_completed task_id={}", taskId);
            }
        }

        /**
         * Mark task as failed with error.
         */
        public void markFailed(String taskId, String error) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("error");
                row.setError(error != null ? error : "");
                LOG.debug("[SessionToolkit] mark_failed task_id={}", taskId);
            }
        }

        /**
         * Mark task as canceled.
         */
        public void markCanceled(String taskId) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("canceled");
                LOG.debug("[SessionToolkit] mark_canceled task_id={}", taskId);
            }
        }

        /**
         * List all tasks.
         */
        public List<SessionTaskRow> listAll() {
            return new ArrayList<>(rows.values());
        }

        /**
         * Get task by id.
         */
        public SessionTaskRow get(String taskId) {
            return rows.get(taskId);
        }

        /**
         * Clear all tasks.
         */
        public void clear() {
            rows.clear();
            LOG.debug("[SessionToolkit] clear");
        }
    }
}