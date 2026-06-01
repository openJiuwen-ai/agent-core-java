/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.security.guardrail;

import com.openjiuwen.core.runner.callback.CallbackFramework;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test configuration and fixtures for guardrail framework tests.
 * Mirrors Python's {@code tests/unit_tests/core/security/guardrail/conftest.py}.
 */
public final class GuardrailTestConfig {

    private GuardrailTestConfig() {
    }

    public static CallbackFramework framework() {
        return new CallbackFramework(false, false);
    }

    public static CallbackFramework frameworkWithLogging() {
        return new CallbackFramework(false, true);
    }

    public static GuardrailBackend mockBackend() {
        return data -> RiskAssessment.builder()
                .hasRisk(false)
                .riskLevel(RiskLevel.SAFE)
                .riskType(null)
                .build();
    }

    public static GuardrailBackend riskyBackend() {
        return data -> RiskAssessment.builder()
                .hasRisk(true)
                .riskLevel(RiskLevel.HIGH)
                .riskType("test_risk")
                .build();
    }

    public static GuardrailResult safeGuardrailResult() {
        return GuardrailResult.pass();
    }

    public static GuardrailResult blockedGuardrailResult() {
        Map<String, Object> details = new HashMap<>();
        details.put("matched_pattern", "ignore instructions");
        return GuardrailResult.block(RiskLevel.HIGH, "prompt_injection", details, null);
    }

    public static GuardrailBackend riskyBackendWithDetails() {
        return data -> {
            Map<String, Object> details = new HashMap<>();
            details.put("matched_pattern", "ignore previous instructions");
            details.put("confidence", 0.95);
            return RiskAssessment.builder()
                    .hasRisk(true)
                    .riskLevel(RiskLevel.HIGH)
                    .riskType("prompt_injection")
                    .details(details)
                    .build();
        };
    }

    public static BaseGuardrail simpleGuardrail() {
        return new BaseGuardrail(null, Arrays.asList("test_event"), true) {
            @Override
            protected List<String> defaultEvents() {
                return Arrays.asList("test_event");
            }

            @Override
            public GuardrailResult detect(String eventName, Object[] args, Map<String, Object> kwargs) throws Exception {
                return GuardrailResult.pass();
            }
        };
    }

    public static Map<String, Object> userInputData() {
        Map<String, Object> data = new HashMap<>();
        data.put("text", "Hello, how are you?");
        data.put("user_id", "user123");
        data.put("session_id", "session456");
        return data;
    }

    public static Map<String, Object> llmInputData() {
        Map<String, Object> data = new HashMap<>();
        data.put("prompt", "You are a helpful assistant.\nUser: What is 2+2?");
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a helpful assistant.");
        messages.add(systemMsg);
        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "What is 2+2?");
        messages.add(userMsg);
        data.put("messages", messages);
        data.put("model", "gpt-4");
        return data;
    }

    public static Map<String, Object> llmOutputData() {
        Map<String, Object> data = new HashMap<>();
        data.put("content", "2+2 equals 4.");
        data.put("tool_calls", new ArrayList<>());
        data.put("model", "gpt-4");
        return data;
    }

    public static Map<String, Object> toolInputData() {
        Map<String, Object> data = new HashMap<>();
        data.put("tool_name", "search");
        Map<String, String> toolInput = new HashMap<>();
        toolInput.put("query", "weather today");
        data.put("tool_input", toolInput);
        Map<String, String> callerContext = new HashMap<>();
        callerContext.put("user_id", "user123");
        data.put("caller_context", callerContext);
        return data;
    }

    public static Map<String, Object> toolOutputData() {
        Map<String, Object> data = new HashMap<>();
        data.put("tool_name", "search");
        Map<String, String> toolOutput = new HashMap<>();
        toolOutput.put("result", "Sunny, 25C");
        data.put("tool_output", toolOutput);
        data.put("execution_time", 0.5);
        return data;
    }

    public static Map<String, Object> planningStartData() {
        Map<String, Object> data = new HashMap<>();
        data.put("task", "Find the best Italian restaurant nearby");
        return data;
    }

    public static Map<String, Object> planningCompleteData() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> plan = new HashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        Map<String, Object> step1 = new HashMap<>();
        step1.put("action", "search");
        Map<String, String> params1 = new HashMap<>();
        params1.put("query", "Italian restaurants");
        step1.put("params", params1);
        steps.add(step1);
        Map<String, Object> step2 = new HashMap<>();
        step2.put("action", "filter");
        Map<String, String> params2 = new HashMap<>();
        params2.put("rating", ">4.0");
        step2.put("params", params2);
        steps.add(step2);
        Map<String, Object> step3 = new HashMap<>();
        step3.put("action", "sort");
        Map<String, String> params3 = new HashMap<>();
        params3.put("by", "distance");
        step3.put("params", params3);
        steps.add(step3);
        plan.put("steps", steps);
        data.put("plan", plan);
        data.put("steps", steps);
        return data;
    }
}
