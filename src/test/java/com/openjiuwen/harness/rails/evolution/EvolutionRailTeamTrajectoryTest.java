/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agent_evolving.trajectory.FileTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryRegistry;
import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.agent_evolving.trajectory.MemberTrajectorySnapshot;
import com.openjiuwen.agent_evolving.trajectory.ToolCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectorySink;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.core.single_agent.rail.InvokeInputs;
import com.openjiuwen.core.single_agent.rail.ModelCallInputs;
import com.openjiuwen.core.single_agent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.CallbackContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <p>Mirrors Python's {@code TestEvolutionRailTeamTrajectory} in
 * {@code tests/unit_tests/harness/rails/evolution/test_evolution_rail_team_store.py}.</p>
 */
class EvolutionRailTeamTrajectoryTest {

    @TempDir
    Path tempDir;

    @Test
    void afterInvokePublishesMemberSnapshot() {
        InMemoryTrajectoryStore personal = new InMemoryTrajectoryStore();
        CaptureSink sink = new CaptureSink();
        EvolutionRail rail = new EvolutionRail(personal, false);
        rail.setTrajectorySink(sink, "team-a");

        recordToolInvoke(rail);

        assertThat(personal.query(null, null, null)).hasSize(1);
        assertThat(sink.snapshots).hasSize(1);
        MemberTrajectorySnapshot snapshot = sink.snapshots.getFirst();
        assertThat(snapshot.getTeamId()).isEqualTo("team-a");
        assertThat(snapshot.getSessionId()).isEqualTo("test-session");
        assertThat(snapshot.getMemberId()).isEqualTo("test-agent");
        assertThat(snapshot.getMemberRole()).isNull();
        assertThat(snapshot.getTrajectory().getSteps()).hasSize(1);
    }

    @Test
    void afterInvokeWithoutSinkOnlySavesPersonalStore() {
        InMemoryTrajectoryStore personal = new InMemoryTrajectoryStore();
        EvolutionRail rail = new EvolutionRail(personal, false);

        recordToolInvoke(rail);

        assertThat(personal.query(null, null, null)).hasSize(1);
    }

    @Test
    void afterInvokePublishesSnapshotEachTime() {
        InMemoryTrajectoryStore personal = new InMemoryTrajectoryStore();
        CaptureSink sink = new CaptureSink();
        EvolutionRail rail = new EvolutionRail(personal, false);
        rail.setTrajectorySink(sink, "team-a");

        recordToolInvoke(rail);
        recordToolInvoke(rail);

        assertThat(personal.query(null, null, null)).hasSize(2);
        assertThat(sink.snapshots).hasSize(2);
        assertThat(sink.snapshots).allSatisfy(snapshot -> {
            assertThat(snapshot.getTeamId()).isEqualTo("team-a");
            assertThat(snapshot.getMemberId()).isEqualTo("test-agent");
        });
        assertThat(sink.snapshots.stream().map(snapshot -> snapshot.getTrajectory().getSteps().size()).toList())
                .containsExactly(1, 2);
    }

    @Test
    void teamTrajectoryStoreIsDeprecatedButAccepted() {
        FileTrajectoryStore teamStore = new FileTrajectoryStore(tempDir);
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), teamStore, false);

        assertThat(rail).isNotNull();
        assertThat(rail.getDeprecatedTeamTrajectoryStore()).isSameAs(teamStore);
    }

    @Test
    void trajectorySinkRequiresTeamId() {
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), false);
        CaptureSink sink = new CaptureSink();

        assertThatThrownBy(() -> rail.setTrajectorySink(sink, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team_id is required");
    }

    @Test
    void baseSinkBindingWithoutMemberRoleDoesNotInventRole() {
        InMemoryTrajectoryStore personal = new InMemoryTrajectoryStore();
        CaptureSink sink = new CaptureSink();
        EvolutionRail rail = new EvolutionRail(personal, false);
        rail.setTrajectorySink(sink, "team-a");

        recordToolInvoke(rail, "test-session", "view_task", Map.of(), MockAgent.withoutRole());

        MemberTrajectorySnapshot snapshot = sink.snapshots.getFirst();
        assertThat(snapshot.getMemberRole()).isNull();
        assertThat(snapshot.getTrajectory().getMeta()).doesNotContainKey("member_role");
    }

    @Test
    void boundMemberRoleFillsSnapshotWhenCtxAgentHasNoRole() {
        InMemoryTrajectoryStore personal = new InMemoryTrajectoryStore();
        CaptureSink sink = new CaptureSink();
        EvolutionRail rail = new EvolutionRail(personal, false);
        rail.setTrajectorySink(sink, "team-a", TeamRole.LEADER);

        recordToolInvoke(rail, "test-session", "view_task", Map.of(), MockAgent.withoutRole());

        MemberTrajectorySnapshot snapshot = sink.snapshots.getFirst();
        assertThat(snapshot.getMemberId()).isEqualTo("jiuwen_team_a_team_leader");
        assertThat(snapshot.getMemberRole()).isEqualTo("leader");
        assertThat(snapshot.getTrajectory().getMeta()).containsEntry("member_role", "leader");
    }

    @Test
    void boundLeaderRoleKeepsLlmStepsInTeamAggregateWithoutCtxAgentRole() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), false);
        rail.setTrajectorySink(registry, "team-a", TeamRole.LEADER);
        MockAgent agent = MockAgent.withoutRole();
        CallbackContext invokeCtx = ctx(agent, invokeInputs("test-session"));

        rail.beforeInvoke(invokeCtx);
        ModelCallInputs modelInputs = new ModelCallInputs();
        modelInputs.setMessages(List.of(Map.of("role", "user", "content", "test query")));
        modelInputs.setResponse(Map.of("role", "assistant", "content", "thinking"));
        rail.afterModelCall(ctx(agent, modelInputs));
        rail.afterToolCall(ctx(agent, toolInputs("view_task", Map.of())));
        rail.afterInvoke(invokeCtx);

        Trajectory aggregated = registry.getTrajectory("team-a", "test-session", true);

        assertThat(aggregated).isNotNull();
        assertThat(aggregated.getSteps().stream().map(step -> step.getKind()).toList())
                .containsExactly("llm", "tool");
    }

    @Test
    void registryAggregateUsesLatestTrajectoryForRepeatedMember() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), false);
        rail.setTrajectorySink(registry, "team-a");

        recordToolInvoke(rail, "test-session", "view_task", null, MockAgent.defaultAgent());
        recordToolInvoke(rail, "test-session", "send_message", null, MockAgent.defaultAgent());

        Trajectory aggregated = registry.getTrajectory("team-a", "test-session", true);

        assertThat(aggregated).isNotNull();
        assertThat(aggregated.getSteps().stream()
                .map(step -> ((ToolCallDetail) step.getDetail()).getToolName())
                .toList()).containsExactly("view_task", "send_message");
    }

    @Test
    void deprecatedTeamTrajectoryStoreDoesNotAppendSnapshots() {
        FileTrajectoryStore teamStore = new FileTrajectoryStore(tempDir);
        EvolutionRail rail = new EvolutionRail(new InMemoryTrajectoryStore(), teamStore, false);

        recordToolInvoke(rail, "test-session", "read_file", "x".repeat(1000), MockAgent.defaultAgent());
        recordToolInvoke(rail, "test-session", "read_file", "y".repeat(1000), MockAgent.defaultAgent());

        assertThat(Files.exists(tempDir.resolve("trajectories_default.jsonl"))).isFalse();
    }

    private static void recordToolInvoke(EvolutionRail rail) {
        recordToolInvoke(rail, "test-session", "view_task", null, MockAgent.defaultAgent());
    }

    private static void recordToolInvoke(EvolutionRail rail,
                                         String conversationId,
                                         String toolName,
                                         Object toolResult,
                                         MockAgent agent) {
        CallbackContext invokeCtx = ctx(agent, invokeInputs(conversationId));
        rail.beforeInvoke(invokeCtx);
        rail.afterToolCall(ctx(agent, toolInputs(toolName, toolResult)));
        rail.afterInvoke(invokeCtx);
    }

    private static InvokeInputs invokeInputs(String conversationId) {
        InvokeInputs inputs = new InvokeInputs();
        inputs.setQuery("test query");
        inputs.setConversationId(conversationId);
        return inputs;
    }

    private static ToolCallInputs toolInputs(String toolName, Object toolResult) {
        ToolCallInputs inputs = new ToolCallInputs();
        inputs.setToolName(toolName);
        inputs.setToolArgs(Map.of());
        inputs.setToolResult(toolResult);
        return inputs;
    }

    private static CallbackContext ctx(MockAgent agent, Object inputs) {
        return new CallbackContext(null, Map.of("agent", agent, "inputs", inputs));
    }

    private static final class CaptureSink implements TrajectorySink {
        private final List<MemberTrajectorySnapshot> snapshots = new ArrayList<>();

        @Override
        public void publishMemberTrajectory(MemberTrajectorySnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }

    private record MockCard(String id) {
    }

    private record MockAgent(MockCard card) {
        private static MockAgent defaultAgent() {
            return new MockAgent(new MockCard("test-agent"));
        }

        private static MockAgent withoutRole() {
            return new MockAgent(new MockCard("jiuwen_team_a_team_leader"));
        }
    }
}
