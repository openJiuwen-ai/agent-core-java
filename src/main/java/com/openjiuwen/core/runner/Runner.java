/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.SessionManager;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.agent.TeamMember;
import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.monitor.TeamMonitor;
import com.openjiuwen.agent_teams.monitor.TeamStreamLogger;
import com.openjiuwen.agent_teams.runtime.RunActionKind;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeActivation;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeMetadata;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamOutputSchema;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.multi_agent.BaseTeam;
import com.openjiuwen.core.multi_agent.team_runtime.TeamRuntime;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageQueueFactory;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfigs;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnMessage;
import com.openjiuwen.core.runner.spawn.SpawnMessageType;
import com.openjiuwen.core.runner.spawn.SpawnProcesses;
import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.BaseAgent;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

/**
 * Singleton facade for the core runner.
 *
 * <p>Mirrors Python's {@code Runner} and process-global {@code GLOBAL_RUNNER} in
 * {@code openjiuwen/core/runner/runner.py}.</p>
 */
public final class Runner {

    private static final RunnerImpl GLOBAL_RUNNER = new RunnerImpl(RunnerConfig.DEFAULT_RUNNER_CONFIG);

    public static final ResourceMgr resourceMgr = GLOBAL_RUNNER.resourceMgr();
    public static final LocalMessageQueue pubsub = GLOBAL_RUNNER.pubsub();
    public static final AsyncCallbackFramework callbackFramework = GLOBAL_RUNNER.callbackFramework();

    private Runner() {
    }

    public static ResourceMgr getResourceMgr() {
        return GLOBAL_RUNNER.resourceMgr();
    }

    public static ResourceMgr resourceMgr() {
        return getResourceMgr();
    }

    public static LocalMessageQueue getPubsub() {
        return GLOBAL_RUNNER.pubsub();
    }

    public static Object getDistPubsub() {
        return GLOBAL_RUNNER.distPubsub();
    }

    public static ReplyTopicSubscription getSystemReplySub() {
        return GLOBAL_RUNNER.systemReplySub();
    }

    public static AsyncCallbackFramework getCallbackFramework() {
        return GLOBAL_RUNNER.callbackFramework();
    }

    public static Object getRootTaskGroup() {
        return GLOBAL_RUNNER.getRootTaskGroup();
    }

    public static void setConfig(RunnerConfig config) {
        GLOBAL_RUNNER.setConfig(config);
    }

    public static RunnerConfig getConfig() {
        return GLOBAL_RUNNER.getConfig();
    }

    public static CompletionStage<Boolean> start() {
        return GLOBAL_RUNNER.start();
    }

    public static CompletionStage<Boolean> stop() {
        return GLOBAL_RUNNER.stop();
    }

    public static CompletionStage<Object> runWorkflow(String workflow, Object inputs) {
        return runWorkflow(workflow, inputs, null, null, null);
    }

    public static CompletionStage<Object> runWorkflow(String workflow, Object inputs, Object session) {
        return runWorkflow(workflow, inputs, session, null, null);
    }

    public static CompletionStage<Object> runWorkflow(Workflow workflow, Object inputs) {
        return runWorkflow(workflow, inputs, null, null, null);
    }

    public static CompletionStage<Object> runWorkflow(Workflow workflow, Object inputs, Object session) {
        return runWorkflow(workflow, inputs, session, null, null);
    }

    public static CompletionStage<Object> runWorkflow(Object workflow, Object inputs, Object session,
                                                      ModelContext context, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, envs);
    }

    public static CompletionStage<Iterator<WorkflowChunk>> runWorkflowStreaming(
            Object workflow,
            Object inputs,
            Object session,
            ModelContext context,
            List<StreamMode> streamModes,
            Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes, envs);
    }

    public static CompletionStage<Object> runAgent(String agent, Object inputs) {
        return runAgent(agent, inputs, null, null, null);
    }

    public static CompletionStage<Object> runAgent(BaseAgent agent, Object inputs) {
        return runAgent(agent, inputs, null, null, null);
    }

    public static CompletionStage<Object> runAgent(Object agent, Object inputs, Object session,
                                                   ModelContext context, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context, envs);
    }

    public static CompletionStage<Iterator<Object>> runAgentStreaming(Object agent, Object inputs, Object session,
                                                                      ModelContext context,
                                                                      List<StreamMode> streamModes,
                                                                      Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, envs);
    }

    public static CompletionStage<Void> release(String sessionId) {
        return release(sessionId, false);
    }

    public static CompletionStage<Void> release(String sessionId, boolean force) {
        return GLOBAL_RUNNER.release(sessionId, force);
    }

    public static CompletionStage<Object> spawnAgent(Object agentConfig, Object inputs, Object session,
                                                     ModelContext context, Map<String, Object> envs,
                                                     Object spawnConfig) {
        return GLOBAL_RUNNER.spawnAgent(agentConfig, inputs, session, spawnConfig)
                .thenApply(handle -> handle);
    }

    public static CompletionStage<Iterator<Object>> spawnAgentStreaming(Object agentConfig, Object inputs,
                                                                        Object session, ModelContext context,
                                                                        List<StreamMode> streamModes,
                                                                        Map<String, Object> envs,
                                                                        Object spawnConfig) {
        return GLOBAL_RUNNER.spawnAgentStreaming(agentConfig, inputs, session, streamModes, spawnConfig);
    }

    public static CompletionStage<Object> runAgentTeam(Object agentTeam, Object inputs) {
        return runAgentTeam(agentTeam, inputs, false, false, null, null, null);
    }

    public static CompletionStage<Object> runAgentTeam(Object agentTeam, Object inputs, Object session,
                                                       ModelContext context, Map<String, Object> envs) {
        return runAgentTeam(agentTeam, inputs, false, false, session, context, envs);
    }

    public static CompletionStage<Object> runAgentTeam(Object agentTeam, Object inputs, boolean base, boolean member,
                                                       Object session, ModelContext context,
                                                       Map<String, Object> envs) {
        if (base) {
            return GLOBAL_RUNNER.runBaseTeam(agentTeam, inputs, session, context, envs);
        }
        return GLOBAL_RUNNER.runAgentTeam(agentTeam, inputs, member, session, context, envs);
    }

    public static CompletionStage<Iterator<Object>> runAgentTeamStreaming(Object agentTeam, Object inputs) {
        return runAgentTeamStreaming(agentTeam, inputs, false, false, null, null, null, null, null);
    }

    public static CompletionStage<Iterator<Object>> runAgentTeamStreaming(Object agentTeam, Object inputs,
                                                                          Object session,
                                                                          ModelContext context,
                                                                          List<StreamMode> streamModes,
                                                                          Map<String, Object> envs) {
        return runAgentTeamStreaming(agentTeam, inputs, false, false, session, context, streamModes, envs, null);
    }

    public static CompletionStage<Iterator<Object>> runAgentTeamStreaming(Object agentTeam, Object inputs,
                                                                          boolean base, boolean member,
                                                                          Object session,
                                                                          ModelContext context,
                                                                          List<StreamMode> streamModes,
                                                                          Map<String, Object> envs,
                                                                          TeamStreamLogger streamLogger) {
        if (base) {
            return GLOBAL_RUNNER.runBaseTeamStreaming(agentTeam, inputs, session, context, streamModes, envs);
        }
        return GLOBAL_RUNNER.runAgentTeamStreaming(
                agentTeam, inputs, member, session, context, streamModes, envs, streamLogger);
    }

    public static CompletionStage<DeliverResult> interactAgentTeam(Object payload, String teamName, String sessionId) {
        return GLOBAL_RUNNER.interactAgentTeam(payload, teamName, sessionId);
    }

    public static CompletionStage<Boolean> registerHumanAgentInbound(String teamName, String sessionId,
                                                                     String memberName, Object callback) {
        return GLOBAL_RUNNER.registerHumanAgentInbound(teamName, sessionId, memberName, callback);
    }

    public static CompletionStage<Boolean> pauseAgentTeam(String teamName, String sessionId) {
        return GLOBAL_RUNNER.pauseAgentTeam(teamName, sessionId);
    }

    public static CompletionStage<Boolean> stopAgentTeam(String teamName, String sessionId) {
        return GLOBAL_RUNNER.stopAgentTeam(teamName, sessionId);
    }

    public static CompletionStage<Object> getAgentTeamMonitor(String teamName, String sessionId, boolean hideDm) {
        return GLOBAL_RUNNER.getAgentTeamMonitor(teamName, sessionId, hideDm);
    }

    public static List<TeamRuntimeManager.RuntimeEntryInfo> listActiveTeams() {
        return GLOBAL_RUNNER.listActiveTeams();
    }

    public static CompletionStage<Boolean> deleteAgentTeam(String teamName, List<String> sessionIds, boolean force) {
        return GLOBAL_RUNNER.deleteAgentTeam(teamName, sessionIds, force);
    }

    private static <T> CompletionStage<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    /**
     * Instance implementation behind the static facade.
     *
     * <p>Mirrors Python's {@code _RunnerImpl} in
     * {@code openjiuwen/core/runner/runner.py}.</p>
     */
    static final class RunnerImpl {
        private static final String DEFAULT_AGENT_SESSION_ID = "default_session";
        private static final String AGENT_CONVERSATION_ID = "conversation_id";

        private final String runnerId;
        private final ResourceMgr resourceManager = new ResourceMgr();
        private final LocalMessageQueue messageQueue = new LocalMessageQueue();
        private final AsyncCallbackFramework callbackFramework = new AsyncCallbackFramework();
        private final Set<String> knownTeamNames = ConcurrentHashMap.newKeySet();
        private MessageQueueBase distributedMessageQueue;
        private ReplyTopicSubscription systemReplySub;
        private Object rootTaskGroup;
        private TeamRuntimeManager teamRuntimeManager;

        private RunnerImpl(RunnerConfig config) {
            this("global", config);
        }

        private RunnerImpl(String runnerId, RunnerConfig config) {
            this.runnerId = runnerId == null || runnerId.isBlank() ? "global" : runnerId;
            RunnerConfig.setRunnerConfig(config == null ? RunnerConfig.DEFAULT_RUNNER_CONFIG : config);
        }

        private ResourceMgr resourceMgr() {
            return resourceManager;
        }

        private LocalMessageQueue pubsub() {
            return messageQueue;
        }

        private Object distPubsub() {
            return distributedMessageQueue;
        }

        private ReplyTopicSubscription systemReplySub() {
            return systemReplySub;
        }

        private AsyncCallbackFramework callbackFramework() {
            return callbackFramework;
        }

        private Object getRootTaskGroup() {
            return rootTaskGroup;
        }

        private void setConfig(RunnerConfig config) {
            RunnerConfig.setRunnerConfig(config);
        }

        private RunnerConfig getConfig() {
            return RunnerConfig.getRunnerConfig();
        }

        private CompletionStage<Boolean> start() {
            return CompletableFuture.supplyAsync(() -> {
                RunnerConfig config = RunnerConfig.getRunnerConfig();
                initializeCheckpointer(config);
                if (config.isDistributedMode()) {
                    distributedMessageQueue = MessageQueueFactory.create(config.getDistributedConfig().getMessageQueueConfig());
                    distributedMessageQueue.start();
                    systemReplySub = new ReplyTopicSubscription(distributedMessageQueue);
                    systemReplySub.activate();
                    return messageQueue.start();
                }
                return true;
            });
        }

        private CompletionStage<Boolean> stop() {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (RunnerConfig.getRunnerConfig().isDistributedMode()) {
                        if (systemReplySub != null) {
                            systemReplySub.deactivate();
                            systemReplySub = null;
                        }
                        if (distributedMessageQueue != null) {
                            distributedMessageQueue.stop();
                            distributedMessageQueue = null;
                        }
                    }
                    return messageQueue.stop();
                } finally {
                    resourceManager.release().toCompletableFuture().join();
                    rootTaskGroup = null;
                }
            });
        }

        private CompletionStage<Object> runWorkflow(Object workflow, Object inputs, Object session,
                                                    ModelContext context, Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                PreparedWorkflow prepared = prepareWorkflow(workflow, session);
                return prepared.workflow().invoke(inputs, prepared.session(), context);
            });
        }

        private CompletionStage<Iterator<WorkflowChunk>> runWorkflowStreaming(
                Object workflow,
                Object inputs,
                Object session,
                ModelContext context,
                List<StreamMode> streamModes,
                Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                PreparedWorkflow prepared = prepareWorkflow(workflow, session);
                List<StreamMode> effectiveModes = streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes;
                return prepared.workflow().stream(inputs, prepared.session(), context, effectiveModes);
            });
        }

        private CompletionStage<Object> runAgent(Object agent, Object inputs, Object session,
                                                 ModelContext context, Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                PreparedAgent prepared = prepareAgent(agent, inputs, session);
                if (prepared.agent() instanceof RemoteAgent remoteAgent) {
                    return await(remoteAgent.invoke(asStringObjectMap(inputs)));
                }
                if (prepared.agent() instanceof BaseAgent baseAgent) {
                    Object result = await(baseAgent.invoke(inputs, prepared.agentSession()));
                    if (prepared.agentSessionFacade() != null) {
                        prepared.agentSessionFacade().postRun();
                    }
                    return result;
                }
                throw new IllegalArgumentException("unsupported agent type: " + prepared.agent());
            });
        }

        private CompletionStage<Iterator<Object>> runAgentStreaming(Object agent, Object inputs, Object session,
                                                                    ModelContext context,
                                                                    List<StreamMode> streamModes,
                                                                    Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                PreparedAgent prepared = prepareAgent(agent, inputs, session);
                List<StreamMode> effectiveModes = streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes;
                if (prepared.agent() instanceof RemoteAgent remoteAgent) {
                    return remoteAgent.stream(asStringObjectMap(inputs));
                }
                if (prepared.agent() instanceof BaseAgent baseAgent) {
                    Iterator<Object> iterator = baseAgent.stream(inputs, prepared.agentSession(), effectiveModes);
                    if (prepared.agentSessionFacade() != null) {
                        prepared.agentSessionFacade().postRun();
                    }
                    return iterator;
                }
                throw new IllegalArgumentException("unsupported agent type: " + prepared.agent());
            });
        }

        private CompletionStage<SpawnedProcessHandle> spawnAgent(
                Object agentConfig,
                Object inputs,
                Object session,
                Object spawnConfig) {
            if (!(agentConfig instanceof SpawnAgentConfig config)) {
                return failedFuture(new IllegalArgumentException("Runner.spawn_agent now requires SpawnAgentConfig."));
            }
            Map<String, Object> normalizedInputs = normalizeSpawnInputs(inputs);
            String sessionId = String.valueOf(normalizedInputs.getOrDefault(
                    AGENT_CONVERSATION_ID,
                    session instanceof String value ? value : DEFAULT_AGENT_SESSION_ID
            ));
            SpawnAgentConfig spawnPayload = SpawnAgentConfigs.parseSpawnAgentConfig(config.toMap());
            spawnPayload.setSessionId(sessionId);
            if (spawnPayload.getLoggingConfig() == null) {
                spawnPayload.setLoggingConfig(LoggingDefaults.getLogConfigSnapshot());
            }
            SpawnConfig resolvedConfig = spawnConfig instanceof SpawnConfig value ? value : null;
            return SpawnProcesses.spawnProcess(spawnPayload.toMap(), normalizedInputs, resolvedConfig)
                    .thenCompose(handle -> {
                        if (resolvedConfig != null) {
                            return handle.startHealthCheck().thenApply(ignored -> handle);
                        }
                        return CompletableFuture.completedFuture(handle);
                    });
        }

        private CompletionStage<Iterator<Object>> spawnAgentStreaming(
                Object agentConfig,
                Object inputs,
                Object session,
                List<StreamMode> streamModes,
                Object spawnConfig) {
            return spawnAgent(agentConfig, inputs, session, spawnConfig)
                    .thenApply(SpawnAgentIterator::new);
        }

        private CompletionStage<Void> release(String sessionId, boolean force) {
            return getTeamRuntimeManager()
                    .releaseSession(sessionId, force)
                    .thenRun(() -> CheckpointerFactory.getCheckpointer().release(sessionId));
        }

        private CompletionStage<Object> spawnDependencyPending(String methodName) {
            return failedFuture(new UnsupportedOperationException(
                    "Runner." + methodName + " depends on pending spawn process-manager translation."));
        }

        private CompletionStage<Object> runAgentTeam(Object agentTeam, Object inputs, boolean member,
                                                     Object session, ModelContext context,
                                                     Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                if (member) {
                    return await(runTeamMember(agentTeam, inputs, session));
                }
                TeamSpecAdapter spec = resolveTeamAgentSpec(agentTeam, session);
                AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, spec.teamName());
                TeamRuntimeActivation activation = await(getTeamRuntimeManager().activate(spec, teamSession, inputs));
                try {
                    rememberActivatedTeam(spec, activation);
                    if (isRejectKind(activation.action().kind())) {
                        return null;
                    }
                    return await(asTeamAgentRuntime(activation.agent()).invoke(inputs, activation.session()));
                } finally {
                    finalizeTeamActivation(spec.teamName(), activation, teamSession);
                }
            });
        }

        private CompletionStage<Iterator<Object>> runAgentTeamStreaming(
                Object agentTeam,
                Object inputs,
                boolean member,
                Object session,
                ModelContext context,
                List<StreamMode> streamModes,
                Map<String, Object> envs,
                TeamStreamLogger streamLogger) {
            return CompletableFuture.supplyAsync(() -> {
                if (member) {
                    return await(runTeamMemberStreaming(agentTeam, inputs, session, streamModes));
                }
                TeamSpecAdapter spec = resolveTeamAgentSpec(agentTeam, session);
                AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, spec.teamName());
                TeamRuntimeActivation activation = await(getTeamRuntimeManager().activate(spec, teamSession, inputs));
                List<Object> chunks = new java.util.ArrayList<>();
                try {
                    rememberActivatedTeam(spec, activation);
                    if (isRejectKind(activation.action().kind())) {
                        return chunks.iterator();
                    }
                    TeamAgentRuntimeAdapter runtime = asTeamAgentRuntime(activation.agent());
                    Object readyChunk = buildTeamRuntimeReadyChunk(spec.teamName(), activation, runtime.agent());
                    if (streamLogger != null) {
                        streamLogger.feed(readyChunk);
                    }
                    chunks.add(readyChunk);
                    Iterator<Object> stream = await(runtime.stream(inputs, activation.session(), streamModes));
                    while (stream.hasNext()) {
                        Object chunk = stream.next();
                        if (streamLogger != null) {
                            streamLogger.feed(chunk);
                        }
                        chunks.add(chunk);
                    }
                    return chunks.iterator();
                } finally {
                    if (streamLogger != null) {
                        streamLogger.flush();
                    }
                    finalizeTeamActivation(spec.teamName(), activation, teamSession);
                }
            });
        }

        private CompletionStage<Object> runBaseTeam(Object baseTeam, Object inputs, Object session,
                                                    ModelContext context, Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                BaseTeam team = await(prepareBaseTeam(baseTeam));
                AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, team.getTeamId());
                TeamRuntime runtime = team.getRuntime();
                teamSession.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null)
                        .toCompletableFuture()
                        .join();
                if (runtime != null) {
                    runtime.bindTeamSession(teamSession);
                }
                try {
                    return await(team.invoke(inputs, teamSession));
                } finally {
                    if (runtime != null) {
                        runtime.unbindTeamSession(teamSession.getSessionId());
                    }
                    teamSession.postRun();
                }
            });
        }

        private CompletionStage<Iterator<Object>> runBaseTeamStreaming(Object baseTeam, Object inputs, Object session,
                                                                       ModelContext context,
                                                                       List<StreamMode> streamModes,
                                                                       Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                BaseTeam team = await(prepareBaseTeam(baseTeam));
                AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, team.getTeamId());
                TeamRuntime runtime = team.getRuntime();
                teamSession.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null)
                        .toCompletableFuture()
                        .join();
                if (runtime != null) {
                    runtime.bindTeamSession(teamSession);
                }
                try {
                    Stream<Object> stream = team.stream(inputs, teamSession);
                    return stream == null ? List.<Object>of().iterator() : stream.toList().iterator();
                } finally {
                    if (runtime != null) {
                        runtime.unbindTeamSession(teamSession.getSessionId());
                    }
                    teamSession.postRun();
                }
            });
        }

        private CompletionStage<DeliverResult> interactAgentTeam(Object payload, String teamName, String sessionId) {
            if (teamName == null || sessionId == null) {
                return CompletableFuture.completedFuture(DeliverResult.failure("missing_target"));
            }
            return getTeamRuntimeManager().interact(payload, teamName, sessionId);
        }

        private CompletionStage<Boolean> registerHumanAgentInbound(String teamName, String sessionId,
                                                                   String memberName, Object callback) {
            return getTeamRuntimeManager().registerHumanAgentInbound(teamName, sessionId, memberName, callback);
        }

        private CompletionStage<Boolean> pauseAgentTeam(String teamName, String sessionId) {
            return getTeamRuntimeManager().pause(teamName, sessionId);
        }

        private CompletionStage<Boolean> stopAgentTeam(String teamName, String sessionId) {
            return getTeamRuntimeManager().stopTeam(teamName, sessionId);
        }

        private CompletionStage<Object> getAgentTeamMonitor(String teamName, String sessionId, boolean hideDm) {
            return getTeamRuntimeManager().getMonitor(teamName, sessionId, hideDm);
        }

        private List<TeamRuntimeManager.RuntimeEntryInfo> listActiveTeams() {
            return getTeamRuntimeManager().listActiveTeams();
        }

        private CompletionStage<Boolean> deleteAgentTeam(String teamName, List<String> sessionIds, boolean force) {
            return getTeamRuntimeManager().deleteTeam(teamName, sessionIds, force);
        }

        private CompletionStage<Object> runTeamMember(Object agent, Object inputs, Object session) {
            return CompletableFuture.supplyAsync(() -> {
                TeamAgentRuntimeAdapter runtime = adaptTeamAgent(agent);
                AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, runtime.teamName());
                teamSession.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null)
                        .toCompletableFuture()
                        .join();
                try {
                    return await(runtime.invoke(inputs, teamSession));
                } finally {
                    await(TeamRuntimeManager.finalizeMember(runtime));
                    teamSession.postRun();
                }
            });
        }

        private CompletionStage<Iterator<Object>> runTeamMemberStreaming(Object agent, Object inputs,
                                                                         Object session,
                                                                         List<StreamMode> streamModes) {
            return CompletableFuture.supplyAsync(() -> {
                TeamAgentRuntimeAdapter runtime = adaptTeamAgent(agent);
                AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, runtime.teamName());
                teamSession.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null)
                        .toCompletableFuture()
                        .join();
                try {
                    return await(runtime.stream(inputs, teamSession, streamModes));
                } finally {
                    await(TeamRuntimeManager.finalizeMember(runtime));
                    teamSession.postRun();
                }
            });
        }

        private TeamRuntimeManager getTeamRuntimeManager() {
            if (teamRuntimeManager == null) {
                teamRuntimeManager = new TeamRuntimeManager(
                        this::inspectTeamSession,
                        TeamRuntimeManager.RuntimeCleanup.noop(),
                        this::createTeamMonitor
                );
            }
            return teamRuntimeManager;
        }

        private CompletionStage<TeamRuntimeManager.SessionInspection> inspectTeamSession(
                TeamRuntimeManager.TeamSpecView spec,
                TeamRuntimeManager.AgentTeamSessionView session,
                String teamName) {
            boolean teamInSession = false;
            String teamDbState = null;
            if (session instanceof TeamRuntimeMetadata.SessionStateAccess stateAccess) {
                teamInSession = TeamRuntimeMetadata.readTeamNamespace(stateAccess, teamName) != null;
                teamDbState = TeamRuntimeMetadata.readTeamDbState(stateAccess, teamName);
            }
            boolean teamInDb = knownTeamNames.contains(teamName)
                    || TeamRuntimeMetadata.TEAM_DB_STATE_CREATED.equals(teamDbState);
            return CompletableFuture.completedFuture(new TeamRuntimeManager.SessionInspection(
                    teamInSession, teamInDb, teamDbState));
        }

        private Object createTeamMonitor(TeamRuntimeManager.TeamAgentRuntime agent, boolean hideDm) {
            if (agent instanceof TeamAgentRuntimeAdapter adapter) {
                return TeamMonitor.createMonitor(adapter.agent(), hideDm);
            }
            return agent;
        }

        private TeamSpecAdapter resolveTeamAgentSpec(Object agentTeam, Object session) {
            if (agentTeam instanceof TeamAgentSpec spec) {
                return new TeamSpecAdapter(spec);
            }
            if (agentTeam instanceof String teamName) {
                TeamRuntimeManager.RuntimeEntry entry = getTeamRuntimeManager().pool().get(teamName);
                if (entry != null && entry.agent() instanceof TeamAgentRuntimeAdapter adapter
                        && adapter.spec() != null) {
                    return adapter.spec();
                }
                TeamSpecAdapter specFromBucket = resolveSpecFromSessionBucket(teamName, session);
                if (specFromBucket != null) {
                    return specFromBucket;
                }
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_TEAM_CONFIG_INVALID,
                        "reason",
                        "team '" + teamName + "' has no live pool entry and no persisted spec in the supplied session; "
                                + "first-time runs must pass a TeamAgentSpec on a new session"
                );
            }
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    "reason",
                    "run_agent_team accepts str | TeamAgentSpec; got "
                            + typeName(agentTeam) + ". For BaseTeam pass base=True."
            );
        }

        private TeamSpecAdapter resolveSpecFromSessionBucket(String teamName, Object session) {
            if (session == null) {
                return null;
            }
            AgentTeamSessionAdapter teamSession = createAgentTeamSession(session, teamName);
            try {
                teamSession.preRun(null).toCompletableFuture().join();
                Map<String, Object> bucket = TeamRuntimeMetadata.readTeamNamespace(teamSession, teamName);
                if (bucket == null || bucket.get("spec") == null) {
                    return null;
                }
                TeamAgentSpec spec = new TeamAgentSpec();
                spec.setTeamName(teamName);
                return new TeamSpecAdapter(spec);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private CompletionStage<BaseTeam> prepareBaseTeam(Object baseTeam) {
            if (baseTeam instanceof String teamId) {
                return resourceManager.getAgentTeam(teamId).thenApply(resolved -> {
                    if (resolved instanceof BaseTeam team) {
                        return team;
                    }
                    throw ErrorHelper.buildError(
                            StatusCode.AGENT_TEAM_CONFIG_INVALID,
                            "reason",
                            "team '" + teamId + "' is not a BaseTeam"
                    );
                });
            }
            if (baseTeam instanceof BaseTeam team) {
                return CompletableFuture.completedFuture(team);
            }
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    "reason",
                    "run_agent_team(base=True) accepts str | BaseTeam; got "
                            + typeName(baseTeam) + ". For TeamAgentSpec drop base=True."
            );
        }

        private AgentTeamSessionAdapter createAgentTeamSession(Object session, String teamId) {
            if (session instanceof AgentTeamSessionAdapter adapter) {
                return adapter;
            }
            if (session instanceof AgentTeamSession teamSession) {
                return new AgentTeamSessionAdapter(teamSession);
            }
            if (session instanceof AgentSession agentSession) {
                return new AgentTeamSessionAdapter(AgentTeamSession.createAgentTeamSession(
                        agentSession.getSessionId(), agentSession.getEnvs(), teamId));
            }
            if (session instanceof String sessionId) {
                return new AgentTeamSessionAdapter(AgentTeamSession.createAgentTeamSession(sessionId, null, teamId));
            }
            return new AgentTeamSessionAdapter(AgentTeamSession.createAgentTeamSession(null, null, teamId));
        }

        private void rememberActivatedTeam(TeamSpecAdapter spec, TeamRuntimeActivation activation) {
            if (activation != null && activation.agent() != null && !isRejectKind(activation.action().kind())) {
                knownTeamNames.add(spec.teamName());
            }
        }

        private void finalizeTeamActivation(String teamName, TeamRuntimeActivation activation,
                                            AgentTeamSessionAdapter teamSession) {
            try {
                if (activation != null) {
                    String sessionId = activation.session() == null ? teamSession.getSessionId()
                            : activation.session().getSessionId();
                    await(getTeamRuntimeManager().finalizeTeam(teamName, sessionId));
                    closeTeamInteractGate(teamName, sessionId);
                }
            } finally {
                teamSession.postRun();
            }
        }

        private void closeTeamInteractGate(String teamName, String sessionId) {
            TeamRuntimeManager.RuntimeEntry entry = getTeamRuntimeManager().pool().get(teamName);
            if (entry == null || !Objects.equals(entry.currentSessionId(), sessionId)) {
                return;
            }
            entry.interactGate().closeAndDrain();
        }

        private static boolean isRejectKind(RunActionKind kind) {
            return kind == RunActionKind.REJECT_RUNNING
                    || kind == RunActionKind.REJECT_ORPHANED
                    || kind == RunActionKind.REJECT_INCONSISTENT;
        }

        private static TeamAgentRuntimeAdapter asTeamAgentRuntime(TeamRuntimeManager.TeamAgentRuntime runtime) {
            if (runtime instanceof TeamAgentRuntimeAdapter adapter) {
                return adapter;
            }
            throw new IllegalStateException("team runtime agent is not a Runner adapter: " + typeName(runtime));
        }

        private static TeamAgentRuntimeAdapter adaptTeamAgent(Object agent) {
            if (agent instanceof TeamAgentRuntimeAdapter adapter) {
                return adapter;
            }
            if (agent instanceof TeamAgent teamAgent) {
                return new TeamAgentRuntimeAdapter(null, teamAgent);
            }
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_TEAM_CONFIG_INVALID,
                    "reason",
                    "run_agent_team(member=True) accepts TeamAgent; got " + typeName(agent)
            );
        }

        private static Object buildTeamRuntimeReadyChunk(
                String teamName,
                TeamRuntimeActivation activation,
                TeamAgent agent) {
            TeamRole role = agent == null ? null : agent.getRole();
            String leaderMemberName = agent == null ? null : agent.getMemberName();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_type", "team.runtime_ready");
            payload.put("team_name", teamName);
            payload.put("session_id", activation.session().getSessionId());
            payload.put("activation_kind", activation.action().kind().getValue());
            return new TeamOutputSchema("message", 0, payload, leaderMemberName, role);
        }

        private static Map<String, Object> copyStringMap(Map<?, ?> values) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    result.put(key, entry.getValue());
                }
            }
            return result;
        }

        private static String typeName(Object value) {
            return value == null ? "null" : value.getClass().getSimpleName();
        }

        /**
         * Adapter from the public TeamAgentSpec model to the runtime manager's narrow spec view.
         *
         * <p>Mirrors Python's {@code _resolve_team_agent_spec} output in
         * {@code openjiuwen/core/runner/team_runner.py}.</p>
         */
        private static final class TeamSpecAdapter implements TeamRuntimeManager.TeamSpecView {
            private final TeamAgentSpec spec;

            private TeamSpecAdapter(TeamAgentSpec spec) {
                this.spec = Objects.requireNonNull(spec, "spec");
            }

            @Override
            public String teamName() {
                return spec.getTeamName();
            }

            @Override
            public TeamRuntimeManager.TeamAgentRuntime build() {
                return new TeamAgentRuntimeAdapter(this, spec.build());
            }

            @Override
            public TeamRuntimeManager.TeamAgentRuntime recoverFromSession(
                    TeamRuntimeManager.AgentTeamSessionView session,
                    String teamName) {
                if (session instanceof SessionManager.AgentTeamSessionView sessionView) {
                    return new TeamAgentRuntimeAdapter(this, TeamAgent.recoverFromSession(sessionView, teamName, spec));
                }
                return build();
            }
        }

        /**
         * Adapter from TeamAgent to the runtime manager's lifecycle surface plus invoke/stream helpers.
         *
         * <p>Mirrors Python's leader/member TeamAgent object used by
         * {@code openjiuwen/core/runner/team_runner.py}.</p>
         */
        private static final class TeamAgentRuntimeAdapter implements TeamRuntimeManager.TeamAgentRuntime {
            private final TeamSpecAdapter spec;
            private final TeamAgent agent;

            private TeamAgentRuntimeAdapter(TeamSpecAdapter spec, TeamAgent agent) {
                this.spec = spec;
                this.agent = Objects.requireNonNull(agent, "agent");
            }

            private TeamSpecAdapter spec() {
                return spec;
            }

            private TeamAgent agent() {
                return agent;
            }

            private String teamName() {
                if (spec != null) {
                    return spec.teamName();
                }
                if (agent.getTeamName() != null) {
                    return agent.getTeamName();
                }
                return "agent_team";
            }

            private CompletionStage<Object> invoke(Object inputs, TeamRuntimeManager.AgentTeamSessionView session) {
                return stream(inputs, session, null).thenApply(iterator -> {
                    Object last = null;
                    while (iterator.hasNext()) {
                        last = iterator.next();
                    }
                    return last;
                });
            }

            private CompletionStage<Iterator<Object>> stream(
                    Object inputs,
                    TeamRuntimeManager.AgentTeamSessionView session,
                    List<StreamMode> streamModes) {
                SessionManager.AgentTeamSessionView sessionView = asAgentSessionView(session);
                return agent.stream(inputs, sessionView, streamModes)
                        .thenApply(chunks -> chunks == null ? List.<Object>of().iterator() : chunks.iterator());
            }

            @Override
            public CompletionStage<Void> deliverInput(String body) {
                return agent.deliverInput(body);
            }

            @Override
            public CompletionStage<Void> pauseCoordination() {
                return agent.pauseCoordination();
            }

            @Override
            public CompletionStage<Void> stopCoordination() {
                return agent.stopCoordination();
            }

            @Override
            public CompletionStage<Boolean> isShutdownRequested() {
                return agent.isShutdownRequested();
            }

            @Override
            public String lifecycle() {
                return agent.getLifecycle();
            }

            @Override
            public boolean hasPendingInterrupt() {
                return agent.hasPendingInterrupt();
            }

            @Override
            public CompletionStage<Void> resumeInterrupt(com.openjiuwen.core.session.interaction.InteractiveInput input) {
                return agent.resumeInterrupt(input);
            }

            @Override
            public CompletionStage<Void> autoStartAll() {
                return agent.autoStartAll().thenApply(ignored -> null);
            }

            @Override
            public CompletionStage<Void> autoStartMember(String memberName) {
                return agent.autoStartMember(memberName).thenApply(ignored -> null);
            }

            @Override
            public CompletionStage<TeamRuntimeManager.TeamAgentRuntime> lookupHumanAgentRuntime(String memberName) {
                return agent.lookupHumanAgentRuntime(memberName).thenApply(runtime -> {
                    if (runtime instanceof TeamAgent teamAgent) {
                        return new TeamAgentRuntimeAdapter(null, teamAgent);
                    }
                    return null;
                });
            }

            @Override
            public CompletionStage<Void> resumeForNewSession(TeamRuntimeManager.AgentTeamSessionView session) {
                return agent.resumeForNewSession(asAgentSessionView(session));
            }

            @Override
            public CompletionStage<Void> recoverTeam() {
                return agent.recoverTeam();
            }

            @Override
            public void persistSessionManifest(TeamRuntimeManager.AgentTeamSessionView session) {
                agent.persistSessionManifest(asAgentSessionView(session));
            }

            @Override
            public TeamRuntimeManager.TeamMemberRuntime teamMember() {
                TeamMember member = agent.getTeamMember();
                return member == null ? null : new TeamMemberRuntimeAdapter(member);
            }

            private static SessionManager.AgentTeamSessionView asAgentSessionView(
                    TeamRuntimeManager.AgentTeamSessionView session) {
                if (session instanceof SessionManager.AgentTeamSessionView sessionView) {
                    return sessionView;
                }
                throw new IllegalArgumentException("session must implement AgentTeamSessionView");
            }
        }

        /**
         * Adapter for TeamMember lifecycle status used by finalize_member.
         *
         * <p>Mirrors Python's member status transitions in
         * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
         */
        private static final class TeamMemberRuntimeAdapter implements TeamRuntimeManager.TeamMemberRuntime {
            private final TeamMember member;

            private TeamMemberRuntimeAdapter(TeamMember member) {
                this.member = member;
            }

            @Override
            public CompletionStage<TeamRuntimeManager.MemberStatus> status() {
                return member.status().thenApply(TeamMemberRuntimeAdapter::toRuntimeStatus);
            }

            @Override
            public CompletionStage<Void> updateStatus(TeamRuntimeManager.MemberStatus status) {
                com.openjiuwen.agent_teams.schema.status.MemberStatus target = toSchemaStatus(status);
                if (target == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return member.updateStatus(target).thenApply(ignored -> null);
            }

            private static TeamRuntimeManager.MemberStatus toRuntimeStatus(
                    com.openjiuwen.agent_teams.schema.status.MemberStatus status) {
                if (status == null) {
                    return null;
                }
                return switch (status) {
                    case READY -> TeamRuntimeManager.MemberStatus.READY;
                    case STOPPED -> TeamRuntimeManager.MemberStatus.STOPPED;
                    case PAUSED -> TeamRuntimeManager.MemberStatus.PAUSED;
                    case SHUTDOWN -> TeamRuntimeManager.MemberStatus.SHUTDOWN;
                    case SHUTDOWN_REQUESTED -> TeamRuntimeManager.MemberStatus.SHUTDOWN_REQUESTED;
                    default -> null;
                };
            }

            private static com.openjiuwen.agent_teams.schema.status.MemberStatus toSchemaStatus(
                    TeamRuntimeManager.MemberStatus status) {
                if (status == null) {
                    return null;
                }
                return switch (status) {
                    case READY -> com.openjiuwen.agent_teams.schema.status.MemberStatus.READY;
                    case STOPPED -> com.openjiuwen.agent_teams.schema.status.MemberStatus.STOPPED;
                    case PAUSED -> com.openjiuwen.agent_teams.schema.status.MemberStatus.PAUSED;
                    case SHUTDOWN -> com.openjiuwen.agent_teams.schema.status.MemberStatus.SHUTDOWN;
                    case SHUTDOWN_REQUESTED -> com.openjiuwen.agent_teams.schema.status.MemberStatus.SHUTDOWN_REQUESTED;
                    default -> null;
                };
            }
        }

        /**
         * Session adapter shared by TeamRuntimeManager, TeamAgent, and BaseTeam.
         *
         * <p>Mirrors Python's {@code create_agent_team_session(...)} use in
         * {@code openjiuwen/core/runner/team_runner.py}.</p>
         */
        private static final class AgentTeamSessionAdapter implements TeamRuntimeManager.AgentTeamSessionView,
                SessionManager.AgentTeamSessionView, AgentSessionApi {
            private final AgentTeamSession session;

            private AgentTeamSessionAdapter(AgentTeamSession session) {
                this.session = Objects.requireNonNull(session, "session");
            }

            @Override
            public String getSessionId() {
                return session.getSessionId();
            }

            @Override
            public CompletionStage<Void> preRun(Map<String, Object> inputs) {
                session.preRun(inputs == null ? null : Map.of("inputs", inputs));
                return CompletableFuture.completedFuture(null);
            }

            private void postRun() {
                session.postRun();
            }

            @Override
            public CompletionStage<Void> flushCheckpoint() {
                session.flushCheckpoint();
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public Object getState(String key) {
                return session.getState(key);
            }

            @Override
            public void updateState(Map<String, Object> data) {
                session.updateState(data);
            }

            @Override
            public void writeStream(Object data) {
                session.writeStream(data);
            }

            @Override
            public Iterator<Object> streamIterator() {
                return session.streamIterator();
            }
        }

        private PreparedWorkflow prepareWorkflow(Object workflow, Object session) {
            Object workflowSession = createWorkflowSession(session);
            if (workflow instanceof String workflowId) {
                Object resolved = await(resourceManager.getWorkflow(workflowId, workflowSession));
                if (!(resolved instanceof Workflow workflowInstance)) {
                    throw ErrorHelper.buildError(
                            StatusCode.WORKFLOW_EXECUTION_ERROR,
                            "workflow", workflowId,
                            "reason", "workflow not exist");
                }
                return new PreparedWorkflow(workflowInstance, workflowSession);
            }
            if (workflow instanceof Workflow workflowInstance) {
                return new PreparedWorkflow(workflowInstance, workflowSession);
            }
            throw ErrorHelper.buildError(
                    StatusCode.WORKFLOW_EXECUTION_ERROR,
                    "workflow", String.valueOf(workflow),
                    "reason", "unsupported workflow type");
        }

        private Object createWorkflowSession(Object session) {
            if (session == null) {
                return new WorkflowSessionApi();
            }
            if (session instanceof String sessionId) {
                return new WorkflowSessionApi(null, sessionId, null);
            }
            if (session instanceof AgentSession agentSession) {
                return agentSession.createWorkflowSession();
            }
            return session;
        }

        private PreparedAgent prepareAgent(Object agent, Object inputs, Object session) {
            if (session instanceof AgentSession agentSession) {
                if (agent instanceof String agentId) {
                    Object resolved = await(resourceManager.getAgent(agentId));
                    if (resolved == null) {
                        throw missingAgent(agentId);
                    }
                    agentSession.preRun(inputKwargs(inputs));
                    return new PreparedAgent(resolved, agentSession, agentSession);
                }
                if (agent instanceof BaseAgent baseAgent) {
                    agentSession.preRun(inputKwargs(inputs));
                    return new PreparedAgent(baseAgent, agentSession, agentSession);
                }
            }

            Map<String, Object> inputMap = asStringObjectMap(inputs);
            String sessionId = inputMap.containsKey(AGENT_CONVERSATION_ID)
                    ? String.valueOf(inputMap.get(AGENT_CONVERSATION_ID))
                    : session instanceof String value ? value : DEFAULT_AGENT_SESSION_ID;

            Object resolvedAgent = agent;
            if (agent instanceof String agentId) {
                resolvedAgent = await(resourceManager.getAgent(agentId));
                if (resolvedAgent == null) {
                    throw missingAgent(agentId);
                }
                if (resolvedAgent instanceof RemoteAgent) {
                    inputMap.putIfAbsent(AGENT_CONVERSATION_ID, sessionId);
                    syncStringObjectMap(inputs, inputMap);
                    return new PreparedAgent(resolvedAgent, null, null);
                }
            }

            if (resolvedAgent instanceof BaseAgent baseAgent) {
                AgentSession agentSession = createAgentSession(baseAgent, sessionId);
                agentSession.preRun(inputKwargs(inputs));
                return new PreparedAgent(baseAgent, agentSession, agentSession);
            }
            if (resolvedAgent instanceof RemoteAgent) {
                inputMap.putIfAbsent(AGENT_CONVERSATION_ID, sessionId);
                syncStringObjectMap(inputs, inputMap);
                return new PreparedAgent(resolvedAgent, null, null);
            }
            throw new IllegalArgumentException("unsupported agent type: " + resolvedAgent);
        }

        private AgentSession createAgentSession(BaseAgent agent, String sessionId) {
            AgentCard card = agent == null ? null : agent.getCard();
            return AgentSession.createAgentSession(sessionId, null, card);
        }

        private RuntimeException missingAgent(String agentId) {
            return ErrorHelper.buildError(
                    StatusCode.RUNNER_RUN_AGENT_ERROR,
                    "agent", agentId,
                    "reason", "agent not exist");
        }

        private static void initializeCheckpointer(RunnerConfig config) {
            if (config == null || config.getCheckpointerConfig() == null) {
                return;
            }
            Checkpointer checkpointer = CheckpointerFactory.create(config.getCheckpointerConfig());
            CheckpointerFactory.setDefaultCheckpointer(checkpointer);
        }

        private static Map<String, Object> inputKwargs(Object inputs) {
            Map<String, Object> kwargs = new LinkedHashMap<>();
            kwargs.put("inputs", inputs);
            return kwargs;
        }

        private static Map<String, Object> normalizeSpawnInputs(Object inputs) {
            if (inputs instanceof Map<?, ?> rawMap) {
                return copyStringMap(rawMap);
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("data", inputs);
            return wrapped;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> asStringObjectMap(Object value) {
            if (!(value instanceof Map<?, ?> rawMap)) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            syncStringObjectMap(value, result);
            return result;
        }

        @SuppressWarnings("unchecked")
        private static void syncStringObjectMap(Object value, Map<String, Object> normalized) {
            if (!(value instanceof Map<?, ?>)) {
                return;
            }
            try {
                ((Map<Object, Object>) value).clear();
                normalized.forEach(((Map<Object, Object>) value)::put);
            } catch (UnsupportedOperationException ignored) {
                // Immutable inputs are normalized for use inside Runner without mutating the caller.
            }
        }

        private static <T> T await(CompletionStage<T> stage) {
            try {
                return stage.toCompletableFuture().get();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CompletionException(interrupted);
            } catch (ExecutionException error) {
                Throwable cause = error.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new CompletionException(cause);
            }
        }

        private static final class SpawnAgentIterator implements Iterator<Object> {
            private final SpawnedProcessHandle handle;
            private boolean first = true;
            private boolean finished;

            private SpawnAgentIterator(SpawnedProcessHandle handle) {
                this.handle = handle;
            }

            @Override
            public boolean hasNext() {
                return !finished && (first || handle.isAlive());
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                if (first) {
                    first = false;
                    return handle;
                }
                SpawnMessage message = handle.receiveMessage().toCompletableFuture().join();
                if (message == null) {
                    finished = true;
                    throw new NoSuchElementException();
                }
                if (message.getType() == SpawnMessageType.DONE || message.getType() == SpawnMessageType.ERROR) {
                    finished = true;
                }
                return message.getPayload();
            }
        }
    }

    /**
     * Prepared workflow and session pair.
     *
     * <p>Mirrors Python's workflow/session tuple prepared by {@code _prepare_workflow} in
     * {@code openjiuwen/core/runner/runner.py}.</p>
     */
    private record PreparedWorkflow(Workflow workflow, Object session) {
    }

    /**
     * Prepared agent, API session, and facade session tuple.
     *
     * <p>Mirrors Python's agent/session tuple prepared by {@code _prepare_agent} in
     * {@code openjiuwen/core/runner/runner.py}.</p>
     */
    private record PreparedAgent(Object agent, AgentSessionApi agentSession, AgentSession agentSessionFacade) {
    }
}
