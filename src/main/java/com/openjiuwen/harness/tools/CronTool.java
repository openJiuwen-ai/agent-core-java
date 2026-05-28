/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;

import java.util.*;

/**
 * Cron tool context and factory for creating cron tools.
 * <p>
 * Mirrors Python's {@code CronToolContext / CronToolBackend / create_cron_tools} in
 * {@code openjiuwen.harness.tools.cron}.
 */
public final class CronTool {

    private CronTool() {
    }

    // ── Context ──────────────────────────────────────────────────────

    /** Runtime context bound to a cron tool registration. */
    public static class CronToolContext {
        private final String channelId;
        private final String sessionId;
        private final Map<String, Object> metadata;
        private final String mode;

        public CronToolContext(String channelId, String sessionId,
                               Map<String, Object> metadata, String mode) {
            this.channelId = channelId;
            this.sessionId = sessionId;
            this.metadata = metadata;
            this.mode = mode;
        }

        public String getToolScope() {
            String channel = (channelId != null && !channelId.isBlank()) ? channelId.trim() : "unknown";
            String session = (sessionId != null && !sessionId.isBlank()) ? sessionId.trim() : "default";
            return channel + "_" + session;
        }

        public String getChannelId() { return channelId; }
        public String getSessionId() { return sessionId; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getMode() { return mode; }
    }

    // ── Backend interface ────────────────────────────────────────────

    /**
     * Host-provided cron backend interface.
     * <p>
     * Mirrors Python's {@code CronToolBackend} protocol.
     */
    public interface CronToolBackend {
        List<Map<String, Object>> listJobs(boolean includeDisabled) throws Exception;
        Map<String, Object> getJob(String jobId) throws Exception;
        Map<String, Object> createJob(Map<String, Object> params, CronToolContext context) throws Exception;
        Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronToolContext context) throws Exception;
        boolean deleteJob(String jobId) throws Exception;
        Map<String, Object> toggleJob(String jobId, boolean enabled) throws Exception;
        List<Map<String, Object>> previewJob(String jobId, int count) throws Exception;
        String runNow(String jobId) throws Exception;
        Map<String, Object> status() throws Exception;
        List<Map<String, Object>> getRuns(String jobId, int limit) throws Exception;
        Map<String, Object> wake(String text, CronToolContext context, String mode) throws Exception;
    }

    // ── Dispatch ─────────────────────────────────────────────────────

    /**
     * Unified dispatch for cron actions.
     */
    public static Object dispatchAction(CronToolBackend backend, CronToolContext context,
                                         String action, Map<String, Object> params) throws Exception {
        String actionName = (action != null) ? action.trim().toLowerCase() : "";

        switch (actionName) {
            case "status":
                return backend.status();
            case "list":
                boolean includeDisabled = Boolean.TRUE.equals(params.get("includeDisabled"));
                return Map.of("jobs", backend.listJobs(includeDisabled));
            case "add":
                Map<String, Object> createInput = new LinkedHashMap<>(params);
                createInput.remove("action");
                return backend.createJob(createInput, context);
            case "update": {
                String jobId = (String) params.getOrDefault("jobId", params.get("id"));
                if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("jobId is required");
                Map<String, Object> patch = params.containsKey("patch")
                        ? (Map<String, Object>) params.get("patch")
                        : new LinkedHashMap<>(params);
                ((Map) patch).remove("action");
                ((Map) patch).remove("jobId");
                return backend.updateJob(jobId.trim(), patch, context);
            }
            case "remove": {
                String jobId = (String) params.getOrDefault("jobId", params.get("id"));
                if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("jobId is required");
                return Map.of("deleted", backend.deleteJob(jobId.trim()));
            }
            case "run": {
                String jobId = (String) params.getOrDefault("jobId", params.get("id"));
                if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("jobId is required");
                return Map.of("run_id", backend.runNow(jobId.trim()));
            }
            case "runs": {
                String jobId = (String) params.getOrDefault("jobId", params.get("id"));
                if (jobId == null || jobId.isBlank()) throw new IllegalArgumentException("jobId is required");
                return Map.of("runs", backend.getRuns(jobId.trim(), 20));
            }
            case "wake":
                return backend.wake((String) params.getOrDefault("text", ""), context,
                        (String) params.get("mode"));
            default:
                throw new IllegalArgumentException("Unsupported cron action: " + actionName);
        }
    }
}
