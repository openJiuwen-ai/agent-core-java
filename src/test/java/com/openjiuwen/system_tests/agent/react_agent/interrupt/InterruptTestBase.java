/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests/system_tests/agent/react_agent/interrupt/test_base.py.
 * Shared test infrastructure for interrupt tests.
 */
public final class InterruptTestBase {

    public static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    public static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    public static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "qwen3-coder-flash");
    public static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");

    private InterruptTestBase() {}

    public static boolean hasApiConfig() {
        return !API_KEY.isEmpty() && !API_BASE.isEmpty();
    }

    public static void assertInterruptResult(Map<String, Object> result, int expectedCount) {
        assertNotNull(result, "result must not be null");
        assertEquals("interrupt", result.get("result_type"),
                "Expected result_type=interrupt, got " + result.get("result_type"));
        List<?> interruptIds = (List<?>) result.get("interrupt_ids");
        List<?> stateList = (List<?>) result.get("state");
        assertNotNull(interruptIds, "interrupt_ids must not be null");
        assertEquals(expectedCount, interruptIds.size(),
                "Expected " + expectedCount + " interrupts, actual " + interruptIds.size());
        if (stateList != null) {
            assertEquals(expectedCount, stateList.size());
        }
    }

    public static void assertInterruptResult(Map<String, Object> result) {
        assertInterruptResult(result, 1);
    }

    public static List<String> getInterruptIds(Map<String, Object> result) {
        return (List<String>) result.get("interrupt_ids");
    }

    public static List<?> getStateList(Map<String, Object> result) {
        return (List<?>) result.get("state");
    }

    public static void assertAnswerResult(Map<String, Object> result) {
        assertNotNull(result, "result must not be null");
        assertEquals("answer", result.get("result_type"),
                "Expected result_type=answer, got " + result.get("result_type"));
    }

    @SuppressWarnings("unchecked")
    public static String getToolNameFromState(Object stateItem) {
        if (stateItem instanceof Map) {
            Map<String, Object> state = (Map<String, Object>) stateItem;
            Object payload = state.get("payload");
            if (payload instanceof Map) {
                return (String) ((Map<String, Object>) payload).getOrDefault("tool_name", "");
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public static String getFilepathFromState(Object stateItem) {
        if (stateItem instanceof Map) {
            Map<String, Object> state = (Map<String, Object>) stateItem;
            Object payload = state.get("payload");
            if (payload instanceof Map) {
                Object toolArgs = ((Map<String, Object>) payload).get("tool_args");
                if (toolArgs instanceof Map) {
                    return (String) ((Map<String, Object>) toolArgs).getOrDefault("filepath", "");
                } else if (toolArgs instanceof String) {
                    try {
                        return ""; // simplified
                    } catch (Exception e) {
                        return "";
                    }
                }
            }
        }
        return "";
    }

    public static InteractiveInput confirmInterrupt(String toolCallId) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", true,
                "feedback", "Confirm"
        ));
        return input;
    }

    public static InteractiveInput confirmInterrupt(String toolCallId, boolean autoConfirm) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", true,
                "feedback", "Confirm",
                "auto_confirm", autoConfirm
        ));
        return input;
    }

    public static InteractiveInput rejectInterrupt(String toolCallId, String feedback) {
        InteractiveInput input = new InteractiveInput();
        input.update(toolCallId, Map.of(
                "approved", false,
                "feedback", feedback
        ));
        return input;
    }
}
