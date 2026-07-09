/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.autoharness.pipelines.ExtendedEvolvePipeline;
import com.openjiuwen.autoharness.pipelines.MetaEvolvePipeline;
import com.openjiuwen.autoharness.schema.Gap;
import com.openjiuwen.autoharness.schema.OptimizationTask;
import com.openjiuwen.autoharness.schema.PipelineSelectionArtifact;
import com.openjiuwen.autoharness.schema.PullRequestDraft;
import com.openjiuwen.core.session.stream.OutputSchema;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsers.
 * 
 * @since 0.1.7
 */
public final class Parsers {
    private static final Logger logger = LoggerFactory.getLogger(Parsers.class);

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern JSON_CODE_BLOCK = Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern JSON_ARRAY = Pattern.compile("\\[.*]", Pattern.DOTALL);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /**
     * Pattern.compile.
     * 
     * @since 0.1.7
     */
    private static final Pattern KIND_LINE = Pattern.compile("(?m)^/kind\\s+([a-z_]+)\\s*$");

    /**
     * Set.of.
     * 
     * @since 0.1.7
     */
    private static final Set<String> ALLOWED_PR_KINDS = Set.of("bug", "task", "feature", "refactor", "clean_code");

    /**
     * Map.of.
     * 
     * @since 0.1.7
     */
    private static final Map<String, String> PIPELINE_NAME_ALIASES =
        Map.of("pr_pipeline", MetaEvolvePipeline.NAME, "extended_harness_pipeline", ExtendedEvolvePipeline.NAME);

    /**
     * Parsers.
     * 
     * @since 0.1.7
     */
    private Parsers() {
    }

    /**
     * parseTasks.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    public static List<OptimizationTask> parseTasks(String raw) {
        String json = extractJsonArray(raw);
        if (json == null) {
            return List.of();
        }

        JsonNode items = readJson(json, "Failed to parse plan JSON");
        if (items == null || !items.isArray()) {
            return List.of();
        }

        List<OptimizationTask> tasks = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isObject() || !item.has("topic")) {
                continue;
            }
            tasks.add(OptimizationTask.builder().topic(stringValue(item.get("topic")))
                    .description(stringValue(item.get("description"))).files(stringList(item.get("files")))
                    .expectedEffect(stringValue(item.get("expected_effect")))
                    .pipelineName(normalizePipelineName(stringValue(item.get("pipeline_name")))).build());
        }
        return tasks;
    }

    /**
     * parseLearnings.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    public static List<Map<String, Object>> parseLearnings(String raw) {
        String json = extractJsonArray(raw);
        if (json == null) {
            return List.of();
        }

        JsonNode items = readJson(json, "Failed to parse learnings JSON");
        if (items == null || !items.isArray()) {
            return List.of();
        }

        List<Map<String, Object>> learnings = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isObject() || !item.has("topic")) {
                continue;
            }
            learnings.add(OBJECT_MAPPER.convertValue(item, LinkedHashMap.class));
        }
        return learnings;
    }

    /**
     * parsePrDraftWithError.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    public static PullRequestDraftParseResult parsePrDraftWithError(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            return new PullRequestDraftParseResult(null, "未找到 JSON 对象");
        }

        JsonNode item = readJson(json, "Failed to parse PR draft JSON");
        if (item == null) {
            return new PullRequestDraftParseResult(null, "JSON 解析失败");
        }
        if (!item.isObject()) {
            return new PullRequestDraftParseResult(null, "JSON 顶层必须是对象");
        }

        String title = stringValue(item.get("title")).trim();
        String body = stringValue(item.get("body")).trim();
        String kind = stringValue(item.get("kind")).trim();
        if (kind.isBlank() && !body.isBlank()) {
            Matcher matcher = KIND_LINE.matcher(body);
            if (matcher.find()) {
                kind = matcher.group(1).trim();
            }
        }
        if (title.isBlank() || body.isBlank()) {
            return new PullRequestDraftParseResult(null, "缺少 title 或 body");
        }
        if (!ALLOWED_PR_KINDS.contains(kind)) {
            return new PullRequestDraftParseResult(null, "kind 必须是 bug/task/feature/refactor/clean_code 之一");
        }
        return new PullRequestDraftParseResult(PullRequestDraft.builder().title(title).body(body).kind(kind).build(),
                "");
    }

    /**
     * parsePrDraft.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    public static PullRequestDraft parsePrDraft(String raw) {
        return parsePrDraftWithError(raw).draft();
    }

    /**
     * parsePipelineSelection.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    public static PipelineSelectionArtifact parsePipelineSelection(String raw) {
        String json = extractJsonObject(raw);
        if (json == null) {
            return null;
        }

        JsonNode item = readJson(json, "Failed to parse pipeline selection JSON");
        if (item == null || !item.isObject()) {
            return null;
        }

        String pipelineName = normalizePipelineName(stringValue(item.get("pipeline_name")).trim());
        if (pipelineName.isBlank()) {
            return null;
        }

        return PipelineSelectionArtifact.builder().pipelineName(pipelineName).reason(stringValue(item.get("reason")))
                .alternatives(normalizePipelineNames(stringList(item.get("alternatives"))))
                .confidence(doubleValue(item.get("confidence"))).riskLevel(stringValue(item.get("risk_level")))
                .requiredInputs(stringList(item.get("required_inputs")))
                .fallbackPipeline(normalizePipelineName(stringValue(item.get("fallback_pipeline")))).build();
    }

    /**
     * extractText.
     * 
     * @param chunk chunk
     * @return the result
     * @since 0.1.7
     */
    public static String extractText(Object chunk) {
        if (chunk instanceof OutputSchema outputSchema) {
            Object payload = outputSchema.getPayload();
            if (payload instanceof Map<?, ?> payloadMap) {
                Object content = payloadMap.get("content");
                return content == null ? "" : String.valueOf(content);
            }
        }
        return "";
    }

    /**
     * parseGaps.
     * 
     * @param rawText rawText
     * @return the result
     * @since 0.1.7
     */
    public static List<Gap> parseGaps(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }

        List<Gap> gaps = new ArrayList<>();
        String[] lines = rawText.strip().split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("|--") || !line.startsWith("|")) {
                continue;
            }

            String[] split = line.split("\\|", -1);
            if (split.length < 10) {
                continue;
            }

            List<String> cells = new ArrayList<>();
            for (int i = 1; i < split.length - 1; i++) {
                cells.add(split[i].trim());
            }
            if (cells.size() < 8) {
                continue;
            }
            String firstCell = cells.get(0).toLowerCase(Locale.ROOT);
            if ("competitor".equals(firstCell) || "竞品".equals(firstCell)) {
                continue;
            }

            Gap gap = rowToGap(cells);
            if (gap != null) {
                gaps.add(gap);
            }
        }

        gaps.sort(Comparator.comparingDouble(Gap::priority).reversed());
        logger.info("Parsed {} gaps from raw text", gaps.size());
        return gaps;
    }

    /**
     * rowToGap.
     * 
     * @param cells cells
     * @return the result
     * @since 0.1.7
     */
    private static Gap rowToGap(List<String> cells) {
        try {
            return Gap.builder().id(UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                    .competitor(cells.get(0)).feature(cells.get(1)).currentState(cells.get(2))
                    .gapDescription(cells.get(3)).impact(Double.parseDouble(cells.get(4)))
                    .feasibility(Double.parseDouble(cells.get(5))).suggestedApproach(cells.get(6))
                    .targetFiles(splitTargetFiles(cells.get(7))).build();
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            List<String> sampleCells = cells.subList(0, Math.min(4, cells.size()));
            logger.warn("Skipping malformed gap row: {}", String.join(" | ", sampleCells));
            return nullValue();
        }
    }

    /**
     * splitTargetFiles.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static List<String> splitTargetFiles(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        for (String item : raw.split(",")) {
            String normalized = item.trim();
            if (!normalized.isEmpty()) {
                files.add(normalized);
            }
        }
        return files;
    }

    /**
     * readJson.
     * 
     * @param json json
     * @param warningMessage warningMessage
     * @return the result
     * @since 0.1.7
     */
    private static JsonNode readJson(String json, String warningMessage) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            logger.warn(warningMessage, ex);
            return nullValue();
        }
    }

    /**
     * extractJsonArray.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static String extractJsonArray(String raw) {
        String codeBlock = extractCodeBlock(raw);
        if (codeBlock != null) {
            return codeBlock;
        }
        return extractFirst(JSON_ARRAY, raw);
    }

    /**
     * extractJsonObject.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static String extractJsonObject(String raw) {
        String codeBlock = extractCodeBlock(raw);
        if (codeBlock != null) {
            return codeBlock;
        }
        return extractFirst(JSON_OBJECT, raw);
    }

    /**
     * extractCodeBlock.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static String extractCodeBlock(String raw) {
        return extractFirst(JSON_CODE_BLOCK, raw);
    }

    /**
     * extractFirst.
     * 
     * @param pattern pattern
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static String extractFirst(Pattern pattern, String raw) {
        if (raw == null || raw.isBlank()) {
            return nullValue();
        }
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return nullValue();
        }
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
    }

    /**
     * stringValue.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.isTextual() ? node.asText() : node.toString();
    }

    /**
     * doubleValue.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    private static double doubleValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0.0;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        try {
            return Double.parseDouble(node.asText());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    /**
     * stringList.
     * 
     * @param node node
     * @return the result
     * @since 0.1.7
     */
    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            values.add(stringValue(element));
        }
        return values;
    }

    /**
     * normalizePipelineNames.
     * 
     * @param values values
     * @return the result
     * @since 0.1.7
     */
    private static List<String> normalizePipelineNames(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            normalized.add(normalizePipelineName(value));
        }
        return normalized;
    }

    /**
     * normalizePipelineName.
     * 
     * @param name name
     * @return the result
     * @since 0.1.7
     */
    private static String normalizePipelineName(String name) {
        return PIPELINE_NAME_ALIASES.getOrDefault(name, name);
    }

    /**
     * Public record PullRequestDraftParseResult used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record PullRequestDraftParseResult(PullRequestDraft draft, String error) {
    }

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
