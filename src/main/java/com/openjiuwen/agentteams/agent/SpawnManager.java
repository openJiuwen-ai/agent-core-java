/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.events.TeamTopic;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamModelConfig;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.spawn.InProcessSpawn;
import com.openjiuwen.agentteams.spawn.ProcessSpawnHandle;
import com.openjiuwen.agentteams.spawn.SpawnHandle;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMember;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.spawn.SpawnConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Narrow Java port of Python {@code agent/spawn_manager.py} lifecycle orchestration.
 *
 * <p>This slice covers the locally verifiable process lifecycle path: register spawned handles,
 * attach the unhealthy callback, cleanup handles, rebuild teammate context from the team backend,
 * and retry restart attempts across in-process and runner subprocess modes.
 *
 * @since 2026/7/9
 */
public class SpawnManager {
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 1_000L;
    private static final long HEALTH_CHECK_INTERVAL_MILLIS = 50_000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final TeamAgent teamAgent;
    private final TeamBackend teamBackend;
    private final RecoveryManager recoveryManager;
    private final Supplier<String> sessionIdGetter;
    private final ThreadPoolExecutor executor;
    private final Map<String, SpawnHandle> spawnedHandles = new LinkedHashMap<>();
    private final Set<Future<?>> recoveryTasks = ConcurrentHashMap.newKeySet();

    /**
     * Construct a SpawnManager that uses a default daemon-threaded executor for recovery tasks.
     *
     * @param teamAgent the owning team agent
     * @param teamBackend the team backend used for member lookups and event publishing
     * @param recoveryManager the manager that tracks spawned-handle recovery state
     * @param sessionIdGetter the supplier for the current session id; may be {@code null}
     * @since 0.1.7
     */
    public SpawnManager(
            TeamAgent teamAgent,
            TeamBackend teamBackend,
            RecoveryManager recoveryManager,
            Supplier<String> sessionIdGetter) {
        this(
                teamAgent,
                teamBackend,
                recoveryManager,
                sessionIdGetter,
                buildDefaultExecutor());
    }

    /**
     * Construct a SpawnManager with an explicit executor used to run recovery tasks.
     *
     * @param teamAgent the owning team agent; must not be {@code null}
     * @param teamBackend the team backend used for member lookups and event publishing; must not be {@code null}
     * @param recoveryManager the manager that tracks spawned-handle recovery state; must not be {@code null}
     * @param sessionIdGetter the supplier for the current session id;
     *     {@code null} is treated as a constant {@code null}
     * @param executor the executor used to run unhealthy-recovery tasks; must not be {@code null}
     * @since 0.1.7
     */
    public SpawnManager(
            TeamAgent teamAgent,
            TeamBackend teamBackend,
            RecoveryManager recoveryManager,
            Supplier<String> sessionIdGetter,
            ThreadPoolExecutor executor) {
        this.teamAgent = Objects.requireNonNull(teamAgent, "teamAgent is required");
        this.teamBackend = Objects.requireNonNull(teamBackend, "teamBackend is required");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager is required");
        this.sessionIdGetter = sessionIdGetter != null ? sessionIdGetter : () -> null;
        this.executor = Objects.requireNonNull(executor, "executor is required");
    }

    /**
     * Build the default daemon-threaded executor used when no executor is supplied.
     *
     * <p>Pool size: {@code max(8, availableProcessors * 2)} for both core and max. Setting
     * core=max ensures the pool pre-spawns enough workers for concurrent teammates; the
     * previous core=0 + bounded-queue configuration caused {@code invokeForSpawn} (which
     * blocks until shutdown) to pin the single worker, serializing all subsequent spawns.</p>
     *
     * @return the constructed executor service
     * @since 0.1.7
     */
    private static ThreadPoolExecutor buildDefaultExecutor() {
        int poolSize = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory factory = runnable -> {
            Thread thread = java.util.concurrent.Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("agent-teams-spawn-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, throwable) ->
                    Loggers.AGENT.exception("Uncaught exception in spawn executor thread=" + t.getName(), throwable));
            return thread;
        };
        return new ThreadPoolExecutor(
                poolSize, poolSize,
                60L, TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(256),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * Get a snapshot of currently registered spawned handles keyed by member name.
     *
     * @return an immutable copy of the spawned-handles map
     * @since 0.1.7
     */
    public Map<String, SpawnHandle> getSpawnedHandles() {
        return Map.copyOf(spawnedHandles);
    }

    /**
     * Get the count of still-running recovery tasks after pruning finished ones.
     *
     * @return the number of in-flight recovery tasks
     * @since 0.1.7
     */
    public int getRecoveryTaskCount() {
        cleanupFinishedRecoveryTasks();
        return recoveryTasks.size();
    }

    /**
     * Spawn a teammate with default spawn config.
     *
     * @param ctx the runtime context for the teammate; must have a non-blank member name
     * @param initialMessage the optional initial message handed to the spawned process
     * @return the {@link SpawnHandle} for the spawned teammate
     * @since 0.1.7
     */
    public SpawnHandle spawnTeammate(TeamRuntimeContext ctx, String initialMessage) {
        return spawnTeammate(ctx, initialMessage, null);
    }

    /**
     * Spawn a teammate in the configured spawn mode (in-process or subprocess),
     * register the handle, and start its health check.
     *
     * @param ctx the runtime context for the teammate; must have a non-blank member name
     * @param initialMessage the optional initial message handed to the spawned process
     * @param spawnConfig the spawn config override; {@code null} uses defaults with 50s health-check interval
     * @return the {@link SpawnHandle} for the spawned teammate
     * @since 0.1.7
     */
    public SpawnHandle spawnTeammate(
            TeamRuntimeContext ctx, String initialMessage, SpawnConfig spawnConfig) {
        if (ctx == null || ctx.getMemberName() == null || ctx.getMemberName().isBlank()) {
            throw new IllegalArgumentException("teammate context with memberName is required");
        }
        Loggers.AGENT.info("spawnTeammate: enter member={} mode={} initialMessageLen={} thread={}",
                ctx.getMemberName(), teamAgent.getSpec().getSpawnMode(),
                initialMessage != null ? initialMessage.length() : 0,
                Thread.currentThread().getName());
        SpawnConfig effectiveConfig =
                spawnConfig != null
                        ? spawnConfig
                        : SpawnConfig.builder().healthCheckTimeout(30.0).healthCheckInterval(50.0).build();
        SpawnHandle handle;
        if ("inprocess".equals(teamAgent.getSpec().getSpawnMode())) {
            handle =
                    InProcessSpawn.inprocessSpawn(
                            teamAgent, ctx, executor, initialMessage, sessionIdGetter.get());
        } else {
            handle =
                    new ProcessSpawnHandle(
                            Runner.spawnAgent(
                                    teamAgent.buildSpawnConfig(ctx),
                                    teamAgent.buildSpawnPayload(ctx, initialMessage),
                                    sessionIdGetter.get(),
                                    effectiveConfig));
        }
        registerHandle(
                ctx.getMemberName(), handle, secondsToMillis(effectiveConfig.getHealthCheckInterval()));
        Loggers.AGENT.info("spawnTeammate: registered member={} handleType={} handleId={} alive={}",
                ctx.getMemberName(),
                handle.getClass().getSimpleName(),
                Integer.toHexString(System.identityHashCode(handle)),
                handle.isAlive());
        return handle;
    }

    /**
     * Register a spawned handle using the default 50s health-check interval.
     *
     * @param memberName the member name; blank or {@code null} is ignored
     * @param handle the spawn handle; {@code null} is ignored
     * @since 0.1.7
     */
    public void registerHandle(String memberName, SpawnHandle handle) {
        registerHandle(memberName, handle, HEALTH_CHECK_INTERVAL_MILLIS);
    }

    /**
     * Register a spawned handle, track it in the recovery manager, and start a periodic health check.
     *
     * @param memberName the member name; blank or {@code null} is ignored
     * @param handle the spawn handle; {@code null} is ignored
     * @param healthCheckIntervalMillis the health-check interval in milliseconds; clamped to at least 1ms
     * @since 0.1.7
     */
    public void registerHandle(
            String memberName, SpawnHandle handle, long healthCheckIntervalMillis) {
        if (memberName == null || memberName.isBlank() || handle == null) {
            return;
        }
        spawnedHandles.put(memberName, handle);
        recoveryManager.registerSpawnedHandle(memberName);
        handle.setOnUnhealthy(() -> triggerUnhealthyRecovery(memberName));
        handle.startHealthCheck(Math.max(1L, healthCheckIntervalMillis));
    }

    /**
     * Remove a teammate's handle, stop its health check, and force-kill the underlying process if still alive.
     *
     * @param memberName the member name whose handle should be cleaned up
     * @since 0.1.7
     */
    public void cleanupTeammate(String memberName) {
        SpawnHandle handle = spawnedHandles.remove(memberName);
        recoveryManager.removeSpawnedHandle(memberName);
        if (handle == null) {
            return;
        }
        try {
            handle.stopHealthCheck();
            if (handle.isAlive()) {
                handle.forceKill();
            }
        } catch (IllegalArgumentException | IllegalStateException ignored) {
            // Python logs and continues cleanup; Java keeps the same warning-only recovery shape.
        }
    }

    /**
     * Restart a teammate with the default of 3 retry attempts.
     *
     * @param memberName the member to restart
     * @return {@code true} if the teammate was respawned successfully
     * @since 0.1.7
     */
    public boolean restartTeammate(String memberName) {
        return restartTeammate(memberName, 3);
    }

    /**
     * Restart a teammate: clean up the old handle, rebuild its context from the backend,
     * and retry spawn up to {@code maxRetries} times.
     *
     * @param memberName the member to restart
     * @param maxRetries the maximum number of spawn attempts
     * @return {@code true} if the teammate was respawned successfully, {@code false} otherwise
     * @since 0.1.7
     */
    public boolean restartTeammate(String memberName, int maxRetries) {
        Loggers.AGENT.info("restartTeammate: member={} maxRetries={}", memberName, maxRetries);
        cleanupTeammate(memberName);
        TeamRuntimeContext ctx = buildContextFromBackend(memberName);
        if (ctx == null) {
            Loggers.AGENT.warn("restartTeammate: buildContextFromBackend returned null for member={}", memberName);
            return false;
        }
        TeamMember teammate = teamBackend.getMember(memberName);
        String initialMessage =
                teammate != null
                        && teammate.getDescription() != null
                        && !teammate.getDescription().isBlank()
                        ? teammate.getDescription()
                        : null;
        int attempts = Math.max(1, maxRetries);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                spawnTeammate(ctx, initialMessage);
                teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.RESTARTING);
                publishRestartEvent(memberName, attempt);
                return true;
            } catch (IllegalStateException | IllegalArgumentException | RejectedExecutionException ignored) {
                if (attempt == attempts) {
                    teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.ERROR);
                    return false;
                }
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1_000L);
                } catch (InterruptedException interruptedException) {
                    // retry interrupted: abandon retry loop cooperatively (G.CON.10)
                    teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.ERROR);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Handle an unhealthy teammate by cleaning it up, marking it restarting, and triggering a restart.
     *
     * @param memberName the member that is unhealthy
     * @since 0.1.7
     */
    public void onTeammateUnhealthy(String memberName) {
        cleanupTeammate(memberName);
        teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.RESTARTING);
        restartTeammate(memberName);
    }

    /**
     * Submit an asynchronous recovery task for an unhealthy teammate.
     *
     * @param memberName the member to recover
     * @return the future tracking the recovery task
     * @since 0.1.7
     */
    public Future<?> triggerUnhealthyRecovery(String memberName) {
        Future<?> task =
                executor.submit(
                        () -> {
                            try {
                                onTeammateUnhealthy(memberName);
                            } finally {
                                cleanupFinishedRecoveryTasks();
                            }
                        });
        recoveryTasks.add(task);
        cleanupFinishedRecoveryTasks();
        return task;
    }

    /**
     * Publish a {@link TeamEvent#MEMBER_RESTARTED} event on the TEAM topic so peers learn about the restart.
     *
     * @param memberName the member that was restarted
     * @param restartCount the 1-based restart attempt number
     * @since 0.1.7
     */
    public void publishRestartEvent(String memberName, int restartCount) {
        // Mirrors Python spawn_manager.py: TeamTopic.TEAM.build(get_session_id(), team_name).
        teamBackend
                .getMessager()
                .publish(
                        TeamTopic.TEAM.build(teamBackend.getTeamSessionId(), teamBackend.getTeamName()),
                        EventMessage.builder()
                                .eventType(TeamEvent.MEMBER_RESTARTED)
                                .payload(
                                        Map.of(
                                                "team_name",
                                                teamBackend.getTeamName(),
                                                "member_name",
                                                memberName,
                                                "reason",
                                                "health_check_failure",
                                                "restart_count",
                                                restartCount))
                                .build())
                .join();
    }

    /**
     * Rebuild a teammate runtime context from the team backend, including its persona and persisted member model.
     *
     * @param memberName the member whose context should be rebuilt
     * @return the rebuilt {@link TeamRuntimeContext}, or {@code null} when the member is unknown to the backend
     * @since 0.1.7
     */
    public TeamRuntimeContext buildContextFromBackend(String memberName) {
        Loggers.AGENT.info("buildContextFromBackend: enter member={} thread={}",
                memberName, Thread.currentThread().getName());
        TeamMember teammate = teamBackend.getMember(memberName);
        if (teammate == null) {
            Loggers.AGENT.warn("buildContextFromBackend: getMember returned null for member={}, returning null ctx",
                    memberName);
            return nullValue();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (teammate.getDescription() != null && !teammate.getDescription().isBlank()) {
            metadata.put("persona", teammate.getDescription());
        }
        TeamModelConfig memberModel = resolvePersistedMemberModel(memberName);
        if (memberModel != null) {
            metadata.put("member_model", memberModel);
        }
        return TeamRuntimeContext.builder()
                .teamId(teamBackend.getTeamName())
                .sessionId(sessionIdGetter.get())
                .memberName(teammate.getMemberName())
                .role(TeamRole.MEMBER)
                .metadata(metadata)
                .build();
    }

    /**
     * Shut down every registered handle within the configured timeout and clear internal state.
     *
     * @since 0.1.7
     */
    public void shutdownAllHandles() {
        for (Map.Entry<String, SpawnHandle> entry : new LinkedHashMap<>(spawnedHandles).entrySet()) {
            try {
                entry.getValue().shutdown(SHUTDOWN_TIMEOUT_MILLIS);
            } catch (IllegalStateException | IllegalArgumentException ignored) {
                // Keep shutdown best-effort like Python's cleanup path.
            }
            recoveryManager.removeSpawnedHandle(entry.getKey());
        }
        spawnedHandles.clear();
    }

    /**
     * Cancel all in-flight recovery tasks and clear the recovery set.
     *
     * @since 0.1.7
     */
    public void cancelRecoveryTasks() {
        for (Future<?> task : recoveryTasks) {
            if (!task.isDone()) {
                task.cancel(true);
            }
        }
        recoveryTasks.clear();
    }

    /**
     * Build a runtime context from a member spec, deriving role and team id from the team backend.
     *
     * @param memberSpec the spec describing the member; may be {@code null}
     * @return the built {@link TeamRuntimeContext}, or {@code null} when {@code memberSpec} is {@code null}
     * @since 0.1.7
     */
    public TeamRuntimeContext buildContextFromSpec(TeamMemberSpec memberSpec) {
        if (memberSpec == null) {
            return nullValue();
        }
        return TeamRuntimeContext.builder()
                .teamId(teamBackend.getTeamName())
                .sessionId(sessionIdGetter.get())
                .memberName(memberSpec.getName())
                .role(memberSpec.getRole() == TeamRole.LEADER ? TeamRole.LEADER : TeamRole.MEMBER)
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private TeamModelConfig resolvePersistedMemberModel(String memberName) {
        MemberRecord record =
                teamBackend.getDb().member.getMember(memberName, teamBackend.getTeamName());
        if (record == null || record.getModelRefJson() == null || record.getModelRefJson().isBlank()) {
            return nullValue();
        }
        try {
            Map<String, Object> ref = OBJECT_MAPPER.readValue(record.getModelRefJson(), MAP_TYPE);
            return ModelAllocators.resolveMemberModel(
                    teamAgent.getSpec(),
                    stringValue(ref.get("model_name")),
                    integerValue(ref.get("model_index")));
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return nullValue();
        }
    }

    private void cleanupFinishedRecoveryTasks() {
        recoveryTasks.removeIf(Future::isDone);
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static long secondsToMillis(double seconds) {
        return Math.max(1L, Math.round(seconds * 1_000.0));
    }

    private static Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return nullValue();
            }
        }
        return nullValue();
    }

    private static <T> T nullValue() {
        return null;
    }
}
