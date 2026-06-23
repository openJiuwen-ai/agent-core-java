/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.operator.tool_call;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.UpdateValue;
import com.openjiuwen.core.operator.TunableSpec;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code TestToolCallOperator} and {@code TestToolCallOperatorCallbacks} in
 * {@code tests/unit_tests/core/operator/test_tool_call.py}.
 *
 * <p>Also validates the Java type that mirrors Python's {@code ToolCallOperator} in
 * {@code openjiuwen/core/operator/tool_call/base.py}.</p>
 */
class ToolCallOperatorTest {

    @Test
    void operatorId() {
        ToolCallOperator operator = new ToolCallOperator("test_tool");

        assertEquals("test_tool", operator.getOperatorId());
    }

    @Test
    void getTunablesWithoutDescriptions() {
        ToolCallOperator operator = new ToolCallOperator("test_tool");

        assertEquals(Map.of(), operator.getTunables());
    }

    @Test
    void getTunablesWithDescriptions() {
        ToolCallOperator operator = new ToolCallOperator(
                "test_tool",
                Map.of("tool1", "Description 1", "tool2", "Description 2")
        );

        Map<String, TunableSpec> tunables = operator.getTunables();

        assertTrue(tunables.containsKey("tool_description"));
        assertEquals("text", tunables.get("tool_description").kind());
    }

    @Test
    void setParameterToolDescriptionTriggersCallback() {
        RecordingCallback callback = new RecordingCallback();
        ToolCallOperator operator = new ToolCallOperator("test_tool", null, callback::accept);
        Map<String, String> descriptions = orderedDescriptions("tool1", "Updated description 1", "tool2",
                "Updated description 2");

        operator.setParameter("tool_description", descriptions);

        assertEquals(1, callback.count);
        assertEquals("tool_description", callback.target);
        assertEquals(descriptions, callback.value);
    }

    @Test
    void setParameterUnknownTargetIgnoresUpdate() {
        RecordingCallback callback = new RecordingCallback();
        ToolCallOperator operator = new ToolCallOperator("test_tool", null, callback::accept);

        operator.setParameter("unknown", "value");

        assertEquals(0, callback.count);
        assertEquals(Map.of(), operator.getDescriptions());
    }

    @Test
    void setParameterInvalidValueIgnoresUpdate() {
        RecordingCallback callback = new RecordingCallback();
        ToolCallOperator operator = new ToolCallOperator("test_tool", null, callback::accept);

        operator.setParameter("tool_description", "not a dict");

        assertEquals(0, callback.count);
        assertEquals(Map.of(), operator.getDescriptions());
    }

    @Test
    void getStateReturnsToolDescription() {
        ToolCallOperator operator = new ToolCallOperator("test_tool", Map.of("tool1", "Description 1"));

        Map<String, Object> state = operator.getState();

        assertTrue(state.containsKey("tool_description"));
        assertEquals(Map.of("tool1", "Description 1"), state.get("tool_description"));
    }

    @Test
    void loadStateRestoresToolDescription() {
        ToolCallOperator operator = new ToolCallOperator("test_tool");

        operator.loadState(Map.of("tool_description", Map.of("tool1", "loaded desc")));

        assertEquals(Map.of("tool1", "loaded desc"), operator.getState().get("tool_description"));
    }

    @Test
    void setParameterTriggersCallback() {
        RecordingCallback callback = new RecordingCallback();
        ToolCallOperator operator = new ToolCallOperator("test_tool", null, callback::accept);

        operator.setParameter("tool_description", Map.of("tool1", "new desc"));

        assertEquals(1, callback.count);
        assertEquals("tool_description", callback.target);
        assertEquals(Map.of("tool1", "new desc"), callback.value);
    }

    @Test
    void loadStateTriggersCallback() {
        RecordingCallback callback = new RecordingCallback();
        ToolCallOperator operator = new ToolCallOperator("test_tool", null, callback::accept);

        operator.loadState(Map.of("tool_description", Map.of("tool1", "loaded desc")));

        assertEquals(1, callback.count);
        assertEquals("tool_description", callback.target);
        assertEquals(Map.of("tool1", "loaded desc"), callback.value);
    }

    @Test
    void applyUpdateReusesReplaceStateBehavior() {
        ToolCallOperator operator = new ToolCallOperator("test_tool");

        ApplyResult result = operator.applyUpdate(
                "tool_description",
                new UpdateValue(Map.of("tool1", "Updated description"))
        );

        assertEquals(Map.of("tool1", "Updated description"), operator.getState().get("tool_description"));
        assertTrue(result.isApplied());
        assertEquals(Map.of("tool1", "Updated description"), result.getValue());
    }

    @Test
    void applyUpdateReportsNoopForInvalidToolDescriptionValue() {
        ToolCallOperator operator = new ToolCallOperator("test_tool");

        ApplyResult result = operator.applyUpdate("tool_description", new UpdateValue("not a dict"));

        assertEquals(Map.of(), operator.getState().get("tool_description"));
        assertFalse(result.isApplied());
    }

    private static Map<String, String> orderedDescriptions(String key1, String value1, String key2, String value2) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put(key1, value1);
        descriptions.put(key2, value2);
        return descriptions;
    }

    private static final class RecordingCallback {
        private int count;
        private String target;
        private Object value;

        private void accept(String target, Object value) {
            this.count++;
            this.target = target;
            this.value = value;
        }
    }
}
