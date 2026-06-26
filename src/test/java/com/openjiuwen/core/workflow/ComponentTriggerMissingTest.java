/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.callback.CallbackDecorators;
import com.openjiuwen.core.runner.callback.WorkflowEvents;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests workflow component trigger events before and after invoke.
 *
 * <p>Mirrors Python's module in
 * {@code tests/unit_tests/core/workflow/test_component_trigger.py}.</p>
 */
class ComponentTriggerMissingTest {

    private static final String CALLBACK_NAMESPACE = "component-trigger-missing-test";

    @AfterEach
    void cleanup() {
        callbackFramework().unregisterNamespace(CALLBACK_NAMESPACE);
    }

    @Test
    void componentBatchInputTriggerFires() {
        List<String> triggered = synchronizedEvents();
        registerTransform(WorkflowEvents.COMPONENT_BATCH_INPUT, "before", kwargs -> {
            triggered.add("before");
            return CallbackDecorators.TRANSFORM_NOOP;
        });

        createSimpleWorkflow().invoke(Map.of("value", "test"), createWorkflowSession(), null);

        assertFalse(triggered.isEmpty(), "COMPONENT_BATCH_INPUT trigger should fire");
    }

    @Test
    void componentBatchOutputTriggerFires() {
        List<String> triggered = synchronizedEvents();
        registerTransform(WorkflowEvents.COMPONENT_BATCH_OUTPUT, "after", kwargs -> {
            triggered.add("after");
            return kwargs.get("result");
        });

        createSimpleWorkflow().invoke(Map.of("value", "test"), createWorkflowSession(), null);

        assertFalse(triggered.isEmpty(), "COMPONENT_BATCH_OUTPUT trigger should fire");
    }

    @Test
    void triggerFiresInCorrectOrder() {
        List<String> sequence = synchronizedEvents();
        registerTransform(WorkflowEvents.COMPONENT_BATCH_INPUT, "input", kwargs -> {
            sequence.add("input");
            return CallbackDecorators.TRANSFORM_NOOP;
        });
        registerTransform(WorkflowEvents.COMPONENT_BATCH_OUTPUT, "output", kwargs -> {
            sequence.add("output");
            return kwargs.get("result");
        });

        createSimpleWorkflow().invoke(Map.of("value", "test"), createWorkflowSession(), null);

        for (int index = 0; index < sequence.size(); index += 2) {
            assertEquals("input", sequence.get(index));
            if (index + 1 < sequence.size()) {
                assertEquals("output", sequence.get(index + 1));
            }
        }
    }

    @Test
    void componentStreamOutputTriggerFires() {
        List<String> triggered = synchronizedEvents();
        registerTransform(WorkflowEvents.COMPONENT_STREAM_OUTPUT, "stream_output", kwargs -> {
            triggered.add("stream_output");
            return kwargs.get("result");
        });

        Iterator<WorkflowChunk> stream = createStreamWorkflow().stream(
                Map.of("value", "test"),
                createWorkflowSession(),
                null);
        while (stream.hasNext()) {
            stream.next();
        }

        assertFalse(triggered.isEmpty(), "COMPONENT_STREAM_OUTPUT trigger should fire");
    }

    private static Workflow createSimpleWorkflow() {
        Workflow flow = new Workflow(new WorkflowCard("test_wf", "workflow", "test", "1.0", Map.of()));
        flow.setStartComp("start", new Start(), Map.of("value", "${inputs.value}"));
        flow.addWorkflowComp("comp", new SimpleComponent(), Map.of("value", "${start.value}"));
        flow.setEndComp("end", new End(new EndConfig("{{output}}")), Map.of("output", "${comp.value}"));
        flow.addConnection("start", "comp");
        flow.addConnection("comp", "end");
        return flow;
    }

    private static Workflow createStreamWorkflow() {
        Workflow flow = new Workflow(new WorkflowCard("stream_test_wf", "workflow", "stream_test", "1.0", Map.of()));
        flow.setStartComp("start", new Start(), Map.of("value", "${inputs.value}"));
        flow.addWorkflowComp("comp", new SimpleComponent(), Map.of("value", "${start.value}"),
                false, List.of(ComponentAbility.STREAM));
        flow.setEndComp("end", new End(new EndConfig("{{output}}")), null, null,
                Map.of("output", "${comp.value}"), null, "streaming");
        flow.addConnection("start", "comp");
        flow.addStreamConnection("comp", "end");
        return flow;
    }

    private static WorkflowSession createWorkflowSession() {
        return new WorkflowSession();
    }

    private static AsyncCallbackFramework callbackFramework() {
        return Runner.getCallbackFramework();
    }

    private static List<String> synchronizedEvents() {
        return Collections.synchronizedList(new ArrayList<>());
    }

    private static void registerTransform(
            String event,
            String name,
            Function<Map<String, Object>, Object> callback) {
        callbackFramework().registerSync(
                event,
                new NamedCallback(name, callback),
                0,
                false,
                CALLBACK_NAMESPACE,
                Set.of(),
                List.of(),
                null,
                null,
                0,
                0.0,
                null,
                AsyncCallbackFramework.CALLBACK_TYPE_TRANSFORM);
    }

    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final class SimpleComponent extends WorkflowComponent<Object, Object> {
        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            return List.of(inputs).iterator();
        }
    }
}
