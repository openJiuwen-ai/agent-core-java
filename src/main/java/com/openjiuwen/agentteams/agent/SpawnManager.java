/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.schema.events.EventMessage;
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
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.spawn.SpawnConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Narrow Java port of Python {@code agent/spawn_manager.py} lifecycle orchestration.
 * <p>
 * This slice covers the locally verifiable process lifecycle path: register spawned handles,
 * attach the unhealthy callback, cleanup handles, rebuild teammate context from the team backend,
 * and retry restart attempts across in-process and runner subprocess modes.
 * 
 * @since 0.1.7
 */
public class SpawnManager {
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 1_000L;
    private static final long HEALTH_CHECK_INTERVAL_MILLIS = 50_000L;

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * TypeReference<>.
     * 
     * @since 0.1.7
     */
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final TeamAgent teamAgent;
    private final TeamBackend teamBackend;
    private final RecoveryManager recoveryManager;
    private final Supplier<String> sessionIdGetter;
    private final ExecutorService executor;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private final Map<String, SpawnHandle> spawnedHandles = new LinkedHashMap<>();

    /**
     * ConcurrentHashMap.newKeySet.
     * 
     * @since 0.1.7
     */
    private final Set<Future<?>> recoveryTasks = ConcurrentHashMap.newKeySet();

    /**
     * SpawnManager.
     * 
     * @param teamAgent teamAgent
     * @param teamBackend teamBackend
     * @param recoveryManager recoveryManager
     * @param sessionIdGetter sessionIdGetter
     * @since 0.1.7
     */
    public SpawnManager(TeamAgent teamAgent, TeamBackend teamBackend, RecoveryManager recoveryManager,
            Supplier<String> sessionIdGetter) {
        this(teamAgent, teamBackend, recoveryManager, sessionIdGetter,
                OpenJiuwenExecutors.newCachedThreadPool("agent-teams-spawn", false));
    }

    /**
     * SpawnManager.
     * 
     * @param teamAgent teamAgent
     * @param teamBackend teamBackend
     * @param recoveryManager recoveryManager
     * @param sessionIdGetter sessionIdGetter
     * @param executor executor
     * @since 0.1.7
     */
    public SpawnManager(TeamAgent teamAgent, TeamBackend teamBackend, RecoveryManager recoveryManager,
            Supplier<String> sessionIdGetter, ExecutorService executor) {
        this.teamAgent = Objects.requireNonNull(teamAgent, "teamAgent is required");
        this.teamBackend = Objects.requireNonNull(teamBackend, "teamBackend is required");
        this.recoveryManager = Objects.requireNonNull(recoveryManager, "recoveryManager is required");
        this.sessionIdGetter = sessionIdGetter != null ? sessionIdGetter : () -> null;
        this.executor = Objects.requireNonNull(executor, "executor is required");
    }

    /**
     * getSpawnedHandles.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, SpawnHandle> getSpawnedHandles() {
        return Map.copyOf(spawnedHandles);
    }

    /**
     * getRecoveryTaskCount.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRecoveryTaskCount() {
        cleanupFinishedRecoveryTasks();
        return recoveryTasks.size();
    }

    /**
     * spawnTeammate.
     * 
     * @param ctx ctx
     * @param initialMessage initialMessage
     * @return the result
     * @since 0.1.7
     */
    public SpawnHandle spawnTeammate(TeamRuntimeContext ctx, String initialMessage) {
        return spawnTeammate(ctx, initialMessage, null);
    }

    /**
     * spawnTeammate.
     * 
     * @param ctx ctx
     * @param initialMessage initialMessage
     * @param spawnConfig spawnConfig
     * @return the result
     * @since 0.1.7
     */
    public SpawnHandle spawnTeammate(TeamRuntimeContext ctx, String initialMessage, SpawnConfig spawnConfig) {
        if (ctx == null || ctx.getMemberName() == null || ctx.getMemberName().isBlank()) {
            throw new IllegalArgumentException("teammate context with memberName is required");
        }
        Loggers.AGENT.info("spawnTeammate: spawning member={} mode={} initialMessage={}", ctx.getMemberName(),
                teamAgent.getSpec().getSpawnMode(),
                initialMessage != null ? initialMessage.substring(0, Math.min(80, initialMessage.length())) : "null");
        SpawnConfig effectiveConfig = spawnConfig != null
                ? spawnConfig
                : SpawnConfig.builder().healthCheckTimeout(30.0).healthCheckInterval(50.0).build();
        SpawnHandle handle;
        if ("inprocess".equals(teamAgent.getSpec().getSpawnMode())) {
            handle = InProcessSpawn.inprocessSpawn(teamAgent, ctx, executor, initialMessage, sessionIdGetter.get());
        } else {
            handle = new ProcessSpawnHandle(Runner.spawnAgent(teamAgent.buildSpawnConfig(ctx),
                    teamAgent.buildSpawnPayload(ctx, initialMessage), sessionIdGetter.get(), effectiveConfig));
        }
        registerHandle(ctx.getMemberName(), handle, secondsToMillis(effectiveConfig.getHealthCheckInterval()));
        return handle;
    }

    /**
     * registerHandle.
     * 
     * @param memberName memberName
     * @param handle handle
     * @since 0.1.7
     */
    public void registerHandle(String memberName, SpawnHandle handle) {
        registerHandle(memberName, handle, HEALTH_CHECK_INTERVAL_MILLIS);
    }

    /**
     * registerHandle.
     * 
     * @param memberName memberName
     * @param handle handle
     * @param healthCheckIntervalMillis healthCheckIntervalMillis
     * @since 0.1.7
     */
    public void registerHandle(String memberName, SpawnHandle handle, long healthCheckIntervalMillis) {
        if (memberName == null || memberName.isBlank() || handle == null) {
            return;
        }
        spawnedHandles.put(memberName, handle);
        recoveryManager.registerSpawnedHandle(memberName);
        handle.setOnUnhealthy(() -> triggerUnhealthyRecovery(memberName));
        handle.startHealthCheck(Math.max(1L, healthCheckIntervalMillis));
    }

    /**
     * cleanupTeammate.
     * 
     * @param memberName memberName
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
     * restartTeammate.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public boolean restartTeammate(String memberName) {
        return restartTeammate(memberName, 3);
    }

    /**
     * restartTeammate.
     * 
     * @param memberName memberName
     * @param maxRetries maxRetries
     * @return the result
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
            teammate != null && teammate.getDescription() != null && !teammate.getDescription().isBlank()
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
                    teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.ERROR);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * onTeammateUnhealthy.
     * 
     * @param memberName memberName
     * @since 0.1.7
     */
    public void onTeammateUnhealthy(String memberName) {
        cleanupTeammate(memberName);
        teamBackend.forceUpdateMemberStatus(memberName, MemberStatus.RESTARTING);
        restartTeammate(memberName);
    }

    /**
     * triggerUnhealthyRecovery.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public Future<?> triggerUnhealthyRecovery(String memberName) {
        Future<?> task = executor.submit(() -> {
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
     * publishRestartEvent.
     * 
     * @param memberName memberName
     * @param restartCount restartCount
     * @since 0.1.7
     */
    public void publishRestartEvent(String memberName, int restartCount) {
        teamBackend.getMessager()
                .publish("team:" + teamBackend.getTeamName(),
                        EventMessage.builder().eventType("member_restarted")
                                .payload(Map.of("team_name", teamBackend.getTeamName(), "member_name", memberName,
                                        "reason", "health_check_failure", "restart_count", restartCount))
                                .build())
                .join();
    }

    /**
     * buildContextFromBackend.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    public TeamRuntimeContext buildContextFromBackend(String memberName) {
        TeamMember teammate = teamBackend.getMember(memberName);
        if (teammate == null) {
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
        return TeamRuntimeContext.builder().teamId(teamBackend.getTeamName()).sessionId(sessionIdGetter.get())
                .memberName(teammate.getMemberName()).role(TeamRole.MEMBER).metadata(metadata).build();
    }

    /**
     * shutdownAllHandles.
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
     * cancelRecoveryTasks.
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
     * buildContextFromSpec.
     * 
     * @param memberSpec memberSpec
     * @return the result
     * @since 0.1.7
     */
    public TeamRuntimeContext buildContextFromSpec(TeamMemberSpec memberSpec) {
        if (memberSpec == null) {
            return nullValue();
        }
        return TeamRuntimeContext.builder().teamId(teamBackend.getTeamName()).sessionId(sessionIdGetter.get())
                .memberName(memberSpec.getName())
                .role(memberSpec.getRole() == TeamRole.LEADER ? TeamRole.LEADER : TeamRole.MEMBER)
                .metadata(new LinkedHashMap<>()).build();
    }

    /**
     * resolvePersistedMemberModel.
     * 
     * @param memberName memberName
     * @return the result
     * @since 0.1.7
     */
    private TeamModelConfig resolvePersistedMemberModel(String memberName) {
        MemberRecord record = teamBackend.getDb().member.getMember(memberName, teamBackend.getTeamName());
        if (record == null || record.getModelRefJson() == null || record.getModelRefJson().isBlank()) {
            return nullValue();
        }
        try {
            Map<String, Object> ref = OBJECT_MAPPER.readValue(record.getModelRefJson(), MAP_TYPE);
            return ModelAllocators.resolveMemberModel(teamAgent.getSpec(), stringValue(ref.get("model_name")),
                    integerValue(ref.get("model_index")));
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return nullValue();
        }
    }

    /**
     * cleanupFinishedRecoveryTasks.
     * 
     * @since 0.1.7
     */
    private void cleanupFinishedRecoveryTasks() {
        recoveryTasks.removeIf(Future::isDone);
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * secondsToMillis.
     * 
     * @param seconds seconds
     * @return the result
     * @since 0.1.7
     */
    private static long secondsToMillis(double seconds) {
        return Math.max(1L, Math.round(seconds * 1_000.0));
    }

    /**
     * integerValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * nullValue.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static <T> T nullValue() {
        return null;
    }
}
