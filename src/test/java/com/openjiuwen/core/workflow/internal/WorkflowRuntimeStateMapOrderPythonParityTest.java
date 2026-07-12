/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's {@code CommitState.get_inputs} and {@code get_by_schema} in
 * {@code openjiuwen/core/session/state/workflow_state.py} and
 * {@code openjiuwen/core/session/utils.py}.
 */
class WorkflowRuntimeStateMapOrderPythonParityTest {

    @Test
    void nestedDomainMapsAndListMapsPreserveInsertionOrderAndValues() {
        Map<String, Object> metadata = linkedMap(
                "trace_id", "trace-1",
                "score", 0.75D);
        Map<String, Object> intent = linkedMap(
                "classification_id", 2,
                "reason", "query matches weather intent",
                "category_name", "weather",
                "metadata", metadata);
        Map<String, Object> generic = linkedMap(
                "zeta", 1,
                "alpha", true,
                "middle", "value",
                "index", 7);
        Map<String, Object> listItem = linkedMap(
                "dict", "dictionary",
                "k1", "key",
                "arr", "array");
        Map<String, Object> source = linkedMap(
                "intent", intent,
                "generic", generic,
                "items", List.of(listItem));
        WorkflowRuntimeState state = WorkflowRuntimeState.create("", "consumer");
        state.getIoState().setState(source);
        state.setState(castMap(sortRecursively(state.getState())));
        Map<String, Object> schema = linkedMap(
                "data", "${intent}",
                "generic", "${generic}",
                "items", "${items}");

        Map<String, Object> inputs = state.getInputs(schema);

        Map<String, Object> actualIntent = castMap(inputs.get("data"));
        assertEquals(List.of("classification_id", "reason", "category_name", "metadata"),
                List.copyOf(actualIntent.keySet()));
        assertEquals(2, actualIntent.get("classification_id"));
        assertEquals("query matches weather intent", actualIntent.get("reason"));
        assertEquals("weather", actualIntent.get("category_name"));
        assertEquals(List.of("trace_id", "score"),
                List.copyOf(castMap(actualIntent.get("metadata")).keySet()));

        Map<String, Object> actualGeneric = castMap(inputs.get("generic"));
        assertEquals(List.of("zeta", "alpha", "middle", "index"), List.copyOf(actualGeneric.keySet()));
        assertEquals(1, actualGeneric.get("zeta"));
        assertEquals(Boolean.TRUE, actualGeneric.get("alpha"));
        assertEquals("value", actualGeneric.get("middle"));
        assertEquals(7, actualGeneric.get("index"));

        List<?> actualItems = assertInstanceOf(List.class, inputs.get("items"));
        assertEquals(List.of("dict", "k1", "arr"), List.copyOf(castMap(actualItems.get(0)).keySet()));
        assertEquals(List.of("classification_id", "reason", "category_name", "metadata"),
                List.copyOf(intent.keySet()));
        assertEquals(List.of("dict", "k1", "arr"), List.copyOf(listItem.keySet()));
        assertEquals(List.of("classification_id", "reason", "category_name", "metadata"),
                List.copyOf(castMap(state.getIoState().get("intent")).keySet()));
    }

    @Test
    void changedValuesAreAcceptedInsteadOfBeingReplacedByCurrentState() {
        WorkflowRuntimeState state = WorkflowRuntimeState.create("", "consumer");
        state.getIoState().setState(linkedMap(
                "payload", linkedMap("first", 1, "second", 2)));
        Map<String, Object> incomingState = castMap(sortRecursively(state.getState()));
        castMap(castMap(incomingState.get("io_state")).get("payload")).put("second", 22);

        state.setState(incomingState);

        Map<String, Object> actualPayload = castMap(state.getIoState().get("payload"));
        assertEquals(22, actualPayload.get("second"));
    }

    @Test
    void legacyCompatibilityOrderingRemainsNarrowlyScoped() {
        WorkflowRuntimeState state = WorkflowRuntimeState.create("", "consumer");
        state.getIoState().setState(linkedMap(
                "arr", "array",
                "k1", "key",
                "dict", "dictionary",
                "input1", 1,
                "input2", 2,
                "input3", 3,
                "input4", 4,
                "l_item", "item",
                "other", "other-value",
                "l_index", 0));

        Map<String, Object> schemaPriority = linkedMap(
                "dict", "${dict}",
                "k1", "${k1}",
                "arr", "${arr}");
        assertEquals(List.of("arr", "k1", "dict"),
                List.copyOf(state.getInputs(schemaPriority).keySet()));

        Map<String, Object> numberedInputs = linkedMap(
                "input1", "${input1}",
                "input2", "${input2}",
                "input3", "${input3}",
                "input4", "${input4}");
        assertEquals(List.of("input4", "input3", "input2", "input1"),
                List.copyOf(state.getInputs(numberedInputs).keySet()));

        Map<String, Object> loopInputs = linkedMap(
                "l_index", "${l_index}",
                "other", "${other}",
                "l_item", "${l_item}");
        assertEquals(List.of("l_item", "other", "l_index"),
                List.copyOf(state.getInputs(loopInputs).keySet()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) assertInstanceOf(Map.class, value);
    }

    private static Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put(String.valueOf(entries[index]), entries[index + 1]);
        }
        return result;
    }

    private static Object sortRecursively(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), sortRecursively(entry.getValue()));
            }
            return new LinkedHashMap<>(sorted);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(WorkflowRuntimeStateMapOrderPythonParityTest::sortRecursively).toList();
        }
        return value;
    }
}
