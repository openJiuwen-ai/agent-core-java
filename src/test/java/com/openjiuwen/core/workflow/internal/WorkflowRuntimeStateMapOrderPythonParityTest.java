/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Mirrors Python's {@code CommitState.get_inputs} and {@code get_by_schema} in
 * {@code openjiuwen/core/session/state/workflow_state.py} and
 * {@code openjiuwen/core/session/utils.py}.
 */
class WorkflowRuntimeStateMapOrderPythonParityTest {

    @Test
    void setStateUsesIncomingOrderAcrossAllStatePartitions() {
        WorkflowRuntimeState state = WorkflowRuntimeState.create("", "consumer");
        state.setState(workflowState(
                linkedMap("zeta", 1, "alpha", 2),
                linkedMap("global_zeta", 3, "global_alpha", 4),
                linkedMap("comp_zeta", 5, "comp_alpha", 6),
                linkedMap("workflow_zeta", 7, "workflow_alpha", 8)));
        Map<String, Object> incoming = workflowState(
                linkedMap("alpha", 2, "zeta", 1),
                linkedMap("global_alpha", 4, "global_zeta", 3),
                linkedMap("comp_alpha", 6, "comp_zeta", 5),
                linkedMap("workflow_alpha", 8, "workflow_zeta", 7));

        state.setState(incoming);

        assertPartitionOrder(state, "io_state", List.of("alpha", "zeta"));
        assertPartitionOrder(state, "global_state", List.of("global_alpha", "global_zeta"));
        assertPartitionOrder(state, "comp_state", List.of("comp_alpha", "comp_zeta"));
        assertPartitionOrder(state, "workflow_state", List.of("workflow_alpha", "workflow_zeta"));
        assertPartitionOrder(incoming, "io_state", List.of("alpha", "zeta"));
        assertPartitionOrder(incoming, "global_state", List.of("global_alpha", "global_zeta"));
        assertPartitionOrder(incoming, "comp_state", List.of("comp_alpha", "comp_zeta"));
        assertPartitionOrder(incoming, "workflow_state", List.of("workflow_alpha", "workflow_zeta"));
    }

    @Test
    void getInputsPreservesStoredNestedOrderWithoutPreSorting() {
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
                "items", List.of(listItem),
                "extra_field", "extra-value");
        WorkflowRuntimeState state = WorkflowRuntimeState.create("", "consumer");
        state.getIoState().setState(source);
        Map<String, Object> schema = linkedMap(
                "data", "${intent}",
                "generic", "${generic}",
                "items", "${items}",
                "extra_field", "${extra_field}");

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
        assertEquals("extra-value", inputs.get("extra_field"));
        assertInstanceOf(LinkedHashMap.class, actualIntent);
        assertInstanceOf(LinkedHashMap.class, actualGeneric);
        assertInstanceOf(LinkedHashMap.class, castMap(actualItems.get(0)));
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
        Map<String, Object> incomingState = state.getState();
        Map<String, Object> incomingPayload = linkedMap("second", 22, "first", 1, "extra", true);
        castMap(incomingState.get("io_state")).put("payload", incomingPayload);

        state.setState(incomingState);

        Map<String, Object> actualPayload = castMap(state.getIoState().get("payload"));
        assertEquals(List.of("second", "first", "extra"), List.copyOf(actualPayload.keySet()));
        assertEquals(22, actualPayload.get("second"));
        assertEquals(Boolean.TRUE, actualPayload.get("extra"));
        assertEquals(List.of("second", "first", "extra"), List.copyOf(incomingPayload.keySet()));
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

    private static Map<String, Object> workflowState(
            Map<String, Object> ioState,
            Map<String, Object> globalState,
            Map<String, Object> compState,
            Map<String, Object> workflowState) {
        return linkedMap(
                "io_state", ioState,
                "global_state", globalState,
                "comp_state", compState,
                "workflow_state", workflowState);
    }

    private static void assertPartitionOrder(
            WorkflowRuntimeState state, String partition, List<String> expectedOrder) {
        assertPartitionOrder(state.getState(), partition, expectedOrder);
    }

    private static void assertPartitionOrder(
            Map<String, Object> state, String partition, List<String> expectedOrder) {
        assertEquals(expectedOrder, List.copyOf(castMap(state.get(partition)).keySet()));
    }
}
