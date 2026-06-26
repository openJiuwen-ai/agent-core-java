/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.summary.task.reasoning_bank;

import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code messages_to_text} helper in
 * {@code openjiuwen/extensions/context_evolver/summary/task/reasoning_bank/update.py}.
 */
public final class ReasoningBankUpdateUtils {

    private ReasoningBankUpdateUtils() {
    }

    public static String messagesToText(List<Map<String, Object>> messages) {
        StringBuilder output = new StringBuilder();
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.get("role"));
            String content = String.valueOf(message.get("content"));
            switch (role) {
                case "system" -> output.append("SYSTEM:\n").append(content).append("\n");
                case "assistant" -> output.append("ASSISTANT:\n").append(content).append("\n");
                case "user" -> output.append("USER:\n").append(content).append("\n");
                default -> throw new IllegalArgumentException("Unknown message role " + role + " in: " + message);
            }
        }
        return output.toString().strip();
    }

    @SuppressWarnings("unchecked")
    static String trajectoryToText(Object trajectory) {
        if (trajectory instanceof List<?> rawList) {
            return messagesToText((List<Map<String, Object>>) rawList);
        }
        return String.valueOf(trajectory);
    }

    static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }
}
