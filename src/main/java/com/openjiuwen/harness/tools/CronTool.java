/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cron tool context, backend contract, and factory helpers.
 *
 * <p>Mirrors Python's {@code CronToolContext}, {@code CronToolBackend}, and
 * {@code create_cron_tools} in {@code openjiuwen/harness/tools/cron.py}.</p>
 */
public final class CronTool {

    private CronTool() {
    }

    /**
     * Mirrors Python's {@code CronToolContext} in
     * {@code openjiuwen/harness/tools/cron.py}.
     */
    public record CronToolContext(String channelId, String sessionId, Map<String, Object> metadata, String mode) {
        public String toolScope() {
            String channel = channelId == null || channelId.isBlank() ? "unknown" : channelId.trim();
            String session = sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
            return channel + ":" + session;
        }
    }

    /**
     * Mirrors Python's {@code CronToolBackend} protocol in
     * {@code openjiuwen/harness/tools/cron.py}.
     */
    public interface CronToolBackend {
        List<Map<String, Object>> listJobs(boolean includeDisabled) throws Exception;

        Map<String, Object> getJob(String jobId) throws Exception;

        Map<String, Object> createJob(Map<String, Object> params, CronToolContext context) throws Exception;

        Map<String, Object> updateJob(String jobId, Map<String, Object> patch, CronToolContext context)
                throws Exception;

        boolean deleteJob(String jobId) throws Exception;

        Map<String, Object> toggleJob(String jobId, boolean enabled) throws Exception;

        List<Map<String, Object>> previewJob(String jobId, int count) throws Exception;

        String runNow(String jobId) throws Exception;

        Map<String, Object> status() throws Exception;

        List<Map<String, Object>> getRuns(String jobId, int limit) throws Exception;

        Map<String, Object> wake(String text, CronToolContext context, String mode) throws Exception;
    }

    public static Object dispatchCronAction(CronToolBackend backend, CronToolContext context,
                                            String action, Map<String, Object> params) throws Exception {
        if (backend == null) {
            throw new IllegalStateException("cron backend is not configured");
        }
        Map<String, Object> safeParams = params == null ? Map.of() : params;
        String actionName = action == null ? "" : action.trim().toLowerCase();
        String targetJobId = stringValue(firstPresent(safeParams.get("jobId"), safeParams.get("id"))).trim();
        return switch (actionName) {
            case "status" -> backend.status();
            case "list" -> Map.of("jobs", backend.listJobs(boolValue(safeParams.get("includeDisabled"), false)));
            case "add" -> backend.createJob(createPayload(safeParams), context);
            case "update" -> {
                requireJobId(targetJobId);
                Map<String, Object> patch = stringObjectMap(safeParams.get("patch"));
                yield backend.updateJob(targetJobId, patch.isEmpty() ? flatKwargs(safeParams) : patch, context);
            }
            case "remove" -> {
                requireJobId(targetJobId);
                yield Map.of("deleted", backend.deleteJob(targetJobId));
            }
            case "run" -> {
                requireJobId(targetJobId);
                yield Map.of("run_id", backend.runNow(targetJobId));
            }
            case "runs" -> {
                requireJobId(targetJobId);
                yield Map.of("runs", backend.getRuns(targetJobId, intValue(safeParams.get("limit"), 20)));
            }
            case "wake" -> backend.wake(stringValue(safeParams.get("text")), context,
                    safeParams.get("mode") == null ? null : stringValue(safeParams.get("mode")));
            default -> throw new IllegalArgumentException("unsupported cron action: " + action);
        };
    }

    public static List<Tool> createCronTools(CronToolBackend backend, CronToolContext context) {
        return createCronTools(backend, context, "cn", List.of(), null, true, null);
    }

    public static List<Tool> createCronTools(CronToolBackend backend, CronToolContext context, String language,
                                             Collection<String> targetChannels, String defaultTargetChannel,
                                             boolean includeLegacyCompat, String agentId) {
        String scope = toolScope(context);
        String resolvedAgentId = agentId == null || agentId.isBlank() ? scope : agentId;
        List<Tool> tools = new ArrayList<>();
        tools.add(makeTool("cron", scope, language, resolvedAgentId, null,
                inputs -> dispatchCronAction(backend, context, stringValue(inputs.get("action")), inputs)));
        if (!includeLegacyCompat) {
            return tools;
        }
        Map<String, Object> targetSchema = targetSchema(targetChannels, defaultTargetChannel);
        tools.add(makeTool("cron_list_jobs", scope, language, resolvedAgentId, null,
                inputs -> backend.listJobs(true)));
        tools.add(makeTool("cron_get_job", scope, language, resolvedAgentId, null,
                inputs -> backend.getJob(requiredString(inputs, "job_id"))));
        tools.add(makeTool("cron_create_job", scope, language, resolvedAgentId, targetSchema,
                inputs -> backend.createJob(new LinkedHashMap<>(inputs), context)));
        tools.add(makeTool("cron_update_job", scope, language, resolvedAgentId, null,
                inputs -> backend.updateJob(requiredString(inputs, "job_id"),
                        stringObjectMap(inputs.get("patch")), context)));
        tools.add(makeTool("cron_delete_job", scope, language, resolvedAgentId, null,
                inputs -> backend.deleteJob(requiredString(inputs, "job_id"))));
        tools.add(makeTool("cron_toggle_job", scope, language, resolvedAgentId, null,
                inputs -> backend.toggleJob(requiredString(inputs, "job_id"),
                        boolValue(inputs.get("enabled"), false))));
        tools.add(makeTool("cron_preview_job", scope, language, resolvedAgentId, null,
                inputs -> backend.previewJob(requiredString(inputs, "job_id"), intValue(inputs.get("count"), 5))));
        return tools;
    }

    private static Tool makeTool(String name, String scope, String language, String agentId,
                                 Map<String, Object> targetSchema, ThrowingFunction function) {
        ToolCard card = ToolCard.builder()
                .id(name + "_" + scope)
                .name(name)
                .description("Cron action " + name + " for " + agentId + " (" + language + ").")
                .inputParams(inputSchema(targetSchema))
                .build();
        return new LocalFunction(card, inputs -> {
            try {
                return function.apply(inputs == null ? Map.of() : inputs);
            } catch (RuntimeException runtimeException) {
                throw runtimeException;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static Map<String, Object> inputSchema(Map<String, Object> targetSchema) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (targetSchema != null) {
            properties.put("targets", targetSchema);
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return schema;
    }

    private static Map<String, Object> targetSchema(Collection<String> targetChannels, String defaultTargetChannel) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        if (targetChannels != null && !targetChannels.isEmpty()) {
            schema.put("enum", targetChannels.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList());
        }
        if (defaultTargetChannel != null && !defaultTargetChannel.isBlank()) {
            schema.put("default", defaultTargetChannel.trim());
        }
        return schema;
    }

    private static Map<String, Object> createPayload(Map<String, Object> params) {
        Map<String, Object> job = stringObjectMap(params.get("job"));
        return job.isEmpty() ? flatKwargs(params) : job;
    }

    private static Map<String, Object> flatKwargs(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!List.of("action", "job", "jobId", "patch", "includeDisabled", "text", "mode", "id")
                    .contains(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static String toolScope(CronToolContext context) {
        return (context == null ? "cron:default" : context.toolScope()).replace(":", "_");
    }

    private static Object firstPresent(Object left, Object right) {
        return left == null ? right : left;
    }

    private static void requireJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
    }

    private static String requiredString(Map<String, Object> inputs, String key) {
        String value = stringValue(inputs.get(key)).trim();
        if (value.isBlank() && key.contains("_")) {
            value = stringValue(inputs.get(key.replace("_", ""))).trim();
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return List.of("1", "true", "yes", "on").contains(String.valueOf(value).trim().toLowerCase());
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? defaultValue : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        }
        return result;
    }

    @FunctionalInterface
    private interface ThrowingFunction {
        Object apply(Map<String, Object> inputs) throws Exception;
    }
}
