/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.EventListener;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamAgent}.
 *
 * <p>Mirrors Python's {@code TeamAgent} in
 * {@code openjiuwen/agent_teams/agent/team_agent.py}.</p>
 */
class TeamAgentTest {

    @Test
    void configureWiresManagersStateAndCoordination() {
        RuntimeStub runtime = new RuntimeStub();
        TeamAgent agent = new TeamAgent(new AgentCard("card", "Card", "desc"));

        agent.configure(spec(), context(TeamRole.LEADER, "leader"), runtime);

        assertThat(agent.getHarness()).isSameAs(runtime);
        assertThat(agent.getBlueprint().getMemberName()).isEqualTo("leader");
        assertThat(agent.getRole()).isEqualTo(TeamRole.LEADER);
        assertThat(agent.getTeamName()).isEqualTo("team-a");
        assertThat(agent.getTeamMember()).isNotNull();
        assertThat(agent.getCoordinationLoop()).isNotNull();
        assertThat(agent.getSessionManager()).isNotNull();
        assertThat(agent.getSpawnManager()).isNotNull();
        assertThat(agent.getStreamController()).isNotNull();
    }

    @Test
    void deliverInputSteersRunningAgentAndQueuesInFlightTransition() {
        RuntimeStub runtime = new RuntimeStub();
        TeamAgent agent = configured(runtime);

        setField(agent.getStreamController(), "streamingActive", true);
        agent.deliverInput("steer-input", true).toCompletableFuture().join();

        assertThat(runtime.steered).containsExactly("steer-input");

        setField(agent.getStreamController(), "streamingActive", false);
        setField(agent.getStreamController(), "agentTask", new CompletableFuture<Void>());
        agent.deliverInput("queued-input", true).toCompletableFuture().join();

        assertThat(agent.getStreamController().getPendingInputs()).containsExactly("queued-input");
    }

    @Test
    void eventListenersAreAddedRemovedAndFilteredForCoordination() {
        TeamAgent agent = configured(new RuntimeStub());
        List<String> seen = new ArrayList<>();
        EventListener listener = event -> {
            seen.add(event.getEventType());
            return CompletableFuture.completedFuture(null);
        };

        agent.addEventListener(listener);

        assertThat(agent.getState().getEventListeners()).contains(listener);
        assertThat(agent.getEventListeners()).containsExactly(listener);

        agent.removeEventListener(listener);

        assertThat(agent.getEventListeners()).isEmpty();
    }

    @Test
    void fromSpawnPayloadRestoresAgentSpecAndRuntimeContext() {
        TeamAgent configured = configured(new RuntimeStub());
        TeamRuntimeContext teammate = context(TeamRole.TEAMMATE, "dev");
        SpawnAgentConfig spawnConfig = configured.buildSpawnConfig(teammate);

        TeamAgent restored = TeamAgent.fromSpawnPayload(spawnConfig.getPayload()).toCompletableFuture().join();

        assertThat(restored.getRuntimeContext().getRole()).isEqualTo(TeamRole.TEAMMATE);
        assertThat(restored.getRuntimeContext().getMemberName()).isEqualTo("dev");
        assertThat(restored.getSpec().getTeamName()).isEqualTo("team-a");
        assertThat(restored.getBlueprint().getMemberName()).isEqualTo("dev");
    }

    @Test
    void buildHelpersDelegateToConfigurator() {
        TeamAgent agent = configured(new RuntimeStub());

        Map<String, Object> payload = agent.buildSpawnPayload(context(TeamRole.TEAMMATE, "worker"), "hello");

        assertThat(payload).containsEntry("query", "hello");
        assertThat(agent.buildMemberContext(memberSpec()).getMemberName()).isEqualTo("member-a");
        assertThat(agent.buildSpawnConfig(context(TeamRole.TEAMMATE, "worker")).getPayload())
                .containsKeys("spec", "context");
    }

    private static TeamAgent configured(RuntimeStub runtime) {
        TeamAgent agent = new TeamAgent(new AgentCard("card", "Card", "desc"));
        agent.configure(spec(), context(TeamRole.LEADER, "leader"), runtime);
        return agent;
    }

    private static TeamAgentSpec spec() {
        TeamAgentSpec spec = new TeamAgentSpec();
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("en");
        spec.setTeamName("team-a");
        spec.setAgents(Map.of("leader", leader, "teammate", new DeepAgentSpec()));
        return spec;
    }

    private static TeamRuntimeContext context(TeamRole role, String memberName) {
        TeamRuntimeContext context = new TeamRuntimeContext();
        context.setRole(role);
        context.setMemberName(memberName);
        context.setPersona("persona");
        context.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        return context;
    }

    private static AgentConfigurator.TeamMemberSpec memberSpec() {
        AgentConfigurator.TeamMemberSpec memberSpec = new AgentConfigurator.TeamMemberSpec();
        memberSpec.setMemberName("member-a");
        memberSpec.setRoleType(TeamRole.TEAMMATE);
        return memberSpec;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class RuntimeStub implements MemberRuntime {
        private final List<String> steered = new ArrayList<>();

        @Override
        public Iterator<Object> runStreaming(Map<String, Object> inputs, String sessionId) {
            return List.of().iterator();
        }

        @Override
        public CompletionStage<Void> steer(String content) {
            steered.add(content);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> followUp(String content) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> abort() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void initCwdForRound() {
        }

        @Override
        public boolean hasPendingInterrupt() {
            return false;
        }

        @Override
        public boolean isPendingInterruptResumeValid(Object userInput) {
            return true;
        }

        @Override
        public List<Object> findRails(Class<?> railType) {
            return List.of();
        }

        @Override
        public CompletionStage<Void> registerRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterRail(Object rail) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void registerMemberTools(Object memoryManager) {
        }

        @Override
        public CompletionStage<Void> injectMemberMemory(Object memoryManager, String query) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void runAgentCustomizer(AgentCustomizer customizer) {
        }

        @Override
        public Object workspace() {
            return null;
        }

        @Override
        public Object sysOperation() {
            return null;
        }
    }
}
