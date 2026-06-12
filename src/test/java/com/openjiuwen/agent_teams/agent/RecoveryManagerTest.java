/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.Allocation;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ModelAllocator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link RecoveryManager}.
 *
 * <p>Mirrors Python's {@code RecoveryManager} in
 * {@code openjiuwen/agent_teams/agent/recovery_manager.py}.</p>
 */
class RecoveryManagerTest {

    @Test
    void recoverTeamReturnsEmptyWithoutBackend() {
        RecoveryManager manager = new RecoveryManager(configurator(null), new RecordingSpawnManager());

        assertEquals(List.of(), await(manager.recoverTeam()));
    }

    @Test
    void recoverTeamMarksTeammatesRestartingAndReturnsSuccessfulRestarts() {
        RecordingRegistry registry = new RecordingRegistry(
                new RecoveryManager.MemberRecord("leader", MemberStatus.READY.value()),
                new RecoveryManager.MemberRecord("dev", MemberStatus.READY.value()),
                new RecoveryManager.MemberRecord("qa", MemberStatus.PAUSED.value())
        );
        RecordingSpawnManager spawnManager = new RecordingSpawnManager();
        spawnManager.restartResults.put("qa", false);
        RecoveryManager manager = new RecoveryManager(configurator(registry), spawnManager);

        assertEquals(List.of("dev"), await(manager.recoverTeam()));
        assertEquals(List.of("dev:restarting", "qa:restarting"), registry.statusUpdates);
        assertEquals(List.of("dev", "qa"), spawnManager.restartCalls);
    }

    @Test
    void markTeammateRestartingNormalizesActiveStatusesThroughError() {
        RecordingRegistry registry = new RecordingRegistry();
        RecoveryManager manager = new RecoveryManager(configurator(registry), new RecordingSpawnManager());

        assertTrue(await(manager.markTeammateRestartingForSessionSwitch("dev", MemberStatus.READY)));

        assertEquals(List.of("dev:error", "dev:restarting"), registry.statusUpdates);
    }

    @Test
    void markTeammateRestartingUsesDirectTransitionForPausedAndStopsOnFailure() {
        RecordingRegistry registry = new RecordingRegistry();
        RecoveryManager manager = new RecoveryManager(configurator(registry), new RecordingSpawnManager());

        assertTrue(await(manager.markTeammateRestartingForSessionSwitch("dev", MemberStatus.PAUSED)));
        registry.updateResult = false;
        assertFalse(await(manager.markTeammateRestartingForSessionSwitch("qa", MemberStatus.BUSY)));

        assertEquals(List.of("dev:restarting", "qa:error"), registry.statusUpdates);
    }

    @Test
    void collectLiveTeammatesFiltersLeaderDeadHandlesAndTerminalStatuses() {
        RecordingRegistry registry = new RecordingRegistry(
                new RecoveryManager.MemberRecord("leader", MemberStatus.READY.value()),
                new RecoveryManager.MemberRecord("dev", MemberStatus.BUSY.value()),
                new RecoveryManager.MemberRecord("qa", MemberStatus.SHUTDOWN.value()),
                new RecoveryManager.MemberRecord("ops", MemberStatus.UNSTARTED.value()),
                new RecoveryManager.MemberRecord("null-handle", MemberStatus.READY.value())
        );
        RecordingSpawnManager spawnManager = new RecordingSpawnManager();
        spawnManager.spawned.put("dev", new Object());
        spawnManager.spawned.put("qa", new Object());
        spawnManager.spawned.put("ops", new Object());
        spawnManager.spawned.put("null-handle", null);
        RecoveryManager manager = new RecoveryManager(configurator(registry), spawnManager);

        List<RecoveryManager.RecoverableMember> live = await(manager.collectLiveTeammatesForSessionSwitch());

        assertEquals(List.of(new RecoveryManager.RecoverableMember("dev", MemberStatus.BUSY)), live);
    }

    @Test
    void restartForSessionSwitchOptionallyCleansUpAndRestartsMarkedMembers() {
        RecordingRegistry registry = new RecordingRegistry();
        RecordingSpawnManager spawnManager = new RecordingSpawnManager();
        RecoveryManager manager = new RecoveryManager(configurator(registry), spawnManager);

        await(manager.restartForSessionSwitch(
                List.of(new RecoveryManager.RecoverableMember("dev", MemberStatus.BUSY)),
                true
        ));

        assertEquals(List.of("dev"), spawnManager.cleanupCalls);
        assertEquals(List.of("dev:error", "dev:restarting"), registry.statusUpdates);
        assertEquals(List.of("dev"), spawnManager.restartCalls);
    }

    @Test
    void persistLeaderConfigWritesTeamNamespaceWithDbStateAndAllocatorState() {
        RecordingRegistry registry = new RecordingRegistry();
        AgentConfigurator configurator = configurator(registry);
        configurator.setModelAllocator(new RecordingAllocator());
        RecordingSession session = new RecordingSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "team-a", Map.of(
                TeamRuntimeMetadata.TEAM_DB_STATE_KEY,
                TeamRuntimeMetadata.TEAM_DB_STATE_CREATED
        ));
        RecoveryManager manager = new RecoveryManager(configurator, new RecordingSpawnManager());

        manager.persistLeaderConfig(session);

        Map<String, Object> namespace = TeamRuntimeMetadata.readTeamNamespace(session, "team-a");
        assertEquals(TeamRuntimeMetadata.TEAM_DB_STATE_CREATED, namespace.get(TeamRuntimeMetadata.TEAM_DB_STATE_KEY));
        assertTrue(namespace.get("spec") instanceof Map);
        assertTrue(namespace.get("context") instanceof Map);
        assertEquals(Map.of("cursor", 3), namespace.get("model_allocator_state"));
    }

    @Test
    void persistAllocatorStateMergesExistingNamespace() {
        RecordingRegistry registry = new RecordingRegistry();
        AgentConfigurator configurator = configurator(registry);
        configurator.setModelAllocator(new RecordingAllocator());
        RecordingSession session = new RecordingSession();
        TeamRuntimeMetadata.writeTeamNamespace(session, "team-a", Map.of("keep", "yes"));
        RecoveryManager manager = new RecoveryManager(configurator, new RecordingSpawnManager());

        manager.persistAllocatorState(session);

        Map<String, Object> namespace = TeamRuntimeMetadata.readTeamNamespace(session, "team-a");
        assertEquals("yes", namespace.get("keep"));
        assertEquals(Map.of("cursor", 3), namespace.get("model_allocator_state"));
    }

    private static AgentConfigurator configurator(RecordingRegistry registry) {
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("agent", "Agent", "description"));
        TeamAgentSpec spec = new TeamAgentSpec();
        spec.setAgents(Map.of("leader", new DeepAgentSpec()));
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("leader");
        ctx.setTeamSpec(new TeamSpec("team-a", "Team A", "leader"));
        configurator.setupInfra(spec, ctx);
        if (registry != null) {
            configurator.setTeamBackend(new ConfiguredTeamBackend(
                    "team-a",
                    "leader",
                    true,
                    Map.of(),
                    null,
                    "",
                    List.of(),
                    null,
                    null,
                    true,
                    false,
                    List.of(),
                    null,
                    null,
                    "leader",
                    registry
            ));
        }
        return configurator;
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static final class RecordingRegistry implements RecoveryManager.MemberRegistry {
        private final List<RecoveryManager.MemberRecord> members = new ArrayList<>();
        private final List<String> statusUpdates = new ArrayList<>();
        private boolean updateResult = true;

        private RecordingRegistry(RecoveryManager.MemberRecord... members) {
            this.members.addAll(List.of(members));
        }

        @Override
        public CompletionStage<List<RecoveryManager.MemberRecord>> listMembers() {
            return CompletableFuture.completedFuture(new ArrayList<>(members));
        }

        @Override
        public CompletionStage<TeamMember.MemberSnapshot> getMember(String memberName, String teamName) {
            return CompletableFuture.completedFuture(
                    new TeamMember.MemberSnapshot(MemberStatus.READY.value(), ExecutionStatus.IDLE.value())
            );
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            statusUpdates.add(memberName + ":" + status);
            return CompletableFuture.completedFuture(updateResult);
        }

        @Override
        public CompletionStage<Boolean> updateMemberExecutionStatus(
                String memberName,
                String teamName,
                String status
        ) {
            return CompletableFuture.completedFuture(updateResult);
        }
    }

    private static final class RecordingSpawnManager implements RecoveryManager.SpawnManagerPort {
        private final Map<String, Object> spawned = new LinkedHashMap<>();
        private final Map<String, Boolean> restartResults = new LinkedHashMap<>();
        private final List<String> restartCalls = new ArrayList<>();
        private final List<String> cleanupCalls = new ArrayList<>();

        @Override
        public CompletionStage<Boolean> restartTeammate(String memberName) {
            restartCalls.add(memberName);
            return CompletableFuture.completedFuture(restartResults.getOrDefault(memberName, true));
        }

        @Override
        public CompletionStage<Void> cleanupTeammate(String memberName) {
            cleanupCalls.add(memberName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Map<String, Object> spawnedHandles() {
            return spawned;
        }
    }

    private static final class RecordingAllocator implements ModelAllocator, RecoveryManager.StatefulAllocator {
        @Override
        public Allocation allocate(String modelName) {
            return () -> Map.of("model", modelName);
        }

        @Override
        public void loadStateDict(Map<String, Object> state) {
        }

        @Override
        public Map<String, Object> stateDict() {
            return Map.of("cursor", 3);
        }
    }

    private static final class RecordingSession implements TeamRuntimeMetadata.SessionStateAccess {
        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }
}
