/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.graph.pregel.GraphInterrupt;
import com.openjiuwen.core.workflow.component.ComponentAbility;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for {@link Vertex}.
 *
 * <p>Mirrors Python's {@code Vertex} in
 * {@code openjiuwen/core/graph/vertex.py}.</p>
 */
class VertexTest {

    private static final Throwable NO_THROWABLE = null;

    @Test
    void collectRefSourceIdsWalksNestedSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("a", "${first.value}");
        schema.put("b", List.of(Map.of("c", "${second.items[0]}"), "plain"));
        schema.put("d", Map.of("e", "${first.other}"));

        assertEquals(SetSupport.linkedSet("first", "second"), Vertex.collectRefSourceIds(schema));
    }

    @Test
    void callRaisesWhenNotInitialized() {
        Vertex vertex = new Vertex("uninitialized", null, Runnable::run);

        Throwable error = unwrap(vertex.call(null).toCompletableFuture()::join);

        BaseError baseError = assertInstanceOf(BaseError.class, error);
        assertEquals(StatusCode.GRAPH_VERTEX_EXECUTION_ERROR, baseError.getStatus());
        assertEquals("node is not initialized", baseError.getParams().get("reason"));
    }

    @Test
    void callRetriesInvokeUntilSuccess() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> {
            if (executable.invokeCount < 3) {
                throw new IllegalStateException("transient");
            }
            return Map.of("result", "ok");
        };
        RecordingSession session = RecordingSession.withConfig(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.INVOKE),
                new Vertex.VertexIoConfig(),
                new Vertex.VertexIoConfig(),
                2,
                -1.0d,
                null));
        Vertex vertex = new Vertex("retry_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals(3, executable.invokeCount);
        assertEquals("ok", session.state.outputs.get("result"));
    }

    @Test
    void callSucceedsWithoutRetry() {
        RecordingExecutable executable = new RecordingExecutable();
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(0));
        Vertex vertex = new Vertex("success_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals(1, executable.invokeCount);
    }

    @Test
    void callRetriesExhaustedRaisesWorkflowComponentError() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> {
            throw new IllegalStateException("persistent");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(1));
        Vertex vertex = new Vertex("failed_node", executable, Runnable::run);
        vertex.init(session);

        Throwable error = unwrap(vertex.call(null).toCompletableFuture()::join);

        BaseError baseError = assertInstanceOf(BaseError.class, error);
        assertEquals(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR, baseError.getStatus());
        assertEquals(2, executable.invokeCount);
    }

    @Test
    void callDoesNotRetryWhenMaxRetriesZero() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> {
            throw new IllegalStateException("fail");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(0));
        Vertex vertex = new Vertex("no_retry_node", executable, Runnable::run);
        vertex.init(session);

        Throwable error = unwrap(vertex.call(null).toCompletableFuture()::join);

        assertInstanceOf(BaseError.class, error);
        assertEquals(1, executable.invokeCount);
    }

    @Test
    void callDoesNotRetryGraphInterrupt() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> sneakyThrow(new GraphInterrupt("stop"));
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(3));
        Vertex vertex = new Vertex("interrupt_node", executable, Runnable::run);
        vertex.init(session);

        Throwable error = unwrap(vertex.call(null).toCompletableFuture()::join);

        assertInstanceOf(GraphInterrupt.class, error);
        assertEquals(1, executable.invokeCount);
    }

    @Test
    void callRetriesBaseErrorAndSucceeds() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> {
            if (executable.invokeCount < 2) {
                throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                        null, null, null, Map.of("reason", "retryable"));
            }
            return Map.of("result", "ok");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(2));
        Vertex vertex = new Vertex("base_error_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals(2, executable.invokeCount);
    }

    @Test
    void innerErrorIsTracedForRuntimeRetry() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> {
            if (executable.invokeCount < 2) {
                throw new IllegalStateException("transient");
            }
            return Map.of("result", "ok");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(2));
        session.trace = new RecordingTraceSink();
        Vertex vertex = new Vertex("trace_runtime_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals(1, session.trace.innerErrorCount());
        assertTrue(session.trace.traceData.get(0).containsKey("current_time"));
        assertFalse(session.trace.traceData.get(0).containsKey("end_time"));
    }

    @Test
    void innerErrorIsTracedForBaseErrorRetry() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> {
            if (executable.invokeCount < 2) {
                throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                        null, null, null, Map.of("reason", "base error"));
            }
            return Map.of("result", "ok");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(2));
        session.trace = new RecordingTraceSink();
        Vertex vertex = new Vertex("trace_base_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals(1, session.trace.innerErrorCount());
    }

    @Test
    void skipTraceSuppressesRetryTrace() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.skipTrace = true;
        executable.invokeFunction = ignored -> {
            if (executable.invokeCount < 2) {
                throw new IllegalStateException("transient");
            }
            return Map.of("result", "ok");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(2));
        session.trace = new RecordingTraceSink();
        Vertex vertex = new Vertex("skip_trace_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals(0, session.trace.innerErrorCount());
        assertEquals(0, session.trace.traceErrorCount);
    }

    @Test
    void invokeTracesFinalErrorWhenCallFails() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.postCommit = false;
        executable.invokeFunction = ignored -> {
            throw new IllegalStateException("fail");
        };
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(0));
        session.trace = new RecordingTraceSink();
        Vertex vertex = new Vertex("invoke_trace_node", executable, Runnable::run);
        vertex.init(session);

        Throwable error = unwrap(() -> vertex.invoke(new GraphState(), null).toCompletableFuture().join());

        assertInstanceOf(BaseError.class, error);
        assertEquals(1, session.trace.traceErrorCount);
    }

    @Test
    void graphInterruptDoesNotTraceFinalError() {
        RecordingExecutable executable = new RecordingExecutable();
        executable.invokeFunction = ignored -> sneakyThrow(new GraphInterrupt("stop"));
        RecordingSession session = RecordingSession.withConfig(defaultInvokeConfig(1));
        session.trace = new RecordingTraceSink();
        Vertex vertex = new Vertex("interrupt_trace_node", executable, Runnable::run);
        vertex.init(session);

        Throwable error = unwrap(vertex.call(null).toCompletableFuture()::join);

        assertInstanceOf(GraphInterrupt.class, error);
        assertEquals(0, session.trace.traceErrorCount);
        assertEquals(0, session.trace.innerErrorCount());
    }

    @Test
    void postInvokeFiltersNullOutputsWhenOutputSchemaIsDict() {
        RecordingExecutable executable = new RecordingExecutable();
        Map<String, Object> outputSchema = new LinkedHashMap<>();
        outputSchema.put("kept", "${kept}");
        outputSchema.put("removed", "${removed}");
        executable.invokeFunction = ignored -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kept", "value");
            result.put("removed", null);
            return result;
        };
        RecordingSession session = RecordingSession.withConfig(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.INVOKE),
                new Vertex.VertexIoConfig(null, outputSchema),
                new Vertex.VertexIoConfig(),
                0,
                -1.0d,
                null));
        Vertex vertex = new Vertex("filter_node", executable, Runnable::run);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        assertEquals("value", session.state.outputs.get("kept"));
        assertFalse(session.state.outputs.containsKey("removed"));
    }

    @Test
    void endNodeSanitizesUnexecutedBranchInputBeforeInvoke() {
        RecordingExecutable executable = new RecordingExecutable();
        RecordingSession session = RecordingSession.withConfig(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.INVOKE),
                new Vertex.VertexIoConfig(Map.of("branch", "${branch_node.output}", "literal", "literal"), null),
                new Vertex.VertexIoConfig(),
                0,
                -1.0d,
                null));
        session.state.inputs.put("branch", null);
        session.state.inputs.put("literal", "kept");
        Vertex vertex = new Vertex("end_node", executable, Runnable::run);
        vertex.setEndNode(true);
        vertex.init(session);

        vertex.call(null).toCompletableFuture().join();

        Map<String, Object> inputs = executable.receivedInputs.get(0);
        assertEquals("", inputs.get("branch"));
        assertEquals("kept", inputs.get("literal"));
    }

    @Test
    void streamCallReportsMissingActorManager() throws Exception {
        RecordingSession session = RecordingSession.withConfig(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.COLLECT),
                new Vertex.VertexIoConfig(),
                new Vertex.VertexIoConfig(),
                0,
                -1.0d,
                null));
        Vertex vertex = new Vertex("stream_node", new RecordingExecutable(), Runnable::run);
        vertex.init(session);
        CountDownLatch latch = new CountDownLatch(1);
        List<Exception> errors = new ArrayList<>();

        vertex.streamCall(latch, errors::add);

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(vertex.streamCalled());
        assertEquals(1, errors.size());
        BaseError error = assertInstanceOf(BaseError.class, errors.get(0));
        assertEquals(StatusCode.GRAPH_VERTEX_STREAM_CALL_ERROR, error.getStatus());
    }

    @Test
    void initCallsSetMixWhenStreamInputsExist() {
        RecordingExecutable executable = new RecordingExecutable();
        RecordingSession session = RecordingSession.withConfig(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.INVOKE, ComponentAbility.COLLECT),
                new Vertex.VertexIoConfig(),
                new Vertex.VertexIoConfig(Map.of("chunk", "${source.output}"), null),
                0,
                -1.0d,
                null));
        Vertex vertex = new Vertex("mix_node", executable, Runnable::run);

        vertex.init(session);

        assertEquals(1, executable.mixCount);
        assertTrue(vertex.shouldHandleMessage());
    }

    @Test
    void resetClearsCallAndStreamCounters() {
        RecordingSession session = RecordingSession.withConfig(new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.COLLECT),
                new Vertex.VertexIoConfig(),
                new Vertex.VertexIoConfig(),
                0,
                -1.0d,
                null));
        Vertex vertex = new Vertex("reset_node", new RecordingExecutable(), Runnable::run);
        vertex.init(session);
        vertex.streamCall(new CountDownLatch(0), ignored -> { });
        assertEquals(1, vertex.streamCallCount());

        vertex.reset();

        assertEquals(0, vertex.callCount());
        assertEquals(0, vertex.streamCallCount());
    }

    private static Throwable unwrap(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return NO_THROWABLE;
        } catch (CompletionException completionException) {
            return completionException.getCause();
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private static Vertex.VertexNodeConfig defaultInvokeConfig(int maxRetries) {
        return new Vertex.VertexNodeConfig(
                List.of(ComponentAbility.INVOKE),
                new Vertex.VertexIoConfig(),
                new Vertex.VertexIoConfig(),
                maxRetries,
                -1.0d,
                null);
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    /**
     * Mirrors Python's executable object used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    private static final class RecordingExecutable extends Executable<Map<String, Object>, Map<String, Object>>
            implements Vertex.TemplateAware, Vertex.MixConfigurable {
        private final List<Map<String, Object>> receivedInputs = new ArrayList<>();
        private final RecordingTemplate template = new RecordingTemplate();
        private Function<Map<String, Object>, Map<String, Object>> invokeFunction = ignored -> Map.of("ok", true);
        private int invokeCount;
        private int mixCount;
        private boolean skipTrace;
        private boolean postCommit = true;

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                            com.openjiuwen.core.session.BaseSession session,
                                            Object... kwargs) {
            invokeCount += 1;
            receivedInputs.add(inputs == null ? null : new LinkedHashMap<>(inputs));
            return invokeFunction.apply(inputs);
        }

        @Override
        public Vertex.TemplateDataSourceCounter templateDataSourceCounter() {
            return template;
        }

        @Override
        public void setMix() {
            mixCount += 1;
        }

        @Override
        public boolean skipTrace() {
            return skipTrace;
        }

        @Override
        public boolean postCommit() {
            return postCommit;
        }
    }

    /**
     * Mirrors Python's template counter used by {@code Vertex._sanitize_end_node_inputs} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    private static final class RecordingTemplate implements Vertex.TemplateDataSourceCounter {
        private int dataSourceCount = -1;

        @Override
        public void setDataSourceCount(int count) {
            dataSourceCount = count;
        }
    }

    /**
     * Mirrors Python's node session used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    private static final class RecordingSession extends Vertex.VertexSession {
        private final RecordingState state = new RecordingState();
        private final Vertex.VertexNodeConfig config;
        private RecordingTraceSink trace;

        private RecordingSession(Vertex.VertexNodeConfig config) {
            this.config = config;
        }

        private static RecordingSession withConfig(Vertex.VertexNodeConfig config) {
            return new RecordingSession(config);
        }

        @Override
        public RecordingState state() {
            return state;
        }

        @Override
        public Vertex.VertexNodeConfig nodeConfig() {
            return config;
        }

        @Override
        public String workflowId() {
            return "workflow";
        }

        @Override
        public Vertex.VertexTraceSink tracer() {
            return trace;
        }
    }

    /**
     * Mirrors Python's trace utility calls used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    private static final class RecordingTraceSink implements Vertex.VertexTraceSink {
        private final List<Map<String, Object>> traceData = new ArrayList<>();
        private int traceErrorCount;

        @Override
        public void trace(Vertex.VertexSession session, Map<String, Object> data) {
            traceData.add(new LinkedHashMap<>(data));
        }

        @Override
        public void traceError(Vertex.VertexSession session, Throwable error) {
            traceErrorCount += 1;
        }

        private int innerErrorCount() {
            return (int) traceData.stream()
                    .filter(data -> data.get("inner_error") != null)
                    .count();
        }
    }

    /**
     * Mirrors Python's workflow state calls used by {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    private static final class RecordingState implements Vertex.VertexState {
        private final Map<String, Object> inputs = new LinkedHashMap<>();
        private final Map<String, Object> outputs = new LinkedHashMap<>();
        private final Map<String, Object> workflowState = new LinkedHashMap<>();
        private final Map<String, Object> values = new LinkedHashMap<>();

        @Override
        public Map<String, Object> getInputs(Object schema) {
            return inputs;
        }

        @Override
        public Map<String, Object> getInputsByTransformer(Vertex.ValueTransformer transformer) {
            return transformer.apply(inputs);
        }

        @Override
        public Object getOutputs(String nodeId) {
            return outputs;
        }

        @Override
        public void setOutputs(Map<String, Object> outputs) {
            this.outputs.clear();
            this.outputs.putAll(outputs);
            values.put("node", new LinkedHashMap<>(outputs));
        }

        @Override
        public Object getWorkflowState(String key) {
            return workflowState.get(key);
        }

        @Override
        public void updateAndCommitWorkflowState(Map<String, Object> data) {
            workflowState.putAll(data);
        }

        @Override
        public Object get(Object key) {
            return values.get(String.valueOf(key));
        }

        @Override
        public Object get(String key) {
            return values.get(key);
        }

        @Override
        public void update(Map<String, Object> data) {
            values.putAll(data);
        }
    }

    /**
     * Mirrors Python's exception-producing callable paths tested around {@code Vertex} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    /**
     * Mirrors Python's ordered set expectations for {@code _collect_ref_source_ids} in
     * {@code openjiuwen/core/graph/vertex.py}.
     */
    private static final class SetSupport {
        private SetSupport() {
        }

        private static java.util.Set<String> linkedSet(String... values) {
            return new java.util.LinkedHashSet<>(List.of(values));
        }
    }
}
