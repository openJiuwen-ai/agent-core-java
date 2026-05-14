/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.TuneUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Judge prompt/scoring helpers.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen.agent_evolving.agent_rl.online.judge.scoring}.
 */
public final class JudgeScoring {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() { };

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
        List<String> candidates = List.of(safeContent, extractJsonCandidate(safeContent));
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
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
        double taskCompletion = doubleValue(parsed, "task_completion", "task_completion_score");
        double responseQuality = doubleValue(parsed, "response_quality", "response_quality_score");
        double toolUsage = doubleValue(parsed, "tool_usage", "tool_usage_score");
        double coherence = doubleValue(parsed, "coherence", "coherence_score");
        double overall = (taskCompletion + responseQuality + toolUsage + coherence) / 4.0;
        parsed.put("overall", overall);
    }

    private static double doubleValue(Map<String, Object> parsed, String primaryKey, String aliasKey) {
        Object value = parsed.containsKey(primaryKey) ? parsed.get(primaryKey) : parsed.get(aliasKey);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return value != null ? Double.parseDouble(String.valueOf(value)) : 5.0;
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
