/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.StreamController;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.spawn.SharedResources;
import com.openjiuwen.agent_teams.tools.TeamDatabase;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.harness.DeepAgentConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_runner_team_runtime.py}.
 */
class RuntimeManagerTest {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private Checkpointer originalCheckpointer;
    private InMemoryCheckpointer isolatedCheckpointer;

    @AfterEach
    void tearDown() {
        if (originalCheckpointer != null) {
            CheckpointerFactory.setDefaultCheckpointer(originalCheckpointer);
        }
        SharedResources.cleanupSharedResources();
    }

    @Test
    void test_runner_run_agent_team_streaming_accepts_spec_and_emits_runtime_ready() {
        useIsolatedCheckpointer();
        String teamName = teamName("spec_team");
        String sessionId = sessionId("team_spec");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");

        List<Object> chunks = collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "hello"), sessionId));

        assertEquals(2, chunks.size());
        OutputSchema ready = assertInstanceOf(OutputSchema.class, chunks.get(0));
        Map<?, ?> readyPayload = assertInstanceOf(Map.class, ready.getPayload());
        assertEquals("team.runtime_ready", readyPayload.get("event_type"));
        assertEquals("create", readyPayload.get("activation_kind"));
        assertEquals(teamName, readyPayload.get("team_name"));
        assertEquals(sessionId, readyPayload.get("session_id"));
        OutputSchema body = assertInstanceOf(OutputSchema.class, chunks.get(1));
        assertEquals("team.chunk", ((Map<?, ?>) body.getPayload()).get("event_type"));

        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void test_runner_team_runtime_manager_resumes_new_session_and_recovers_history() {
        useIsolatedCheckpointer();
        RuntimeManager manager = new RuntimeManager();
        String teamName = teamName("persistent_team");
        String sessionOne = sessionId("resume_one");
        String sessionTwo = sessionId("resume_two");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");

        RuntimeManager.TeamRuntimeActivation first =
                manager.activate(spec, sessionOne, Map.of("query", "first")).join();
        spec.agent.invoke(Map.of("query", "first"), first.getSession());
        postRun(first.getSession());

        RuntimeManager.TeamRuntimeActivation second =
                manager.activate(spec, sessionTwo, Map.of("query", "second")).join();
        RuntimeManager.TeamRuntimeActivation third =
                manager.activate(spec, sessionOne, Map.of("query", "third")).join();

        assertEquals("create", first.getActivationKind());
        assertEquals("resume", second.getActivationKind());
        assertEquals("recover", third.getActivationKind());
        assertEquals(List.of(sessionTwo, sessionOne), spec.agent.resumeCalls);
        assertTrue(spec.agent.stopCalls >= 1);

        isolatedCheckpointer.release(sessionOne);
        isolatedCheckpointer.release(sessionTwo);
    }

    @Test
    void test_runner_same_session_streaming_short_circuits_and_skips_second_stream() {
        useIsolatedCheckpointer();
        String sessionId = sessionId("same");
        RecordingSpec spec = new RecordingSpec(teamName("same_team"), "active.chunk");

        List<Object> first = collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "first"), sessionId));
        List<Object> second = collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "same-session"), sessionId));

        assertEquals("create", payload(first.get(0)).get("activation_kind"));
        assertTrue(second.isEmpty());
        assertEquals(1, spec.agent.streamCalls);
        assertEquals(1, spec.agent.invokeCalls);
        assertTrue(spec.agent.resumeCalls.isEmpty());

        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void test_runner_same_session_after_pause_resumes_paused_runtime() {
        useIsolatedCheckpointer();
        String teamName = teamName("paused_team");
        String sessionId = sessionId("paused");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");

        List<Object> first = collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "first"), sessionId));
        assertTrue(Runner.pauseAgentTeam(teamName, sessionId));
        List<Object> second = collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "resume paused"), sessionId));

        assertEquals("create", payload(first.get(0)).get("activation_kind"));
        assertEquals("resume_paused", payload(second.get(0)).get("activation_kind"));
        assertEquals("team.chunk", payload(second.get(1)).get("event_type"));
        assertEquals(1, spec.agent.pauseCalls);
        assertEquals(2, spec.agent.streamCalls);
        assertEquals(2, spec.agent.invokeCalls);

        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void test_runner_paused_same_session_resume_uses_same_prepared_session() {
        useIsolatedCheckpointer();
        String teamName = teamName("paused_same_team");
        String sessionId = sessionId("paused_same");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");

        List<Object> initial = collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "first"), sessionId));
        assertEquals("create", payload(initial.get(0)).get("activation_kind"));
        assertTrue(Runner.pauseAgentTeam(teamName, sessionId));

        Object resumed = Runner.runAgentTeam(spec, Map.of("query", "resume invoke"), sessionId);

        assertEquals(sessionId, ((Map<?, ?>) resumed).get("session_id"));
        assertEquals(2, spec.agent.invokeCalls);
        assertEquals(1, spec.agent.pauseCalls);

        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void test_runner_existing_session_without_team_name_short_circuits() {
        useIsolatedCheckpointer();
        String sessionId = sessionId("invalid_missing");
        seedSession(sessionId, Map.of("spec", Map.of("team_name", "invalid_team")));
        RecordingSpec spec = new RecordingSpec(teamName("invalid_team"), "team.chunk");

        Object result = Runner.runAgentTeam(spec, Map.of("query", "should short circuit"), sessionId);

        assertNull(result);
        assertEquals(0, spec.agent.invokeCalls);
        assertEquals(0, spec.agent.streamCalls);

        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void test_runner_existing_session_with_wrong_team_name_short_circuits() {
        useIsolatedCheckpointer();
        String sessionId = sessionId("invalid_mismatch");
        String targetTeam = teamName("target_team");
        seedSession(sessionId, Map.of(
                "team_name", "other_team",
                "spec", Map.of("team_name", "other_team")
        ));
        RecordingSpec spec = new RecordingSpec(targetTeam, "team.chunk");

        List<Object> chunks = collect(Runner.runAgentTeamStreaming(
                spec,
                Map.of("query", "should short circuit"),
                sessionId
        ));

        assertTrue(chunks.isEmpty());
        assertEquals(0, spec.agent.invokeCalls);
        assertEquals(0, spec.agent.streamCalls);

        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void test_runner_interact_pause_and_delete_agent_team_route_through_team_runtime_manager() {
        useIsolatedCheckpointer();
        String teamName = teamName("delete_team");
        String sessionId = sessionId("delete");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");
        createSharedTeam(teamName);

        collect(Runner.runAgentTeamStreaming(spec, Map.of("query", "hello"), sessionId));

        assertTrue(Runner.interactAgentTeam("follow-up", teamName, sessionId));
        assertEquals(List.of("follow-up"), spec.agent.interactions);
        assertTrue(Runner.pauseAgentTeam(teamName, sessionId));
        assertEquals(1, spec.agent.pauseCalls);
        assertTrue(Runner.deleteAgentTeam(teamName, List.of(sessionId)));
        assertEquals(1, spec.agent.stopCalls);
        assertFalse(isolatedCheckpointer.sessionExists(sessionId));
    }

    @Test
    void test_team_agent_cancelled_round_does_not_restart_follow_up() {
        List<ExecutionStatus> statuses = new ArrayList<>();
        StreamController controller = new StreamController(
                "cancelled_team",
                ignored -> {
                    throw new CancellationException("cancelled");
                },
                statuses::add
        );

        assertThrows(CancellationException.class, () -> controller.executeRound("cancelled"));
        assertTrue(controller.drainStreamQueue().isEmpty());
        assertEquals(List.of(ExecutionStatus.RUNNING), statuses);
    }

    @Test
    void test_team_agent_resume_for_new_session_rebinds_only_live_teammates() {
        useIsolatedCheckpointer();
        TeamAgent agent = persistentAgent("resume_live");
        prepareRecoverableMembers(agent);
        String sessionId = sessionId("session");
        AgentSessionApi newSession = AgentSessionApi.create(sessionId, Map.of(), null);

        agent.resumeForNewSession(newSession);

        assertEquals(sessionId, agent.getSessionManager().getSessionId());
        assertEquals(MemberStatus.READY, agent.getTeamBackend().getMember("worker_busy").getStatus());
        assertEquals(MemberStatus.READY, agent.getTeamBackend().getMember("worker_ready").getStatus());
        assertEquals(MemberStatus.UNSTARTED, agent.getTeamBackend().getMember("worker_idle_no_handle").getStatus());
        assertEquals(MemberStatus.SHUTDOWN, agent.getTeamBackend().getMember("worker_shutdown").getStatus());
        assertTrue(agent.getTeamBackend().getMemberRuntime("worker_busy") != null);
        assertTrue(agent.getTeamBackend().getMemberRuntime("worker_ready") != null);
    }

    @Test
    void test_team_agent_recover_for_existing_session_rebinds_live_teammates() {
        useIsolatedCheckpointer();
        TeamAgent agent = persistentAgent("recover_live");
        prepareRecoverableMembers(agent);
        String sessionId = sessionId("recover");
        AgentSessionApi existingSession = AgentSessionApi.create(sessionId, Map.of(), null);
        existingSession.updateState(Map.of(
                "agent_team_recoverable_members",
                List.of(
                        Map.of("member_name", "worker_busy", "status", "BUSY"),
                        Map.of("member_name", "worker_ready", "status", "READY")
                )
        ));

        agent.recoverForExistingSession(existingSession);

        assertEquals(sessionId, agent.getSessionManager().getSessionId());
        assertEquals(MemberStatus.BUSY, agent.getTeamBackend().getMember("worker_busy").getStatus());
        assertEquals(MemberStatus.READY, agent.getTeamBackend().getMember("worker_ready").getStatus());
        assertTrue(agent.getTeamBackend().getMemberSession("worker_busy") != null);
        assertTrue(agent.getTeamBackend().getMemberSession("worker_ready") != null);
    }

    @Test
    void test_team_agent_recover_from_session_restores_session_id() {
        String sessionId = sessionId("recover_state");
        AgentSessionApi session = AgentSessionApi.create(sessionId, Map.of(), null);
        session.updateState(Map.of(
                "spec",
                Map.of("team_name", teamName("persistent_team"), "agents", Map.of("leader", Map.of())),
                "context",
                Map.of(
                        "role", "leader",
                        "member_name", "leader",
                        "persona", "leader",
                        "team_spec", Map.of(
                                "team_name", teamName("persistent_team"),
                                "display_name", "persistent_team",
                                "leader_member_name", "leader"
                        ),
                        "messager_config", Map.of(),
                        "db_config", Map.of()
                )
        ));

        TeamAgent agent = TeamAgent.recoverFromSession(session);

        assertEquals(sessionId, agent.getSessionManager().getSessionId());
    }

    @Test
    void test_team_session_forwards_child_stream_output_with_source_tags() {
        String sessionId = sessionId("stream");
        AgentTeamSession teamSession = new AgentTeamSession(sessionId, "stream_team");
        teamSession.preRun(Map.of("query", "hello"));

        AgentSessionApi childSession = teamSession.createAgentSession("worker_a");
        childSession.writeStream(Map.of("kind", "agent"));
        teamSession.writeStream(Map.of("kind", "team"));
        teamSession.postRun();

        List<Object> chunks = collect(teamSession.streamIterator());

        assertTrue(chunks.stream().map(RuntimeManagerTest::streamPayload).anyMatch(item ->
                "worker_a".equals(item.get("source_agent_id"))
                        && "stream_team".equals(item.get("source_team_id"))));
        assertTrue(chunks.stream().map(RuntimeManagerTest::streamPayload).anyMatch(item ->
                "team".equals(item.get("kind"))
                        && "stream_team".equals(item.get("source_team_id"))));
    }

    @Test
    void test_release_session_drops_tables_for_inactive_session() {
        useIsolatedCheckpointer();
        RuntimeManager manager = new RuntimeManager();
        String sessionId = sessionId("release_inactive");
        seedSession(sessionId, Map.of(
                "team_name", "release_team",
                "context", Map.of("db_config", DatabaseConfig.inMemory())
        ));

        manager.releaseSession(sessionId).join();

        assertTrue(manager.getActiveSessionId().isEmpty());
        assertFalse(isolatedCheckpointer.sessionExists(sessionId));
    }

    @Test
    void test_release_session_stops_active_session_coordination() {
        useIsolatedCheckpointer();
        RuntimeManager manager = new RuntimeManager();
        String teamName = teamName("active_release_team");
        String sessionId = sessionId("release_active");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");

        manager.activate(spec, sessionId, Map.of("query", "hello")).join();
        manager.releaseSession(sessionId).join();

        assertEquals(1, spec.agent.stopCalls);
        assertTrue(manager.getActiveSessionId().isEmpty());
        assertTrue(manager.getActiveTeamName().isEmpty());
        assertTrue(manager.getActiveAgent().isEmpty());
    }

    @Test
    void test_release_session_empty_session_id_returns_early() {
        RuntimeManager manager = new RuntimeManager();

        manager.releaseSession("").join();
        manager.releaseSession(null).join();
    }

    @Test
    void test_release_session_handles_missing_context() {
        useIsolatedCheckpointer();
        RuntimeManager manager = new RuntimeManager();
        String sessionId = sessionId("release_no_context");
        seedSession(sessionId, Map.of("team_name", "no_context_team"));

        manager.releaseSession(sessionId).join();

        assertFalse(isolatedCheckpointer.sessionExists(sessionId));
    }

    @Test
    void test_delete_team_fetches_db_config_from_session() {
        useIsolatedCheckpointer();
        RuntimeManager manager = new RuntimeManager();
        String teamName = teamName("delete_config_team");
        String sessionId = sessionId("delete_team_config");
        seedSession(sessionId, Map.of(
                "team_name", teamName,
                "context", Map.of("db_config", DatabaseConfig.inMemory())
        ));
        createSharedTeam(teamName);

        boolean result = manager.deleteTeam(teamName, List.of(sessionId)).join();

        assertTrue(result);
        assertFalse(isolatedCheckpointer.sessionExists(sessionId));
    }

    @Test
    void test_delete_team_stops_active_runtime() {
        useIsolatedCheckpointer();
        RuntimeManager manager = new RuntimeManager();
        String teamName = teamName("delete_active_team");
        String sessionId = sessionId("delete_active");
        RecordingSpec spec = new RecordingSpec(teamName, "team.chunk");
        createSharedTeam(teamName);

        manager.activate(spec, sessionId, Map.of("query", "hello")).join();
        boolean result = manager.deleteTeam(teamName, List.of(sessionId)).join();

        assertTrue(result);
        assertEquals(1, spec.agent.stopCalls);
        assertTrue(manager.getActiveAgent().isEmpty());
        assertTrue(manager.getActiveTeamName().isEmpty());
        assertTrue(manager.getActiveSessionId().isEmpty());
    }

    private void useIsolatedCheckpointer() {
        originalCheckpointer = CheckpointerFactory.getCheckpointer();
        isolatedCheckpointer = new InMemoryCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(isolatedCheckpointer);
    }

    private static TeamAgent persistentAgent(String suffix) {
        TeamAgentSpec spec = createSpec(teamName(suffix));
        spec.setLifecycle(TeamLifecycle.PERSISTENT);
        spec.setPredefinedMembers(List.of(
                member("worker_busy"),
                member("worker_ready"),
                member("worker_idle_no_handle"),
                member("worker_shutdown")
        ));
        TeamAgent agent = spec.build();
        agent.getTeamBackend().getMember("worker_busy").setStatus(MemberStatus.BUSY);
        agent.getTeamBackend().getMember("worker_ready").setStatus(MemberStatus.READY);
        agent.getTeamBackend().getMember("worker_shutdown").setStatus(MemberStatus.SHUTDOWN);
        return agent;
    }

    private static void prepareRecoverableMembers(TeamAgent agent) {
        agent.getTeamBackend().ensureMemberRuntime("worker_busy");
        agent.getTeamBackend().ensureMemberRuntime("worker_ready");
    }

    private static TeamMemberSpec member(String memberName) {
        TeamMemberSpec spec = new TeamMemberSpec();
        spec.setMemberName(memberName);
        spec.setDisplayName(memberName);
        spec.setPersona(memberName + " persona");
        return spec;
    }

    private static TeamAgentSpec createSpec(String teamName) {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName(teamName);

        LeaderSpec leader = new LeaderSpec();
        leader.setMemberName("leader");
        spec.setLeader(leader);

        DeepAgentConfig config = new DeepAgentConfig();
        config.setSystemPrompt("Lead.");
        DeepAgentSpec leaderAgent = new DeepAgentSpec();
        leaderAgent.setConfig(config);
        spec.setAgents(Map.of("leader", leaderAgent));
        return spec;
    }

    private static void seedSession(String sessionId, Map<String, Object> state) {
        AgentSessionApi session = AgentSessionApi.create(sessionId, Map.of(), null);
        session.preRun(Map.of("query", "seed"));
        session.updateState(state);
        session.postRun();
    }

    private static void createSharedTeam(String teamName) {
        TeamDatabase database = SharedResources.getSharedDb(DatabaseConfig.inMemory());
        database.initialize();
        database.getTeamDao().createTeam(teamName, teamName, "leader", "desc", null).join();
    }

    private static List<Object> collect(Iterator<?> iterator) {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    private static Map<?, ?> payload(Object chunk) {
        return (Map<?, ?>) ((OutputSchema) chunk).getPayload();
    }

    private static Map<?, ?> streamPayload(Object chunk) {
        if (chunk instanceof OutputSchema schema) {
            Object payload = schema.getPayload();
            return payload instanceof Map<?, ?> map ? map : Map.of();
        }
        return chunk instanceof Map<?, ?> map ? map : Map.of();
    }

    private static void postRun(Object session) {
        if (session instanceof AgentSessionApi api) {
            api.postRun();
        }
    }

    private static String teamName(String prefix) {
        return prefix + "_" + COUNTER.incrementAndGet();
    }

    private static String sessionId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static final class RecordingSpec {
        private final String teamName;
        private final RecordingAgent agent;

        RecordingSpec(String teamName, String streamLabel) {
            this.teamName = teamName;
            this.agent = new RecordingAgent(teamName, streamLabel);
        }

        public String getTeamName() {
            return teamName;
        }

        public RecordingAgent build() {
            return agent;
        }
    }

    public static final class RecordingAgent {
        private final String teamName;
        private final String streamLabel;
        private final List<String> resumeCalls = new ArrayList<>();
        private final List<String> interactions = new ArrayList<>();
        private int pauseCalls;
        private int stopCalls;
        private int invokeCalls;
        private int streamCalls;

        RecordingAgent(String teamName, String streamLabel) {
            this.teamName = teamName;
            this.streamLabel = streamLabel;
        }

        public Map<String, Object> invoke(Object inputs, Object session) {
            invokeCalls++;
            if (session instanceof Session typedSession) {
                typedSession.updateState(Map.of(
                        "team_name", teamName,
                        "spec", Map.of("team_name", teamName),
                        "context", Map.of(
                                "role", "leader",
                                "member_name", "leader",
                                "team_spec", Map.of("team_name", teamName),
                                "db_config", DatabaseConfig.inMemory()
                        )
                ));
                return Map.of("team_name", teamName, "session_id", typedSession.getSessionId());
            }
            return Map.of("team_name", teamName);
        }

        public Iterator<Object> stream(Object inputs, Object session) {
            streamCalls++;
            Map<String, Object> result = invoke(inputs, session);
            Object sessionId = result.get("session_id");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_type", streamLabel);
            payload.put("session_id", sessionId);
            return List.of((Object) new OutputSchema("message", 1, payload)).iterator();
        }

        public void resumeForNewSession(Object session) {
            resumeCalls.add(sessionIdOf(session));
        }

        public void recoverForExistingSession(Object session) {
            resumeCalls.add(sessionIdOf(session));
            stopCalls++;
        }

        public void pauseCoordination() {
            pauseCalls++;
        }

        public void interact(String message) {
            interactions.add(message);
        }

        public void stopCoordination() {
            stopCalls++;
        }

        private String sessionIdOf(Object session) {
            return session instanceof Session typedSession ? typedSession.getSessionId() : String.valueOf(session);
        }
    }
}
