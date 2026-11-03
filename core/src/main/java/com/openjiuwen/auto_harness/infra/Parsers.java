/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.pipelines.AutoHarnessPipelineNames;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.ExtensionDesign;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.Gap;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.OptimizationTask;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PipelineSelectionArtifact;
import com.openjiuwen.auto_harness.schema.AutoHarnessSchema.PullRequestDraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Agent output parsing helpers.
 * <p>
 * Mirrors Python's module functions in
 * {@code openjiuwen/auto_harness/infra/parsers.py}.
 */
public final class Parsers {

    private static final Logger LOGGER = Logger.getLogger(Parsers.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_FENCE = Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL);
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);
    private static final Pattern KIND_LINE = Pattern.compile("(?m)^/kind\\s+([a-z_]+)\\s*$");
    private static final Set<String> ALLOWED_PR_KINDS = Set.of(
            "bug",
            "task",
            "feature",
            "refactor",
            "clean_code"
    );

    private Parsers() {
    }

    public static List<OptimizationTask> parseTasks(String raw) {
        String json = extractJsonArray(raw);
        if (json == null) {
            return List.of();
        }
        Object data;
        try {
            data = MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            LOGGER.warning("Failed to parse plan JSON");
            return List.of();
        }
        if (!(data instanceof List<?> items)) {
            return List.of();
        }
        List<OptimizationTask> tasks = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map && map.containsKey("topic")) {
                tasks.add(mapToOptimizationTask(map));
            }
        }
        return tasks;
    }

    public static List<Map<String, Object>> parseLearnings(String raw) {
        String json = extractJsonArray(raw);
        if (json == null) {
            return List.of();
        }
        Object data;
        try {
            data = MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            LOGGER.warning("Failed to parse learnings JSON");
            return List.of();
        }
        if (!(data instanceof List<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> learnings = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map && map.containsKey("topic")) {
                learnings.add(stringObjectMap(map));
            }
        }
        return learnings;
    }

    public static PullRequestDraftParseResult parsePrDraftWithError(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            return new PullRequestDraftParseResult(null, "未找到 JSON 对象");
        }
        Object data;
        try {
            data = MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            LOGGER.warning("Failed to parse PR draft JSON");
            return new PullRequestDraftParseResult(null, "JSON 解析失败");
        }
        if (!(data instanceof Map<?, ?> item)) {
            return new PullRequestDraftParseResult(null, "JSON 顶层必须是对象");
        }

        String title = pythonString(mapValue(item, "title", "")).strip();
        String body = pythonString(mapValue(item, "body", "")).strip();
        String kind = pythonString(mapValue(item, "kind", "")).strip();
        if (kind.isEmpty() && !body.isEmpty()) {
            Matcher matcher = KIND_LINE.matcher(body);
            if (matcher.find()) {
                kind = matcher.group(1).strip();
            }
        }
        if (title.isEmpty() || body.isEmpty()) {
            return new PullRequestDraftParseResult(null, "缺少 title 或 body");
        }
        if (!ALLOWED_PR_KINDS.contains(kind)) {
            return new PullRequestDraftParseResult(
                    null,
                    "kind 必须是 bug/task/feature/refactor/clean_code 之一"
            );
        }
        PullRequestDraft draft = PullRequestDraft.builder()
                .title(title)
                .body(body)
                .kind(kind)
                .build();
        return new PullRequestDraftParseResult(draft, "");
    }

    public static PullRequestDraft parsePrDraft(String raw) {
        return parsePrDraftWithError(raw).draft();
    }

    public static PipelineSelectionArtifact parsePipelineSelection(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            return null;
        }
        Object data;
        try {
            data = MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            LOGGER.warning("Failed to parse pipeline selection JSON");
            return null;
        }
        if (!(data instanceof Map<?, ?> item)) {
            return null;
        }
        String pipelineName = AutoHarnessPipelineNames.normalizePipelineName(
                pythonString(mapValue(item, "pipeline_name", "")).strip()
        );
        if (pipelineName.isEmpty()) {
            return null;
        }
        return PipelineSelectionArtifact.builder()
                .pipelineName(pipelineName)
                .reason(pythonString(mapValue(item, "reason", "")))
                .alternatives(normalizedPipelineNames(item.get("alternatives")))
                .confidence(doubleOrDefault(item.get("confidence"), 0.0))
                .riskLevel(pythonString(mapValue(item, "risk_level", "")))
                .requiredInputs(stringList(item.get("required_inputs")))
                .fallbackPipeline(AutoHarnessPipelineNames.normalizePipelineName(
                        pythonString(mapValue(item, "fallback_pipeline", ""))
                ))
                .build();
    }

    public static String extractText(Object chunk) {
        Object payload = readPayload(chunk);
        if (payload instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (isTruthy(content)) {
                return String.valueOf(content);
            }
            Object output = map.get("output");
            return isTruthy(output) ? String.valueOf(output) : "";
        }
        if (payload instanceof String text) {
            return text;
        }
        return "";
    }

    public static List<Gap> parseGaps(String rawText) {
        String source = rawText == null ? "" : rawText.strip();
        List<Gap> gaps = new ArrayList<>();
        if (source.isEmpty()) {
            return gaps;
        }
        for (String rawLine : source.split("\\R")) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("|--")) {
                continue;
            }
            if (!line.startsWith("|")) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            List<String> cells = new ArrayList<>();
            for (int i = 1; i < parts.length - 1; i++) {
                cells.add(parts[i].strip());
            }
            if (cells.size() < 8) {
                continue;
            }
            String header = cells.get(0).toLowerCase(Locale.ROOT);
            if ("competitor".equals(header) || "竞品".equals(header)) {
                continue;
            }
            Gap gap = rowToGap(cells);
            if (gap != null) {
                gaps.add(gap);
            }
        }
        gaps.sort((left, right) -> Double.compare(right.getPriority(), left.getPriority()));
        LOGGER.info(() -> "Parsed " + gaps.size() + " gaps from raw text");
        return gaps;
    }

    public static ExtensionDesignParseResult parseExtensionDesigns(String raw) {
        String json = extractJsonAnyObjectFirst(raw);
        if (json == null) {
            return new ExtensionDesignParseResult(null, List.of());
        }
        Object data;
        try {
            data = MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            LOGGER.warning("Failed to parse extension designs JSON");
            return new ExtensionDesignParseResult(null, List.of());
        }

        String packageName = null;
        List<?> items;
        if (data instanceof Map<?, ?> map && map.containsKey("designs")) {
            Object packageNameRaw = map.get("package_name");
            if (packageNameRaw instanceof String rawPackageName && !rawPackageName.strip().isEmpty()) {
                packageName = rawPackageName.strip();
            }
            Object rawItems = map.get("designs");
            if (rawItems instanceof List<?> list) {
                items = list;
            } else {
                items = List.of(rawItems);
            }
        } else if (data instanceof List<?> list) {
            items = list;
        } else if (data instanceof Map<?, ?>) {
            items = List.of(data);
        } else {
            return new ExtensionDesignParseResult(null, List.of());
        }

        List<ExtensionDesign> designs = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String extensionName = pythonString(mapValue(map, "extension_name", "")).strip();
            if (extensionName.isEmpty()) {
                continue;
            }
            String kind = pythonString(mapValue(map, "kind", "capability")).strip();
            if (kind.isEmpty() || (!"capability".equals(kind) && !"constraint".equals(kind))) {
                kind = "capability";
            }
            designs.add(ExtensionDesign.builder()
                    .gapId(pythonString(mapValue(map, "gap_id", "")))
                    .extensionName(extensionName)
                    .kind(kind)
                    .dependsOn(stringList(map.get("depends_on")))
                    .appliesTo(stringList(map.get("applies_to")))
                    .components(stringList(map.get("components")))
                    .filePlan(stringStringMap(map.get("file_plan")))
                    .harnessConfigPatch(stringObjectMap(map.get("harness_config_patch")))
                    .skillSource(pythonString(mapValue(map, "skill_source", "")))
                    .build());
        }
        return new ExtensionDesignParseResult(packageName, designs);
    }

    private static OptimizationTask mapToOptimizationTask(Map<?, ?> item) {
        OptimizationTask task = new OptimizationTask();
        task.setTopic(pythonString(item.get("topic")));
        task.setDescription(pythonString(mapValue(item, "description", "")));
        task.setFiles(stringList(item.get("files")));
        task.setExpectedEffect(pythonString(mapValue(item, "expected_effect", "")));
        task.setPipelineName(AutoHarnessPipelineNames.normalizePipelineName(
                pythonString(mapValue(item, "pipeline_name", ""))
        ));
        return task;
    }

    private static Gap rowToGap(List<String> cells) {
        try {
            return Gap.builder()
                    .id(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                    .competitor(cells.get(0))
                    .feature(cells.get(1))
                    .currentState(cells.get(2))
                    .gapDescription(cells.get(3))
                    .impact(Double.parseDouble(cells.get(4)))
                    .feasibility(Double.parseDouble(cells.get(5)))
                    .suggestedApproach(cells.get(6))
                    .targetFiles(splitTargetFiles(cells.get(7)))
                    .build();
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            LOGGER.warning(() -> "Skipping malformed gap row: "
                    + String.join(" | ", cells.subList(0, Math.min(4, cells.size()))));
            return null;
        }
    }

    private static List<String> splitTargetFiles(String value) {
        List<String> files = new ArrayList<>();
        for (String raw : value.split(",")) {
            String file = raw.strip();
            if (!file.isEmpty()) {
                files.add(file);
            }
        }
        return files;
    }

    private static String extractJsonArray(String raw) {
        String fenced = extractFence(raw);
        if (fenced != null) {
            return fenced;
        }
        Matcher matcher = JSON_ARRAY.matcher(raw == null ? "" : raw);
        return matcher.find() ? matcher.group(0) : null;
    }

    private static String extractJsonObject(String raw) {
        String fenced = extractFence(raw);
        if (fenced != null) {
            return fenced;
        }
        Matcher matcher = JSON_OBJECT.matcher(raw == null ? "" : raw);
        return matcher.find() ? matcher.group(0) : null;
    }

    private static String extractJsonAnyObjectFirst(String raw) {
        String fenced = extractFence(raw);
        if (fenced != null) {
            return fenced;
        }
        String text = raw == null ? "" : raw;
        Matcher objectMatcher = JSON_OBJECT.matcher(text);
        if (objectMatcher.find()) {
            return objectMatcher.group(0);
        }
        Matcher arrayMatcher = JSON_ARRAY.matcher(text);
        return arrayMatcher.find() ? arrayMatcher.group(0) : null;
    }

    private static String extractFence(String raw) {
        Matcher matcher = JSON_FENCE.matcher(raw == null ? "" : raw);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Object readPayload(Object chunk) {
        if (chunk == null) {
            return null;
        }
        if (chunk instanceof Map<?, ?> map && map.containsKey("payload")) {
            return map.get("payload");
        }
        try {
            Method method = chunk.getClass().getMethod("getPayload");
            return method.invoke(chunk);
        } catch (ReflectiveOperationException ignored) {
            // Try a field named payload below.
        }
        try {
            Field field = chunk.getClass().getDeclaredField("payload");
            field.setAccessible(true);
            return field.get(chunk);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        return true;
    }

    private static List<String> normalizedPipelineNames(Object value) {
        List<String> result = new ArrayList<>();
        for (String item : stringList(value)) {
            result.add(AutoHarnessPipelineNames.normalizePipelineName(item));
        }
        return result;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(pythonString(item));
        }
        return result;
    }

    private static Map<String, String> stringStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(pythonString(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        return stringObjectMap(map);
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(pythonString(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static double doubleOrDefault(Object value, double fallback) {
        try {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            return Double.parseDouble(String.valueOf(value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static Object mapValue(Map<?, ?> map, String key, Object fallback) {
        return map.containsKey(key) ? map.get(key) : fallback;
    }

    private static String pythonString(Object value) {
        return value == null ? "None" : String.valueOf(value);
    }

    /**
     * Tuple-style result for PR draft parsing.
     * <p>
     * Mirrors Python's {@code parse_pr_draft_with_error(...)} tuple in
     * {@code openjiuwen/auto_harness/infra/parsers.py}.
     *
     * @param draft parsed draft, or {@code null}
     * @param error parse error text, or empty string
     */
    public record PullRequestDraftParseResult(PullRequestDraft draft, String error) {
    }

    /**
     * Tuple-style result for extension design parsing.
     * <p>
     * Mirrors Python's {@code parse_extension_designs(...)} tuple in
     * {@code openjiuwen/auto_harness/infra/parsers.py}.
     *
     * @param packageName optional package name from the new format
     * @param designs parsed extension designs
     */
    public record ExtensionDesignParseResult(String packageName, List<ExtensionDesign> designs) {
    }
}
