/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.session.stream.AsyncStreamQueue;
import com.openjiuwen.core.session.stream.StreamEmitter;
import com.openjiuwen.core.session.stream.StreamWriterManager;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgentSession;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Verifies that built-in session paths share model-call timeout leases with their stream manager.
 */
@Timeout(5)
class SessionModelTimeoutWindowTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionModelTimeoutWindowTest.class);
    private static final long ORIGINAL_TIMEOUT_MS = 40L;
    private static final long MODEL_IDLE_MS = 100L;

    private final ThreadPoolExecutor componentExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(4),
            SessionModelTimeoutWindowTest::newComponentThread);

    @AfterEach
    void stopComponentExecutor() {
        componentExecutor.shutdownNow();
        try {
            if (!componentExecutor.awaitTermination(2L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Component invocation did not stop");
            }
        } catch (InterruptedException exception) {
            throw new IllegalStateException("Component executor shutdown was interrupted", exception);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("publicSessionPaths")
    void publicFacadeUsesItsOwnManager(String name, AgentSessionApi session, StreamWriterManager manager) {
        long leaseId = session.renewModelCallTimeout(0L, MODEL_IDLE_MS, 0L);

        assertTrue(leaseId > 0L, name);
        assertEquals(leaseId, session.renewModelCallTimeout(leaseId, MODEL_IDLE_MS, 0L));
        assertEquals(leaseId, manager.renewModelCallTimeout(leaseId, MODEL_IDLE_MS, 0L));

        session.endModelCallTimeout(leaseId);

        assertThrows(IllegalArgumentException.class,
                () -> manager.renewModelCallTimeout(leaseId, MODEL_IDLE_MS, 0L));
    }

    @Test
    void workflowSessionWithoutManagerKeepsNoOpLeaseBehavior() {
        List<WorkflowSession> sessions = List.of(
                new WorkflowSession("without-parent"),
                new WorkflowSession(new BaseSession() {
                }, "without-manager"));

        for (WorkflowSession session : sessions) {
            assertEquals(0L, session.renewModelCallTimeout(0L, MODEL_IDLE_MS, 0L));
            assertDoesNotThrow(() -> session.endModelCallTimeout(1L));
        }
    }

    @Test
    void workflowAdapterCoordinatesItsBaseSessionManager() {
        BlockingStreamModelClient client = new BlockingStreamModelClient();
        com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompExecutable executable =
                workflowExecutable(client);
        ManagerFixture fixture = new ManagerFixture();

        Future<Object> invocation = componentExecutor.submit(
                () -> executable.invoke("question", fixture.session, null));

        assertLeaseDuringInvocation(fixture, client, invocation);
    }

    @Test
    void nodeAdapterCoordinatesItsDelegateManager() {
        BlockingStreamModelClient client = new BlockingStreamModelClient();
        com.openjiuwen.core.workflow.component.llm.ReActAgentCompExecutable executable = nodeExecutable(client);
        ManagerFixture fixture = new ManagerFixture();
        NodeSessionApi nodeSession = new NodeSessionApi(fixture.session);

        Future<Object> invocation = componentExecutor.submit(
                () -> executable.invoke("question", nodeSession, null));

        assertLeaseDuringInvocation(fixture, client, invocation);
    }

    @Test
    void adaptersWithoutManagerKeepNoOpLeaseBehavior() {
        BlockingStreamModelClient client = new BlockingStreamModelClient();
        client.release.countDown();
        BaseSession session = new BaseSession() {
        };

        Object workflowResult = workflowExecutable(client).invoke("question", session, null);
        Object nodeResult = nodeExecutable(client).invoke("question", new NodeSessionApi(session), null);

        assertEquals("ok", assertInstanceOf(Map.class, workflowResult).get("output"));
        assertEquals("ok", assertInstanceOf(Map.class, nodeResult).get("output"));
    }

    private void assertLeaseDuringInvocation(ManagerFixture fixture, BlockingStreamModelClient client,
            Future<Object> invocation) {
        try {
            assertTrue(client.entered.await(2L, TimeUnit.SECONDS));
            fixture.manager.streamEmitter().emit("first");
            Iterator<Object> iterator = fixture.manager.streamIterator(1000L, ORIGINAL_TIMEOUT_MS, false);
            assertEquals("first", iterator.next());
            fixture.queue.arm(3, "adapter-lease");
            assertEquals("adapter-lease", iterator.next());

            client.release.countDown();
            invocation.get(2L, TimeUnit.SECONDS);
            fixture.queue.drain();
            BaseError error = assertThrows(BaseError.class, iterator::hasNext);
            assertEquals(StatusCode.STREAM_OUTPUT_CHUNK_INTERVAL_TIMEOUT, error.getStatus());
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            throw new AssertionError("Component lease coordination failed", exception);
        } finally {
            client.release.countDown();
        }
    }

    private static Stream<Arguments> publicSessionPaths() {
        AgentSession agent = new AgentSession("agent-lease", null, null);
        AgentGroupSession group = new AgentGroupSession("group-lease");
        AgentTeamSession team = new AgentTeamSession("team-lease", null, "team");
        AgentSession parent = new AgentSession("workflow-parent-lease", null, null);
        WorkflowSession workflow = new WorkflowSession(parent.getInner(), "workflow-lease");
        DeepAgentSession deepAgent = new DeepAgentSession("deep-agent-lease");
        return Stream.of(
                facadeArguments("AgentSession", agent, agent.getInner()),
                facadeArguments("AgentGroupSession", group, group.getInner()),
                facadeArguments("AgentTeamSession", team, team.getInner()),
                facadeArguments("WorkflowSession", workflow, parent.getInner()),
                facadeArguments("DeepAgentSession", deepAgent, deepAgent.getInner()));
    }

    private static Arguments facadeArguments(String name, AgentSessionApi session, BaseSession inner) {
        return Arguments.of(name, session, inner.streamWriterManager());
    }

    private static com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompExecutable
            workflowExecutable(BlockingStreamModelClient client) {
        com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompConfig config =
                new com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompConfig();
        configureModelCall(config);
        com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompExecutable executable =
                new com.openjiuwen.core.workflow.components.llm.react.ReActAgentCompExecutable(config);
        injectModel(executable, client, config);
        return executable;
    }

    private static com.openjiuwen.core.workflow.component.llm.ReActAgentCompExecutable
            nodeExecutable(BlockingStreamModelClient client) {
        com.openjiuwen.core.workflow.component.llm.ReActAgentCompConfig config =
                new com.openjiuwen.core.workflow.component.llm.ReActAgentCompConfig();
        configureModelCall(config);
        com.openjiuwen.core.workflow.component.llm.ReActAgentCompExecutable executable =
                new com.openjiuwen.core.workflow.component.llm.ReActAgentCompExecutable(config);
        injectModel(executable, client, config);
        return executable;
    }

    private static void configureModelCall(ReActAgentConfig config) {
        config.setMaxIterations(1);
        config.setModelStreamIdleSeconds(0.25D);
    }

    private static void injectModel(Object executable, BlockingStreamModelClient client, ReActAgentConfig config) {
        try {
            StreamingInvokeReActAgent agent = new StreamingInvokeReActAgent();
            agent.configure(config);
            agent.setLlm(new Model(client));
            Field field = executable.getClass().getDeclaredField("reactAgent");
            field.setAccessible(true);
            field.set(executable, agent);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inject the external model boundary", exception);
        }
    }

    private static Thread newComponentThread(Runnable runnable) {
        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
        thread.setName("session-window-test-component");
        thread.setUncaughtExceptionHandler((worker, error) -> LOGGER.error("Component invocation failed", error));
        return thread;
    }

    private static final class ManagerFixture {
        private final FrameOnCallQueue queue = new FrameOnCallQueue();
        private final StreamWriterManager manager = new StreamWriterManager(new StreamEmitter(queue));
        private final ManagerSession session = new ManagerSession(manager);
    }

    private static final class ManagerSession extends BaseSession {
        private final StreamWriterManager manager;

        private ManagerSession(StreamWriterManager manager) {
            this.manager = manager;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public StreamWriterManager streamWriterManager() {
            return manager;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String sessionId() {
            return "workflow-manager";
        }
    }

    private static final class FrameOnCallQueue extends AsyncStreamQueue {
        private int receiveCalls;
        private int frameOnCall = -1;
        private String frame = "";

        private void arm(int call, String value) {
            frameOnCall = call;
            frame = value;
        }

        private void drain() {
            while (tryReceive() != null) {
                // Drain producer output before checking the restored deadline.
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object receive(long timeoutMs) {
            receiveCalls++;
            if (receiveCalls == frameOnCall) {
                send(frame);
            }
            return super.receive(timeoutMs);
        }
    }

    private static final class StreamingInvokeReActAgent extends ReActAgent {
        private StreamingInvokeReActAgent() {
            super(new AgentCard("workflow-streaming", "Workflow streaming", "Workflow streaming agent"));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            return super.invoke(inputs, session, Map.of("_streaming", true));
        }
    }

    private static final class BlockingStreamModelClient implements Model.ModelClient {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("ok"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            entered.countDown();
            awaitRelease();
            return List.of(AssistantMessageChunk.builder().content("ok").finishReason("stop").build()).iterator();
        }

        private void awaitRelease() {
            try {
                if (!release.await(2L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Model release was not signalled");
                }
            } catch (InterruptedException exception) {
                throw new IllegalStateException("Model wait was interrupted", exception);
            }
        }
    }
}
