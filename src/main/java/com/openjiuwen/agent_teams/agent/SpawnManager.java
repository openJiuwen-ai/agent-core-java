/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.SpawnAgentConfig;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.SpawnManagerView;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.schema.MemberRestartedEvent;
import com.openjiuwen.agent_teams.schema.TeamTopic;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages teammate process lifecycle and health monitoring.
 *
 * <p>Mirrors Python's {@code SpawnManager} in
 * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
 */
public class SpawnManager implements RecoveryManager.SpawnManagerPort, SpawnManagerView {

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SessionManager.TeamAgentStateView state;
    private final AgentConfigurator configurator;
    private final Supplier<Object> teamAgentGetter;
    private final SpawnExecutor spawnExecutor;
    private final Map<String, SpawnHandle> spawnedHandles = new LinkedHashMap<>();
    private final Set<String> spawning = new LinkedHashSet<>();
    private final Set<CompletableFuture<Void>> recoveryTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public SpawnManager(
            SessionManager.TeamAgentStateView state,
            AgentConfigurator configurator,
            Supplier<Object> teamAgentGetter
    ) {
        this(state, configurator, teamAgentGetter, request ->
                CompletableFuture.failedFuture(new IllegalStateException("No spawn executor configured")));
    }

    public SpawnManager(
            SessionManager.TeamAgentStateView state,
            AgentConfigurator configurator,
            Supplier<Object> teamAgentGetter,
            SpawnExecutor spawnExecutor
    ) {
        this.state = Objects.requireNonNull(state, "state");
        this.configurator = Objects.requireNonNull(configurator, "configurator");
        this.teamAgentGetter = Objects.requireNonNull(teamAgentGetter, "teamAgentGetter");
        this.spawnExecutor = Objects.requireNonNull(spawnExecutor, "spawnExecutor");
    }

    public Map<String, SpawnHandle> getTypedSpawnedHandles() {
        return spawnedHandles;
    }

    public Set<String> getSpawning() {
        return spawning;
    }

    public Set<CompletableFuture<Void>> getRecoveryTasks() {
        return recoveryTasks;
    }

    public CompletionStage<SpawnHandle> spawnTeammate(TeamRuntimeContext ctx) {
        return spawnTeammate(ctx, null, null, null);
    }

    public CompletionStage<SpawnHandle> spawnTeammate(
            TeamRuntimeContext ctx,
            String initialMessage,
            Object session,
            SpawnOptions spawnConfig
    ) {
        String memberName = ctx.getMemberName();
        SpawnHandle existing = spawnedHandles.get(memberName);
        if (existing != null) {
            TEAM_LOGGER.debug(
                    "[%s] teammate %s already spawned; skip duplicate",
                    memberNameOrQuestion(),
                    memberName
            );
            return CompletableFuture.completedFuture(existing);
        }
        if (spawning.contains(memberName)) {
            TEAM_LOGGER.debug(
                    "[%s] teammate %s spawn already in flight; skip duplicate",
                    memberNameOrQuestion(),
                    memberName
            );
            return CompletableFuture.completedFuture(null);
        }

        spawning.add(memberName);
        CompletionStage<SpawnHandle> spawned = spawnTeammateInner(ctx, initialMessage, session, spawnConfig);
        return spawned.whenComplete((handle, throwable) -> spawning.remove(memberName));
    }

    public Object lookupInprocessAgent(String memberName) {
        SpawnHandle handle = spawnedHandles.get(memberName);
        if (handle instanceof InProcessSpawnHandle inProcessHandle) {
            return inProcessHandle.getAgentRef();
        }
        return null;
    }

    @Override
    public CompletionStage<Void> cleanupTeammate(String memberName) {
        SpawnHandle handle = spawnedHandles.remove(memberName);
        if (handle == null) {
            return CompletableFuture.completedFuture(null);
        }
        detachChunkForwarder(handle);
        try {
            return handle.stopHealthCheck()
                    .thenCompose(ignored -> handle.isAlive() ? handle.forceKill() : CompletableFuture.completedFuture(null))
                    .exceptionally(error -> {
                        TEAM_LOGGER.error("Error cleaning up teammate %s: %s", memberName, error.getMessage());
                        return null;
                    });
        } catch (RuntimeException exception) {
            TEAM_LOGGER.error("Error cleaning up teammate %s: %s", memberName, exception.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public CompletionStage<Boolean> restartTeammate(String memberName) {
        return restartTeammate(memberName, 3);
    }

    public CompletionStage<Boolean> restartTeammate(String memberName, int maxRetries) {
        return cleanupTeammate(memberName).thenCompose(ignored ->
                buildContextFromDb(memberName).thenCompose(ctx -> {
                    if (ctx == null) {
                        TEAM_LOGGER.error("Cannot recover spawn config for %s", memberName);
                        return CompletableFuture.completedFuture(false);
                    }
                    ConfiguredTeamBackend backend = configurator.getTeamBackend();
                    if (!(backend instanceof TeamBackendView teamBackend)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return teamBackend.getMember(memberName)
                            .thenCompose(member -> restartWithRetries(
                                    ctx,
                                    member == null ? null : member.prompt(),
                                    new SpawnOptions(30, 50),
                                    memberName,
                                    maxRetries,
                                    1
                            ));
                }));
    }

    public CompletionStage<Void> onTeammateUnhealthy(String memberName) {
        TEAM_LOGGER.warning("Teammate %s detected as unhealthy, initiating restart", memberName);
        return cleanupTeammate(memberName)
                .thenCompose(ignored -> markRestarting(memberName))
                .thenCompose(ignored -> restartTeammate(memberName).thenApply(restarted -> null));
    }

    public CompletionStage<TeamRuntimeContext> buildContextFromDb(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        if (!(backend instanceof TeamBackendView teamBackend)) {
            return CompletableFuture.completedFuture(null);
        }
        return teamBackend.getMember(memberName).thenApply(member -> {
            if (member == null) {
                TEAM_LOGGER.error("Teammate %s not found in database", memberName);
                return null;
            }

            TeamRuntimeContext baseCtx = configurator.getCtx();
            TeamRuntimeContext ctx = new TeamRuntimeContext();
            ctx.setRole(TeamRole.fromValue(member.role()));
            ctx.setMemberName(member.memberName());
            ctx.setPersona(member.desc() == null ? "" : member.desc());
            ctx.setTeamSpec(baseCtx == null ? null : baseCtx.getTeamSpec());
            ctx.setMessagerConfig(configurator.buildMemberMessagerConfig(member.memberName()));
            ctx.setDbConfig(baseCtx == null ? Map.of() : baseCtx.getDbConfig());
            ctx.setMemberModel(resolveMemberModel(member.modelRefJson()));
            ctx.setCliAgent(teamBackend.getExternalCliAgent(member.memberName()));
            return ctx;
        });
    }

    public CompletionStage<Void> publishRestartEvent(String memberName, int restartCount) {
        Messager messager = configurator.getMessager();
        ConfiguredTeamBackend teamBackend = configurator.getTeamBackend();
        if (messager == null || teamBackend == null) {
            return CompletableFuture.completedFuture(null);
        }

        MemberRestartedEvent event = new MemberRestartedEvent();
        event.setTeamName(teamBackend.getTeamName());
        event.setMemberName(memberName);
        event.setRestartCount(restartCount);
        String topic = TeamTopic.TEAM.build(AgentTeamsContext.getSessionId(), teamBackend.getTeamName());
        return messager.publish(topic, EventMessage.fromEvent(event))
                .exceptionally(error -> {
                    TEAM_LOGGER.error("Failed to publish restart event for %s: %s", memberName, error.getMessage());
                    return null;
                });
    }

    @Override
    public CompletionStage<Void> shutdownAllHandles() {
        List<String> memberNames = new ArrayList<>(spawnedHandles.keySet());
        CompletionStage<Void> stage = CompletableFuture.completedFuture(null);
        for (String memberName : memberNames) {
            stage = stage.thenCompose(ignored -> cleanupTeammate(memberName));
        }
        return stage.thenRun(spawnedHandles::clear);
    }

    @Override
    public CompletionStage<Void> cancelRecoveryTasks() {
        for (CompletableFuture<Void> task : List.copyOf(recoveryTasks)) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
        recoveryTasks.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Map<String, Object> spawnedHandles() {
        return new LinkedHashMap<>(spawnedHandles);
    }

    private CompletionStage<SpawnHandle> spawnTeammateInner(
            TeamRuntimeContext ctx,
            String initialMessage,
            Object session,
            SpawnOptions spawnConfig
    ) {
        String memberName = ctx.getMemberName();
        TEAM_LOGGER.info("[%s] spawning teammate: %s", memberNameOrQuestion(), memberName);

        SpawnKind kind = resolveSpawnKind(ctx);
        Object sessionId = firstNonBlank(AgentTeamsContext.getSessionId(), session);
        SpawnAgentConfig agentConfig = kind == SpawnKind.PROCESS ? configurator.buildSpawnConfig(ctx) : null;
        Map<String, Object> payload = kind == SpawnKind.PROCESS
                ? configurator.buildSpawnPayload(ctx, initialMessage)
                : Map.of();
        SpawnRequest request = new SpawnRequest(
                kind,
                ctx,
                initialMessage,
                sessionId,
                spawnConfig,
                agentConfig,
                payload,
                teamAgentGetter.get()
        );

        return spawnExecutor.spawn(request).thenApply(handle -> {
            spawnedHandles.put(memberName, handle);
            if (handle instanceof InProcessSpawnHandle inProcessHandle) {
                wireInprocessChunkForward(inProcessHandle);
            }
            handle.setOnUnhealthy(() -> triggerUnhealthyRecovery(memberName));
            return handle;
        });
    }

    private SpawnKind resolveSpawnKind(TeamRuntimeContext ctx) {
        if (ctx.getCliAgent() != null && !ctx.getCliAgent().isBlank()) {
            return SpawnKind.EXTERNAL_CLI;
        }
        AgentConfigurator.TeamAgentSpec spec = configurator.getSpec();
        if (spec != null && "inprocess".equals(spec.getSpawnMode())) {
            return SpawnKind.INPROCESS;
        }
        return SpawnKind.PROCESS;
    }

    private CompletionStage<Void> triggerUnhealthyRecovery(String memberName) {
        CompletableFuture<Void> task = onTeammateUnhealthy(memberName).toCompletableFuture();
        recoveryTasks.add(task);
        task.whenComplete((ignored, throwable) -> recoveryTasks.remove(task));
        return task;
    }

    private void wireInprocessChunkForward(InProcessSpawnHandle handle) {
        Object leader = teamAgentGetter.get();
        Object agentRef = handle.getAgentRef();
        if (!(leader instanceof StreamOwner leaderOwner) || !(agentRef instanceof StreamOwner teammateOwner)) {
            return;
        }
        StreamController leaderController = leaderOwner.getStreamController();
        StreamController teammateController = teammateOwner.getStreamController();
        if (leaderController == null || teammateController == null) {
            return;
        }

        ChunkObserver forwarder = chunk -> {
            ChunkQueue queue = leaderController.getStreamQueue();
            if (queue == null) {
                return CompletableFuture.completedFuture(null);
            }
            return queue.put(chunk);
        };
        teammateController.addChunkObserver(forwarder);
        handle.setChunkForward(forwarder);
    }

    private void detachChunkForwarder(SpawnHandle handle) {
        if (!(handle instanceof InProcessSpawnHandle inProcessHandle)) {
            return;
        }
        ChunkObserver forwarder = inProcessHandle.getChunkForward();
        Object agentRef = inProcessHandle.getAgentRef();
        if (forwarder == null || !(agentRef instanceof StreamOwner streamOwner)) {
            return;
        }
        StreamController controller = streamOwner.getStreamController();
        if (controller != null) {
            try {
                controller.removeChunkObserver(forwarder);
            } catch (RuntimeException ignored) {
                // Python suppresses teardown observer-removal failures.
            }
        }
        inProcessHandle.setChunkForward(null);
    }

    private CompletionStage<Boolean> restartWithRetries(
            TeamRuntimeContext ctx,
            String initialMessage,
            SpawnOptions spawnConfig,
            String memberName,
            int maxRetries,
            int attempt
    ) {
        TEAM_LOGGER.info("Restarting teammate %s (attempt %d/%d)", memberName, attempt, maxRetries);
        return spawnTeammate(ctx, initialMessage, AgentTeamsContext.getSessionId(), spawnConfig)
                .thenCompose(handle -> publishRestartEvent(memberName, attempt))
                .thenApply(ignored -> {
                    TEAM_LOGGER.info("Teammate %s restarted successfully", memberName);
                    return true;
                })
                .exceptionallyCompose(error -> {
                    TEAM_LOGGER.error("Restart attempt %d for %s failed: %s", attempt, memberName, error.getMessage());
                    if (attempt < maxRetries) {
                        return restartWithRetries(ctx, initialMessage, spawnConfig, memberName, maxRetries, attempt + 1);
                    }
                    return markError(memberName).thenApply(ignored -> false);
                });
    }

    private CompletionStage<Void> markRestarting(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        String teamName = configurator.getTeamName();
        if (backend instanceof TeamBackendView teamBackend && teamName != null) {
            return teamBackend.updateMemberStatus(memberName, teamName, MemberStatus.RESTARTING.value())
                    .thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletionStage<Void> markError(String memberName) {
        ConfiguredTeamBackend backend = configurator.getTeamBackend();
        String teamName = configurator.getTeamName();
        if (backend instanceof TeamBackendView teamBackend && teamName != null) {
            return teamBackend.updateMemberStatus(memberName, teamName, MemberStatus.ERROR.value())
                    .thenApply(ignored -> null);
        }
        return CompletableFuture.completedFuture(null);
    }

    private Object resolveMemberModel(String modelRefJson) {
        if (modelRefJson == null || modelRefJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(modelRefJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception exception) {
            TEAM_LOGGER.warning(
                    "[%s] malformed model_ref_json on DB record; ignoring: %s",
                    memberNameOrQuestion(),
                    exception.getMessage()
            );
            return null;
        }
    }

    private String memberNameOrQuestion() {
        String memberName = configurator.getMemberName();
        return memberName == null ? "?" : memberName;
    }

    private static Object firstNonBlank(String sessionId, Object fallback) {
        return sessionId == null || sessionId.isBlank() ? fallback : sessionId;
    }

    /**
     * Spawn backend kind selected by the Python branch structure.
     *
     * <p>Mirrors Python's spawn branch selection in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public enum SpawnKind {
        PROCESS,
        INPROCESS,
        EXTERNAL_CLI
    }

    /**
     * Health-check spawn options.
     *
     * <p>Mirrors Python's {@code SpawnConfig} use in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public record SpawnOptions(int healthCheckTimeout, int healthCheckInterval) {
    }

    /**
     * Request handed to the Java spawn boundary.
     *
     * <p>Mirrors Python's arguments to process, in-process, and external CLI spawn calls in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public record SpawnRequest(
            SpawnKind kind,
            TeamRuntimeContext context,
            String initialMessage,
            Object session,
            SpawnOptions spawnOptions,
            SpawnAgentConfig agentConfig,
            Map<String, Object> payload,
            Object teamAgent
    ) {
    }

    /**
     * Spawn executor boundary.
     *
     * <p>Mirrors Python's {@code Runner.spawn_agent}, {@code inprocess_spawn}, and
     * {@code external_cli_spawn} calls in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface SpawnExecutor {
        CompletionStage<SpawnHandle> spawn(SpawnRequest request);
    }

    /**
     * Spawned teammate process or in-process runtime handle.
     *
     * <p>Mirrors Python's {@code SpawnedProcessHandle} use in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface SpawnHandle {
        CompletionStage<Void> stopHealthCheck();

        CompletionStage<Void> forceKill();

        boolean isAlive();

        void setOnUnhealthy(Supplier<CompletionStage<Void>> callback);
    }

    /**
     * In-process teammate handle with direct agent access.
     *
     * <p>Mirrors Python's {@code InProcessSpawnHandle} use in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface InProcessSpawnHandle extends SpawnHandle {
        Object getAgentRef();

        ChunkObserver getChunkForward();

        void setChunkForward(ChunkObserver chunkForward);
    }

    /**
     * Stream-controller owner used for in-process chunk forwarding.
     *
     * <p>Mirrors Python's direct {@code stream_controller} access in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface StreamOwner {
        StreamController getStreamController();
    }

    /**
     * Stream-controller boundary for chunk observer registration.
     *
     * <p>Mirrors Python's {@code add_chunk_observer} and {@code remove_chunk_observer} calls in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface StreamController {
        ChunkQueue getStreamQueue();

        void addChunkObserver(ChunkObserver observer);

        void removeChunkObserver(ChunkObserver observer);
    }

    /**
     * Leader stream queue boundary.
     *
     * <p>Mirrors Python's {@code stream_queue.put(...)} call in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface ChunkQueue {
        CompletionStage<Void> put(Object chunk);
    }

    /**
     * In-process stream chunk observer.
     *
     * <p>Mirrors Python's async in-process chunk forwarder in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface ChunkObserver {
        CompletionStage<Void> onChunk(Object chunk);
    }

    /**
     * Team backend operations used by spawn recovery.
     *
     * <p>Mirrors Python's team backend/database calls in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public interface TeamBackendView {
        CompletionStage<MemberRow> getMember(String memberName);

        CompletionStage<Boolean> updateMemberStatus(String memberName, String teamName, String status);

        default String getExternalCliAgent(String memberName) {
            return null;
        }
    }

    /**
     * Persisted teammate row used to rebuild runtime context.
     *
     * <p>Mirrors Python's teammate database record fields read in
     * {@code openjiuwen/agent_teams/agent/spawn_manager.py}.</p>
     */
    public record MemberRow(
            String memberName,
            String role,
            String desc,
            String prompt,
            String modelRefJson
    ) {
    }
}
