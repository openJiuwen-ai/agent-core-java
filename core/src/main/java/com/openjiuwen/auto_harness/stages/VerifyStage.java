/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.stages;

import com.openjiuwen.auto_harness.contexts.BaseExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abstract base for all verify stages.
 *
 * <p>Mirrors Python's {@code VerifyStage} and module helpers in
 * {@code openjiuwen/auto_harness/stages/verify.py}.</p>
 */
public abstract class VerifyStage extends TaskStage {

    @Override
    public String name() {
        return "verify";
    }

    @Override
    public String slot() {
        return "verify";
    }

    @Override
    public String displayName() {
        return "CI 门禁检查";
    }

    @Override
    public String description() {
        return "Verify code changes.";
    }

    @Override
    public List<String> produces() {
        return List.of("verify_report");
    }

    @Override
    public abstract java.util.Iterator<Object> stream(BaseExecutionContext ctx);

    public static String summarizeText(String text) {
        return summarizeText(text, 6, 400);
    }

    public static String summarizeText(String text, int maxLines, int maxChars) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String stripped = line.strip();
            if (!stripped.isEmpty()) {
                lines.add(stripped);
            }
        }
        String summary = String.join("\n", lines.subList(0, Math.min(maxLines, lines.size()))).strip();
        if (summary.length() > maxChars) {
            return summary.substring(0, Math.max(0, maxChars - 3)).stripTrailing() + "...";
        }
        if (lines.size() > maxLines) {
            return summary + "\n...";
        }
        return summary;
    }

    public static List<String> iterCiGateMessages(Map<String, Object> ciResult) {
        return iterCiGateMessages(ciResult, "");
    }

    public static List<String> iterCiGateMessages(Map<String, Object> ciResult, String prefix) {
        Object rawGates = ciResult == null ? null : ciResult.get("gates");
        if (!(rawGates instanceof List<?> gates) || gates.isEmpty()) {
            String errors = summarizeText(ciResult == null ? "" : String.valueOf(ciResult.getOrDefault("errors", "")));
            if (errors.isEmpty()) {
                errors = "未匹配到任何 CI 门禁";
            }
            return List.of(prefix + "CI 检查未执行: " + errors);
        }
        List<String> parts = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        for (Object gate : gates) {
            if (!(gate instanceof Map<?, ?> map)) {
                continue;
            }
            String name = String.valueOf(map.containsKey("name") ? map.get("name") : "unknown");
            boolean passed = isTruthy(map.get("passed"));
            parts.add(name + "=" + (passed ? "PASS" : "FAIL"));
        }
        messages.add(prefix + "CI 结果: " + String.join(", ", parts));
        for (Object gate : gates) {
            if (!(gate instanceof Map<?, ?> map) || isTruthy(map.get("passed"))) {
                continue;
            }
            String name = String.valueOf(map.containsKey("name") ? map.get("name") : "unknown");
            Object rawOutput = map.containsKey("output") ? map.get("output") : "";
            String detail = summarizeText(String.valueOf(rawOutput));
            messages.add(prefix + "[" + name + "] " + (detail.isEmpty() ? "无错误输出" : detail));
        }
        return messages;
    }

    public static String formatCiStatusForEvaluator(Map<String, Object> ciResult) {
        Object rawGates = ciResult == null ? null : ciResult.get("gates");
        if (!(rawGates instanceof List<?> gates) || gates.isEmpty()) {
            String errors = summarizeText(ciResult == null ? "" : String.valueOf(ciResult.getOrDefault("errors", "")));
            if (errors.isEmpty()) {
                errors = "未执行任何门禁";
            }
            return "结论: blocking failure\n详情: " + errors;
        }
        List<String> lines = new ArrayList<>();
        lines.add(isTruthy(ciResult.get("passed")) ? "结论: pass" : "结论: blocking failure");
        for (Object gate : gates) {
            if (!(gate instanceof Map<?, ?> map)) {
                continue;
            }
            String name = String.valueOf(map.containsKey("name") ? map.get("name") : "unknown");
            boolean passed = isTruthy(map.get("passed"));
            Object rawOutput = map.containsKey("output") ? map.get("output") : "";
            String detail = summarizeText(String.valueOf(rawOutput));
            String line = "- " + name + ": " + (passed ? "PASS" : "FAIL");
            if (!passed && !detail.isEmpty()) {
                line += " | " + detail;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    public static boolean isTruthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0;
        }
        return value != null && !"false".equalsIgnoreCase(String.valueOf(value));
    }
}
