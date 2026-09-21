/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.ToolDecorator;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.resourcemanager.TagMatchStrategy;
import com.openjiuwen.core.session.AgentGroupSession;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Focused tests for the Runner singleton facade.
 *
 * <p>Mirrors Python's {@code TestRunner} in
 * {@code tests/unit_tests/core/runner/test_runner.py}.</p>
 *
 * <p>Mirrors Python's {@code test_team_runner_session} in
 * {@code tests/unit_tests/multi_agent/team/test_team_runner_session.py}.</p>
 */
class RunnerTest {

    @AfterEach
    void resetConfig() {
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());
        CheckpointerFactory.releaseDefaultCheckpointer();
    }

    @Test
    void singletonResourcesAreStable() {
        ResourceMgr resourceMgr = Runner.getResourceMgr();
        AsyncCallbackFramework callbackFramework = Runner.getCallbackFramework();

        assertNotNull(resourceMgr);
        assertSame(resourceMgr, Runner.resourceMgr);
        assertNotNull(Runner.getPubsub());
        assertSame(callbackFramework, Runner.callbackFramework);
    }

    @Test
    void configSetAndGetUseGlobalRunnerConfig() {
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setEnvPrefix("runner-test");

        Runner.setConfig(config);

        assertSame(config, Runner.getConfig());
        assertEquals("runner-test", Runner.getConfig().getEnvPrefix());
    }

    @Test
    void startAndStopDefaultLocalModeReturnSuccess() {
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());

        assertTrue(Runner.start().toCompletableFuture().join());
        assertFalse(Runner.getConfig().isDistributedMode());
        assertNull(Runner.getDistPubsub());
        assertTrue(Runner.stop().toCompletableFuture().join());
    }

    @Test
    void stopCompletesResourceResetBeforeReturningFuture() {
        WorkflowCard card = new WorkflowCard("runner-reset-workflow", "runner-reset-workflow", "", "1.0", null);
        DirectWorkflow workflow = new DirectWorkflow(card);
        Runner.getResourceMgr().addWorkflow(card, () -> workflow, List.of("runner-reset-tag"));

        Runner.stop();

        assertNull(Runner.getResourceMgr().getWorkflow("runner-reset-workflow", null).toCompletableFuture().join());

        WorkflowCard nextCard = new WorkflowCard("runner-reset-workflow", "runner-reset-workflow", "", "1.0", null);
        DirectWorkflow nextWorkflow = new DirectWorkflow(nextCard);
        Runner.getResourceMgr().addWorkflow(nextCard, () -> nextWorkflow, List.of("runner-reset-tag"));

        List<Object> workflows = Runner.getResourceMgr()
                .getWorkflowsByTag(List.of("runner-reset-tag"), TagMatchStrategy.ALL, null)
                .toCompletableFuture()
                .join();
        assertEquals(1, workflows.size());
        assertSame(nextWorkflow, workflows.get(0));
    }

    @Test
    void stopClosesCheckpointerCreatedFromConfigAndFallsBackToInMemory() {
        CloseTrackingCheckpointer created = new CloseTrackingCheckpointer();
        CheckpointerFactory.register("unit-runner-closeable", conf -> created);
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setCheckpointerConfig(new CheckpointerConfig("unit-runner-closeable", Map.of()));
        Runner.setConfig(config);

        assertTrue(Runner.start().toCompletableFuture().join());
        assertSame(created, CheckpointerFactory.getCheckpointer());
        assertTrue(Runner.stop().toCompletableFuture().join());

        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), CheckpointerFactory.getCheckpointer());
        assertTrue(created.closed);
    }

    @Test
    void startFailureClosesCheckpointerCreatedFromConfigAndFallsBackToInMemory() {
        CloseTrackingCheckpointer created = new CloseTrackingCheckpointer();
        CheckpointerFactory.register("unit-runner-start-failure", conf -> created);
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setDistributedMode(true);
        config.setDistributedConfig(DistributedConfig.builder()
                .messageQueueConfig(MessageQueueConfig.builder().type("missing-mq").build())
                .build());
        config.setCheckpointerConfig(new CheckpointerConfig("unit-runner-start-failure", Map.of()));
        Runner.setConfig(config);

        assertThrows(CompletionException.class, () -> Runner.start().toCompletableFuture().join());

        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), CheckpointerFactory.getCheckpointer());
        assertTrue(created.closed);
    }

    @Test
    void stopDoesNotCloseDefaultInstalledOutsideRunnerConfig() {
        CloseTrackingCheckpointer explicit = new CloseTrackingCheckpointer();
        CheckpointerFactory.register("unit-runner-external-default", conf -> explicit);
        CheckpointerFactory.installDefaultCheckpointer(
                new CheckpointerConfig("unit-runner-external-default", Map.of()));
        Runner.setConfig(RunnerConfig.DEFAULT_RUNNER_CONFIG.copy());

        assertTrue(Runner.start().toCompletableFuture().join());
        assertTrue(Runner.stop().toCompletableFuture().join());

        assertSame(explicit, CheckpointerFactory.getCheckpointer());
        assertFalse(explicit.closed);
    }

    @Test
    void runWorkflowResolvesRegisteredWorkflowById() {
        WorkflowCard card = new WorkflowCard("runner-test-workflow", "runner-test-workflow", "", "", null);
        DirectWorkflow workflow = new DirectWorkflow(card);
        Runner.getResourceMgr().addWorkflow(card, () -> workflow);

        Object result = Runner.runWorkflow(
                "runner-test-workflow",
                Map.of("query", "query workflow"),
                "workflow-session"
        ).toCompletableFuture().join();

        assertTrue(result instanceof WorkflowOutput);
        WorkflowOutput output = (WorkflowOutput) result;
        assertEquals(WorkflowExecutionState.COMPLETED, output.getState());
        assertEquals(Map.of("query", "query workflow"), output.getResult());
        assertNotNull(workflow.lastSession);
        Runner.getResourceMgr().removeWorkflow("runner-test-workflow");
    }

    @Test
    void runToolInvokesDecoratedLocalFunction() throws Exception {
        ToolCard addCard = ToolCard.builder()
                .id("add")
                .name("add")
                .description("加法")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "a", Map.of("description", "加数", "type", "number"),
                                "b", Map.of("description", "被加数", "type", "number")),
                        "required", List.of("a", "b")))
                .build();
        LocalFunction addFunction = ToolDecorator.tool(inputs ->
                        ((Number) inputs.get("a")).intValue() + ((Number) inputs.get("b")).intValue(),
                ToolDecorator.Options.builder().card(addCard).build());

        Object result = addFunction.invoke(Map.of("a", 1, "b", 2));

        assertEquals(3, result);
    }

    @Test
    void runAgentCreatesDefaultSessionAndPostsRun() {
        RecordingAgent agent = new RecordingAgent();

        Object result = Runner.runAgent(agent, Map.of("query", "hello")).toCompletableFuture().join();

        assertEquals(Map.of("query", "hello"), result);
        assertEquals("default_session", agent.lastSession.getSessionId());
        assertNotNull(agent.lastSession.streamIterator());
    }

    @Test
    void runAgentPassesThePreparedSessionToDeepAgent() {
        RecordingDeepAgent agent = new RecordingDeepAgent();

        Object result = Runner.runAgent(agent, Map.of("query", "hello"), null, null, null)
                .toCompletableFuture().join();

        assertEquals(Map.of("query", "hello"), result);
        assertNotNull(agent.lastSession);
        assertEquals("default_session", agent.lastSession.getSessionId());
    }

    @Test
    void streamAgentPassesThePreparedSessionToDeepAgent() {
        RecordingDeepAgent agent = new RecordingDeepAgent();

        Iterator<Object> stream = Runner.runAgentStreaming(agent, Map.of("query", "hello"), null, null,
                List.of(StreamMode.OUTPUT), null).toCompletableFuture().join();

        assertTrue(stream.hasNext());
        assertEquals("stream-output", stream.next());
        assertFalse(stream.hasNext());
        assertNotNull(agent.lastSession);
        assertEquals("default_session", agent.lastSession.getSessionId());
    }

    @Test
    void runAgentTeamRejectsUnknownTeamName() {
        assertThrows(RuntimeException.class, () -> Runner.runAgentTeam(
                "no-such-team",
                Map.of("query", "invoke"),
                false,
                false,
                "invoke-session",
                null,
                null
        ).toCompletableFuture().join());
    }

    @Test
    void runAgentTeamMemberPathRejectsNonTeamAgent() {
        assertThrows(RuntimeException.class, () -> Runner.runAgentTeam(
                "not-a-team-agent",
                Map.of("query", "member"),
                false,
                true,
                "member-session",
                null,
                null
        ).toCompletableFuture().join());
    }

    @Test
    void runAgentTeamBaseTrueAcceptsBaseTeamInstance() {
        RecordingBaseTeam team = new RecordingBaseTeam();

        Object result = Runner.runAgentTeam(
                team,
                Map.of("payload", "base"),
                true,
                false,
                "base-session",
                null,
                null
        ).toCompletableFuture().join();

        assertEquals("base:base-session:{payload=base}", result);
        assertEquals(1, team.invokeCalls);
        AgentGroupSession session = assertInstanceOf(AgentGroupSession.class, team.lastSession);
        assertEquals("base-team", session.getTeamId());
    }

    @Test
    void teamSessionForwardsChildStreamOutputWithSourceTags() {
        AgentTeamSession teamSession = AgentTeamSession.createAgentTeamSession(
                "stream_team_session",
                null,
                "stream_team"
        );
        teamSession.preRun(Map.of("inputs", Map.of("query", "hello")));

        AgentSession childSession = teamSession.createAgentSession(
                new AgentCard("worker_a", "worker", "worker"),
                "worker_a"
        );
        childSession.preRun(Map.of("inputs", Map.of("payload", "child")));
        childSession.writeStream(Map.of("kind", "agent"));
        childSession.postRun();

        teamSession.writeStream(Map.of("kind", "team"));
        teamSession.postRun();

        List<Object> chunks = drain(teamSession.streamIterator());

        assertTrue(chunks.stream().map(RunnerTest::payloadMap).anyMatch(payload ->
                "worker_a".equals(payload.get("source_agent_id"))
                        && "stream_team".equals(payload.get("source_team_id"))));
        assertTrue(chunks.stream().map(RunnerTest::payloadMap).anyMatch(payload ->
                "team".equals(payload.get("kind"))
                        && "stream_team".equals(payload.get("source_team_id"))));
    }

    /**
     * Mirrors Python's direct workflow object accepted by {@code Runner.run_workflow} in
     * {@code openjiuwen/core/runner/runner.py}.
     */
    private static final class DirectWorkflow extends Workflow {
        private Object lastSession;

        private DirectWorkflow(WorkflowCard card) {
            super(card);
        }

        @Override
        public WorkflowOutput invoke(Object inputs, Object session, ModelContext context) {
            lastSession = session;
            return new WorkflowOutput(inputs, WorkflowExecutionState.COMPLETED);
        }
    }

    /**
     * Mirrors Python's {@code BaseAgent} object branch in
     * {@code openjiuwen/core/runner/runner.py}.
     */
    private static final class RecordingAgent extends BaseAgent {
        private AgentSessionApi lastSession;

        private RecordingAgent() {
            super(new AgentCard("runner-test-agent", "runner-test-agent", ""));
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            lastSession = session;
            return inputs;
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            lastSession = session;
            return List.of(inputs).iterator();
        }
    }

    /** Records whether Runner uses DeepAgent's session-aware invoke and stream overloads. */
    private static final class RecordingDeepAgent extends DeepAgent {
        private AgentSessionApi lastSession;

        private RecordingDeepAgent() {
            super(new AgentCard("runner-deep-agent", "runner-deep-agent", ""));
        }

        @Override
        public Map<String, Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            lastSession = session;
            session.markPostRunDone();
            return inputs;
        }

        @Override
        public Map<String, Object> invoke(Map<String, Object> inputs) {
            throw new AssertionError("Runner must use the session-aware DeepAgent invoke overload");
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session,
                                       List<StreamMode> streamModes) {
            lastSession = session;
            session.markPostRunDone();
            return List.<Object>of("stream-output").iterator();
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, List<StreamMode> streamModes) {
            throw new AssertionError("Runner must use the session-aware DeepAgent stream overload");
        }
    }

    private static final class CloseTrackingCheckpointer extends Checkpointer implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }

    /**
     * Mirrors Python's {@code BaseTeam} branch behind Runner.run_agent_team(base=True) in
     * {@code openjiuwen/core/runner/team_runner.py}.
     */
    private static final class RecordingBaseTeam extends BaseTeam {
        private int invokeCalls;
        private AgentSessionApi lastSession;

        private RecordingBaseTeam() {
            super(new TeamCard("base-team", "base-team", ""), new TeamConfig());
        }

        @Override
        public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
            invokeCalls += 1;
            lastSession = session;
            return CompletableFuture.completedFuture("base:" + session.getSessionId() + ":" + message);
        }

        @Override
        public Stream<Object> stream(Object message, AgentSessionApi session) {
            lastSession = session;
            return Stream.of("stream:" + session.getSessionId() + ":" + message);
        }
    }

    /**
     * Mirrors Python's counting BaseTeam/worker pair in
     * {@code tests/unit_tests/multi_agent/team/test_team_runner_session.py}.
     */
    private static final class StatefulTeam extends BaseTeam {
        private final AgentCard workerCard;

        private StatefulTeam(String teamId) {
            super(new TeamCard(teamId, teamId, "test team"), new TeamConfig());
            this.workerCard = new AgentCard(teamId + "_worker", "worker", "worker");
        }

        @Override
        public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
            int teamCount = intState(session.getState("team_count")) + 1;
            session.updateState(Map.of("team_count", teamCount));

            AgentTeamSession teamSession = unwrapTeamSession(session);
            AgentSession workerSession = teamSession.createAgentSession(workerCard, workerCard.getId());
            workerSession.preRun(Map.of("inputs", message));
            int workerCount = intState(workerSession.getState("worker_count")) + 1;
            workerSession.updateState(Map.of("worker_count", workerCount));
            workerSession.writeStream(Map.of("kind", "agent", "count", workerCount));
            workerSession.postRun();

            return CompletableFuture.completedFuture(Map.of(
                    "team_count", teamCount,
                    "worker_count", workerCount
            ));
        }

        @Override
        public Stream<Object> stream(Object message, AgentSessionApi session) {
            return Stream.of(invoke(message, session).toCompletableFuture().join());
        }
    }

    private static OutputSchema teamChunk(String payload) {
        return new OutputSchema("message", 0, payload);
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> values = new ArrayList<>();
        while (iterator.hasNext()) {
            values.add(iterator.next());
        }
        return values;
    }

    private static int intState(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static AgentTeamSession unwrapTeamSession(AgentSessionApi session) {
        if (session instanceof AgentTeamSession teamSession) {
            return teamSession;
        }
        try {
            Field field = session.getClass().getDeclaredField("session");
            field.setAccessible(true);
            return (AgentTeamSession) field.get(session);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Map<?, ?> payloadMap(Object chunk) {
        OutputSchema output = assertInstanceOf(OutputSchema.class, chunk);
        return assertInstanceOf(Map.class, output.getPayload());
    }
}
