/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.context_evolver.service;

import com.openjiuwen.extensions.context_evolver.summary.task.reme.ReMeUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SummaryFlowSupport {

    private SummaryFlowSupport() {
    }

    static String normalizeTrajectory(Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof String text) {
            return text.trim();
        }
        if (raw instanceof Map<?, ?> map) {
            return normalizeTrajectoryMap(map);
        }
        if (raw instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                String normalized = normalizeTrajectory(item);
                if (normalized.isBlank()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(normalized);
            }
            return builder.toString().trim();
        }
        return compact(String.valueOf(raw));
    }

    static String compact(String value) {
        return ReMeUtils.compactWhitespace(value);
    }

    static String normalizeForMatch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", " ")
            .trim()
            .replaceAll("\\s+", " ");
    }

    static List<String> actionLines(String trajectory) {
        return ReMeUtils.extractPrefixedLines(trajectory, "ACTION:");
    }

    static List<String> observationKeys(String trajectory) {
        return ReMeUtils.extractObservationKeys(trajectory);
    }

    static List<String> toolNames(String trajectory) {
        return ReMeUtils.extractToolNames(trajectory);
    }

    static String assistantSummary(String trajectory) {
        String assistant = ReMeUtils.lastPrefixedLine(trajectory, "ASSISTANT:");
        if (!assistant.isBlank()) {
            return limit(compact(assistant), 220);
        }
        return limit(compact(trajectory), 220);
    }

    static String firstToolName(List<String> tools, List<String> actionLines) {
        if (tools != null && !tools.isEmpty()) {
            return compact(tools.get(0));
        }
        if (actionLines != null && !actionLines.isEmpty()) {
            String action = compact(actionLines.get(0));
            int bracketIndex = action.indexOf('(');
            return bracketIndex >= 0 ? compact(action.substring(0, bracketIndex)) : action;
        }
        return "";
    }

    static String queryHint(String query) {
        return limit(compact(query), 120);
    }

    static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String compacted = compact(value);
        if (compacted.length() <= maxLength) {
            return compacted;
        }
        return compacted.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    static double scoreAt(List<?> rawScores, int index, double defaultValue) {
        if (rawScores == null || index < 0 || index >= rawScores.size()) {
            return defaultValue;
        }
        Object raw = rawScores.get(index);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return defaultValue;
    }

    static Boolean feedbackLabel(String trajectory) {
        String feedback = ReMeUtils.extractFeedbackSignal(trajectory);
        if ("helpful".equals(feedback)) {
            return true;
        }
        if ("harmful".equals(feedback)) {
            return false;
        }
        return null;
    }

    static Double feedbackScore(String trajectory) {
        Boolean label = feedbackLabel(trajectory);
        if (label == null) {
            return null;
        }
        return label ? 1.0d : 0.0d;
    }

    private static String normalizeTrajectoryMap(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder();
        if (map.containsKey("role") && map.containsKey("content")) {
            String role = String.valueOf(map.get("role")).toUpperCase(Locale.ROOT);
            appendLine(builder, role, map.get("content"));
            return builder.toString().trim();
        }

        appendLine(builder, "USER", map.get("query"));
        appendLine(builder, "ASSISTANT", map.get("response"));
        appendLine(builder, "FEEDBACK", map.get("feedback"));
        if (builder.length() > 0) {
            return builder.toString().trim();
        }
        return compact(String.valueOf(map));
    }

    private static void appendLine(StringBuilder builder, String prefix, Object value) {
        if (value == null) {
            return;
        }
        String text = compact(String.valueOf(value));
        if (text.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(prefix).append(": ").append(text);
    }
}
