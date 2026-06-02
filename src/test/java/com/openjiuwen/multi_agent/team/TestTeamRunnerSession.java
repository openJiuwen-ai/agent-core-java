/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.CommunicableAgent;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.core.session.internal.AgentTeamSession;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Runner-owned BaseTeam session recovery and child stream forwarding.
 *
 * <p>Mirrors Python's {@code test_team_runner_session.py} in
 * {@code tests.unit_tests.multi_agent.team}.</p>
 */
class TestTeamRunnerSession {

    private Checkpointer originalCheckpointer;
    private InMemoryCheckpointer isolatedCheckpointer;

    @BeforeEach
    void setUp() {
        originalCheckpointer = CheckpointerFactory.getCheckpointer();
        isolatedCheckpointer = new InMemoryCheckpointer();
        CheckpointerFactory.setDefaultCheckpointer(isolatedCheckpointer);
    }

    @AfterEach
    void tearDown() {
        CheckpointerFactory.setDefaultCheckpointer(originalCheckpointer);
    }

    static class CountingWorker extends BaseAgent implements CommunicableAgent {
        CountingWorker(AgentCard card) {
            super(card);
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            int count = ((Number) java.util.Optional.ofNullable(session.getState("worker_count")).orElse(0)).intValue()
                    + 1;
            session.updateState(Map.of("worker_count", count));
            session.writeStream(Map.of("kind", "agent", "count", count));
            return Map.of("worker_count", count);
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            return List.of(invoke(inputs, session)).iterator();
        }
    }

    static class CountingTeam extends BaseTeam {
        private final AgentCard workerCard;

        CountingTeam(String teamId) {
            super(
                    TeamCard.builder().id(teamId).name(teamId).description("test team").build(),
                    TeamConfig.builder().maxAgents(4).build()
            );
            this.workerCard = AgentCard.builder()
                    .id(teamId + "_worker")
                    .name("worker")
                    .description("worker")
                    .build();
            addAgent(workerCard, () -> new CountingWorker(workerCard));
        }

        @Override
        public CompletableFuture<Object> invoke(Object input) {
            return invoke(input, null);
        }

        @Override
        public CompletableFuture<Object> invoke(Object input, Session session) {
            int teamCount = ((Number) java.util.Optional.ofNullable(session.getState("team_count")).orElse(0))
                    .intValue() + 1;
            session.updateState(Map.of("team_count", teamCount));
            runtime.start();
            return runtime.send(input, workerCard.getId(), workerCard.getId(), session.getSessionId())
                    .thenApply(workerResult -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> workerMap = (Map<String, Object>) workerResult;
                        return Map.of(
                                "team_count", teamCount,
                                "worker_count", workerMap.get("worker_count")
                        );
                    });
        }

        @Override
        public Stream<Object> stream(Object input) {
            return Stream.of(invoke(input).join());
        }

        @Override
        public Stream<Object> stream(Object input, Session session) {
            return Stream.of(invoke(input, session).join());
        }
    }

    @Test
    void testRunnerRunAgentTeamRecoversTeamAndChildState() {
        Runner.start();
        CountingTeam team = new CountingTeam("team_" + UUID.randomUUID().toString().replace("-", ""));
        String sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");

        @SuppressWarnings("unchecked")
        Map<String, Object> result1 =
                (Map<String, Object>) Runner.runAgentTeam(team, Map.of("payload", "first"), sessionId);
        @SuppressWarnings("unchecked")
        Map<String, Object> result2 =
                (Map<String, Object>) Runner.runAgentTeam(team, Map.of("payload", "second"), sessionId);

        assertEquals(1, result1.get("team_count"));
        assertEquals(1, result1.get("worker_count"));
        assertEquals(2, result2.get("team_count"));
        assertEquals(2, result2.get("worker_count"));

        team.getRuntime().stop();
        isolatedCheckpointer.release(sessionId);
    }

    @Test
    void testTeamSessionForwardsChildStreamOutputWithSourceTags() {
        String sessionId = "stream_" + UUID.randomUUID().toString().replace("-", "");
        AgentTeamSession teamSession = new AgentTeamSession(sessionId, "stream_team");
        teamSession.preRun(Map.of("query", "hello"));

        AgentSessionApi childSession = teamSession.createAgentSession("worker_a");
        childSession.preRun(Map.of("payload", "child"));
        childSession.writeStream(Map.of("kind", "agent"));
        childSession.postRun();

        teamSession.writeStream(Map.of("kind", "team"));
        teamSession.postRun();

        List<Object> chunks = streamToList(teamSession.streamIterator());

        assertTrue(chunks.stream().anyMatch(chunk -> Objects.equals(payload(chunk).get("source_agent_id"), "worker_a")
                && Objects.equals(payload(chunk).get("source_team_id"), "stream_team")));
        assertTrue(chunks.stream().anyMatch(chunk -> Objects.equals(payload(chunk).get("kind"), "team")
                && Objects.equals(payload(chunk).get("source_team_id"), "stream_team")));

        isolatedCheckpointer.release(sessionId);
    }

    private static List<Object> streamToList(Iterator<Object> iterator) {
        java.util.ArrayList<Object> result = new java.util.ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Object chunk) {
        OutputSchema output = assertInstanceOf(OutputSchema.class, chunk);
        return (Map<String, Object>) output.getPayload();
    }
}
