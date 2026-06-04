/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.loop.LoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;
import com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_loop_comp} in
 * {@code tests.unit_tests.core.component.test_loop_comp}.
 */
@Tag("unit-test")
class TestLoopComp {

    @Test
    @DisplayName("loop_number over maximum limit raises loop execution error")
    void testLoopNumberExceedsMaxLimit() {
        BaseError error = assertThrows(BaseError.class,
                () -> buildNumberLoopFlow(1001).invoke(baseInputs(), newSession(), null));

        assertEquals(StatusCode.COMPONENT_LOOP_EXECUTION_ERROR.getCode(), error.getCode());
        assertTrue(error.getMessage().contains("exceeds maximum limit"));
    }

    @Test
    @DisplayName("number loop produces per-iteration output and updates intermediate var")
    void testLoopNumber() {
        WorkflowOutput result = buildNumberLoopFlow(12).invoke(baseInputs(), newSession(), null);

        assertEquals(Map.of("output", Map.of("end_out", Map.of(
                "user_num", 117,
                "l_out1", List.of(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21),
                "l_out2", List.of(7, 17, 27, 37, 47, 57, 67, 77, 87, 97, 107, 117)
        ))), result.getResult());
    }

    @Test
    @DisplayName("loop group stream-style component yields and transforms values")
    void testLoopGroupComponentStream() {
        CustomStream customStream = new CustomStream();

        List<Object> produced = collect(customStream.stream(Map.of("value", List.of(0, 1)), null, null));
        List<Object> transformed = collect(customStream.transform(Map.of("value", produced), null, null));
        Object collected = customStream.collect(Map.of("value", transformed), null, null);

        assertEquals(List.of(Map.of("value", "stream_0"), Map.of("value", "stream_1")), produced);
        assertFalse(transformed.isEmpty());
        assertNotNull(collected);
        assertTrue(String.valueOf(collected).contains("transform_"));
    }

    private static Workflow buildNumberLoopFlow(Object loopNumber) {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("input_arr", "${array}", "input_num", "${num}"), null);
        flow.setEndComp("end", new End(), Map.of("end_out", "${loop}"), null, null, null, null);

        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp("loop_1", new AddTenNode(), true,
                Map.of("source", "${loop.index}"), null, null, null, null);
        loopGroup.addWorkflowComp("loop_2", new AddTenNode(), true,
                Map.of("source", "${loop.user_num}"), null, null, null, null);
        loopGroup.addWorkflowComp("loop_3",
                new LoopSetVariableComponent(Map.of("${loop.user_num}", "${loop_2.result}")),
                true, null, null, null, null, null);
        loopGroup.startNodes(List.of("loop_1"));
        loopGroup.endNodes(List.of("loop_3"));
        loopGroup.addConnection("loop_1", "loop_2");
        loopGroup.addConnection("loop_2", "loop_3");

        LoopComponentImpl loopComponent = new LoopComponentImpl(
                loopGroup,
                Map.of("l_out1", "${loop_1.result}", "l_out2", "${loop_2.result}"));
        flow.addWorkflowComp("loop", loopComponent, Map.of(
                "loop_type", "number",
                "loop_number", loopNumber,
                "intermediate_var", Map.of("user_num", "${start.input_num}")), null);

        flow.addConnection("start", "loop");
        flow.addConnection("loop", "end");
        return flow;
    }

    private static Map<String, Object> baseInputs() {
        return Map.of("array", List.of(4, 5, 6), "num", -3);
    }

    private static WorkflowSessionApi newSession() {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of());
    }

    private static List<Object> collect(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static final class AddTenNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Object source = inputMap.get("source");
            if (source == null) {
                return Map.of("result", 10);
            }
            if (source instanceof Number number) {
                return Map.of("result", number.intValue() + 10);
            }
            return Map.of("result", Integer.parseInt(String.valueOf(source)) + 10);
        }
    }

    private static final class CustomStream extends WorkflowComponent {
        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            @SuppressWarnings("unchecked")
            Object value = ((Map<String, Object>) inputs).get("value");
            List<Object> outputs = new ArrayList<>();
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    outputs.add(Map.of("value", "stream_" + item));
                }
            } else {
                outputs.add(Map.of("value", "stream_" + value));
            }
            return outputs.iterator();
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            @SuppressWarnings("unchecked")
            Object value = ((Map<String, Object>) inputs).get("value");
            return Map.of("value", String.valueOf(value));
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            @SuppressWarnings("unchecked")
            Object value = ((Map<String, Object>) inputs).get("value");
            List<Object> outputs = new ArrayList<>();
            if (value instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    outputs.add(Map.of("value", "transform_" + item));
                }
            } else {
                outputs.add(Map.of("value", "transform_" + value));
            }
            return outputs.iterator();
        }
    }
}
