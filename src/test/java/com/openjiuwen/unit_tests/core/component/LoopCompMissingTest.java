/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.component;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.component.loop.LoopBreakComponent;
import com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent;
import com.openjiuwen.core.workflow.condition.ArrayConditionInSession;
import com.openjiuwen.core.workflow.condition.NumberConditionInSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import com.openjiuwen.core.session.state.InMemoryState;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Mirrors Python's {@code test_loop_comp.py} in
 * {@code tests/unit_tests/core/component/test_loop_comp.py}.
 */
class LoopCompMissingTest {

    @Test
    void testLoopNumberExceedsMaxLimit() {
        BaseError error = ErrorHelper.buildError(
                StatusCode.COMPONENT_LOOP_EXECUTION_ERROR,
                "reason", "loop_number 1001 exceeds maximum limit 1000",
                "comp", "loop");

        assertEquals(StatusCode.COMPONENT_LOOP_EXECUTION_ERROR.getCode(), error.getCode());
        assertThat(error.getMessage()).contains("exceeds maximum limit");
    }

    @Test
    void testLoopNumber() {
        NumberConditionInSession condition = new NumberConditionInSession(12);
        int userNum = -3;
        List<Integer> lOut1 = new ArrayList<>();
        List<Integer> lOut2 = new ArrayList<>();

        for (int index = 0; (Boolean) condition.doInvoke(Map.of(), sessionWithIndex(index)); index++) {
            int loop1Result = addTen(index);
            int loop2Result = addTen(userNum);
            LoopSetVariableComponent.generateOutput(new String[] {"user_num"}, loop2Result);
            userNum = loop2Result;
            lOut1.add(loop1Result);
            lOut2.add(loop2Result);
        }

        Map<String, Object> loopOutput = new LinkedHashMap<>();
        loopOutput.put("user_num", userNum);
        loopOutput.put("l_out1", lOut1);
        loopOutput.put("l_out2", lOut2);

        assertThat(Map.of("output", Map.of("end_out", loopOutput))).isEqualTo(Map.of(
                "output", Map.of(
                        "end_out", Map.of(
                                "user_num", 117,
                                "l_out1", List.of(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21),
                                "l_out2", List.of(7, 17, 27, 37, 47, 57, 67, 77, 87, 97, 107, 117)))));
    }

    @Test
    void testLoopGroupComponentStream() {
        CustomStream producer = new CustomStream();
        CustomStream transformer = new CustomStream();
        CustomStream consumer = new CustomStream();

        List<Object> collectedChunks = new ArrayList<>();
        Iterator<Object> produced = producer.stream(Map.of("value", 0), session(), null);
        Iterator<Object> transformed = transformer.transform(Map.of("value", produced), session(), null);
        Object collected = consumer.collect(Map.of("value", transformed), session(), null);
        collectedChunks.add(collected);

        assertThat(collectedChunks).isNotEmpty();
        assertThat(String.valueOf(collectedChunks)).contains("stream_0").contains("transform_");
    }

    @Test
    void testLoopBreakComponentSignalsParentLoopState() {
        WorkflowRuntimeSession parent = new WorkflowRuntimeSession(
                "workflow",
                null,
                "session",
                WorkflowRuntimeState.from(InMemoryState.create()),
                null);
        WorkflowRuntimeSession breakNodeSession = WorkflowRuntimeSession.nodeSession(parent, "break");

        LoopBreakComponent breakNode = new LoopBreakComponent();
        Object result = breakNode.invoke(Map.of(), new NodeSessionApi(breakNodeSession), null);

        assertEquals(Map.of(), result);
        assertThat(parent.state().get("_broken")).isEqualTo(true);
        assertThat(breakNode.to_executable()).isSameAs(breakNode.toExecutable());
    }

    @Test
    void testLoopConditionBoundaries() {
        assertThat((Boolean) new ArrayConditionInSession(Map.of()).doInvoke(Map.of(), sessionWithIndex(0))).isFalse();
        assertThat((Boolean) new NumberConditionInSession(1000).doInvoke(Map.of(), sessionWithIndex(999))).isTrue();
        assertThat((Boolean) new NumberConditionInSession(1000).doInvoke(Map.of(), sessionWithIndex(1000))).isFalse();
    }

    private static int addTen(int value) {
        return value + 10;
    }

    private static WorkflowRuntimeSession sessionWithIndex(Object index) {
        WorkflowRuntimeSession session = new WorkflowRuntimeSession(
                "workflow",
                null,
                "session",
                WorkflowRuntimeState.from(InMemoryState.create()),
                null);
        session.state().update(Map.of("index", index));
        session.state().commit();
        return session;
    }

    private static BaseSession session() {
        return new BaseSession() {
            @Override
            public String sessionId() {
                return "loop-comp-missing-test";
            }
        };
    }

    private static final class CustomStream extends WorkflowComponent<Object, Object> {
        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            if (!(inputs instanceof Map<?, ?> inputMap) || !inputMap.containsKey("value")) {
                return List.<Object>of(1).iterator();
            }
            Object value = inputMap.get("value");
            if (value instanceof Integer integer) {
                return List.<Object>of(Map.of("value", "stream_" + integer)).iterator();
            }
            if (value instanceof Iterable<?> iterable) {
                List<Object> output = new ArrayList<>();
                for (Object item : iterable) {
                    output.add(Map.of("value", "stream_" + item));
                }
                return output.iterator();
            }
            return List.<Object>of(Map.of("value", "stream_" + value)).iterator();
        }

        @Override
        public Object collect(Object inputs, BaseSession session, ModelContext context) {
            Object values = ((Map<?, ?>) inputs).get("value");
            if (values instanceof Iterator<?> iterator) {
                StringBuilder total = new StringBuilder();
                while (iterator.hasNext()) {
                    total.append(iterator.next()).append(';');
                }
                return Map.of("value", total.toString());
            }
            return Map.of("value", String.valueOf(values));
        }

        @Override
        public Iterator<Object> transform(Object inputs, BaseSession session, ModelContext context) {
            Object values = ((Map<?, ?>) inputs).get("value");
            if (values instanceof Iterator<?> iterator) {
                List<Object> output = new ArrayList<>();
                while (iterator.hasNext()) {
                    output.add(Map.of("value", "transform_" + iterator.next()));
                }
                return output.iterator();
            }
            return List.<Object>of(Map.of("value", "transform_" + values)).iterator();
        }
    }
}
