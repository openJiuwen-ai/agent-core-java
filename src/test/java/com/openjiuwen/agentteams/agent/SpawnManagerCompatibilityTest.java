
package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.ModelPoolEntry;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamModelConfig;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.spawn.InProcessSpawnHandle;
import com.openjiuwen.agentteams.spawn.ProcessSpawnHandle;
import com.openjiuwen.agentteams.spawn.SpawnHandle;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.runner.spawn.Message;
import com.openjiuwen.core.runner.spawn.MessageType;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfigs;
import com.openjiuwen.core.runner.spawn.SpawnAgentKind;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class SpawnManagerCompatibilityTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void spawnManagerShouldRegisterInprocessHandleAndRebuildContextFromBackend() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec());
        agent.resumeForNewSession("spawn-session-101");
        TeamRuntimeContext ctx = TeamRuntimeContext.builder().teamId("spawn-manager-team")
                .sessionId("spawn-session-101").memberName("worker-1").role(TeamRole.MEMBER).build();

        SpawnHandle handle = agent.getSpawnManager().spawnTeammate(ctx, "@worker-1 handle backlog");
        agent.getTeamBackend().updateMemberStatus("worker-1", MemberStatus.READY);
        TeamRuntimeContext rebuilt = agent.getSpawnManager().buildContextFromBackend("worker-1");

        assertThat(agent.getSpawnManager().getSpawnedHandles()).containsKey("worker-1");
        assertThat(handle.isHealthCheckRunning()).isTrue();
        assertThat(agent.getRecoveryManager().collectLiveTeammatesForSessionSwitch())
                .extracting(RecoveryManager.RecoverableMember::memberName).contains("worker-1");
        assertThat(rebuilt.getTeamId()).isEqualTo("spawn-manager-team");
        assertThat(rebuilt.getSessionId()).isEqualTo("spawn-session-101");
        assertThat(rebuilt.getMemberName()).isEqualTo("worker-1");

        handle.shutdown(100L);
        agent.getSpawnManager().shutdownAllHandles();
    }

    @Test
    void spawnManagerShouldUseRunnerProcessSpawnWhenSpecRequestsProcessMode() {
        TeamAgent agent =
            TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("spawn-manager-team").spawnMode("process")
                    .members(List.of(
                            TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                    .role(TeamRole.LEADER).build(),
                            TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).description("Worker one")
                                    .build()))
                    .build());
        agent.resumeForNewSession("spawn-session-process");
        TeamRuntimeContext ctx = TeamRuntimeContext.builder().teamId("spawn-manager-team")
                .sessionId("spawn-session-process").memberName("worker-1").role(TeamRole.MEMBER).build();

        SpawnHandle handle = agent.getSpawnManager().spawnTeammate(ctx, "Process branch work.");
        Message done = ((ProcessSpawnHandle) handle).delegate().receiveMessage();
        int exitCode = handle.waitForCompletion();

        assertThat(handle).isInstanceOf(ProcessSpawnHandle.class);
        assertThat(done.getType()).isEqualTo(MessageType.DONE);
        assertThat(done.getPayload()).asString().contains("spawn-manager-team");
        assertThat(exitCode).isZero();

        agent.getSpawnManager().shutdownAllHandles();
    }

    @Test
    void healthCheckLoopShouldTriggerUnhealthyRecoveryWhenHandleStops() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec());
        agent.resumeForNewSession("spawn-session-health");
        agent.getTeamBackend().spawnMember("worker-1", "Worker One",
                AgentCard.builder().name("worker-1").description("Worker one").build()).join();
        InProcessSpawnHandle stoppedHandle = InProcessSpawnHandle.builder().processId("stopped-worker")
                .task(CompletableFuture.completedFuture(null)).build();

        agent.getSpawnManager().registerHandle("worker-1", stoppedHandle);
        stoppedHandle.startHealthCheck(1L);

        awaitRecoveryTasksDrained(agent);

        assertThat(stoppedHandle.isHealthCheckRunning()).isFalse();
        assertThat(agent.getTeamBackend().getMember("worker-1").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getSpawnManager().getSpawnedHandles()).containsKey("worker-1");

        agent.getSpawnManager().shutdownAllHandles();
    }

    @Test
    void processHealthCheckShouldTriggerSpawnManagerRecoveryAndMonitorVisibleRestart() throws Exception {
        TeamAgent agent =
            TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("spawn-manager-team").spawnMode("process")
                    .members(List.of(
                            TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                    .role(TeamRole.LEADER).build(),
                            TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).description("Worker one")
                                    .build()))
                    .build());
        agent.resumeForNewSession("spawn-session-process-health");
        List<EventMessage> events = new CopyOnWriteArrayList<>();
        agent.getTeamBackend().getMessager().subscribe("team:spawn-manager-team", message -> {
            events.add(message);
            return CompletableFuture.completedFuture(null);
        }).join();
        TeamRuntimeContext ctx = TeamRuntimeContext.builder().teamId("spawn-manager-team")
                .sessionId("spawn-session-process-health").memberName("worker-1").role(TeamRole.MEMBER).build();

        SpawnHandle firstHandle = agent.getSpawnManager().spawnTeammate(ctx, "run long enough for health failure",
                SpawnConfig.builder().healthCheckInterval(0.01).healthCheckTimeout(0.001).build());

        awaitRestartEvent(agent, events);

        assertThat(firstHandle).isInstanceOf(ProcessSpawnHandle.class);
        assertThat(firstHandle.isAlive()).isFalse();
        assertThat(agent.getTeamBackend().getMember("worker-1").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getSpawnManager().getSpawnedHandles()).containsKey("worker-1");
        assertThat(agent.getSpawnManager().getSpawnedHandles().get("worker-1")).isNotSameAs(firstHandle);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("member_restarted");
            assertThat(event.getPayload()).containsEntry("member_name", "worker-1");
            assertThat(event.getPayload()).containsEntry("reason", "health_check_failure");
        });

        agent.getSpawnManager().shutdownAllHandles();
    }

    @Test
    void unhealthyHandleShouldCleanupMarkRestartingAndRestartTeammateLikePythonSpawnManager() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec());
        agent.resumeForNewSession("spawn-session-102");
        List<EventMessage> events = new CopyOnWriteArrayList<>();
        agent.getTeamBackend().getMessager().subscribe("team:spawn-manager-team", message -> {
            events.add(message);
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }).join();
        TeamRuntimeContext ctx = TeamRuntimeContext.builder().teamId("spawn-manager-team")
                .sessionId("spawn-session-102").memberName("worker-1").role(TeamRole.MEMBER).build();

        SpawnHandle firstHandle = agent.getSpawnManager().spawnTeammate(ctx, "@worker-1 start");
        agent.getTeamBackend().updateMemberStatus("worker-1", MemberStatus.READY);
        agent.getTeamBackend().updateMemberStatus("worker-1", MemberStatus.BUSY);

        Future<?> recoveryTask = agent.getSpawnManager().triggerUnhealthyRecovery("worker-1");
        recoveryTask.get(5, TimeUnit.SECONDS);

        assertThat(agent.getTeamBackend().getMember("worker-1").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(firstHandle.isAlive()).isFalse();
        assertThat(agent.getSpawnManager().getSpawnedHandles()).containsKey("worker-1");
        assertThat(agent.getSpawnManager().getSpawnedHandles().get("worker-1")).isNotSameAs(firstHandle);
        assertThat(events).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo("member_restarted");
            assertThat(event.getPayload()).containsEntry("member_name", "worker-1");
            assertThat(event.getPayload()).containsEntry("restart_count", 1);
        });

        agent.getSpawnManager().shutdownAllHandles();
        assertThat(agent.getSpawnManager().getSpawnedHandles()).isEmpty();
        assertThat(agent.getRecoveryManager().collectLiveTeammatesForSessionSwitch()).isEmpty();
        assertThat(agent.getSpawnManager().getRecoveryTaskCount()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void teamAgentShouldBuildSpawnPayloadAndRecoverFromSpawnConfigPayload() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec());
        TeamRuntimeContext ctx = agent.buildMemberContext(
                TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).description("Backend worker").build());

        Map<String, Object> payload = agent.buildSpawnPayload(ctx, "Review the migration delta.");
        Map<String, Object> coordination = (Map<String, Object>) payload.get("coordination");

        assertThat(coordination).containsEntry("team_name", "spawn-manager-team");
        assertThat(coordination).containsEntry("leader_member_name", TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertThat(coordination).containsEntry("member_name", "worker-1");
        assertThat(coordination).containsEntry("role", "teammate");
        assertThat(coordination).containsEntry("persona", "Backend worker");
        assertThat(payload).containsEntry("query", "Review the migration delta.");

        SpawnAgentConfig spawnConfig = agent.buildSpawnConfig(ctx);
        assertThat(spawnConfig.getAgentKind()).isEqualTo(SpawnAgentKind.TEAM_AGENT);
        assertThat(spawnConfig.getRunnerConfig()).isNotNull();
        Map<String, Object> configPayload = spawnConfig.getPayload();
        assertThat(configPayload).containsKeys("spec", "context");
        Map<String, Object> serializedContext = (Map<String, Object>) configPayload.get("context");
        assertThat(serializedContext).containsEntry("role", "teammate");
        assertThat(serializedContext).containsEntry("member_name", "worker-1");
        assertThat(OBJECT_MAPPER.writeValueAsString(spawnConfig)).contains("\"agent_kind\":\"team_agent\"");

        Map<String, Object> spawnConfigPayload = agent.buildSpawnConfigPayload(ctx);
        assertThat(spawnConfigPayload).containsEntry("agent_kind", "team_agent");
        assertThat(SpawnAgentConfigs.parseSpawnAgentConfig(spawnConfigPayload).getAgentKind())
                .isEqualTo(SpawnAgentKind.TEAM_AGENT);

        TeamAgent teammate = TeamAgent.fromSpawnPayload(configPayload);

        assertThat(teammate.getContext().getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(teammate.getContext().getMemberName()).isEqualTo("worker-1");
        assertThat(teammate.getContext().getMetadata()).containsEntry("persona", "Backend worker");
        assertThat(teammate.getSpec().getName()).isEqualTo("spawn-manager-team");
    }

    @Test
    @SuppressWarnings("unchecked")
    void spawnPayloadJsonRoundTripShouldPreserveMemberModelForDeepAgentConfig() throws Exception {
        TeamAgent agent =
            TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("spawn-model-team").spawnMode("process")
                    .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                            .role(TeamRole.LEADER).modelName("gpt-4").build()))
                    .modelPool(List.of(poolEntry("gpt-4", "http://a1"), poolEntry("gpt-4", "http://a2")))
                    .modelPoolStrategy("by_model_name").build());
        agent.getTeamBackend()
                .spawnMember("pool-worker", "Pool Worker",
                        AgentCard.builder().name("worker").description("pool-backed worker").build(), TeamRole.MEMBER,
                        "Resume the backlog.", agent.allocateModel("gpt-4"))
                .join();
        TeamRuntimeContext ctx = agent.getSpawnManager().buildContextFromBackend("pool-worker");

        String json = OBJECT_MAPPER.writeValueAsString(agent.buildSpawnConfig(ctx).getPayload());
        Map<String, Object> decodedPayload = OBJECT_MAPPER.readValue(json, Map.class);
        TeamAgent teammate = TeamAgent.fromSpawnPayload(decodedPayload);

        assertThat(teammate.getContext().getMetadata().get("member_model")).isInstanceOf(TeamModelConfig.class);
        assertThat(teammate.getDeepAgent().getConfig().getModel()).isInstanceOf(ModelRequestConfig.class);
        assertThat(teammate.getDeepAgent().getConfig().getBackend()).isInstanceOf(ModelClientConfig.class);
        ModelRequestConfig request = (ModelRequestConfig) teammate.getDeepAgent().getConfig().getModel();
        ModelClientConfig backend = (ModelClientConfig) teammate.getDeepAgent().getConfig().getBackend();
        assertThat(backend.getApiBase()).isEqualTo("http://a2");
        assertThat(request.getModelName()).isEqualTo("gpt-4");
    }

    @Test
    void spawnManagerShouldResolvePersistedModelRefFromLivePoolWhenRebuildingContext() {
        TeamAgent agent =
            TeamFactory.createAgentTeam(TeamAgentSpec.builder().name("spawn-manager-team").spawnMode("inprocess")
                    .members(List.of(TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                            .role(TeamRole.LEADER).modelName("gpt-4").build()))
                    .modelPool(List.of(poolEntry("gpt-4", "http://a1"), poolEntry("gpt-4", "http://a2"),
                            poolEntry("claude", "http://c1")))
                    .modelPoolStrategy("by_model_name").build());
        Allocation allocation = agent.allocateModel("gpt-4");

        agent.getTeamBackend()
                .spawnMember("dynamic-worker", "Dynamic Worker",
                        AgentCard.builder().name("worker").description("pool-backed worker").build(), TeamRole.MEMBER,
                        "Resume the backlog.", allocation)
                .join();

        TeamRuntimeContext rebuilt = agent.getSpawnManager().buildContextFromBackend("dynamic-worker");

        assertThat(agent.getTeamBackend().getDb().member.getMember("dynamic-worker", "spawn-manager-team")
                .getModelRefJson()).contains("\"model_name\":\"gpt-4\"").contains("\"model_index\":1");
        assertThat(rebuilt.getMetadata()).containsEntry("persona", "pool-backed worker");
        assertThat(rebuilt.getMetadata().get("member_model")).isInstanceOf(TeamModelConfig.class);
        TeamModelConfig memberModel = (TeamModelConfig) rebuilt.getMetadata().get("member_model");
        assertThat(memberModel.modelClientConfig().getApiBase()).isEqualTo("http://a2");

        agent.updateModelPool(List.of(poolEntry("gpt-4", "http://new-a1"), poolEntry("gpt-4", "http://new-a2"),
                poolEntry("claude", "http://new-c1")));

        TeamRuntimeContext afterRefresh = agent.getSpawnManager().buildContextFromBackend("dynamic-worker");
        TeamModelConfig refreshedModel = (TeamModelConfig) afterRefresh.getMetadata().get("member_model");
        assertThat(refreshedModel.modelClientConfig().getApiBase()).isEqualTo("http://new-a2");
    }

    @Test
    void spawnManagerShouldIgnoreMalformedPersistedModelRef() {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec());
        agent.getTeamBackend().spawnMember("dynamic-worker", "Dynamic Worker",
                AgentCard.builder().name("worker").description("worker").build()).join();
        agent.getTeamBackend().getDb().member.getMember("dynamic-worker", "spawn-manager-team")
                .setModelRefJson("{not-json");

        TeamRuntimeContext rebuilt = agent.getSpawnManager().buildContextFromBackend("dynamic-worker");

        assertThat(rebuilt.getMetadata()).doesNotContainKey("member_model");
    }

    @Test
    void cancelRecoveryTasksShouldCancelAndClearTrackedTasks() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(teamSpec());
        Future<?> recoveryTask = agent.getSpawnManager().triggerUnhealthyRecovery("missing-worker");

        assertThat(agent.getSpawnManager().getRecoveryTaskCount()).isGreaterThanOrEqualTo(0);
        agent.getSpawnManager().cancelRecoveryTasks();

        assertThat(agent.getSpawnManager().getRecoveryTaskCount()).isZero();
        assertThat(recoveryTask.isCancelled() || recoveryTask.isDone()).isTrue();
    }

    private static void awaitRecoveryTasksDrained(TeamAgent agent) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (agent.getSpawnManager().getRecoveryTaskCount() == 0
                    && agent.getTeamBackend().getMember("worker-1").getStatus() == MemberStatus.RESTARTING) {
                return;
            }
            Thread.sleep(10L);
        }
    }

    private static void awaitRestartEvent(TeamAgent agent, List<EventMessage> events) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            boolean restarted = events.stream().anyMatch(event -> "member_restarted".equals(event.getEventType()));
            if (restarted && agent.getSpawnManager().getRecoveryTaskCount() == 0
                    && agent.getSpawnManager().getSpawnedHandles().containsKey("worker-1")) {
                return;
            }
            Thread.sleep(10L);
        }
    }

    private static TeamAgentSpec teamSpec() {
        return TeamAgentSpec.builder().name("spawn-manager-team").members(List.of(
                TeamMemberSpec.builder().name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME).role(TeamRole.LEADER).build(),
                TeamMemberSpec.builder().name("worker-1").role(TeamRole.MEMBER).description("Worker one").build()))
                .build();
    }

    private static ModelPoolEntry poolEntry(String modelName, String apiBaseUrl) {
        return ModelPoolEntry.builder().modelName(modelName).provider("OpenAI").apiKey("key").apiBaseUrl(apiBaseUrl)
                .build();
    }
}
