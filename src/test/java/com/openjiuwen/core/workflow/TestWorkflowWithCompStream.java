/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.WorkflowInteraction;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.ComponentAbility;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.EndConfig;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow component streaming tests.
 *
 * <p>Mirrors Python's {@code test_workflow_with_comp_stream.py} in
 * {@code tests/unit_tests/core/workflow/test_workflow_with_comp_stream.py}.</p>
 */
@DisplayName("TestWorkflowWithCompStream")
class TestWorkflowWithCompStream {

    @Test
    void testNoStreamCalled() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("array", "${inputs}"), null);
        flow.addWorkflowComp("stream", new SlowStreamNode(150), true,
                Map.of("array", "${start.array}"), null, null, null, List.of(ComponentAbility.STREAM));
        flow.setEndComp("end", new End(), null, null,
                Map.of("output", "${stream.output}"), null, "streaming");
        flow.addConnection("start", "stream");
        flow.addStreamConnection("stream", "end");

        BaseError error = assertThrows(BaseError.class, () -> flow.invoke(
                Map.of("inputs", List.of(1)), newSession(Map.of(SessionConstants.WORKFLOW_EXECUTE_TIMEOUT, 0.05)),
                null));
        assertEquals(StatusCode.WORKFLOW_EXECUTION_TIMEOUT.getCode(), error.getCode());
    }

    @Test
    void testMultiStreamWorkflow() {
        Workflow workflow = createComponentStreamWorkflowWithTemplate(true);

        List<OutputSchema> chunks = collectOutputChunks(workflow,
                Map.of("inputs", List.of(1, 2, 3)));

        assertEquals(14, chunks.size());
        assertEquals(Map.of("response", "a: "), chunks.get(0).getPayload());
        assertEquals(Map.of("response", "; b: "), chunks.get(10).getPayload());
        assertEquals(Map.of("response", 3), chunks.get(13).getPayload());
    }

    @Test
    void testBatchMultiStreamWorkflow() {
        Workflow workflow = createComponentStreamWorkflowWithTemplate(false);

        WorkflowOutput result = workflow.invoke(Map.of("inputs", List.of(1, 2, 3)), newSession(), null);

        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        assertEquals(Map.of("response", "a: 123; c: 123; batch: [1, 2, 3]; b: 123"), result.getResult());
    }

    @Test
    void testStreamComponentInSubWorkflowWithInvoke() {
        Workflow main = new Workflow();
        main.setStartComp("main_start", new Start(), Map.of("array", "${inputs}"), null);
        main.addWorkflowComp("workflow",
                new SubWorkflowComponentImpl(createComponentStreamWorkflowWithoutTemplate(false)),
                Map.of("inputs", "${main_start.array}"), null);
        main.setEndComp("main_end", new End(new EndConfig("sub_workflow: {{sub_workflow}}")),
                Map.of("sub_workflow", "${workflow.output}"), null, null, null, "streaming");
        main.addConnection("main_start", "workflow");
        main.addConnection("workflow", "main_end");

        List<OutputSchema> chunks = collectOutputChunks(main, Map.of("inputs", List.of(1, 2, 3)));

        assertEquals(2, chunks.size());
        assertEquals(Map.of("response", "sub_workflow: "), chunks.get(0).getPayload());
        assertTrue(chunks.get(1).getPayload().toString().contains("batch=[1, 2, 3]"));
    }

    @Test
    void testStreamComponentInSubWorkflowWithStream() {
        Workflow main = createStreamingSubWorkflow("${workflow.response}");

        List<OutputSchema> chunks = collectOutputChunks(main, Map.of("inputs", List.of(1, 2, 3)));

        assertEquals(15, chunks.size());
        assertEquals(Map.of("response", "sub_workflow: "), chunks.get(0).getPayload());
        assertEquals(Map.of("response", "a: "), chunks.get(1).getPayload());
    }

    @Test
    void testStreamComponentInSubWorkflowWithStreamCollect() {
        Workflow main = new Workflow();
        main.setStartComp("main_start", new Start(), Map.of("array", "${inputs}"), null);
        main.addWorkflowComp("workflow",
                new SubWorkflowComponentImpl(createComponentStreamWorkflowWithTemplate(true)),
                Map.of("inputs", "${main_start.array}"), null);
        main.setEndComp("main_end", new End(new EndConfig("sub_workflow: {{sub_workflow}}")),
                Map.of("sub_workflow", "${workflow.stream}"), null, null, null, "streaming");
        main.addConnection("main_start", "workflow");
        main.addConnection("workflow", "main_end");

        List<OutputSchema> chunks = collectOutputChunks(main, Map.of("inputs", List.of(1, 2, 3)));

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(1).getPayload().toString().contains("response=a: "));
    }

    @Test
    void testStreamComponentInSubWorkflowWithSubstream() {
        Workflow main = createSubstreamWorkflow(createComponentStreamWorkflowWithoutTemplate(true), "${workflow.output}");

        List<OutputSchema> chunks = collectOutputChunks(main, Map.of("inputs", List.of(1, 2, 3)));

        assertEquals(10, chunks.size());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getPayload().toString().contains("sub_workflow={a=1}")));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getPayload().toString().contains("sub_workflow={batch=[1, 2, 3]}")));
    }

    @Test
    void testStreamComponentInSubWorkflowWithSubstreamTemplate() {
        Workflow main = createSubstreamWorkflow(createComponentStreamWorkflowWithTemplate(true), "${workflow.response}");

        List<OutputSchema> chunks = collectOutputChunks(main, Map.of("inputs", List.of(1, 2, 3)));

        assertEquals(14, chunks.size());
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.getPayload().toString().contains("sub_workflow=a: ")));
    }

    @Test
    void testInteractionWithStream() {
        Interaction interaction = new Interaction();

        WorkflowInteraction.GraphInterruptRuntimeWrapper interrupted = assertThrows(
                WorkflowInteraction.GraphInterruptRuntimeWrapper.class,
                () -> interaction.invoke(Map.of(), WorkflowTestSupport.nodeSession("interaction"), null));

        assertTrue(interrupted.getGraphInterrupt().getValue().toString().contains("Please enter any key"));
    }

    @Test
    void testInteractionWithException() {
        Interaction interaction = new Interaction(true);

        BaseError error = assertThrows(BaseError.class,
                () -> interaction.stream(Map.of(), WorkflowTestSupport.streamNodeSession("interaction"), null));

        assertEquals(StatusCode.COMP_SESSION_INTERACT_ERROR.getCode(), error.getCode());
    }

    @Test
    void testWorkflowStreamWithException() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), null, null);
        flow.addWorkflowComp("bad", new StreamNodeWithException(true, false), true,
                null, null, null, null, List.of(ComponentAbility.STREAM));
        flow.setEndComp("end", new End(), null, null, Map.of("data", "${bad.output}"), null, "streaming");
        flow.addConnection("start", "bad");
        flow.addStreamConnection("bad", "end");

        Iterator<WorkflowChunk> iterator = flow.stream(Map.of(), newSession(), null, List.of(StreamMode.OUTPUT));

        BaseError error = assertThrows(BaseError.class, iterator::hasNext);
        assertEquals(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR.getCode(), error.getCode());
    }

    @Test
    void testNodeWithDualStreamAbilitiesTransformAndStream() {
        DualAbilityComponent component = new DualAbilityComponent(false, false);

        List<Object> streamFrames = toList(component.stream(Map.of("a", 3, "b", 3),
                WorkflowTestSupport.nodeSession("dual"), null));
        List<Object> transformFrames = toList(component.transform(
                Map.of("data", List.of(Map.of("a_A", 1), Map.of("result_A", 3)).iterator()),
                WorkflowTestSupport.nodeSession("dual"), null));

        assertTrue(streamFrames.contains(Map.of("result", 6)));
        assertTrue(transformFrames.contains(Map.of("result_A", 3)));
    }

    @Test
    void testDualAbilityNodeWithStreamError() {
        DualAbilityComponent component = new DualAbilityComponent(true, false);

        assertThrows(RuntimeException.class,
                () -> component.stream(Map.of("a", 1, "b", 2), WorkflowTestSupport.nodeSession("dual"), null));
    }

    @Test
    void testDualAbilityNodeWithTransformError() {
        DualAbilityComponent component = new DualAbilityComponent(false, true);

        assertThrows(RuntimeException.class,
                () -> component.transform(Map.of("data", List.of().iterator()),
                        WorkflowTestSupport.nodeSession("dual"), null));
    }

    @Test
    void testStreamTriggerConsumerTwice() {
        Workflow flow = newTwoProducerWorkflow();

        List<OutputSchema> chunks = collectOutputChunks(flow, Map.of("query", "intro"));

        assertEquals(20, chunks.size());
        for (int i = 0; i < 10; i++) {
            int value = i;
            assertTrue(chunks.stream().anyMatch(chunk ->
                    Map.of("output", Map.of("output", value)).equals(chunk.getPayload())));
            assertTrue(chunks.stream().anyMatch(chunk ->
                    Map.of("output", Map.of("output2", value)).equals(chunk.getPayload())));
        }
    }

    @Test
    void testStreamTriggerConsumer() {
        Workflow flow = newOneProducerWorkflow();

        List<OutputSchema> chunks = collectOutputChunks(flow, Map.of("query", "intro"));

        assertEquals(10, chunks.size());
        assertEquals(Map.of("output", Map.of("output", 0)), chunks.get(0).getPayload());
        assertEquals(Map.of("output", Map.of("output", 9)), chunks.get(9).getPayload());
    }

    @Test
    void testAutoAbilityWithConditionEdge() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), null, null);
        BranchComponent branch = new BranchComponent();
        branch.addBranch("${user_input.x} == 1", List.of("invoke"), "branch1");
        branch.addBranch("${user_input.x} == 2", List.of("stream"), "branch2");
        flow.addWorkflowComp("branch", branch);
        flow.addWorkflowComp("invoke", new MockNode());
        flow.addWorkflowComp("stream", new MockNode());
        flow.addWorkflowComp("collect", new MockNode(), true, null, null,
                Map.of("result2", "${stream.output}"), null, List.of(ComponentAbility.COLLECT));
        flow.setEndComp("end", new End(), Map.of("result", "${collect.output}", "result2", "${invoke.output}"),
                null);
        flow.addConnection("start", "branch");
        flow.addConnection("invoke", "end");
        flow.addStreamConnection("stream", "collect");
        flow.addConnection("collect", "end");

        WorkflowOutput invokeResult = flow.invoke(Map.of("user_input", Map.of("x", 1)), newSession(), null);
        WorkflowOutput streamResult = flow.invoke(Map.of("user_input", Map.of("x", 2)), newSession(), null);

        assertEquals(Map.of("output", Map.of("result2", 1)), invokeResult.getResult());
        assertEquals(Map.of("output", Map.of("result", 3)), streamResult.getResult());
    }

    @Test
    void testStreamCallFastThanCall() {
        Workflow flow = newWorkflowForFastStreamTemplate("####这是 a={{a}}, #####");

        List<OutputSchema> chunks = collectOutputChunks(flow, Map.of("user", Map.of("query", "i am a girl")));

        assertFalse(chunks.isEmpty());
        assertEquals(Map.of("response", "####这是 a="), chunks.get(0).getPayload());
        assertEquals(Map.of("response", ", #####"), chunks.get(chunks.size() - 1).getPayload());
    }

    @Test
    void testWorkflowWithIntentNode() {
        Workflow invokeBranch = createWorkflowWithIntentNode("node1", 0);
        Workflow streamBranch = createWorkflowWithIntentNode("node2", 1);

        WorkflowOutput invokeResult = invokeBranch.invoke(Map.of("query", "旅游"), newSession(), null);
        WorkflowOutput streamResult = streamBranch.invoke(Map.of("query", "旅游2"), newSession(), null);

        assertEquals(Map.of("response", "输出:{data={classification_id=0}} 输出2:"), invokeResult.getResult());
        assertEquals(Map.of("response", "输出: 输出2:{data3=0}{data3=1}{data3=2}{data3=3}{data3=4}"),
                streamResult.getResult());
    }

    private static Workflow createComponentStreamWorkflowWithTemplate(boolean responseStreaming) {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new Start(), Map.of("array", "${inputs}"), null);
        for (String id : List.of("a", "b", "c")) {
            workflow.addWorkflowComp(id, new Producer(), true, Map.of("array", "${start.array}"),
                    null, null, null, List.of(ComponentAbility.STREAM));
        }
        workflow.addWorkflowComp("batch", new Producer(), Map.of("array", "${start.array}"), null);
        workflow.setEndComp("end", new End(new EndConfig("a: {{a}}; c: {{c}}; batch: {{batch}}; b: {{b}}")),
                Map.of("batch", "${batch.output}"), null,
                Map.of("a", "${a.output}", "b", "${b.output}", "c", "${c.output}"),
                null, responseStreaming ? "streaming" : null);
        workflow.addConnection("start", "a");
        workflow.addConnection("start", "b");
        workflow.addConnection("start", "c");
        workflow.addConnection("start", "batch");
        workflow.addConnection("batch", "end");
        workflow.addStreamConnection("a", "end");
        workflow.addStreamConnection("b", "end");
        workflow.addStreamConnection("c", "end");
        return workflow;
    }

    private static Workflow createComponentStreamWorkflowWithoutTemplate(boolean responseStreaming) {
        Workflow workflow = new Workflow();
        workflow.setStartComp("start", new Start(), Map.of("array", "${inputs}"), null);
        for (String id : List.of("a", "b", "c")) {
            workflow.addWorkflowComp(id, new Producer(), true, Map.of("array", "${start.array}"),
                    null, null, null, List.of(ComponentAbility.STREAM));
        }
        workflow.addWorkflowComp("batch", new Producer(), Map.of("array", "${start.array}"), null);
        workflow.setEndComp("end", new End(), Map.of("batch", "${batch.output}"), null,
                Map.of("a", "${a.output}", "b", "${b.output}", "c", "${c.output}"),
                null, responseStreaming ? "streaming" : null);
        workflow.addConnection("start", "a");
        workflow.addConnection("start", "b");
        workflow.addConnection("start", "c");
        workflow.addConnection("start", "batch");
        workflow.addConnection("batch", "end");
        workflow.addStreamConnection("a", "end");
        workflow.addStreamConnection("b", "end");
        workflow.addStreamConnection("c", "end");
        return workflow;
    }

    private static Workflow createStreamingSubWorkflow(String subWorkflowSchema) {
        Workflow main = new Workflow();
        main.setStartComp("main_start", new Start(), Map.of("array", "${inputs}"), null);
        main.addWorkflowComp("workflow",
                new SubWorkflowComponentImpl(createComponentStreamWorkflowWithTemplate(true)),
                Map.of("inputs", "${main_start.array}"), null);
        main.setEndComp("main_end", new End(new EndConfig("sub_workflow: {{sub_workflow}}")),
                null, null, Map.of("sub_workflow", subWorkflowSchema), null, "streaming");
        main.addConnection("main_start", "workflow");
        main.addStreamConnection("workflow", "main_end");
        return main;
    }

    private static Workflow createSubstreamWorkflow(Workflow subWorkflow, String subWorkflowSchema) {
        Workflow main = new Workflow();
        main.setStartComp("main_start", new Start(), Map.of("array", "${inputs}"), null);
        main.addWorkflowComp("workflow", new SubWorkflowComponentImpl(subWorkflow),
                Map.of("inputs", "${main_start.array}"), null);
        main.setEndComp("main_end", new End(), null, null,
                Map.of("sub_workflow", subWorkflowSchema), null, "streaming");
        main.addConnection("main_start", "workflow");
        main.addStreamConnection("workflow", "main_end");
        return main;
    }

    private static Workflow newTwoProducerWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start(), Map.of("query", "${query}"), null);
        flow.addWorkflowComp("llm", new StreamNode(0), true, Map.of("query", "${s.query}"),
                null, null, null, List.of(ComponentAbility.STREAM));
        flow.addWorkflowComp("llm2", new StreamNode(0), true, Map.of("query", "${s.query}"),
                null, null, null, List.of(ComponentAbility.STREAM));
        flow.setEndComp("e", new End(), null, null,
                Map.of("output", "${llm.output}", "output2", "${llm2.output}"), null, "streaming");
        flow.addConnection("s", "llm");
        flow.addConnection("s", "llm2");
        flow.addStreamConnection("llm", "e");
        flow.addStreamConnection("llm2", "e");
        return flow;
    }

    private static Workflow newOneProducerWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start(), Map.of("query", "${query}"), null);
        flow.addWorkflowComp("llm", new StreamNode(0), true, Map.of("query", "${s.query}"),
                null, null, null, List.of(ComponentAbility.STREAM));
        flow.setEndComp("e", new End(), null, null, Map.of("output", "${llm.output}"), null, "streaming");
        flow.addConnection("s", "llm");
        flow.addStreamConnection("llm", "e");
        return flow;
    }

    private static Workflow newWorkflowForFastStreamTemplate(String template) {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("query", "${user.query}"), null);
        flow.addWorkflowComp("llm", new MockLLMComponent(
                List.of("2019", "年", "，", "Rivian", "在", "没有", "一辆车", "下线", "的", "情况",
                        "下", "一年", "进行", "了", "四轮", "融资")),
                true, Map.of("query", "${start.query}"), null, null, null, List.of(ComponentAbility.STREAM));
        flow.setEndComp("end", new End(new EndConfig(template)), Map.of("query", "${start.query}"), null,
                Map.of("a", "${llm.a}"), null, "streaming");
        flow.addConnection("start", "llm");
        flow.addStreamConnection("llm", "end");
        return flow;
    }

    private static Workflow createWorkflowWithIntentNode(String mode, int classificationId) {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
        IntentNode intent = new IntentNode(classificationId);
        intent.addBranch("${intent.classification_id} == 0", List.of("node1"), null);
        intent.addBranch("${intent.classification_id} == 1", List.of("node2"), null);
        flow.addWorkflowComp("intent", intent, Map.of("query", "${start.query}"), null);
        flow.addWorkflowComp("node1", new AllAbilityNode(), Map.of("data", "${intent}"), null);
        flow.addWorkflowComp("node2", new AllAbilityNode(), true, Map.of("value", "${intent.classification_id}"),
                null, null, null, List.of(ComponentAbility.STREAM));
        flow.addWorkflowComp("node3", new AllAbilityNode(), true, null, null,
                Map.of("data3", "${node2.value}"), null, List.of(ComponentAbility.TRANSFORM));
        flow.setEndComp("end", new End(new EndConfig("输出:{{end_input}} 输出2:{{end_input2}}")),
                Map.of("end_input", "${node1}"), null, Map.of("end_input2", "${node3}"), null, null);
        flow.addConnection("start", "intent");
        if ("node1".equals(mode)) {
            flow.addConnection("node1", "end");
        } else {
            flow.addStreamConnection("node2", "node3");
            flow.addStreamConnection("node3", "end");
        }
        return flow;
    }

    private static List<OutputSchema> collectOutputChunks(Workflow workflow, Map<String, Object> inputs) {
        List<OutputSchema> chunks = new ArrayList<>();
        Iterator<WorkflowChunk> iterator = workflow.stream(inputs, newSession(), null, List.of(StreamMode.OUTPUT));
        while (iterator.hasNext()) {
            WorkflowChunk chunk = iterator.next();
            assertInstanceOf(OutputSchema.class, chunk);
            chunks.add((OutputSchema) chunk);
        }
        return chunks;
    }

    private static WorkflowSessionApi newSession() {
        return newSession(Map.of());
    }

    private static WorkflowSessionApi newSession(Map<String, Object> envs) {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), envs);
    }

    private static List<Object> toList(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static class MockStreamNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            return List.of(inputs).iterator();
        }
    }

    private static final class SlowStreamNode extends MockStreamNode {
        private final long delayMs;

        private SlowStreamNode(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            return new Iterator<>() {
                private boolean emitted;

                @Override
                public boolean hasNext() {
                    return !emitted;
                }

                @Override
                public Object next() {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    emitted = true;
                    return Map.of("output", ((Map<?, ?>) inputs).get("array"));
                }
            };
        }
    }

    private static final class Producer extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return Map.of("output", ((Map<?, ?>) inputs).get("array"));
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (Object value : (List<?>) ((Map<?, ?>) inputs).get("array")) {
                frames.add(Map.of("output", value));
            }
            return frames.iterator();
        }
    }

    private static final class Interaction extends WorkflowComponent {
        private final boolean streamModeOnly;

        private Interaction() {
            this(false);
        }

        private Interaction(boolean streamModeOnly) {
            this.streamModeOnly = streamModeOnly;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            if (streamModeOnly) {
                return Map.of();
            }
            return session.interact("Please enter any key");
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            session.interact("Please enter any key");
            return List.<Object>of().iterator();
        }
    }

    private static final class StreamNodeWithException extends WorkflowComponent {
        private final boolean errorInStream;
        private final boolean errorInTransform;

        private StreamNodeWithException(boolean errorInStream, boolean errorInTransform) {
            this.errorInStream = errorInStream;
            this.errorInTransform = errorInTransform;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            if (errorInStream) {
                throw new RuntimeException("stream error");
            }
            return List.<Object>of(Map.of("output", 1)).iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            if (errorInTransform) {
                throw new RuntimeException("transform error");
            }
            return List.<Object>of(Map.of("output", 1)).iterator();
        }
    }

    private static final class DualAbilityComponent extends WorkflowComponent {
        private final boolean errorInStream;
        private final boolean errorInTransform;

        private DualAbilityComponent(boolean errorInStream, boolean errorInTransform) {
            this.errorInStream = errorInStream;
            this.errorInTransform = errorInTransform;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            if (errorInStream) {
                throw new RuntimeException("stream error");
            }
            Map<?, ?> map = (Map<?, ?>) inputs;
            int a = ((Number) map.get("a")).intValue();
            int b = ((Number) map.get("b")).intValue();
            return List.<Object>of(Map.of("a", a), Map.of("op", "+"), Map.of("b", b),
                    Map.of("result", a + b)).iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            if (errorInTransform) {
                throw new RuntimeException("transform error");
            }
            Iterator<?> iterator = (Iterator<?>) ((Map<?, ?>) inputs).get("data");
            List<Object> frames = new ArrayList<>();
            iterator.forEachRemaining(frames::add);
            return frames.iterator();
        }
    }

    private static final class StreamNode extends WorkflowComponent {
        private final long delayMs;

        private StreamNode(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                frames.add(Map.of("output", i));
            }
            return frames.iterator();
        }
    }

    private static final class MockNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return Map.of("output", 1);
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            return List.<Object>of(Map.of("output", 2)).iterator();
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            Object stream = ((Map<?, ?>) inputs).get("result2");
            if (stream instanceof Iterator<?> iterator) {
                while (iterator.hasNext()) {
                    iterator.next();
                }
            }
            return Map.of("output", 3);
        }
    }

    private static final class MockLLMComponent extends WorkflowComponent {
        private final List<String> streamOutputs;

        private MockLLMComponent(List<String> streamOutputs) {
            this.streamOutputs = streamOutputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (String output : streamOutputs) {
                frames.add(Map.of("a", output));
            }
            return frames.iterator();
        }
    }

    private static final class IntentNode extends BranchComponent {
        private final int classificationId;

        private IntentNode(int classificationId) {
            this.classificationId = classificationId;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            router().setSession(session);
            return Map.of("classification_id", classificationId);
        }
    }

    private static final class AllAbilityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            List<Object> frames = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                frames.add(Map.of("value", i));
            }
            return frames.iterator();
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            Iterator<?> iterator = (Iterator<?>) ((Map<?, ?>) inputs).get("data3");
            List<Object> frames = new ArrayList<>();
            iterator.forEachRemaining(value -> frames.add(Map.of("data3", value)));
            return frames.iterator();
        }
    }

    private static final class WorkflowTestSupport {
        private WorkflowTestSupport() {
        }

        private static NodeSessionApi nodeSession(String nodeId) {
            return new NodeSessionApi(new com.openjiuwen.core.session.internal.NodeSession(
                    new com.openjiuwen.core.session.internal.WorkflowSession("stream_test"), nodeId));
        }

        private static NodeSessionApi streamNodeSession(String nodeId) {
            return new NodeSessionApi(new com.openjiuwen.core.session.internal.NodeSession(
                    new com.openjiuwen.core.session.internal.WorkflowSession("stream_test"), nodeId), true);
        }
    }
}
