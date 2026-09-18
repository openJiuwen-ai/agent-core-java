/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.SpawnManager;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.logging.Loggers;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Spawns a teammate TeamAgent inside the current Java process.
 *
 * <p>Mirrors Python's {@code inprocess_spawn} in
 * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
 */
public final class InProcessSpawn {

    public static final String DEFAULT_INITIAL_QUERY = "Join the team and wait for your first assignment.";

    private static final LoggerProtocol TEAM_LOGGER = Loggers.TEAM;
    private static final MemberRunner DEFAULT_MEMBER_RUNNER =
            (teammate, inputs, member, sessionId) -> teammate.startAgent(inputs);

    private InProcessSpawn() {
    }

    public static CompletionStage<SpawnManager.InProcessSpawnHandle> inprocessSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx
    ) {
        return inprocessSpawn(teamAgent, ctx, null, null);
    }

    public static CompletionStage<SpawnManager.InProcessSpawnHandle> inprocessSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx,
            String initialMessage,
            String sessionId
    ) {
        return inprocessSpawn(teamAgent, ctx, initialMessage, sessionId, DEFAULT_MEMBER_RUNNER);
    }

    static CompletionStage<SpawnManager.InProcessSpawnHandle> inprocessSpawn(
            TeamAgent teamAgent,
            TeamRuntimeContext ctx,
            String initialMessage,
            String sessionId,
            MemberRunner memberRunner
    ) {
        Objects.requireNonNull(teamAgent, "teamAgent");
        Objects.requireNonNull(ctx, "ctx");
        MemberRunner effectiveMemberRunner = memberRunner == null ? DEFAULT_MEMBER_RUNNER : memberRunner;
        TeamAgentSpec spec = Objects.requireNonNull(teamAgent.getSpec(), "teamAgent.spec");
        DeepAgentSpec agentSpec = resolvePythonAgentSpec(spec, ctx);

        String memberName = ctx.getMemberName();
        TeamAgent teammate = createTeammate(spec, ctx, memberName, agentSpec);
        String query = initialMessage == null || initialMessage.isEmpty()
                ? DEFAULT_INITIAL_QUERY
                : initialMessage;
        Map<String, Object> inputs = Map.of("query", query);

        CompletableFuture<Void> task = startMemberTask(teammate, inputs, sessionId, effectiveMemberRunner, memberName);
        SpawnedInProcessHandle handle = new SpawnedInProcessHandle(
                "inproc-" + pythonString(memberName),
                task,
                teammate
        );
        TEAM_LOGGER.info("[inprocess] spawned teammate {} as task {}", memberName, handle.getProcessId());
        return CompletableFuture.completedFuture(handle);
    }

    public static CompletionStage<SpawnManager.SpawnHandle> spawn(SpawnManager.SpawnRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.kind() != SpawnManager.SpawnKind.INPROCESS) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "InProcessSpawn can only handle INPROCESS spawn requests"));
        }
        if (!(request.teamAgent() instanceof TeamAgent teamAgent)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "InProcessSpawn requires a TeamAgent request.teamAgent"));
        }
        String sessionId = request.session() == null ? null : String.valueOf(request.session());
        return inprocessSpawn(teamAgent, request.context(), request.initialMessage(), sessionId)
                .thenApply(handle -> handle);
    }

    private static TeamAgent createTeammate(
            TeamAgentSpec spec,
            TeamRuntimeContext ctx,
            String memberName,
            DeepAgentSpec agentSpec
    ) {
        String contextTeamName = ctx.getTeamSpec() == null ? null : ctx.getTeamSpec().getTeamName();
        String teamName = contextTeamName == null || contextTeamName.isEmpty()
                ? spec.getTeamName()
                : contextTeamName;
        String cardId = memberName == null || memberName.isEmpty() ? "unknown" : teamName + "_" + memberName;
        String cardName = memberName == null || memberName.isEmpty() ? "unknown" : memberName;
        String description = ctx.getPersona() == null || ctx.getPersona().isEmpty()
                ? "Teammate"
                : "Teammate: " + ctx.getPersona();
        AgentCard configuredCard = agentSpecCard(agentSpec);
        TeamAgent teammate = new TeamAgent(
                configuredCard == null ? new AgentCard(cardId, cardName, description) : configuredCard
        );
        teammate.configure(spec, ctx);
        return teammate;
    }

    private static DeepAgentSpec resolvePythonAgentSpec(TeamAgentSpec spec, TeamRuntimeContext ctx) {
        Objects.requireNonNull(ctx.getRole(), "ctx.role");
        DeepAgentSpec agentSpec = spec.getAgents().get(ctx.getRole().value());
        if (agentSpec != null) {
            return agentSpec;
        }
        DeepAgentSpec leaderSpec = spec.getAgents().get("leader");
        if (leaderSpec == null) {
            throw new IllegalArgumentException("agents dict must contain a 'leader' key");
        }
        return leaderSpec;
    }

    private static AgentCard agentSpecCard(DeepAgentSpec agentSpec) {
        if (agentSpec == null) {
            return null;
        }
        try {
            Method method = agentSpec.getClass().getMethod("getCard");
            Object value = method.invoke(agentSpec);
            return value instanceof AgentCard card ? card : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static CompletableFuture<Void> startMemberTask(
            TeamAgent teammate,
            Map<String, Object> inputs,
            String sessionId,
            MemberRunner memberRunner,
            String memberName
    ) {
        CompletionStage<Void> runStage;
        AgentTeamsContext.SessionIdToken token = null;
        boolean sessionSet = false;
        try {
            if (sessionId != null && !sessionId.isEmpty()) {
                token = AgentTeamsContext.setSessionId(sessionId);
                sessionSet = true;
            }
            TEAM_LOGGER.info("[inprocess] teammate {} started", memberName);
            runStage = Objects.requireNonNull(
                    memberRunner.run(teammate, inputs, true, sessionId),
                    "memberRunner.run"
            );
        } catch (Throwable throwable) {
            runStage = CompletableFuture.failedFuture(throwable);
        } finally {
            if (sessionSet) {
                AgentTeamsContext.resetSessionId(token);
            }
        }

        CompletableFuture<Void> future = runStage.toCompletableFuture();
        future.whenComplete((ignored, error) -> {
            if (error != null && !future.isCancelled()) {
                TEAM_LOGGER.error("[inprocess] teammate {} crashed", memberName, error);
            }
        });
        return future;
    }

    private static String pythonString(String value) {
        return value == null ? "None" : value;
    }

    /**
     * Member runner boundary around the Java equivalent of {@code Runner.run_agent_team}.
     *
     * <p>Mirrors Python's {@code Runner.run_agent_team(..., member=True, session=...)} call in
     * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
     */
    @FunctionalInterface
    public interface MemberRunner {
        CompletionStage<Void> run(
                TeamAgent teammate,
                Map<String, Object> inputs,
                boolean member,
                String sessionId
        );
    }

    /**
     * Spawn handle for an in-process teammate.
     *
     * <p>Mirrors Python's {@code InProcessSpawnHandle} returned by
     * {@code openjiuwen/agent_teams/spawn/inprocess_spawn.py}.</p>
     */
    public static final class SpawnedInProcessHandle implements SpawnManager.InProcessSpawnHandle {
        private final String processId;
        private final CompletableFuture<Void> task;
        private final TeamAgent agentRef;
        private Supplier<CompletionStage<Void>> onUnhealthy;
        private SpawnManager.ChunkObserver chunkForward;

        public SpawnedInProcessHandle(String processId, CompletableFuture<Void> task, TeamAgent agentRef) {
            this.processId = Objects.requireNonNull(processId, "processId");
            this.task = Objects.requireNonNull(task, "task");
            this.agentRef = Objects.requireNonNull(agentRef, "agentRef");
        }

        public String getProcessId() {
            return processId;
        }

        public CompletableFuture<Void> getTask() {
            return task;
        }

        public Supplier<CompletionStage<Void>> getOnUnhealthy() {
            return onUnhealthy;
        }

        @Override
        public CompletionStage<Void> stopHealthCheck() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> forceKill() {
            task.cancel(true);
            return agentRef.cancelAgent().exceptionally(error -> null);
        }

        @Override
        public boolean isAlive() {
            return !task.isDone();
        }

        @Override
        public void setOnUnhealthy(Supplier<CompletionStage<Void>> callback) {
            this.onUnhealthy = callback;
        }

        @Override
        public Object getAgentRef() {
            return agentRef;
        }

        @Override
        public SpawnManager.ChunkObserver getChunkForward() {
            return chunkForward;
        }

        @Override
        public void setChunkForward(SpawnManager.ChunkObserver chunkForward) {
            this.chunkForward = chunkForward;
        }
    }
}
