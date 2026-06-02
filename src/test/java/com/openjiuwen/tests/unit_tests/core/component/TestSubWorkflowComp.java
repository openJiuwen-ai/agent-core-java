/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.core.component;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.WorkflowError;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowComponent;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.components.flow.SubWorkflowComponent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_sub_workflow_comp.py} in
 * {@code tests/unit_tests/core/component/test_sub_workflow_comp.py}.
 */
@Tag("unit-test")
class TestSubWorkflowComp {

    @Test
    @DisplayName("self-referential sub-workflow component fails during invocation")
    void testAddComponent() {
        Workflow mainWorkflow = new Workflow(2);
        mainWorkflow.setStartComp("start", new Start(), null);
        mainWorkflow.addWorkflowComp("fick_comp", new SubWorkflowComponent(mainWorkflow));
        mainWorkflow.setEndComp("end", new End(), null);
        mainWorkflow.addConnection("start", "fick_comp");
        mainWorkflow.addConnection("fick_comp", "end");

        assertThrows(BaseError.class,
                () -> mainWorkflow.invoke(Map.of(), WorkflowSessions.createWorkflowSession(), null));
    }

    @Test
    @DisplayName("sub-workflow nesting depth follows workflow_max_nesting_depth")
    void testSubInvoke() {
        BaseError error = assertThrows(BaseError.class,
                () -> createNestingWorkflow(3, 1)
                        .invoke(Map.of(), WorkflowSessions.createWorkflowSession(), null));

        assertInstanceOf(WorkflowError.class, error);
        assertTrue(error.getMessage().contains("workflow nesting hierarchy is too big, must <= 1"));

        assertDoesNotThrow(() -> createNestingWorkflow(3, 3)
                .invoke(Map.of(), WorkflowSessions.createWorkflowSession(), null));
        assertDoesNotThrow(() -> createNestingWorkflow(0, 0)
                .invoke(Map.of(), WorkflowSessions.createWorkflowSession(), null));
    }

    @Test
    @DisplayName("streaming sub-workflow forwards end-node output chunks")
    void testWorkflow() {
        Workflow mainWorkflow = createMainWorkflow(createStreamingSubWorkflow(), false, false);

        List<WorkflowChunk> chunks = collect(mainWorkflow.stream(
                Map.of(), WorkflowSessions.createWorkflowSession(), null, List.of(StreamMode.OUTPUT)));

        assertEquals(expectedChunks(), chunks);
    }

    @Test
    @DisplayName("cache_stream lets a batch edge consume merged stream output from an LLM-style sub-workflow")
    void testWorkflowWithLlmStreamAndBatchEdge() {
        BatchConsumerComponent.lastInvokedInputs = null;
        Workflow mainWorkflow = createMainWorkflow(createStreamingSubWorkflow(), true, true);

        List<WorkflowChunk> chunks = collect(mainWorkflow.stream(
                Map.of(), WorkflowSessions.createWorkflowSession(), null, List.of(StreamMode.OUTPUT)));

        assertEquals(3, chunks.size());
        assertMergedBatchResult();
    }

    @Test
    @DisplayName("cache_stream supports simultaneous stream and batch edges")
    void testWorkflowWithStreamAndBatchEdge() {
        BatchConsumerComponent.lastInvokedInputs = null;
        Workflow mainWorkflow = createMainWorkflow(createStreamingSubWorkflow(), true, true);

        List<WorkflowChunk> chunks = collect(mainWorkflow.stream(
                Map.of(), WorkflowSessions.createWorkflowSession(), null, List.of(StreamMode.OUTPUT)));

        assertEquals(expectedChunks(), chunks);
        assertMergedBatchResult();
    }

    private Workflow createNestingWorkflow(int subWorkflowDepth, int workflowMaxNestingDepth) {
        Workflow workflow = new Workflow(workflowMaxNestingDepth);
        workflow.setStartComp("start", new Start(), null);
        if (subWorkflowDepth > 0) {
            workflow.addWorkflowComp(
                    "sub" + subWorkflowDepth,
                    new SubWorkflowComponent(createNestingWorkflow(subWorkflowDepth - 1)));
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

    private Workflow createNestingWorkflow(int subWorkflowDepth) {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new Start(), null);
        if (subWorkflowDepth > 0) {
            workflow.addWorkflowComp(
                    "sub" + subWorkflowDepth,
                    new SubWorkflowComponent(createNestingWorkflow(subWorkflowDepth - 1)));
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

    private static Workflow createStreamingSubWorkflow() {
        Workflow subWorkflow = new Workflow();
        subWorkflow.setStartComp("sub_start", new Start(), null);
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

    private static Workflow createMainWorkflow(Workflow subWorkflow, boolean cacheStream, boolean addBatchEdge) {
        Workflow mainWorkflow = new Workflow();
        mainWorkflow.setStartComp("start", new Start(), Map.of());
        mainWorkflow.addWorkflowComp(
                "sub_workflow_comp",
                new SubWorkflowComponent(subWorkflow, cacheStream),
                Map.of());
        mainWorkflow.setEndComp("end", new End(), null, null,
                Map.of("result", "${sub_workflow_comp.output.out}"), null, "streaming");
        if (addBatchEdge) {
            mainWorkflow.addWorkflowComp("batch_consumer", new BatchConsumerComponent(),
                    Map.of("result", "${sub_workflow_comp.output}"));
        }
        mainWorkflow.addConnection("start", "sub_workflow_comp");
        if (addBatchEdge) {
            mainWorkflow.addConnection("sub_workflow_comp", "batch_consumer");
            mainWorkflow.addConnection("batch_consumer", "end");
        }
        mainWorkflow.addStreamConnection("sub_workflow_comp", "end");
        return mainWorkflow;
    }

    private static void assertMergedBatchResult() {
        assertNotNull(BatchConsumerComponent.lastInvokedInputs);
        Object batchResult = BatchConsumerComponent.lastInvokedInputs.get("result");
        assertInstanceOf(Map.class, batchResult);
        assertEquals("transform_stream_1transform_stream_2transform_stream_3",
                ((Map<?, ?>) batchResult).get("out"));
    }

    private static List<WorkflowChunk> expectedChunks() {
        return List.of(
                new OutputSchema(Constant.END_NODE_STREAM, 0,
                        Map.of("output", Map.of("result", "transform_stream_1"))),
                new OutputSchema(Constant.END_NODE_STREAM, 1,
                        Map.of("output", Map.of("result", "transform_stream_2"))),
                new OutputSchema(Constant.END_NODE_STREAM, 2,
                        Map.of("output", Map.of("result", "transform_stream_3"))));
    }

    private static List<WorkflowChunk> collect(Iterator<WorkflowChunk> iterator) {
        List<WorkflowChunk> chunks = new ArrayList<>();
        iterator.forEachRemaining(chunks::add);
        return chunks;
    }

    private static final class CustomStream extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return Map.of("custom_output", inputs instanceof Map ? inputs : Map.of());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> chunks = new ArrayList<>();
            if (!(inputs instanceof Map<?, ?> inputMap)) {
                chunks.add(1);
                return chunks.iterator();
            }
            for (Object value : expandValues(((Map<String, Object>) inputMap).get("value"))) {
                chunks.add(Map.of("value", "stream_" + value));
            }
            return chunks.iterator();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            Object values = inputs instanceof Map<?, ?> inputMap
                    ? ((Map<String, Object>) inputMap).get("value")
                    : null;
            Iterator<?> iterator = toIterator(values);
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Object next() {
                    return Map.of("value", "transform_" + iterator.next());
                }
            };
        }

        private static List<Object> expandValues(Object values) {
            if (values == null) {
                return List.of();
            }
            if (values instanceof CharSequence sequence) {
                List<Object> expanded = new ArrayList<>();
                for (int i = 0; i < sequence.length(); i++) {
                    expanded.add(String.valueOf(sequence.charAt(i)));
                }
                return expanded;
            }
            if (values instanceof Iterable<?> iterable) {
                List<Object> expanded = new ArrayList<>();
                iterable.forEach(expanded::add);
                return expanded;
            }
            if (values instanceof Iterator<?> iterator) {
                List<Object> expanded = new ArrayList<>();
                iterator.forEachRemaining(expanded::add);
                return expanded;
            }
            return List.of(values);
        }

        private static Iterator<?> toIterator(Object values) {
            if (values instanceof Iterator<?> iterator) {
                return iterator;
            }
            if (values instanceof Iterable<?> iterable) {
                return iterable.iterator();
            }
            return expandValues(values).iterator();
        }
    }

    private static final class BatchConsumerComponent extends WorkflowComponent {
        private static Map<String, Object> lastInvokedInputs;

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = inputs instanceof Map<?, ?>
                    ? new LinkedHashMap<>((Map<String, Object>) inputs)
                    : new LinkedHashMap<>();
            lastInvokedInputs = inputMap;

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("consumed_result", inputMap.get("result"));
            return output;
        }
    }
}
