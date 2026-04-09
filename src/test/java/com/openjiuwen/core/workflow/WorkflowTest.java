/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.workflow;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.internal.RouterSession;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.component.BranchComponent;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.SubWorkflowComponentImpl;
import com.openjiuwen.core.workflow.component.loop.AdvancedLoopComponentImpl;
import com.openjiuwen.core.workflow.component.loop.LoopGroup;
import com.openjiuwen.core.workflow.component.loop.LoopSetVariableComponent;
import com.openjiuwen.core.workflow.component.loop.callback.IntermediateLoopVarCallback;
import com.openjiuwen.core.workflow.component.loop.callback.OutputCallback;
import com.openjiuwen.core.workflow.condition.NumberCondition;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Workflow regression tests ported from Python workflow unit tests.
 */
class WorkflowTest {

    @Test
    @DisplayName("simple workflow")
    void testSimpleWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)),
                null);
        flow.addWorkflowComp("a", new IdentityNode(),
                Map.of("aa", "${start.a}", "ac", "${start.c}"),
                null);
        flow.setEndComp("end", new IdentityNode(), Map.of("result", "${a.aa}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("a", "end");

        WorkflowOutput result = flow.invoke(Map.of("a", 1, "b", "haha"), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        assertEquals(Map.of("result", 1), result.getResult());
    }

    @Test
    @DisplayName("simple workflow with parallel branches")
    void testSimpleWorkflowWithParallelBranches() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("a1", "${a1}", "a2", "${a2}"),
                null);
        flow.addWorkflowComp("a1", new IdentityNode(), Map.of("value", "${start.a1}"), null);
        flow.addWorkflowComp("a2", new IdentityNode(), Map.of("value", "${start.a2}"), null);
        flow.setEndComp("end", new IdentityNode(),
                Map.of("b1", "${a1.value}", "b2", "${a2.value}"),
                null);
        flow.addConnection("start", "a1");
        flow.addConnection("start", "a2");
        flow.addConnection("a1", "end");
        flow.addConnection("a2", "end");

        WorkflowOutput result = flow.invoke(Map.of("a1", 1, "a2", 2), newSession(), null);
        assertEquals(WorkflowExecutionState.COMPLETED, result.getState());
        assertEquals(Map.of("b1", 1, "b2", 2), result.getResult());
    }

    @Test
    @DisplayName("workflow with function router")
    void testSimpleWorkflowWithCondition() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)),
                null);

        flow.addConditionalConnection("start", (Function<Object, Object>) sessionObj -> {
            RouterSession session = (RouterSession) sessionObj;
            if (session.getGlobalState("start.a") != null) {
                return "a";
            }
            if (session.getGlobalState("start.b") != null) {
                return "b";
            }
            return "a";
        });

        flow.addWorkflowComp("a", new IdentityNode(),
                Map.of("a", "${start.a}", "b", "${start.c}"),
                null);
        flow.addWorkflowComp("b", new IdentityNode(), Map.of("b", "${start.b}"), null);
        flow.setEndComp("end", new IdentityNode(),
                Map.of("result1", "${a.a}", "result2", "${b.b}"),
                null);
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        WorkflowOutput resultA = flow.invoke(Map.of("a", 1), newSession(), null);
        assertEquals(mapWithNullableValues("result1", 1, "result2", null), resultA.getResult());

        WorkflowOutput resultB = flow.invoke(Map.of("b", "haha"), newSession(), null);
        assertEquals(mapWithNullableValues("result1", null, "result2", "haha"), resultB.getResult());
    }

    @Test
    @DisplayName("workflow with branch router")
    void testSimpleWorkflowWithBranchCondition() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("a", "${a}", "b", "${b}", "c", 1, "d", List.of(1, 2, 3)),
                null);

        BranchRouter router = new BranchRouter();
        router.addBranch("${start.a} is not None", "a", null);
        router.addBranch("${start.b} is not None", "b", null);
        flow.addConditionalConnection("start", router);

        flow.addWorkflowComp("a", new IdentityNode(),
                Map.of("a", "${start.a}", "b", "${start.c}"),
                null);
        flow.addWorkflowComp("b", new IdentityNode(), Map.of("b", "${start.b}"), null);
        flow.setEndComp("end", new IdentityNode(),
                Map.of("result1", "${a.a}", "result2", "${b.b}"),
                null);
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        WorkflowOutput resultA = flow.invoke(Map.of("a", 1), newSession(), null);
        assertEquals(mapWithNullableValues("result1", 1, "result2", null), resultA.getResult());

        WorkflowOutput resultB = flow.invoke(Map.of("b", "haha"), newSession(), null);
        assertEquals(mapWithNullableValues("result1", null, "result2", "haha"), resultB.getResult());
    }

    @Test
    @DisplayName("workflow with wait_for_all")
    void testWorkflowWithWaitForAll() {
        Workflow waitAllFlow = createWaitForAllWorkflow(true);
        WorkflowOutput waitAllResult = waitAllFlow.invoke(
                Map.of("a", 1, "b", 2, "c", 3, "d", 4), newSession(), null);
        assertEquals(Map.of("result", 1), waitAllResult.getResult());

        Workflow nonWaitAllFlow = createWaitForAllWorkflow(false);
        WorkflowOutput nonWaitAllResult = nonWaitAllFlow.invoke(
                Map.of("a", 1, "b", 2, "c", 3, "d", 4), newSession(), null);
        assertEquals(Map.of("result", 2), nonWaitAllResult.getResult());
    }

    @Test
    @DisplayName("workflow with branch component")
    void testWorkflowWithBranch() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), null, null);
        flow.setEndComp("end", new IdentityNode(),
                Map.of("a", "${a.result}", "b", "${b.result}"),
                null);

        BranchComponent branch = new BranchComponent();
        branch.addBranch("${a} <= 10", List.of("b"), "1");
        branch.addBranch("${a} > 10", List.of("a"), "2");

        flow.addWorkflowComp("sw", branch);
        flow.addWorkflowComp("a", new IdentityNode(), Map.of("result", "${a}"), null);
        flow.addWorkflowComp("b", new AddTenNode(), Map.of("source", "${a}"), null);

        flow.addConnection("start", "sw");
        flow.addConnection("a", "end");
        flow.addConnection("b", "end");

        WorkflowOutput small = flow.invoke(Map.of("a", 2), newSession(), null);
        assertEquals(12, ((Map<?, ?>) small.getResult()).get("b"));

        WorkflowOutput big = flow.invoke(Map.of("a", 15), newSession(), null);
        assertEquals(15, ((Map<?, ?>) big.getResult()).get("a"));
    }

    @Test
    @DisplayName("workflow with loop number condition")
    void testWorkflowWithLoopNumberCondition() {
        Workflow flow = createLoopWorkflow();

        WorkflowOutput result1 = flow.invoke(
                Map.of("input_number", 1, "loop_number", 3), newSession(), null);
        assertEquals(Map.of("array_result", List.of(10, 11, 12), "user_var", 31), result1.getResult());

        WorkflowOutput result2 = flow.invoke(
                Map.of("input_number", 2, "loop_number", 2), newSession(), null);
        assertEquals(Map.of("array_result", List.of(10, 11), "user_var", 22), result2.getResult());
    }

    @Test
    @DisplayName("sub workflow invoke")
    void testSubWorkflow() {
        Workflow subFlow = new Workflow();
        subFlow.setStartComp("sub_start", new Start(),
                Map.of("value", "${value}"),
                null);
        subFlow.addWorkflowComp("sub_a", new IdentityNode(), Map.of("result", "${sub_start.value}"), null);
        subFlow.setEndComp("sub_end", new IdentityNode(), Map.of("result", "${sub_a.result}"), null);
        subFlow.addConnection("sub_start", "sub_a");
        subFlow.addConnection("sub_a", "sub_end");

        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("value", "${value}"), null);
        flow.addWorkflowComp("sub", new SubWorkflowComponentImpl(subFlow), Map.of("value", "${start.value}"), null);
        flow.setEndComp("end", new IdentityNode(), Map.of("result", "${sub.result}"), null);
        flow.addConnection("start", "sub");
        flow.addConnection("sub", "end");

        WorkflowOutput result = flow.invoke(Map.of("value", 7), newSession(), null);
        assertEquals(Map.of("result", 7), result.getResult());
    }

    @Test
    @DisplayName("stream workflow invoke and stream")
    void testStreamingWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("array", "${inputs}"), null);
        flow.addWorkflowComp("producer", new ProducerNode(),
                null,
                Map.of("array", "${start.array}"),
                null,
                null,
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.STREAM));
        flow.setEndComp("end", new com.openjiuwen.core.workflow.component.End(),
                null,
                null,
                Map.of("value", "${producer.output}"),
                null,
                "streaming");
        flow.addConnection("start", "producer");
        flow.addStreamConnection("producer", "end");

        WorkflowOutput invokeResult = flow.invoke(Map.of("inputs", List.of(1, 2, 3)), newSession(), null);
        assertInstanceOf(List.class, invokeResult.getResult());
        @SuppressWarnings("unchecked")
        List<Object> invokeChunks = (List<Object>) invokeResult.getResult();
        assertEquals(3, invokeChunks.size());
        assertOutputChunk(invokeChunks.get(0), 0, Map.of("output", Map.of("value", 1)));
        assertOutputChunk(invokeChunks.get(1), 1, Map.of("output", Map.of("value", 2)));
        assertOutputChunk(invokeChunks.get(2), 2, Map.of("output", Map.of("value", 3)));

        List<Object> streamChunks = new ArrayList<>();
        Iterator<?> iterator = flow.stream(
                Map.of("inputs", List.of(1, 2, 3)),
                newSession(),
                null,
                List.of(StreamMode.OUTPUT));
        iterator.forEachRemaining(streamChunks::add);
        assertEquals(3, streamChunks.size());
        assertOutputChunk(streamChunks.get(0), 0, Map.of("output", Map.of("value", 1)));
        assertOutputChunk(streamChunks.get(1), 1, Map.of("output", Map.of("value", 2)));
        assertOutputChunk(streamChunks.get(2), 2, Map.of("output", Map.of("value", 3)));
    }

    @Test
    @DisplayName("stream component workflow")
    void testStreamComponentWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("a", "${a}"), null);
        flow.addWorkflowComp("a", new StreamCompNode(),
                true,
                Map.of("value", "${start.a}"),
                null,
                null,
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.STREAM));
        flow.addWorkflowComp("b", new CollectCompNode(),
                true,
                Map.of("value1", "${a.value}"),
                null,
                Map.of("value", "${a.value}"),
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.COLLECT));
        flow.setEndComp("end", new IdentityNode(), Map.of("result1", "${b.value}"), null);
        flow.addConnection("start", "a");
        flow.addStreamConnection("a", "b");
        flow.addConnection("b", "end");

        WorkflowOutput result = flow.invoke(Map.of("a", 1), newSession(), null);
        assertEquals(Map.of("result1", 3), result.getResult());
    }

    @Test
    @DisplayName("transform workflow")
    void testTransformWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("a", "${a}"), null);
        flow.addWorkflowComp("a", new StreamCompNode(),
                true,
                Map.of("value", "${start.a}"),
                null,
                null,
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.STREAM));
        flow.addWorkflowComp("b", new TransformCompNode(),
                true,
                Map.of("value1", "${a.value}"),
                null,
                Map.of("value", "${a.value}"),
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.TRANSFORM));
        flow.addWorkflowComp("c", new CollectCompNode(),
                true,
                Map.of("value1", "${b.value}"),
                null,
                Map.of("value", "${b.value}"),
                null,
                List.of(com.openjiuwen.core.workflow.component.ComponentAbility.COLLECT));
        flow.setEndComp("end", new IdentityNode(), Map.of("result", "${c.value}"), null);
        flow.addConnection("start", "a");
        flow.addStreamConnection("a", "b");
        flow.addStreamConnection("b", "c");
        flow.addConnection("c", "end");

        WorkflowOutput result = flow.invoke(Map.of("a", 1), newSession(), null);
        assertEquals(Map.of("result", 3), result.getResult());
    }

    @Test
    @DisplayName("workflow validates inputs and supports skip flag")
    void testWorkflowInputValidation() {
        Workflow flow = new Workflow(WorkflowCard.builder()
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("value", Map.of("type", "integer")),
                        "required", List.of("value")))
                .build());
        flow.setStartComp("start", new Start(), Map.of("value", "${value}"), null);
        flow.setEndComp("end", new IdentityNode(), Map.of("result", "${start.value}"), null);
        flow.addConnection("start", "end");

        assertThrows(RuntimeException.class, () -> flow.invoke(Map.of("value", "bad"), newSession(), null));

        WorkflowOutput skipped = flow.invoke(Map.of("value", "bad"), newSession(), null, false, true);
        assertEquals(Map.of("result", "bad"), skipped.getResult());
    }

    @Test
    @DisplayName("workflow stream yields chunks before execution fully completes")
    void testWorkflowStreamIsIncremental() {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(), Map.of("array", "${inputs}"), null);
        flow.setEndComp("end", new SlowStreamingEndNode(250),
                Map.of("array", "${start.array}"),
                null,
                null,
                null,
                "streaming");
        flow.addConnection("start", "end");

        long startNanos = System.nanoTime();
        Iterator<?> iterator = flow.stream(
                Map.of("inputs", List.of(1, 2, 3)),
                newSession(),
                null,
                List.of(StreamMode.OUTPUT));
        assertTrue(iterator.hasNext());
        Object firstChunk = iterator.next();
        long firstElapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        List<Object> remaining = new ArrayList<>();
        while (iterator.hasNext()) {
            remaining.add(iterator.next());
        }
        long totalElapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertOutputChunk(firstChunk, 0, Map.of("output", Map.of("value", 1)));
        assertEquals(2, remaining.size());
        assertTrue(firstElapsedMs + 150 < totalElapsedMs,
                "first chunk should arrive before the full workflow finishes");
    }

    private static Workflow createWaitForAllWorkflow(boolean waitForAll) {
        Workflow flow = new Workflow();
        flow.setStartComp("start", new Start(),
                Map.of("a", "${a}", "b", "${b}", "c", "${c}", "d", "${d}"),
                null);
        flow.addWorkflowComp("a", new IdentityNode(), Map.of("a", "${start.a}"), null);
        flow.addWorkflowComp("a1", new SlowNode(100), Map.of("a", "${a.a}"), null);
        flow.addWorkflowComp("b", new IdentityNode(), Map.of("b", "${start.b}"), null);
        flow.addWorkflowComp("c", new IdentityNode(), Map.of("c", "${start.c}"), null);
        flow.addWorkflowComp("d", new IdentityNode(), Map.of("d", "${start.d}"), null);
        flow.addWorkflowComp("collect", new CountNode(), waitForAll, null, null, null, null, null);
        flow.setEndComp("end", new IdentityNode(), Map.of("result", "${collect.count}"), null);
        flow.addConnection("start", "a");
        flow.addConnection("start", "b");
        flow.addConnection("start", "c");
        flow.addConnection("start", "d");
        flow.addConnection("a", "a1");
        flow.addConnection("a1", "collect");
        flow.addConnection("b", "collect");
        flow.addConnection("c", "collect");
        flow.addConnection("d", "collect");
        flow.addConnection("collect", "end");
        return flow;
    }

    private static Workflow createLoopWorkflow() {
        Workflow flow = new Workflow();
        flow.setStartComp("s", new Start(), null, null);
        flow.setEndComp("e", new IdentityNode(),
                Map.of("array_result", "${b.array_result}", "user_var", "${b.user_var}"),
                null);
        flow.addWorkflowComp("a", new IdentityNode());
        flow.addWorkflowComp("b", new IdentityNode(),
                Map.of("array_result", "${l.results}", "user_var", "${l.user_var}"),
                null);

        LoopGroup loopGroup = new LoopGroup();
        loopGroup.addWorkflowComp("1", new AddTenNode(), null, Map.of("source", "${l.index}"), null,
                null, null, null);
        loopGroup.addWorkflowComp("2", new AddTenNode(), null,
                Map.of("source", "${l.intermediate_loop_var.user_var}"), null, null, null, null);
        loopGroup.addWorkflowComp("3",
                new LoopSetVariableComponent(Map.of("${l.intermediate_loop_var.user_var}", "${2.result}")),
                null,
                null,
                null,
                null,
                null,
                null);
        loopGroup.startNodes(List.of("1"));
        loopGroup.endNodes(List.of("3"));
        loopGroup.addConnection("1", "2");
        loopGroup.addConnection("2", "3");

        OutputCallback outputCallback = new OutputCallback(
                Map.of("results", "${1.result}", "user_var", "${l.intermediate_loop_var.user_var}"));
        IntermediateLoopVarCallback intermediateCallback = new IntermediateLoopVarCallback(
                Map.of("user_var", "${input_number}"), "intermediate_loop_var");
        AdvancedLoopComponentImpl loop = new AdvancedLoopComponentImpl(
                loopGroup,
                new NumberCondition("${loop_number}"),
                loopGroup.getBreakComponents(),
                List.of(outputCallback, intermediateCallback));

        flow.addWorkflowComp("l", loop, Map.of("input_number", "${input_number}"), null);
        flow.addConnection("s", "a");
        flow.addConnection("a", "l");
        flow.addConnection("l", "b");
        flow.addConnection("b", "e");
        return flow;
    }

    private static WorkflowSessionApi newSession() {
        return new WorkflowSessionApi(null, UUID.randomUUID().toString(), Map.of());
    }

    private static Map<String, Object> mapWithNullableValues(
            String key1,
            Object value1,
            String key2,
            Object value2) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }

    private static void assertOutputChunk(Object chunk, int index, Object payload) {
        assertInstanceOf(com.openjiuwen.core.session.stream.OutputSchema.class, chunk);
        com.openjiuwen.core.session.stream.OutputSchema schema =
                (com.openjiuwen.core.session.stream.OutputSchema) chunk;
        assertEquals("end node stream", schema.getType());
        assertEquals(index, schema.getIndex());
        assertEquals(payload, schema.getPayload());
    }

    private static class IdentityNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            return inputs;
        }
    }

    private static class AddTenNode extends WorkflowComponent {
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Number source = (Number) inputMap.get("source");
            return Map.of("result", source.intValue() + 10);
        }
    }

    private static class CountNode extends WorkflowComponent {
        private int times;

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            times++;
            return Map.of("count", times);
        }
    }

    private static class SlowNode extends WorkflowComponent {
        private final long delayMs;

        private SlowNode(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return inputs;
        }
    }

    private static class ProducerNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            List<Object> frames = new ArrayList<>();
            for (Object item : (List<Object>) inputMap.get("array")) {
                frames.add(Map.of("output", item));
            }
            return frames.iterator();
        }
    }

    private static class SlowProducerNode extends WorkflowComponent {
        private final long delayMs;

        private SlowProducerNode(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            List<Object> values = new ArrayList<>((List<Object>) inputMap.get("array"));
            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < values.size();
                }

                @Override
                public Object next() {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    Object value = values.get(index++);
                    return Map.of("output", value);
                }
            };
        }
    }

    private static class SlowStreamingEndNode extends WorkflowComponent {
        private final long delayMs;

        private SlowStreamingEndNode(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            List<Object> values = new ArrayList<>((List<Object>) inputMap.get("array"));
            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < values.size();
                }

                @Override
                public Object next() {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    Object value = values.get(index++);
                    return Map.of("output", Map.of("value", value));
                }
            };
        }
    }

    private static class StreamCompNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Number value = (Number) inputMap.get("value");
            List<Object> frames = new ArrayList<>();
            for (int i = 1; i < 3; i++) {
                frames.add(Map.of("value", i * value.intValue()));
            }
            return frames.iterator();
        }
    }

    private static class CollectCompNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Object stream = inputMap.get("value");
            int result = 0;
            if (stream instanceof Iterator<?> iterator) {
                while (iterator.hasNext()) {
                    Object value = iterator.next();
                    if (value instanceof Number number) {
                        result += number.intValue();
                    }
                }
            }
            return Map.of("value", result);
        }
    }

    private static class TransformCompNode extends WorkflowComponent {
        @Override
        @SuppressWarnings("unchecked")
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            Map<String, Object> inputMap = (Map<String, Object>) inputs;
            Object stream = inputMap.get("value");
            List<Object> frames = new ArrayList<>();
            if (stream instanceof Iterator<?> iterator) {
                while (iterator.hasNext()) {
                    frames.add(Map.of("value", iterator.next()));
                }
            }
            return frames.iterator();
        }
    }
}
