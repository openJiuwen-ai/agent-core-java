/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.system_tests.agent.react_agent.interrupt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code tests/system_tests/agent/react_agent/interrupt/test_base.py}.
 * Shared test infrastructure for interrupt tests.
 */
public final class InterruptTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final String API_BASE = System.getenv().getOrDefault("API_BASE", "");
    public static final String API_KEY = System.getenv().getOrDefault("API_KEY", "");
    public static final String MODEL_NAME = System.getenv().getOrDefault("MODEL_NAME", "qwen3-coder-flash");
    public static final String MODEL_PROVIDER = System.getenv().getOrDefault("MODEL_PROVIDER", "OpenAI");

    private InterruptTestBase() {
    }

    public static boolean hasApiConfig() {
        return !API_KEY.isEmpty() && !API_BASE.isEmpty();
    }

    public static void assertInterruptResult(Map<String, Object> result, int expectedCount) {
        assertNotNull(result, "result must not be null");
        assertEquals("interrupt", result.get("result_type"),
                "Expected result_type=interrupt, got " + result.get("result_type"));
        List<String> interruptIds = getInterruptIds(result);
        List<?> stateList = getStateList(result);
        assertEquals(expectedCount, interruptIds.size(),
                "Expected " + expectedCount + " interrupts, actual " + interruptIds.size());
        if (!stateList.isEmpty()) {
            assertEquals(expectedCount, stateList.size());
        }
    }

    public static void assertInterruptResult(Map<String, Object> result) {
        assertInterruptResult(result, 1);
    }

    @SuppressWarnings("unchecked")
    public static List<String> getInterruptIds(Map<String, Object> result) {
        Object ids = result.get("interrupt_ids");
        if (ids instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public static List<?> getStateList(Map<String, Object> result) {
        Object state = result.get("state");
        return state instanceof List<?> list ? list : List.of();
    }

    public static void assertAnswerResult(Map<String, Object> result) {
        assertNotNull(result, "result must not be null");
        assertEquals("answer", result.get("result_type"),
                "Expected result_type=answer, got " + result.get("result_type"));
    }

    public static String getToolNameFromState(Object stateItem) {
        Object payload = statePayloadValue(stateItem);
        if (payload instanceof Map<?, ?> payloadMap) {
            Object toolName = payloadMap.get("tool_name");
            return toolName != null ? String.valueOf(toolName) : "";
        }
        return "";
    }

    public static String getFilepathFromState(Object stateItem) {
        Object payload = statePayloadValue(stateItem);
        if (!(payload instanceof Map<?, ?> payloadMap)) {
            return "";
        }
        Object toolArgs = payloadMap.get("tool_args");
        Map<String, Object> args = normalizeArgs(toolArgs);
        Object filepath = args.get("filepath");
        return filepath != null ? String.valueOf(filepath) : "";
    }

    public static InteractiveInput confirmInterrupt(String toolCallId) {
        return confirmInterrupt(toolCallId, false);
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

    public static Map<String, Object> interruptResult(List<ToolCallState> calls) {
        return Map.of(
                "result_type", "interrupt",
                "interrupt_ids", calls.stream().map(ToolCallState::id).toList(),
                "state", calls.stream().map(InterruptTestBase::stateItem).toList()
        );
    }

    private static Object stateItem(ToolCallState call) {
        return Map.of("payload", Map.of("value", Map.of(
                "tool_name", call.toolName(),
                "tool_args", call.arguments(),
                "tool_call_id", call.id()
        )));
    }

    private static Object statePayloadValue(Object stateItem) {
        if (stateItem instanceof Map<?, ?> stateMap) {
            Object payload = stateMap.get("payload");
            if (payload instanceof Map<?, ?> payloadMap && payloadMap.containsKey("value")) {
                return payloadMap.get("value");
            }
            return payload;
        }
        return null;
    }

    private static Map<String, Object> normalizeArgs(Object rawArgs) {
        if (rawArgs instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            return copy;
        }
        if (rawArgs instanceof String text && !text.isBlank()) {
            try {
                return JSON.readValue(text, new TypeReference<>() {
                });
            } catch (JsonProcessingException e) {
                return Map.of();
            }
        }
        return Map.of();
    }

    public record ToolCallState(String id, String toolName, Object arguments) {
    }

    public abstract static class MockTool {
        private final String name;
        private int invokeCount;

        protected MockTool(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public int invokeCount() {
            return invokeCount;
        }

        public Map<String, Object> invoke(Map<String, Object> inputs) {
            invokeCount++;
            return doInvoke(inputs);
        }

        protected abstract Map<String, Object> doInvoke(Map<String, Object> inputs);
    }

    public static final class ReadTool extends MockTool {
        public ReadTool() {
            super("read");
        }

        @Override
        protected Map<String, Object> doInvoke(Map<String, Object> inputs) {
            String filepath = String.valueOf(inputs.getOrDefault("filepath", ""));
            return Map.of(
                    "success", true,
                    "content", "Content of file " + filepath,
                    "invoke_count", invokeCount()
            );
        }
    }

    public static final class WriteTool extends MockTool {
        public WriteTool() {
            super("write");
        }

        @Override
        protected Map<String, Object> doInvoke(Map<String, Object> inputs) {
            String filepath = String.valueOf(inputs.getOrDefault("filepath", ""));
            return Map.of(
                    "success", true,
                    "message", "Written to " + filepath,
                    "invoke_count", invokeCount()
            );
        }
    }

    public static final class ActionTool extends MockTool {
        public ActionTool(String name) {
            super(name);
        }

        @Override
        protected Map<String, Object> doInvoke(Map<String, Object> inputs) {
            String action = String.valueOf(inputs.getOrDefault("action", ""));
            return Map.of(
                    "success", true,
                    "data", "Execute " + name() + ": " + action,
                    "invoke_count", invokeCount()
            );
        }
    }

    public static final class TraceRail {
        private final List<String> toolNames;
        private final Map<String, Integer> executionCounts = new LinkedHashMap<>();

        public TraceRail(List<String> toolNames) {
            this.toolNames = new ArrayList<>(toolNames);
        }

        public void afterToolCall(String toolName) {
            if (toolNames.isEmpty() || toolNames.contains(toolName)) {
                executionCounts.merge(toolName, 1, Integer::sum);
            }
        }

        public int getExecutionCount(String toolName) {
            return executionCounts.getOrDefault(toolName, 0);
        }
    }
}
