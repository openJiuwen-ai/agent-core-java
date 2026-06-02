/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.harness.prompts.tools.ToolDescriptionRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cron tool context and factory for creating cron tools.
 * <p>
 * Mirrors Python's {@code CronToolContext / CronToolBackend / create_cron_tools} in
 * {@code openjiuwen.harness.tools.cron}.
 */
public final class CronTool {

    private CronTool() {
    }

    /** Runtime context bound to a cron tool registration. */
    public static class CronToolContext {
        private final String channelId;
        private final String sessionId;
        private final Map<String, Object> metadata;
        private final String mode;

        public CronToolContext(String channelId, String sessionId) {
            this(channelId, sessionId, null, null);
        }

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
            return channel + ":" + session;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Map<String, Object> getMetadata() {
            return metadata;
        }

        public String getMode() {
            return mode;
        }
    }

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

    /**
     * Unified dispatch for cron actions.
     */
    public static Object dispatchAction(CronToolBackend backend, CronToolContext context,
                                        String action, Map<String, Object> params) throws Exception {
        Map<String, Object> safeParams = params != null ? params : Map.of();
        String actionName = (action != null) ? action.trim().toLowerCase() : "";
        String targetJobId = stringValue(firstPresent(safeParams.get("jobId"), safeParams.get("id"))).trim();

        return switch (actionName) {
            case "status" -> backend.status();
            case "list" -> Map.of("jobs", backend.listJobs(booleanValue(safeParams.get("includeDisabled"))));
            case "add" -> {
                Map<String, Object> createInput = asMap(safeParams.get("job"));
                if (createInput.isEmpty()) {
                    createInput = flatKwargs(safeParams);
                }
                yield backend.createJob(createInput, context);
            }
            case "update" -> {
                requireJobId(targetJobId);
                Map<String, Object> patch = asMap(safeParams.get("patch"));
                if (patch.isEmpty()) {
                    patch = flatKwargs(safeParams);
                }
                yield backend.updateJob(targetJobId, patch, context);
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
                yield Map.of("runs", backend.getRuns(targetJobId, 20));
            }
            case "wake" -> backend.wake(stringValue(safeParams.get("text")), context,
                    safeParams.get("mode") != null ? stringValue(safeParams.get("mode")) : null);
            default -> throw new IllegalArgumentException("unsupported cron action");
        };
    }

    public static List<Tool> createCronTools(CronToolBackend backend, CronToolContext context) {
        return createCronTools(backend, context, "cn", null, null, true, null);
    }

    public static List<Tool> createCronTools(CronToolBackend backend, CronToolContext context,
                                             boolean includeLegacyCompat) {
        return createCronTools(backend, context, "cn", null, null, includeLegacyCompat, null);
    }

    public static List<Tool> createCronTools(CronToolBackend backend, CronToolContext context,
                                             String language, Collection<String> targetChannels,
                                             String defaultTargetChannel, boolean includeLegacyCompat,
                                             String agentId) {
        String scope = toolScope(context);
        String finalAgentId = agentId != null && !agentId.isBlank() ? agentId : scope;
        List<Tool> tools = new ArrayList<>();

        tools.add(makeTool("cron", scope, language, finalAgentId, null,
                inputs -> dispatchAction(backend, context, stringValue(inputs.get("action")), inputs)));
        if (!includeLegacyCompat) {
            return tools;
        }

        Map<String, Object> targetSchema = targetSchema(targetChannels, defaultTargetChannel);
        tools.add(makeTool("cron_list_jobs", scope, language, finalAgentId, null,
                inputs -> backend.listJobs(true)));
        tools.add(makeTool("cron_get_job", scope, language, finalAgentId, null,
                inputs -> backend.getJob(requiredString(inputs, "job_id"))));
        tools.add(makeTool("cron_create_job", scope, language, finalAgentId, targetSchema,
                inputs -> backend.createJob(new LinkedHashMap<>(inputs), context)));
        tools.add(makeTool("cron_update_job", scope, language, finalAgentId, null,
                inputs -> backend.updateJob(requiredString(inputs, "job_id"), asMap(inputs.get("patch")), context)));
        tools.add(makeTool("cron_delete_job", scope, language, finalAgentId, null,
                inputs -> backend.deleteJob(requiredString(inputs, "job_id"))));
        tools.add(makeTool("cron_toggle_job", scope, language, finalAgentId, null,
                inputs -> backend.toggleJob(requiredString(inputs, "job_id"), booleanValue(inputs.get("enabled")))));
        tools.add(makeTool("cron_preview_job", scope, language, finalAgentId, null,
                inputs -> backend.previewJob(requiredString(inputs, "job_id"), intValue(inputs.get("count"), 5))));
        return tools;
    }

    private static Tool makeTool(String name, String scope, String language, String agentId,
                                 Map<String, Object> targetSchema, ThrowingFunction function) {
        ToolCard card = buildToolCard(name, scope, language, agentId, targetSchema);
        return new LocalFunction(card, inputs -> {
            try {
                return function.apply(inputs);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static ToolCard buildToolCard(String name, String scope, String language, String agentId,
                                          Map<String, Object> targetSchema) {
        Map<String, Object> rawCard = ToolDescriptionRegistry.buildToolCard(name, name + "_" + scope, language, agentId);
        Map<String, Object> inputParams = copyMap((Map<String, Object>) rawCard.get("input_params"));
        if (targetSchema != null && inputParams.get("properties") instanceof Map<?, ?> rawProps) {
            Map<String, Object> props = copyMap((Map<String, Object>) rawProps);
            props.put("targets", targetSchema);
            inputParams.put("properties", props);
        }
        return ToolCard.builder()
                .id(stringValue(rawCard.get("id")))
                .name(stringValue(rawCard.get("name")))
                .description(stringValue(rawCard.get("description")))
                .inputParams(inputParams)
                .build();
    }

    private static Map<String, Object> targetSchema(Collection<String> targetChannels, String defaultTargetChannel) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", "Legacy compatibility target channel");
        if (targetChannels != null) {
            List<String> enumValues = targetChannels.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
            if (!enumValues.isEmpty()) {
                schema.put("enum", enumValues);
            }
        }
        if (defaultTargetChannel != null && !defaultTargetChannel.isBlank()) {
            schema.put("default", defaultTargetChannel.trim());
        }
        return schema;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(stringValue(entry.getKey()), entry.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyMap(Map<String, Object> value) {
        return value != null ? new LinkedHashMap<>(value) : new LinkedHashMap<>();
    }

    private static Map<String, Object> flatKwargs(Map<String, Object> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!excludedActionKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private static boolean excludedActionKey(String key) {
        return List.of("action", "job", "jobId", "patch", "includeDisabled", "text", "mode",
                "contextMessages", "gatewayUrl", "gatewayToken", "timeoutMs", "runMode", "id").contains(key);
    }

    private static String toolScope(CronToolContext context) {
        String scope = context != null ? context.getToolScope() : "cron:default";
        return scope.replace(":", "_");
    }

    private static Object firstPresent(Object left, Object right) {
        return left != null ? left : right;
    }

    private static void requireJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
    }

    private static String requiredString(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        if (value == null && key.contains("_")) {
            value = inputs.get(key.replace("_", ""));
        }
        String text = stringValue(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return text;
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    @FunctionalInterface
    private interface ThrowingFunction {
        Object apply(Map<String, Object> inputs) throws Exception;
    }
}
