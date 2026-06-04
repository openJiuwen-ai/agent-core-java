/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.TuneUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Judge prompt/scoring helpers.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.online.judge.scoring}.
 */
public final class JudgeScoring {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Pattern CODE_BLOCK = Pattern.compile("```(?:json)?\\s*(.*?)```", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    static final String JUDGE_PROMPT_TEMPLATE = """
            你是一个专业的 AI Agent 质量评估器。请对以下 Agent 对话轮次打分。

            ## 用户指令
            %s

            ## Agent 回复
            %s

            ## 用户反馈（下一轮输入）
            %s

            ## 评分维度（各 0-10 分）
            1. 任务完成度：Agent 是否完成了用户意图？
            2. 响应质量：回答是否准确、有帮助、简洁？
            3. 工具使用合理性：工具调用是否必要且正确？
            4. 对话连贯性：多轮对话是否自然流畅？

            请严格以 JSON 格式返回，不要添加任何其他文字：
            {"task_completion": 8, "response_quality": 7, "tool_usage": 9, "coherence": 8, "overall": 8.0, "reason": "..."}
            """;

    private JudgeScoring() {
    }

    public static String buildJudgePrompt(String instructionText, String responseText, String followupUserFeedback) {
        return JUDGE_PROMPT_TEMPLATE.formatted(
                emptyToDefault(instructionText, "(无)"),
                emptyToDefault(responseText, "(无回复)"),
                emptyToDefault(followupUserFeedback, "(无反馈)")
        );
    }

    public static Map<String, Object> parseJudgeScores(String content, boolean raiseOnError) {
        String safeContent = content != null ? content.trim() : "";
        List<String> candidates = new ArrayList<>();
        if (!safeContent.isBlank()) {
            candidates.add(safeContent);
        }
        Matcher blockMatcher = CODE_BLOCK.matcher(safeContent);
        while (blockMatcher.find()) {
            String block = blockMatcher.group(1).trim();
            if (!block.isBlank()) {
                candidates.add(block);
            }
        }
        String extracted = extractJsonCandidate(safeContent);
        if (extracted != null && !extracted.isBlank()) {
            candidates.add(extracted);
        }
        for (String candidate : candidates) {
            if (candidate.isBlank()) {
                continue;
            }
            Map<String, Object> parsed = parseJsonMap(candidate);
            if (parsed != null) {
                ensureOverall(parsed);
                return parsed;
            }
        }
        if (raiseOnError) {
            throw new IllegalArgumentException("Cannot parse judge response: " + abbreviate(safeContent, 200));
        }
        return null;
    }

    public static double normalizeOverallScore(double overall) {
        return (overall - 5.0) / 5.0;
    }

    private static Map<String, Object> parseJsonMap(String candidate) {
        try {
            return OBJECT_MAPPER.readValue(candidate, MAP_TYPE);
        } catch (JsonProcessingException ignored) {
            Object raw = TuneUtils.parseJsonFromLlmResponse(candidate);
            if (raw instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    copy.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return copy;
            }
            return null;
        }
    }

    private static void ensureOverall(Map<String, Object> parsed) {
        if (parsed.containsKey("overall") && parsed.get("overall") != null) {
            return;
        }
        List<Double> values = new ArrayList<>();
        addDimensionValue(values, parsed, "task_completion", "task_completion_score");
        addDimensionValue(values, parsed, "response_quality", "response_quality_score");
        addDimensionValue(values, parsed, "tool_usage", "tool_usage_score");
        addDimensionValue(values, parsed, "coherence", "coherence_score");
        double overall = values.isEmpty() ? 5.0 : values.stream().mapToDouble(Double::doubleValue).average().orElse(5.0);
        parsed.put("overall", overall);
    }

    private static void addDimensionValue(List<Double> values, Map<String, Object> parsed, String primaryKey, String aliasKey) {
        Object value = parsed.get(primaryKey);
        if (!(value instanceof Number)) {
            value = parsed.get(aliasKey);
        }
        if (value instanceof Number number) {
            values.add(number.doubleValue());
        }
    }

    private static String extractJsonCandidate(String content) {
        int first = content.indexOf('{');
        int last = content.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return content.substring(first, last + 1);
        }
        return null;
    }

    private static String emptyToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String abbreviate(String value, int maxLen) {
        return value.length() <= maxLen ? value : value.substring(0, maxLen);
    }
}
