/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's {@code tests/unit_tests/core/component/test_sub_workflow_comp.py}.
 */
public class SubWorkflowComponentPythonParityTest {

    @Test
    void testAddComponent() {
        Workflow mainWorkflow = new Workflow(2);
        mainWorkflow.setStartComp("start", new Start(), Map.of());
        mainWorkflow.addWorkflowComp("fick_comp",
                new com.openjiuwen.core.workflow.components.flow.SubWorkflowComponent(mainWorkflow));
        mainWorkflow.setEndComp("end", new End(), null);
        mainWorkflow.addConnection("start", "fick_comp");
        mainWorkflow.addConnection("fick_comp", "end");

        BaseError error = assertThrows(BaseError.class,
                () -> mainWorkflow.invoke(Map.of(), workflowSession(), null));

        assertThat(error.getMessage()).contains("workflow nesting hierarchy is too big");
    }

    @Test
    void testSubInvoke() {
        BaseError error = assertThrows(BaseError.class,
                () -> createNestingWorkflow(3, 1).invoke(Map.of(), workflowSession(), null));

        assertThat(error.getMessage()).contains("workflow nesting hierarchy is too big, must <= 1");
        assertThatCode(() -> createNestingWorkflow(3, 3).invoke(Map.of(), workflowSession(), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> createNestingWorkflow(0, 0).invoke(Map.of(), workflowSession(), null))
                .doesNotThrowAnyException();
    }

    @Test
    void testWorkflow() {
        Workflow mainWorkflow = streamingMainWorkflow(false, false);

        List<Object> payloads = endNodePayloads(mainWorkflow.stream(
                Map.of(), workflowSession(), null));

        assertThat(payloads).containsExactly(
                Map.of("output", Map.of("result", "transform_stream_1")),
                Map.of("output", Map.of("result", "transform_stream_2")),
                Map.of("output", Map.of("result", "transform_stream_3"))
        );
    }

    @Test
    void testWorkflowWithLlmStreamAndBatchEdge() {
        BatchConsumerComponent.lastInvokedInputs = null;
        Workflow mainWorkflow = streamingMainWorkflow(true, true);

        List<Object> payloads = endNodePayloads(mainWorkflow.stream(
                Map.of(), workflowSession(), null));

        assertThat(payloads).hasSize(3);
        assertThat(cachedBatchResult().get("out"))
                .isEqualTo("transform_stream_1transform_stream_2transform_stream_3");
    }

    @Test
    void testWorkflowWithStreamAndBatchEdge() {
        BatchConsumerComponent.lastInvokedInputs = null;
        Workflow mainWorkflow = streamingMainWorkflow(true, true);

        List<Object> payloads = endNodePayloads(mainWorkflow.stream(
                Map.of(), workflowSession(), null));

        assertThat(payloads).containsExactly(
                Map.of("output", Map.of("result", "transform_stream_1")),
                Map.of("output", Map.of("result", "transform_stream_2")),
                Map.of("output", Map.of("result", "transform_stream_3"))
        );
        assertThat(cachedBatchResult().get("out"))
                .isEqualTo("transform_stream_1transform_stream_2transform_stream_3");
    }

    private static Workflow createNestingWorkflow(int subWorkflowDepth, Integer workflowMaxNestingDepth) {
        Workflow workflow = workflowMaxNestingDepth == null ? new Workflow() : new Workflow(workflowMaxNestingDepth);
        workflow.setStartComp("start", new Start(), Map.of());
        if (subWorkflowDepth > 0) {
            workflow.addWorkflowComp("sub" + subWorkflowDepth,
                    new com.openjiuwen.core.workflow.components.flow.SubWorkflowComponent(
                            createNestingWorkflow(subWorkflowDepth - 1, null)));
        }
        workflow.setEndComp("end", new End(), null);
        if (subWorkflowDepth > 0) {
            workflow.addConnection("start", "sub" + subWorkflowDepth);
            workflow.addConnection("sub" + subWorkflowDepth, "end");
        } else {
            workflow.addConnection("start", "end");
        }
        return workflow;
    }

    private static Workflow streamingMainWorkflow(boolean cacheStream, boolean includeBatchConsumer) {
        Workflow mainWorkflow = new Workflow();
        mainWorkflow.setStartComp("start", new Start(), Map.of());
        mainWorkflow.addWorkflowComp("sub_workflow_comp",
                new com.openjiuwen.core.workflow.components.flow.SubWorkflowComponent(streamingSubWorkflow(), cacheStream),
                Map.of());
        mainWorkflow.setEndComp("end", new End(), null, null,
                Map.of("result", "${sub_workflow_comp.output.out}"), null, "streaming");
        mainWorkflow.addConnection("start", "sub_workflow_comp");
        if (includeBatchConsumer) {
            mainWorkflow.addWorkflowComp("batch_consumer", new BatchConsumerComponent(),
                    Map.of("result", "${sub_workflow_comp.output}"));
            mainWorkflow.addConnection("sub_workflow_comp", "batch_consumer");
            mainWorkflow.addConnection("batch_consumer", "end");
        }
        mainWorkflow.addStreamConnection("sub_workflow_comp", "end");
        return mainWorkflow;
    }

    private static Workflow streamingSubWorkflow() {
        Workflow subWorkflow = new Workflow();
        subWorkflow.setStartComp("sub_start", new Start(), Map.of());
        subWorkflow.addWorkflowComp("custom", new CustomStream(), Map.of("value", "123"));
        subWorkflow.addWorkflowComp("custom1", new CustomStream(), null, null, true,
                Map.of("value", "${custom.value}"), null);
        subWorkflow.setEndComp("sub_end", new End(), null, null,
                Map.of("out", "${custom1.value}"), null, "streaming");
        subWorkflow.addConnection("sub_start", "custom");
        subWorkflow.addStreamConnection("custom", "custom1");
        subWorkflow.addStreamConnection("custom1", "sub_end");
        return subWorkflow;
    }

    private static List<Object> endNodePayloads(Iterator<WorkflowChunk> chunks) {
        List<Object> payloads = new ArrayList<>();
        while (chunks.hasNext()) {
            WorkflowChunk chunk = chunks.next();
            if (Constant.END_NODE_STREAM.equals(chunk.getType())) {
                payloads.add(chunk.getPayload());
            }
        }
        return payloads;
    }

    private static Map<String, Object> cachedBatchResult() {
        assertThat(BatchConsumerComponent.lastInvokedInputs).isNotNull();
        Object result = BatchConsumerComponent.lastInvokedInputs.get("result");
        assertThat(result).isInstanceOf(Map.class);
        return toMap(result);
    }

    private static WorkflowSession workflowSession() {
        return new WorkflowSession();
    }

    private static final class CustomStream extends com.openjiuwen.core.workflow.WorkflowComponent<Object, Object> {
        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            Object value = valueFrom(inputs);
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("custom_output", inputs);
            output.put("value", value);
            return output;
        }

        @Override
        public Iterator<Object> stream(Object inputs, BaseSession session, ModelContext context) {
            Object value = valueFrom(inputs);
            if (value == null) {
                return List.<Object>of(Map.of("value", "stream_1")).iterator();
            }
            List<Object> outputs = new ArrayList<>();
            for (Object item : iterateLikePython(value)) {
                outputs.add(Map.of("value", "stream_" + item));
            }
            return outputs.iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, BaseSession session, ModelContext context) {
            Object value = valueFrom(inputs);
            List<Object> outputs = new ArrayList<>();
            for (Object item : iterateLikePython(value)) {
                outputs.add(Map.of("value", "transform_" + item));
            }
            return outputs.iterator();
        }
    }

    private static final class BatchConsumerComponent
            extends com.openjiuwen.core.workflow.WorkflowComponent<Object, Object> {
        private static Map<String, Object> lastInvokedInputs;

        @Override
        public Object invoke(Object inputs, BaseSession session, ModelContext context) {
            Map<String, Object> inputMap = toMap(inputs);
            lastInvokedInputs = inputMap;
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("consumed_result", inputMap.get("result"));
            return output;
        }
    }

    private static Object valueFrom(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            return map.get("value");
        }
        return null;
    }

    private static List<Object> iterateLikePython(Object value) {
        if (value instanceof Iterator<?> iterator) {
            List<Object> values = new ArrayList<>();
            iterator.forEachRemaining(values::add);
            return values;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(values::add);
            return values;
        }
        if (value instanceof CharSequence chars) {
            List<Object> values = new ArrayList<>();
            for (int index = 0; index < chars.length(); index++) {
                values.add(String.valueOf(chars.charAt(index)));
            }
            return values;
        }
        return value == null ? List.of() : List.of(value);
    }

    private static Map<String, Object> toMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }
}
