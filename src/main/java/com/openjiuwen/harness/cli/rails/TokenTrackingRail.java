/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.rails;

import com.openjiuwen.core.single_agent.rail.AgentCallbackContext;
import com.openjiuwen.core.single_agent.rail.AgentRail;
import com.openjiuwen.core.single_agent.rail.ModelCallInputs;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Token usage tracking rail for CLI status and cost summaries.
 *
 * <p>Mirrors Python's {@code TokenTrackingRail} in
 * {@code openjiuwen/harness/cli/rails/token_tracker.py}.</p>
 */
public class TokenTrackingRail extends AgentRail {

    private long totalInputTokens;
    private long totalOutputTokens;
    private long callCount;

    public TokenTrackingRail() {
        setPriority(10);
    }

    @Override
    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        callCount++;
        Object response = readResponse(context);
        if (response == null) {
            return completed();
        }
        Object usage = readProperty(response, "usage");
        if (usage == null) {
            usage = readProperty(response, "usage_metadata");
        }
        if (usage == null) {
            return completed();
        }
        totalInputTokens += readTokenCount(usage, "prompt_tokens", "promptTokens", "input_tokens", "inputTokens");
        totalOutputTokens += readTokenCount(
                usage,
                "completion_tokens",
                "completionTokens",
                "output_tokens",
                "outputTokens"
        );
        return completed();
    }

    public Map<String, Long> getSummary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("input_tokens", totalInputTokens);
        summary.put("output_tokens", totalOutputTokens);
        summary.put("total_tokens", totalInputTokens + totalOutputTokens);
        summary.put("model_calls", callCount);
        return summary;
    }

    public long getTotalInputTokens() {
        return totalInputTokens;
    }

    public long getTotalOutputTokens() {
        return totalOutputTokens;
    }

    public long getCallCount() {
        return callCount;
    }

    private static Object readResponse(AgentCallbackContext context) {
        if (context == null) {
            return null;
        }
        Object inputs = context.getInputs();
        if (inputs instanceof ModelCallInputs modelCallInputs) {
            return modelCallInputs.getResponse();
        }
        return readProperty(inputs, "response");
    }

    private static long readTokenCount(
            Object usage,
            String primarySnakeName,
            String primaryCamelName,
            String fallbackSnakeName,
            String fallbackCamelName
    ) {
        Object value = readProperty(usage, primarySnakeName);
        if (value == null) {
            value = readProperty(usage, primaryCamelName);
        }
        if (value == null) {
            value = readProperty(usage, fallbackSnakeName);
        }
        if (value == null) {
            value = readProperty(usage, fallbackCamelName);
        }
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static Object readProperty(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String methodName : new String[] {"get" + suffix, name}) {
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // Preserve Python getattr-style tolerance for dynamic callback payloads.
            }
        }
        return null;
    }
}
