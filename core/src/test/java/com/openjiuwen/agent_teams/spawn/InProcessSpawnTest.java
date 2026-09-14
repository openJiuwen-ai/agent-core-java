/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.SpawnManager;
import com.openjiuwen.agent_teams.agent.SpawnManager.SpawnKind;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link InProcessSpawn}.
 *
 * <p>Mirrors Python's {@code inprocess_spawn.py} behavior in
 * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
 */
class InProcessSpawnTest {

    @Test
    void defaultQueryCreatesFallbackCardAndPassesSessionToMemberRunner() {
        TeamAgentSpec spec = minimalSpec();
        StubTeamAgent leader = new StubTeamAgent(spec);
        TeamRuntimeContext ctx = memberContext("team-a", "dev", "write code");
        RecordingMemberRunner runner = new RecordingMemberRunner();

        SpawnManager.InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(
                leader,
                ctx,
                null,
                "session-1",
                runner
        ).toCompletableFuture().join();

        assertThat(handle).isInstanceOf(InProcessSpawn.SpawnedInProcessHandle.class);
        assertThat(((InProcessSpawn.SpawnedInProcessHandle) handle).getProcessId()).isEqualTo("inproc-dev");
        assertThat(runner.query()).isEqualTo(InProcessSpawn.DEFAULT_INITIAL_QUERY);
        assertThat(runner.memberFlag.get()).isTrue();
        assertThat(runner.sessionId.get()).isEqualTo("session-1");
        assertThat(runner.contextSessionId.get()).isEqualTo("session-1");
        assertThat(runner.teammate.get().getCard().getId()).isEqualTo("team-a_dev");
        assertThat(runner.teammate.get().getCard().getName()).isEqualTo("dev");
        assertThat(runner.teammate.get().getCard().getDescription()).isEqualTo("Teammate: write code");
    }

    @Test
    void emptyInitialMessageFallsBackButWhitespaceSessionIsPropagated() {
        TeamAgentSpec spec = minimalSpec();
        StubTeamAgent leader = new StubTeamAgent(spec);
        TeamRuntimeContext ctx = memberContext("team-a", "dev", "");
        RecordingMemberRunner runner = new RecordingMemberRunner();

        InProcessSpawn.inprocessSpawn(
                leader,
                ctx,
                "",
                "   ",
                runner
        ).toCompletableFuture().join();

        assertThat(runner.query()).isEqualTo(InProcessSpawn.DEFAULT_INITIAL_QUERY);
        assertThat(runner.sessionId.get()).isEqualTo("   ");
        assertThat(runner.contextSessionId.get()).isEqualTo("   ");
        assertThat(runner.teammate.get().getCard().getDescription()).isEqualTo("Teammate");
    }

    @Test
    void configuredAgentSpecCardWinsOverFallbackCard() {
        TeamAgentSpec spec = minimalSpec();
        AgentCard configuredCard = new AgentCard("custom-id", "custom-name", "custom-description");
        spec.getAgents().put("teammate", new CardedDeepAgentSpec(configuredCard));
        StubTeamAgent leader = new StubTeamAgent(spec);
        TeamRuntimeContext ctx = memberContext("team-a", "dev", "write code");
        RecordingMemberRunner runner = new RecordingMemberRunner();

        InProcessSpawn.inprocessSpawn(
                leader,
                ctx,
                "hello",
                "session-card",
                runner
        ).toCompletableFuture().join();

        assertThat(runner.teammate.get().getCard()).isSameAs(configuredCard);
    }

    @Test
    void absentSessionKeepsExistingContextAndInitialMessageOverridesDefault() {
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("outer-session");
        try {
            TeamAgentSpec spec = minimalSpec();
            StubTeamAgent leader = new StubTeamAgent(spec);
            TeamRuntimeContext ctx = memberContext(null, "dev", "debug");
            RecordingMemberRunner runner = new RecordingMemberRunner();

            InProcessSpawn.inprocessSpawn(
                    leader,
                    ctx,
                    "start now",
                    null,
                    runner
            ).toCompletableFuture().join();

            assertThat(runner.query()).isEqualTo("start now");
            assertThat(runner.sessionId.get()).isNull();
            assertThat(runner.contextSessionId.get()).isEqualTo("outer-session");
            assertThat(AgentTeamsContext.getSessionId()).isEqualTo("outer-session");
            assertThat(runner.teammate.get().getCard().getId()).isEqualTo("team-a_dev");
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }
    }

    @Test
    void spawnBridgeAcceptsInprocessRequest() {
        TeamAgentSpec spec = minimalSpec();
        StubTeamAgent leader = new StubTeamAgent(spec);
        TeamRuntimeContext ctx = memberContext("team-a", "dev", "write code");
        SpawnManager.SpawnRequest request = new SpawnManager.SpawnRequest(
                SpawnKind.INPROCESS,
                ctx,
                "hello",
                "session-bridge",
                null,
                null,
                Map.of(),
                leader
        );

        SpawnManager.SpawnHandle handle = InProcessSpawn.spawn(request).toCompletableFuture().join();

        assertThat(handle).isInstanceOf(SpawnManager.InProcessSpawnHandle.class);
        assertThat(((InProcessSpawn.SpawnedInProcessHandle) handle).getProcessId()).isEqualTo("inproc-dev");
    }

    @Test
    void handleTracksLivenessAndForceKillCancelsTask() {
        TeamAgentSpec spec = minimalSpec();
        StubTeamAgent leader = new StubTeamAgent(spec);
        TeamRuntimeContext ctx = memberContext("team-a", "dev", "write code");
        RecordingMemberRunner runner = new RecordingMemberRunner();

        InProcessSpawn.SpawnedInProcessHandle handle = (InProcessSpawn.SpawnedInProcessHandle)
                InProcessSpawn.inprocessSpawn(
                        leader,
                        ctx,
                        "work now",
                        null,
                        runner
                ).toCompletableFuture().join();

        assertThat(handle.isAlive()).isTrue();

        runner.runFuture.complete(null);
        handle.getTask().join();
        assertThat(handle.isAlive()).isFalse();

        RecordingMemberRunner secondRunner = new RecordingMemberRunner();
        InProcessSpawn.SpawnedInProcessHandle secondHandle = (InProcessSpawn.SpawnedInProcessHandle)
                InProcessSpawn.inprocessSpawn(
                        leader,
                        ctx,
                        "work again",
                        null,
                        secondRunner
                ).toCompletableFuture().join();
        secondHandle.forceKill().toCompletableFuture().join();

        assertThat(secondHandle.isAlive()).isFalse();
        assertThat(secondHandle.getTask()).isCancelled();
    }

    private static TeamAgentSpec minimalSpec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setTeamName("team-a");
        spec.getAgents().put("leader", new DeepAgentSpec());
        spec.getAgents().put("teammate", new DeepAgentSpec());
        return spec;
    }

    private static TeamRuntimeContext memberContext(String teamName, String memberName, String persona) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName(memberName);
        ctx.setPersona(persona);
        if (teamName != null) {
            ctx.setTeamSpec(new TeamSpec(teamName, "Team A", "leader"));
        }
        return ctx;
    }

    /**
     * Agent-spec test double with a Java card accessor matching Python's
     * {@code agent_spec.card} branch.
     *
     * <p>Mirrors Python's {@code DeepAgentSpec.card} in
     * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
     */
    private static final class CardedDeepAgentSpec extends DeepAgentSpec {
        private final AgentCard card;

        private CardedDeepAgentSpec(AgentCard card) {
            this.card = card;
        }

        public AgentCard getCard() {
            return card;
        }
    }

    /**
     * Minimal leader test double that exposes the owning team spec.
     *
     * <p>Mirrors Python's leader {@code TeamAgent} input to
     * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
     */
    private static final class StubTeamAgent extends TeamAgent {
        private final TeamAgentSpec spec;

        private StubTeamAgent(TeamAgentSpec spec) {
            super(new AgentCard("leader-card", "leader", "Leader"));
            this.spec = spec;
        }

        @Override
        public TeamAgentSpec getSpec() {
            return spec;
        }
    }

    /**
     * Captures the in-process member invocation without running a real team loop.
     *
     * <p>Mirrors Python's {@code Runner.run_agent_team} call in
     * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
     */
    private static final class RecordingMemberRunner implements InProcessSpawn.MemberRunner {
        private final CompletableFuture<Void> runFuture = new CompletableFuture<>();
        private final AtomicReference<TeamAgent> teammate = new AtomicReference<>();
        private final AtomicReference<Map<String, Object>> inputs = new AtomicReference<>();
        private final AtomicBoolean memberFlag = new AtomicBoolean(false);
        private final AtomicReference<String> sessionId = new AtomicReference<>();
        private final AtomicReference<String> contextSessionId = new AtomicReference<>();

        @Override
        public CompletionStage<Void> run(
                TeamAgent teammate,
                Map<String, Object> inputs,
                boolean member,
                String sessionId
        ) {
            this.teammate.set(teammate);
            this.inputs.set(inputs);
            this.memberFlag.set(member);
            this.sessionId.set(sessionId);
            this.contextSessionId.set(AgentTeamsContext.getSessionId());
            return runFuture;
        }

        private String query() {
            return String.valueOf(inputs.get().get("query"));
        }
    }
}
