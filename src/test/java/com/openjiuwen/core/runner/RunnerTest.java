/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.SessionManager;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamOutputSchema;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.ToolDecorator;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.multi_agent.BaseTeam;
import com.openjiuwen.core.multi_agent.TeamConfig;
import com.openjiuwen.core.multi_agent.schema.TeamCard;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerConfig;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        CheckpointerFactory.setDefaultCheckpointer(null);
    }

    @Test
    void singletonResourcesAreStableAndPackageCanResolveRunner() {
        ResourceMgr resourceMgr = Runner.getResourceMgr();
        AsyncCallbackFramework callbackFramework = Runner.getCallbackFramework();

        assertNotNull(resourceMgr);
        assertSame(resourceMgr, Runner.resourceMgr);
        assertNotNull(Runner.getPubsub());
        assertSame(callbackFramework, Runner.callbackFramework);
        assertTrue(RunnerPackage.resolveType("Runner").isPresent());
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
    void stopRestoresAndClosesCheckpointerCreatedFromConfig() {
        Checkpointer previous = new InMemoryCheckpointer();
        CloseTrackingCheckpointer created = new CloseTrackingCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(previous);
        CheckpointerFactory.register("unit-runner-closeable", conf -> created);
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setCheckpointerConfig(new CheckpointerConfig("unit-runner-closeable", Map.of()));
        Runner.setConfig(config);

        assertTrue(Runner.start().toCompletableFuture().join());
        assertSame(created, CheckpointerFactory.getCheckpointer());
        assertTrue(Runner.stop().toCompletableFuture().join());

        assertSame(previous, CheckpointerFactory.getCheckpointer());
        assertTrue(created.closed);
    }

    @Test
    void startFailureRestoresAndClosesCheckpointerCreatedFromConfig() {
        Checkpointer previous = new InMemoryCheckpointer();
        CloseTrackingCheckpointer created = new CloseTrackingCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(previous);
        CheckpointerFactory.register("unit-runner-start-failure", conf -> created);
        RunnerConfig config = RunnerConfig.DEFAULT_RUNNER_CONFIG.copy();
        config.setDistributedMode(true);
        config.setDistributedConfig(DistributedConfig.builder()
                .messageQueueConfig(MessageQueueConfig.builder().type("missing-mq").build())
                .build());
        config.setCheckpointerConfig(new CheckpointerConfig("unit-runner-start-failure", Map.of()));
        Runner.setConfig(config);

        assertThrows(CompletionException.class, () -> Runner.start().toCompletableFuture().join());

        assertSame(previous, CheckpointerFactory.getCheckpointer());
        assertTrue(created.closed);
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
    void runAgentTeamStreamingAcceptsSpecAndEmitsRuntimeReadyChunk() {
        RecordingTeamAgent agent = new RecordingTeamAgent("spec-team", List.of(teamChunk("team.chunk")));
        RecordingTeamSpec spec = new RecordingTeamSpec("spec-team", agent);

        List<Object> chunks = drain(Runner.runAgentTeamStreaming(
                spec,
                Map.of("query", "hello"),
                false,
                false,
                "team-session",
                null,
                null,
                null,
                null
        ).toCompletableFuture().join());

        TeamOutputSchema ready = assertInstanceOf(TeamOutputSchema.class, chunks.get(0));
        Map<?, ?> payload = assertInstanceOf(Map.class, ready.getPayload());
        assertEquals("team.runtime_ready", payload.get("event_type"));
        assertEquals("spec-team", payload.get("team_name"));
        assertEquals("team-session", payload.get("session_id"));
        assertEquals("create", payload.get("activation_kind"));
        assertEquals("team.chunk", ((OutputSchema) chunks.get(1)).getPayload());
        assertEquals(Map.of("query", "hello"), agent.lastInputs);
        SessionManager.AgentTeamSessionView session =
                assertInstanceOf(SessionManager.AgentTeamSessionView.class, agent.lastSession);
        assertEquals("team-session", session.getSessionId());
        assertEquals(1, agent.streamCalls);
    }

    @Test
    void runAgentTeamReturnsLastTeamChunk() {
        RecordingTeamAgent agent = new RecordingTeamAgent(
                "invoke-team",
                List.of(teamChunk("first"), teamChunk("last"))
        );

        Object result = Runner.runAgentTeam(
                new RecordingTeamSpec("invoke-team", agent),
                Map.of("query", "invoke"),
                false,
                false,
                "invoke-session",
                null,
                null
        ).toCompletableFuture().join();

        OutputSchema output = assertInstanceOf(OutputSchema.class, result);
        assertEquals("last", output.getPayload());
    }

    @Test
    void runAgentTeamMemberPathExecutesSpawnedTeamAgent() {
        RecordingTeamAgent agent = new RecordingTeamAgent(
                "member-team",
                List.of(teamChunk("member-result"))
        );

        Object result = Runner.runAgentTeam(
                agent,
                Map.of("query", "member"),
                false,
                true,
                "member-session",
                null,
                null
        ).toCompletableFuture().join();

        OutputSchema output = assertInstanceOf(OutputSchema.class, result);
        assertEquals("member-result", output.getPayload());
        assertEquals(1, agent.streamCalls);
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
    }

    @Test
    void runAgentTeamRecoversTeamAndChildState() {
        Checkpointer original = CheckpointerFactory.getCheckpointer();
        InMemoryCheckpointer checkpointer = new InMemoryCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        String teamId = "team_runner_session_team";
        String sessionId = "team_runner_session_state";
        try {
            StatefulTeam team = new StatefulTeam(teamId);

            Map<?, ?> result1 = assertInstanceOf(Map.class, Runner.runAgentTeam(
                    team,
                    Map.of("payload", "first"),
                    true,
                    false,
                    sessionId,
                    null,
                    null
            ).toCompletableFuture().join());
            Map<?, ?> result2 = assertInstanceOf(Map.class, Runner.runAgentTeam(
                    team,
                    Map.of("payload", "second"),
                    true,
                    false,
                    sessionId,
                    null,
                    null
            ).toCompletableFuture().join());

            assertEquals(1, result1.get("team_count"));
            assertEquals(1, result1.get("worker_count"));
            assertEquals(2, result2.get("team_count"));
            assertEquals(2, result2.get("worker_count"));
        } finally {
            checkpointer.release(sessionId);
            CheckpointerFactory.setDefaultCheckpointer(original);
        }
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
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            lastSession = session;
            return java.util.concurrent.CompletableFuture.completedFuture(inputs);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            lastSession = session;
            return List.of(inputs).iterator();
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
     * Mirrors Python's TeamAgentSpec build hook used by
     * {@code openjiuwen/core/runner/team_runner.py}.
     */
    private static final class RecordingTeamSpec extends TeamAgentSpec {
        private final RecordingTeamAgent agent;

        private RecordingTeamSpec(String teamName, RecordingTeamAgent agent) {
            this.agent = agent;
            setTeamName(teamName);
            setAgents(Map.of("leader", new DeepAgentSpec()));
        }

        @Override
        public TeamAgent build() {
            return agent;
        }
    }

    /**
     * Mirrors Python's {@code TeamAgent} methods consumed by Runner team execution in
     * {@code openjiuwen/core/runner/team_runner.py}.
     */
    private static final class RecordingTeamAgent extends TeamAgent {
        private final List<Object> chunks;
        private Object lastInputs;
        private Object lastSession;
        private int streamCalls;

        private RecordingTeamAgent(String teamName, List<Object> chunks) {
            super(new com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard(
                    teamName + "-leader",
                    "leader",
                    "leader"
            ));
            this.chunks = new ArrayList<>(chunks);
        }

        @Override
        public CompletionStage<List<Object>> stream(Object inputs, Object session, Object streamModes) {
            this.lastInputs = inputs;
            this.lastSession = session;
            this.streamCalls += 1;
            return CompletableFuture.completedFuture(new ArrayList<>(chunks));
        }

        @Override
        public CompletionStage<Void> stopCoordination() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> pauseCoordination() {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Mirrors Python's {@code BaseTeam} branch behind Runner.run_agent_team(base=True) in
     * {@code openjiuwen/core/runner/team_runner.py}.
     */
    private static final class RecordingBaseTeam extends BaseTeam {
        private int invokeCalls;

        private RecordingBaseTeam() {
            super(new TeamCard("base-team", "base-team", ""), new TeamConfig());
        }

        @Override
        public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
            invokeCalls += 1;
            return CompletableFuture.completedFuture("base:" + session.getSessionId() + ":" + message);
        }

        @Override
        public Stream<Object> stream(Object message, AgentSessionApi session) {
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
