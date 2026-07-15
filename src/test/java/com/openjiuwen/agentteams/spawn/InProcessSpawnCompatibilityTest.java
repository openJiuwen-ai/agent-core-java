
package com.openjiuwen.agentteams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InProcessSpawnCompatibilityTest {
    @AfterEach
    void cleanup() {
        SpawnContext.resetSessionId(null);
    }

    @Test
    void inprocessSpawnShouldRunTeammateLifecycleEntryPointWithSessionContext() throws Exception {
        TeamAgentSpec spec = TeamAgentSpec.builder().name("spawn-team").members(List.of(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).description("Backend worker").build()))
                .build();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("spawn-team").memberName("worker-1").role(TeamRole.MEMBER).build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(leader, ctx, executor,
                    "@worker-1 take the first task", "spawn-session-1");

            assertThat(handle.getProcessId()).isEqualTo("inproc-worker-1");
            assertThat(handle.waitForCompletion()).isEqualTo(0);
            assertThat(handle.isAlive()).isFalse();
            assertThat(handle.isHealthy()).isFalse();
            assertThat(SpawnContext.getSessionId()).isEmpty();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) handle.getTask().get(1, TimeUnit.SECONDS);
            assertThat(result).containsEntry("team_id", "spawn-team");
            assertThat(result).containsEntry("route", "direct");
            assertThat(result).containsEntry("target", "worker-1");
            assertThat(result).containsEntry("delivered_content", "take the first task");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void inProcessHandleShouldTriggerUnhealthyCallbackAndSupportShutdown() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            InProcessSpawnHandle handle =
                InProcessSpawnHandle.builder().processId("inproc-test").task(executor.submit(() -> {
                    Thread.sleep(5_000L);
                    return null;
                })).build();

            int[] callbackCount = {0};
            handle.setOnUnhealthy(() -> callbackCount[0]++);
            assertThat(handle.isAlive()).isTrue();
            assertThat(handle.isHealthy()).isTrue();

            handle.markUnhealthy();
            assertThat(callbackCount[0]).isEqualTo(1);

            assertThat(handle.shutdown(100L)).isTrue();
            assertThat(handle.isAlive()).isFalse();
            assertThat(handle.isHealthy()).isFalse();
            assertThat(handle.isShutdownRequested()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void spawnContextShouldRestoreAndInprocessSpawnShouldLeaveNoSessionResidue() throws Exception {
        SpawnContext.SessionToken token = SpawnContext.setSessionId("session-root");
        assertThat(SpawnContext.getSessionId()).isEqualTo("session-root");

        TeamAgentSpec spec = TeamAgentSpec.builder().name("spawn-team-2").members(List.of(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("worker-2").role(TeamRole.MEMBER).description("Backend worker").build()))
                .build();
        TeamAgent leader = TeamFactory.createAgentTeam(spec);
        TeamRuntimeContext ctx =
            TeamRuntimeContext.builder().teamId("spawn-team-2").memberName("worker-2").role(TeamRole.MEMBER).build();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(leader, ctx, executor, null, "spawn-session-2");
            assertThat(handle.waitForCompletion()).isEqualTo(0);
            assertThat(SpawnContext.getSessionId()).isEqualTo("session-root");
            assertThat(handle.isHealthy()).isFalse();
        } finally {
            SpawnContext.resetSessionId(token);
            executor.shutdownNow();
        }
        assertThat(SpawnContext.getSessionId()).isEmpty();
    }
}
