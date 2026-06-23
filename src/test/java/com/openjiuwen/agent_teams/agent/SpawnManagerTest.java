/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamSpec;
import com.openjiuwen.agent_teams.agent.SpawnManager.ChunkObserver;
import com.openjiuwen.agent_teams.agent.SpawnManager.MemberRow;
import com.openjiuwen.agent_teams.agent.SpawnManager.SpawnHandle;
import com.openjiuwen.agent_teams.agent.SpawnManager.SpawnKind;
import com.openjiuwen.agent_teams.agent.SpawnManager.SpawnRequest;
import com.openjiuwen.agent_teams.agent.SpawnManager.TeamBackendView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link SpawnManager}.
 *
 * <p>Mirrors Python's {@code SpawnManager} in
 * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
 *
 * <p>Mirrors Python's supplemental missing-test coverage in
 * {@code tests/unit_tests/agent_teams/test_spawn_manager_chunk_forward.py}.</p>
 */
class SpawnManagerTest {

    @Test
    void duplicateSpawnReturnsExistingOrSkipsInFlightMember() {
        RecordingSpawnExecutor executor = new RecordingSpawnExecutor();
        CompletableFuture<SpawnHandle> pending = new CompletableFuture<>();
        executor.pending = pending;
        SpawnManager manager = new SpawnManager(new RecordingState(), configurator("process"), () -> null, executor);
        TeamRuntimeContext ctx = memberContext("dev");

        CompletionStage<SpawnHandle> first = manager.spawnTeammate(ctx);
        CompletionStage<SpawnHandle> second = manager.spawnTeammate(ctx);

        assertThat(second.toCompletableFuture().join()).isNull();
        assertThat(executor.requests).hasSize(1);

        RecordingHandle handle = new RecordingHandle();
        pending.complete(handle);

        assertThat(first.toCompletableFuture().join()).isSameAs(handle);
        assertThat(manager.spawnTeammate(ctx).toCompletableFuture().join()).isSameAs(handle);
        assertThat(executor.requests).hasSize(1);
    }

    @Test
    void inprocessSpawnWiresAndDetachesChunkForwarder() {
        RecordingAgent leader = new RecordingAgent();
        RecordingAgent teammate = new RecordingAgent();
        RecordingInProcessHandle handle = new RecordingInProcessHandle(teammate);
        RecordingSpawnExecutor executor = new RecordingSpawnExecutor(handle);
        SpawnManager manager = new SpawnManager(new RecordingState(), configurator("inprocess"), () -> leader, executor);
        TeamRuntimeContext ctx = memberContext("dev");

        SpawnHandle spawned = manager.spawnTeammate(ctx).toCompletableFuture().join();
        ChunkObserver observer = teammate.streamController.observers.get(0);
        observer.onChunk("chunk-1").toCompletableFuture().join();

        assertThat(spawned).isSameAs(handle);
        assertThat(executor.requests.get(0).kind()).isEqualTo(SpawnKind.INPROCESS);
        assertThat(leader.streamController.queue.chunks).containsExactly("chunk-1");
        assertThat(handle.getChunkForward()).isSameAs(observer);

        manager.cleanupTeammate("dev").toCompletableFuture().join();

        assertThat(teammate.streamController.removed).containsExactly(observer);
        assertThat(handle.getChunkForward()).isNull();
        assertThat(handle.stopCount).isEqualTo(1);
        assertThat(handle.killCount).isEqualTo(1);
        assertThat(manager.getTypedSpawnedHandles()).isEmpty();
    }

    @Test
    void inprocessChunkForwardSkipsWhenLeaderOrAgentRefMissing() {
        RecordingAgent teammate = new RecordingAgent();
        RecordingInProcessHandle handleWithoutLeader = new RecordingInProcessHandle(teammate);
        SpawnManager managerWithoutLeader = new SpawnManager(
                new RecordingState(),
                configurator("inprocess"),
                () -> null,
                new RecordingSpawnExecutor(handleWithoutLeader)
        );

        managerWithoutLeader.spawnTeammate(memberContext("dev")).toCompletableFuture().join();

        assertThat(handleWithoutLeader.getChunkForward()).isNull();
        assertThat(teammate.streamController.observers).isEmpty();

        RecordingAgent leader = new RecordingAgent();
        RecordingInProcessHandle handleWithoutAgentRef = new RecordingInProcessHandle(null);
        SpawnManager managerWithoutAgentRef = new SpawnManager(
                new RecordingState(),
                configurator("inprocess"),
                () -> leader,
                new RecordingSpawnExecutor(handleWithoutAgentRef)
        );

        managerWithoutAgentRef.spawnTeammate(memberContext("dev")).toCompletableFuture().join();

        assertThat(handleWithoutAgentRef.getChunkForward()).isNull();
        assertThat(leader.streamController.queue.chunks).isEmpty();
    }

    @Test
    void externalCliContextSelectsExternalSpawnAndContextSessionId() {
        AgentTeamsContext.SessionIdToken token = AgentTeamsContext.setSessionId("session-ctx");
        try {
            RecordingSpawnExecutor executor = new RecordingSpawnExecutor(new RecordingHandle());
            SpawnManager manager = new SpawnManager(new RecordingState(), configurator("process"), () -> "agent", executor);
            TeamRuntimeContext ctx = memberContext("cli-dev");
            ctx.setCliAgent("codex");

            manager.spawnTeammate(ctx, "hello", "fallback-session", null).toCompletableFuture().join();

            SpawnRequest request = executor.requests.get(0);
            assertThat(request.kind()).isEqualTo(SpawnKind.EXTERNAL_CLI);
            assertThat(request.session()).isEqualTo("session-ctx");
            assertThat(request.initialMessage()).isEqualTo("hello");
            assertThat(request.teamAgent()).isEqualTo("agent");
        } finally {
            AgentTeamsContext.resetSessionId(token);
        }
    }

    @Test
    void buildContextFromDbRestoresRoleCliAgentAndModelRef() {
        AgentConfigurator configurator = configurator("process");
        RecordingBackend backend = new RecordingBackend("team", "leader");
        backend.members.put(
                "bridge",
                new MemberRow("bridge", "bridge_agent", "Bridge persona", "start", "{\"model_name\":\"fast\"}")
        );
        backend.cliAgents.put("bridge", "codex");
        configurator.setTeamBackend(backend);
        SpawnManager manager = new SpawnManager(new RecordingState(), configurator, () -> null, new RecordingSpawnExecutor());

        TeamRuntimeContext ctx = manager.buildContextFromDb("bridge").toCompletableFuture().join();

        assertThat(ctx.getRole()).isEqualTo(TeamRole.BRIDGE_AGENT);
        assertThat(ctx.getMemberName()).isEqualTo("bridge");
        assertThat(ctx.getPersona()).isEqualTo("Bridge persona");
        assertThat(ctx.getCliAgent()).isEqualTo("codex");
        assertThat(ctx.getMemberModel()).isInstanceOf(Map.class);
    }

    @Test
    void unhealthyCallbackTracksRecoveryTaskAndUpdatesRestartingStatus() {
        RecordingHandle handle = new RecordingHandle();
        RecordingSpawnExecutor executor = new RecordingSpawnExecutor(handle);
        AgentConfigurator configurator = configurator("process");
        RecordingBackend backend = new RecordingBackend("team", "leader");
        backend.members.put("dev", new MemberRow("dev", "teammate", "Dev", "resume", null));
        configurator.setTeamBackend(backend);
        SpawnManager manager = new SpawnManager(new RecordingState(), configurator, () -> null, executor);
        TeamRuntimeContext ctx = memberContext("dev");

        manager.spawnTeammate(ctx).toCompletableFuture().join();
        handle.triggerUnhealthy().toCompletableFuture().join();

        assertThat(backend.statusUpdates).contains("dev:team:restarting");
        assertThat(executor.requests).hasSize(2);
        assertThat(manager.getRecoveryTasks()).isEmpty();
    }

    @Test
    void shutdownRoutesThroughCleanupForEveryHandle() {
        RecordingHandle first = new RecordingHandle();
        RecordingHandle second = new RecordingHandle();
        RecordingSpawnExecutor executor = new RecordingSpawnExecutor(first, second);
        SpawnManager manager = new SpawnManager(new RecordingState(), configurator("process"), () -> null, executor);

        manager.spawnTeammate(memberContext("a")).toCompletableFuture().join();
        manager.spawnTeammate(memberContext("b")).toCompletableFuture().join();
        manager.shutdownAllHandles().toCompletableFuture().join();

        assertThat(first.stopCount).isEqualTo(1);
        assertThat(second.stopCount).isEqualTo(1);
        assertThat(manager.getTypedSpawnedHandles()).isEmpty();
    }

    private static AgentConfigurator configurator(String spawnMode) {
        AgentConfigurator configurator = new AgentConfigurator(new AgentCard("card", "card", "desc"));
        TeamAgentSpec spec = new TeamAgentSpec();
        DeepAgentSpec leader = new DeepAgentSpec();
        leader.setLanguage("en");
        spec.setAgents(Map.of("leader", leader));
        spec.setTeamName("team");
        spec.setSpawnMode(spawnMode);

        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.LEADER);
        ctx.setMemberName("leader");
        ctx.setTeamSpec(new TeamSpec("team", "Team", "leader"));
        ctx.setDbConfig(Map.of("db", "config"));

        configurator.setupInfra(spec, ctx);
        return configurator;
    }

    private static TeamRuntimeContext memberContext(String memberName) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(TeamRole.TEAMMATE);
        ctx.setMemberName(memberName);
        ctx.setPersona("persona");
        ctx.setTeamSpec(new TeamSpec("team", "Team", "leader"));
        return ctx;
    }

    private static final class RecordingState implements SessionManager.TeamAgentStateView {
        private SessionManager.AgentTeamSessionView teamSession;

        @Override
        public SessionManager.AgentTeamSessionView getTeamSession() {
            return teamSession;
        }

        @Override
        public void setTeamSession(SessionManager.AgentTeamSessionView session) {
            this.teamSession = session;
        }
    }

    private static class RecordingHandle implements SpawnHandle {
        int stopCount;
        int killCount;
        private boolean alive = true;
        private java.util.function.Supplier<CompletionStage<Void>> onUnhealthy;

        @Override
        public CompletionStage<Void> stopHealthCheck() {
            stopCount++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> forceKill() {
            killCount++;
            alive = false;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void setOnUnhealthy(java.util.function.Supplier<CompletionStage<Void>> callback) {
            this.onUnhealthy = callback;
        }

        private CompletionStage<Void> triggerUnhealthy() {
            return onUnhealthy.get();
        }
    }

    private static final class RecordingInProcessHandle extends RecordingHandle
            implements SpawnManager.InProcessSpawnHandle {
        private final Object agentRef;
        private ChunkObserver chunkForward;

        private RecordingInProcessHandle(Object agentRef) {
            this.agentRef = agentRef;
        }

        @Override
        public Object getAgentRef() {
            return agentRef;
        }

        @Override
        public ChunkObserver getChunkForward() {
            return chunkForward;
        }

        @Override
        public void setChunkForward(ChunkObserver chunkForward) {
            this.chunkForward = chunkForward;
        }
    }

    private static final class RecordingAgent implements SpawnManager.StreamOwner {
        private final RecordingStreamController streamController = new RecordingStreamController();

        @Override
        public SpawnManager.StreamController getStreamController() {
            return streamController;
        }
    }

    private static final class RecordingStreamController implements SpawnManager.StreamController {
        private final RecordingQueue queue = new RecordingQueue();
        private final List<ChunkObserver> observers = new ArrayList<>();
        private final List<ChunkObserver> removed = new ArrayList<>();

        @Override
        public SpawnManager.ChunkQueue getStreamQueue() {
            return queue;
        }

        @Override
        public void addChunkObserver(ChunkObserver observer) {
            observers.add(observer);
        }

        @Override
        public void removeChunkObserver(ChunkObserver observer) {
            removed.add(observer);
            observers.remove(observer);
        }
    }

    private static final class RecordingQueue implements SpawnManager.ChunkQueue {
        private final List<Object> chunks = new ArrayList<>();

        @Override
        public CompletionStage<Void> put(Object chunk) {
            chunks.add(chunk);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingSpawnExecutor implements SpawnManager.SpawnExecutor {
        private final List<SpawnRequest> requests = new ArrayList<>();
        private final List<SpawnHandle> handles = new ArrayList<>();
        private CompletableFuture<SpawnHandle> pending;

        private RecordingSpawnExecutor(SpawnHandle... handles) {
            this.handles.addAll(List.of(handles));
        }

        @Override
        public CompletionStage<SpawnHandle> spawn(SpawnRequest request) {
            requests.add(request);
            if (pending != null) {
                return pending;
            }
            if (handles.isEmpty()) {
                return CompletableFuture.completedFuture(new RecordingHandle());
            }
            return CompletableFuture.completedFuture(handles.remove(0));
        }
    }

    private static final class RecordingBackend extends ConfiguredTeamBackend implements TeamBackendView {
        private final Map<String, MemberRow> members = new LinkedHashMap<>();
        private final Map<String, String> cliAgents = new LinkedHashMap<>();
        private final List<String> statusUpdates = new ArrayList<>();

        private RecordingBackend(String teamName, String memberName) {
            super(
                    teamName,
                    memberName,
                    true,
                    Map.of(),
                    null,
                    "default",
                    List.of(),
                    null,
                    null,
                    false,
                    false,
                    List.of(),
                    null,
                    null,
                    "leader"
            );
        }

        @Override
        public CompletionStage<Object> getMember(String memberName) {
            return CompletableFuture.completedFuture(members.get(memberName));
        }

        @Override
        public CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status) {
            statusUpdates.add(memberName + ":" + teamName + ":" + status);
            return CompletableFuture.completedFuture(true);
        }

        @Override
        public String getExternalCliAgent(String memberName) {
            return cliAgents.get(memberName);
        }
    }
}
