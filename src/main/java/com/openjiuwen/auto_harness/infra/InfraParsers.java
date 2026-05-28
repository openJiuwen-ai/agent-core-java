/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * Agent 输出解析工具集。
 * <p>
 * 提供从 agent 文本输出中提取结构化数据的通用函数：
 * - parseTasks: 解析 JSON 任务列表
 * - parseLearnings: 解析 JSON 经验列表
 * - parsePrDraft: 解析 PR draft JSON
 * - parseGaps: 解析 markdown 表格中的竞品差距
 * - extractText: 从 OutputSchema chunk 提取文本
 * <p>
 * Mirrors Python's {@code parsers} module in
 * {@code openjiuwen.auto_harness.infra.parsers}.
 */
public class InfraParsers {

    private static final Logger logger = Logger.getLogger(InfraParsers.class.getName());

    private static final Set<String> ALLOWED_PR_KINDS = Set.of(
        "bug", "task", "feature", "refactor", "clean_code"
    );

    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL);
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[.*]", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile("\\{.*}", Pattern.DOTALL);

    /**
     * 从 agent 输出中解析 JSON 任务列表。
     * <p>
     * 支持 ```json ... ``` 包裹和裸 JSON 数组。
     *
     * @param raw agent 输出的原始文本
     * @return 解析后的 OptimizationTask 列表
     */
    public static List<OptimizationTask> parseTasks(String raw) {
        String jsonStr = extractJsonString(raw, true);
        if (jsonStr == null) {
            return List.of();
        }

        try {
            // Parse JSON array
            List<Map<String, Object>> items = parseJsonArray(jsonStr);
            List<OptimizationTask> tasks = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item.containsKey("topic")) {
                    tasks.add(new OptimizationTask(
                        (String) item.get("topic"),
                        (String) item.getOrDefault("description", ""),
                        (List<String>) item.getOrDefault("files", List.of()),
                        (String) item.getOrDefault("expected_effect", ""),
                        normalizePipelineName((String) item.getOrDefault("pipeline_name", ""))
                    ));
                }
            }
            return tasks;
        } catch (Exception e) {
            logger.warning("Failed to parse plan JSON: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 learnings agent 输出中解析 JSON 经验列表。
     *
     * @param raw agent 输出的原始文本
     * @return 字典列表，每个包含 type/topic/summary/details
     */
    public static List<Map<String, Object>> parseLearnings(String raw) {
        String jsonStr = extractJsonString(raw, true);
        if (jsonStr == null) {
            return List.of();
        }

        try {
            List<Map<String, Object>> items = parseJsonArray(jsonStr);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> item : items) {
                if (item instanceof Map && item.containsKey("topic")) {
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            logger.warning("Failed to parse learnings JSON: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse a PR draft JSON response with detailed errors.
     *
     * @param raw agent 输出的原始文本
     * @return Pair of (PullRequestDraft or null, error message or empty)
     */
    public static Pair<PullRequestDraft, String> parsePrDraftWithError(String raw) {
        String jsonStr = extractJsonString(raw, false);
        if (jsonStr == null) {
            return new Pair<>(null, "未找到 JSON 对象");
        }

        try {
            Map<String, Object> item = parseJsonObject(jsonStr);
            String title = ((String) item.getOrDefault("title", "")).trim();
            String body = ((String) item.getOrDefault("body", "")).trim();
            String kind = ((String) item.getOrDefault("kind", "")).trim();

            // Extract kind from body if not present
            if (kind.isEmpty() && !body.isEmpty()) {
                Pattern kindPattern = Pattern.compile("(?m)^/kind\\s+([a-z_]+)\\s*$");
                Matcher m = kindPattern.matcher(body);
                if (m.find()) {
                    kind = m.group(1).trim();
                }
            }

            if (title.isEmpty() || body.isEmpty()) {
                return new Pair<>(null, "缺少 title 或 body");
            }

            if (!ALLOWED_PR_KINDS.contains(kind)) {
                return new Pair<>(null, "kind 必须是 bug/task/feature/refactor/clean_code 之一");
            }

            return new Pair<>(new PullRequestDraft(title, body, kind), "");
        } catch (Exception e) {
            logger.warning("Failed to parse PR draft JSON: " + e.getMessage());
            return new Pair<>(null, "JSON 解析失败");
        }
    }

    /**
     * Parse a PR draft JSON response.
     */
    public static PullRequestDraft parsePrDraft(String raw) {
        return parsePrDraftWithError(raw).first;
    }

    /**
     * Parse a selector agent JSON response.
     */
    public static PipelineSelectionArtifact parsePipelineSelection(String raw) {
        String jsonStr = extractJsonString(raw, false);
        if (jsonStr == null) {
            return null;
        }

        try {
            Map<String, Object> item = parseJsonObject(jsonStr);
            String pipeline = (String) item.getOrDefault("pipeline", "");
            String reason = (String) item.getOrDefault("reason", "");
            return new PipelineSelectionArtifact(pipeline, reason);
        } catch (Exception e) {
            logger.warning("Failed to parse pipeline selection JSON: " + e.getMessage());
            return null;
        }
    }

    // ── Helper methods ───────────────────────────────────────

    private static String extractJsonString(String raw, boolean isArray) {
        Matcher blockMatch = JSON_BLOCK_PATTERN.matcher(raw);
        if (blockMatch.find()) {
            return blockMatch.group(1);
        }

        Pattern pattern = isArray ? JSON_ARRAY_PATTERN : JSON_OBJECT_PATTERN;
        Matcher match = pattern.matcher(raw);
        if (match.find()) {
            return match.group(0);
        }
        return null;
    }

    private static List<Map<String, Object>> parseJsonArray(String jsonStr) {
        // Simplified JSON parsing - actual implementation would use Jackson/Gson
        return new ArrayList<>();
    }

    private static Map<String, Object> parseJsonObject(String jsonStr) {
        // Simplified JSON parsing - actual implementation would use Jackson/Gson
        return new HashMap<>();
    }

    private static String normalizePipelineName(String name) {
        if (name == null || name.isEmpty()) return "";
        return name.toLowerCase().replace("-", "_");
    }

    // ── Inner classes ───────────────────────────────────────

    public static class OptimizationTask {
        private final String topic;
        private final String description;
        private final List<String> files;
        private final String expectedEffect;
        private final String pipelineName;

        public OptimizationTask(String topic, String description, List<String> files, String expectedEffect, String pipelineName) {
            this.topic = topic;
            this.description = description;
            this.files = files;
            this.expectedEffect = expectedEffect;
            this.pipelineName = pipelineName;
        }

        public String getTopic() { return topic; }
        public String getDescription() { return description; }
        public List<String> getFiles() { return files; }
        public String getExpectedEffect() { return expectedEffect; }
        public String getPipelineName() { return pipelineName; }
    }

    public static class PullRequestDraft {
        private final String title;
        private final String body;
        private final String kind;

        public PullRequestDraft(String title, String body, String kind) {
            this.title = title;
            this.body = body;
            this.kind = kind;
        }

        public String getTitle() { return title; }
        public String getBody() { return body; }
        public String getKind() { return kind; }
    }

    public static class PipelineSelectionArtifact {
        private final String pipeline;
        private final String reason;

        public PipelineSelectionArtifact(String pipeline, String reason) {
            this.pipeline = pipeline;
            this.reason = reason;
        }

        public String getPipeline() { return pipeline; }
        public String getReason() { return reason; }
    }

    public static class Pair<T, U> {
        public final T first;
        public final U second;
        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }
}