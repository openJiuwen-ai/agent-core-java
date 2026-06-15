/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.core.runner.Runner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E test for AgentTeam functionality aligned with Python's agent_team_e2e.py.
 */
public class AgentTeamE2eTest {

    private static final String TEAM_NAME = "e2e_test_team";
    private static final String LEADER_NAME = "test_leader";
    private static final String SESSION_ID = "e2e_test_session";

    @BeforeEach
    void setUp() {
        Runner.start();
    }

    @AfterEach
    void tearDown() {
        Runner.stop();
    }

    @Test
    void testCreateAgentTeamAndDispatchTask() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        // Verify team was created correctly
        assertThat(leader.getSpec().getName()).isEqualTo(TEAM_NAME);
        assertThat(leader.getContext().getTeamId()).isEqualTo(TEAM_NAME);
        assertThat(leader.getSpec().getMembers()).hasSize(1);
        assertThat(leader.getSpec().getMembers().get(0).getName()).isEqualTo(LEADER_NAME);
        assertThat(leader.getSpec().getMembers().get(0).getRole()).isEqualTo(TeamRole.LEADER);

        // Test dispatchTask
        Map<String, Object> result = leader.dispatchTask("test query");
        assertThat(result).containsEntry("team_id", TEAM_NAME);
        assertThat(result).containsKey("session_id");
        assertThat(result).containsEntry("leader", LEADER_NAME);
        assertThat(result).containsEntry("member_count", 1);
        assertThat(result).containsEntry("query", "test query");
        assertThat(result).containsKey("route");
        assertThat(result).containsKey("target");

        leader.close();
    }

    @Test
    void testStreamMethod() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        // Test stream
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", "stream test query");

        Iterator<Object> stream = leader.stream(inputs, SESSION_ID);
        assertThat(stream).isNotNull();

        leader.close();
    }

    @Test
    void testInteractMethod() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        // Test interact
        leader.interact("test interaction message");

        // Verify interaction was recorded
        assertThat(leader.getContext().getMetadata()).containsKey("last_interact_route");

        leader.close();
    }

    @Test
    void testSnapshotAndRecover() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        // Dispatch a task first
        leader.dispatchTask("snapshot test query");

        // Take snapshot
        Map<String, Object> snapshot = leader.snapshot();
        assertThat(snapshot).containsKey("spec");
        assertThat(snapshot).containsKey("context");
        assertThat(snapshot).containsKey("session_id");
        assertThat(snapshot).containsKey("leader_inbox");
        assertThat(snapshot).containsKey("messages");

        // Recover from snapshot
        TeamAgent recovered = TeamFactory.recoverAgentTeam(snapshot);
        assertThat(recovered.getSpec().getName()).isEqualTo(TEAM_NAME);
        assertThat(recovered.getContext().getTeamId()).isEqualTo(TEAM_NAME);

        leader.close();
    }

    @Test
    void testResumeForNewSession() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        String newSessionId = "new_session_123";
        TeamAgent resumed = leader.resumeForNewSession(newSessionId);

        assertThat(resumed.getContext().getSessionId()).isEqualTo(newSessionId);
        assertThat(resumed.getContext().getMetadata()).containsEntry("session_id", newSessionId);

        leader.close();
    }

    @Test
    void testBroadcast() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        String broadcastId = leader.broadcast("broadcast message to all");
        assertThat(broadcastId).isNotBlank();

        // Verify broadcast was recorded
        assertThat(leader.getMessageManager().getBroadcastMessages(false)).hasSize(1);

        leader.close();
    }

    @Test
    void testUpdateModelPool() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        ModelPoolEntry newModel = ModelPoolEntry.builder()
                .modelId(UUID.randomUUID().toString())
                .provider("openai")
                .modelName("gpt-4-turbo")
                .apiKey("test-key-2")
                .apiBaseUrl("https://api.example.com/v1")
                .description("Updated model")
                .weight(2)
                .build();

        leader.updateModelPool(List.of(newModel));

        assertThat(leader.getSpec().getModelPool()).hasSize(1);
        assertThat(leader.getContext().getMetadata()).containsEntry("model_pool_size", 1);

        leader.close();
    }

    @Test
    void testLifecycleManagement() {
        TeamAgentSpec spec = buildTestSpec();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);

        // Test lifecycle states
        assertThat(leader.getContext().getLifecycle()).isNotNull();

        // Test close
        leader.close();

        // Verify resources are cleaned up
        assertThat(leader.getMemoryManager()).isNull();
    }

    private TeamAgentSpec buildTestSpec() {
        ModelPoolEntry modelPoolEntry = ModelPoolEntry.builder()
                .modelId(UUID.randomUUID().toString())
                .provider("openai")
                .modelName("gpt-4")
                .apiKey("test-api-key")
                .apiBaseUrl("https://api.openai.com/v1")
                .description("Test model for E2E tests")
                .weight(1)
                .build();

        TeamMemberSpec leaderSpec = TeamMemberSpec.builder()
                .name(LEADER_NAME)
                .role(TeamRole.LEADER)
                .description("Test team leader for E2E tests")
                .modelName("gpt-4")
                .build();

        return TeamAgentSpec.builder()
                .name(TEAM_NAME)
                .description("E2E test agent team")
                .members(List.of(leaderSpec))
                .modelPool(List.of(modelPoolEntry))
                .modelPoolStrategy("round_robin")
                .lifecycle("temporary")
                .teammateMode("build_mode")
                .spawnMode("inprocess")
                .transport("inprocess")
                .storage("sqlite")
                .language("cn")
                .build();
    }
}