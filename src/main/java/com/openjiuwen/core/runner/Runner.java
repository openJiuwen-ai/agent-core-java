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
import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.team_runtime.TeamRuntime;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.runner.callback.AsyncCallbackFramework;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageQueueFactory;
import com.openjiuwen.core.runner.drunner.remote_client.RemoteAgent;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfigs;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;
import com.openjiuwen.core.runner.spawn.SpawnMessage;
import com.openjiuwen.core.runner.spawn.SpawnMessageType;
import com.openjiuwen.core.runner.spawn.SpawnProcesses;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.WorkflowSession;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    public static LocalMessageQueue pubsub() {
        return getPubsub();
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

    public static AsyncCallbackFramework callbackFramework() {
        return getCallbackFramework();
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

    public static Object runWorkflow(Object workflow, Object inputs, Object session,
                                     com.openjiuwen.core.context.ModelContext context) {
        return joinOrThrow(runWorkflow(workflow, inputs, session,
                com.openjiuwen.core.context.ModelContext.unwrap(context), null));
    }

    public static CompletionStage<Object> runWorkflow(Object workflow, Object inputs, Object session,
                                                      ModelContext context, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, envs);
    }

    /**
     * Execute a workflow with tenant context binding.
     *
     * @since 0.1.7
     */
    public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context,
                                     Map<String, Object> envs, TenantContext tenantCtx) {
        return joinOrThrow(GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, envs, tenantCtx));
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

    /**
     * Execute a workflow with streaming output and tenant context binding.
     *
     * @since 0.1.7
     */
    public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
                                                               ModelContext context, List<StreamMode> streamModes,
                                                               Map<String, Object> envs, TenantContext tenantCtx) {
        return joinOrThrow(GLOBAL_RUNNER.runWorkflowStreaming(
                workflow, inputs, session, context, streamModes, envs, tenantCtx));
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

    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context) {
        return joinOrThrow(runAgent(agent, inputs, session, context, null));
    }

    /**
     * Execute an agent with tenant context binding.
     *
     * @since 0.1.7
     */
    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context,
                                  Map<String, Object> envs, TenantContext tenantCtx) {
        return joinOrThrow(GLOBAL_RUNNER.runAgent(agent, inputs, session, context, envs, tenantCtx));
    }

    public static CompletionStage<Iterator<Object>> runAgentStreaming(Object agent, Object inputs, Object session,
                                                                      ModelContext context,
                                                                      List<StreamMode> streamModes,
                                                                      Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, envs);
    }

    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
                                                     List<StreamMode> streamModes) {
        return joinOrThrow(runAgentStreaming(agent, inputs, session, context, streamModes, null));
    }

    /**
     * Execute an agent with streaming output and tenant context binding.
     *
     * @since 0.1.7
     */
    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
                                                     List<StreamMode> streamModes, Map<String, Object> envs,
                                                     TenantContext tenantCtx) {
        return joinOrThrow(GLOBAL_RUNNER.runAgentStreaming(
                agent, inputs, session, context, streamModes, envs, tenantCtx));
    }

    /**
     * Reactive version of {@link #runAgent(Object, Object, Object, ModelContext, Map)}.
     *
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param envs environment values, nullable
     * @return Mono emitting the agent result
     */
    public static Mono<Object> runAgentAsync(Object agent, Object inputs, Object session,
                                             ModelContext context, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentAsync(agent, inputs, session, context, envs);
    }

    /**
     * Reactive version of {@link #runAgentStreaming(Object, Object, Object, ModelContext, List, Map)}.
     *
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param streamModes stream output modes
     * @param envs environment values, nullable
     * @return Flux emitting stream chunks
     */
    public static Flux<Object> runAgentStreamingAsync(Object agent, Object inputs, Object session,
                                                      ModelContext context, List<StreamMode> streamModes,
                                                      Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentStreamingAsync(agent, inputs, session, context, streamModes, envs);
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

    public static Object runAgentTeam(String agentTeam, Object inputs, Object session) {
        return joinOrThrow(runAgentTeam(agentTeam, inputs, true, false, session, null, null));
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

    /**
     * Execute an agent team with tenant context binding.
     *
     * @since 0.1.7
     */
    public static Object runAgentTeam(Object agentTeam, Object inputs, Object session, ModelContext context,
                                      Map<String, Object> envs, TenantContext tenantCtx) {
        return joinOrThrow(GLOBAL_RUNNER.runAgentTeam(agentTeam, inputs, false, session, context, envs, tenantCtx));
    }

    public static CompletionStage<Iterator<Object>> runAgentTeamStreaming(Object agentTeam, Object inputs) {
        return runAgentTeamStreaming(agentTeam, inputs, false, false, null, null, null, null, null);
    }

    public static Iterator<Object> runAgentTeamStreaming(String agentTeam, Object inputs, Object session) {
        return joinOrThrow(runAgentTeamStreaming(agentTeam, inputs, true, false, session, null, null, null, null));
    }

    public static Iterator<Object> runAgentTeamStreaming(TeamAgentSpec agentTeam, Map<String, String> inputs,
                                                         String conversationId) {
        return runAgentTeamStreaming(
                agentTeam, inputs, false, false, conversationId, null, null, null, null)
                .toCompletableFuture()
                .join();
    }

    public static Iterator<Object> runAgentTeamStreaming(TeamAgent agentTeam, Map<String, String> inputs,
                                                         String conversationId) {
        return runAgentTeamStreaming(
                agentTeam, inputs, false, true, conversationId, null, null, null, null)
                .toCompletableFuture()
                .join();
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

    private static <T> T joinOrThrow(CompletionStage<T> stage) {
        try {
            return stage.toCompletableFuture().join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error fatalError) {
                throw fatalError;
            }
            throw error;
        }
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
        private boolean defaultCheckpointerInstalledFromConfig;
        private TenantWorkspaceResolver workspaceResolver;

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
                try {
                    if (config.isEnableTenantIsolation()) {
                        String dataRoot = config.getTenantDataRoot();
                        if (dataRoot == null || dataRoot.isEmpty()) {
                            dataRoot = System.getProperty("user.dir");
                        }
                        workspaceResolver = new TenantWorkspaceResolver(dataRoot);
                    } else {
                        workspaceResolver = null;
                    }
                    if (config.isDistributedMode()) {
                        distributedMessageQueue = MessageQueueFactory.create(config.getDistributedConfig().getMessageQueueConfig());
                        distributedMessageQueue.start();
                        systemReplySub = new ReplyTopicSubscription(distributedMessageQueue);
                        systemReplySub.activate();
                        return messageQueue.start();
                    }
                    return true;
                } catch (RuntimeException | Error error) {
                    releaseConfiguredCheckpointerOnStartFailure(error);
                    throw error;
                }
            });
        }

        private CompletionStage<Boolean> stop() {
            try {
                boolean stopped;
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
                stopped = messageQueue.stop();
                return CompletableFuture.completedFuture(stopped);
            } finally {
                resourceManager.release().toCompletableFuture().join();
                releaseConfiguredCheckpointer();
                rootTaskGroup = null;
            }
        }

        private void bindTenantContext(TenantContext ctx) {
            if (ctx != null && ctx.isTenantAware()) {
                TenantContextHolder.setCurrentTenant(ctx);
                if (workspaceResolver != null) {
                    workspaceResolver.initializeTenantSpace(ctx);
                    Path tenantWorkspace = workspaceResolver.resolveWorkspaceRoot(ctx);
                    String workspace = tenantWorkspace.toString();
                    String tenantRoot = workspaceResolver.resolveTenantRoot(ctx).toString();
                    Cwd.setWorkspace(workspace);
                    Cwd.setOriginalCwd(workspace);
                    Cwd.setTenantRoot(tenantRoot);
                    CwdContext.setWorkspace(workspace);
                    CwdContext.setOriginalCwd(workspace);
                    CwdContext.setTenantRoot(tenantRoot);
                }
            }
        }

        private void unbindTenantContext() {
            TenantContextHolder.clearCurrentTenant();
            Cwd.clear();
            CwdContext.reset();
        }

        private Optional<TenantContext> resolveTenantContext(Object session, TenantContext explicitTenantCtx) {
            if (explicitTenantCtx != null) {
                return Optional.of(explicitTenantCtx);
            }
            if (session instanceof AgentSessionApi agentSession) {
                TenantContext sessionCtx = agentSession.getTenantContext();
                if (sessionCtx != null && sessionCtx.isTenantAware()) {
                    return Optional.of(sessionCtx);
                }
            }
            return Optional.empty();
        }

        private <T> Iterator<T> wrapTenantUnbindIterator(Iterator<T> delegate) {
            class TenantUnbindIterator implements Iterator<T>, AutoCloseable {
                private boolean isUnbound;

                @Override
                public boolean hasNext() {
                    boolean hasNext = delegate != null && delegate.hasNext();
                    if (!hasNext) {
                        unbind();
                    }
                    return hasNext;
                }

                @Override
                public T next() {
                    try {
                        if (delegate == null || !delegate.hasNext()) {
                            unbind();
                            throw new NoSuchElementException();
                        }
                        T next = delegate.next();
                        if (!delegate.hasNext()) {
                            unbind();
                        }
                        return next;
                    } catch (NoSuchElementException e) {
                        unbind();
                        throw e;
                    } catch (RuntimeException e) {
                        unbind();
                        throw e;
                    }
                }

                private void unbind() {
                    if (!isUnbound) {
                        unbindTenantContext();
                        isUnbound = true;
                    }
                }

                @Override
                public void close() throws Exception {
                    try {
                        if (delegate instanceof AutoCloseable closeable) {
                            closeable.close();
                        }
                    } finally {
                        unbind();
                    }
                }
            }
            return new TenantUnbindIterator();
        }

        private CompletionStage<Object> runWorkflow(Object workflow, Object inputs, Object session,
                                                    ModelContext context, Map<String, Object> envs) {
            return runWorkflow(workflow, inputs, session, context, envs, null);
        }

        private CompletionStage<Object> runWorkflow(Object workflow, Object inputs, Object session,
                                                    ModelContext context, Map<String, Object> envs,
                                                    TenantContext tenantCtx) {
            return CompletableFuture.supplyAsync(() -> {
                Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
                boolean bound = resolved.isPresent();
                if (bound) {
                    bindTenantContext(resolved.get());
                }
                try {
                    PreparedWorkflow prepared = prepareWorkflow(workflow, session);
                    return prepared.workflow().invoke(inputs, prepared.session(), context);
                } finally {
                    if (bound) {
                        unbindTenantContext();
                    }
                }
            });
        }

        private CompletionStage<Iterator<WorkflowChunk>> runWorkflowStreaming(
                Object workflow,
                Object inputs,
                Object session,
                ModelContext context,
                List<StreamMode> streamModes,
                Map<String, Object> envs) {
            return runWorkflowStreaming(workflow, inputs, session, context, streamModes, envs, null);
        }

        private CompletionStage<Iterator<WorkflowChunk>> runWorkflowStreaming(
                Object workflow,
                Object inputs,
                Object session,
                ModelContext context,
                List<StreamMode> streamModes,
                Map<String, Object> envs,
                TenantContext tenantCtx) {
            return CompletableFuture.supplyAsync(() -> {
                Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
                boolean bound = resolved.isPresent();
                if (bound) {
                    bindTenantContext(resolved.get());
                }
                boolean isSuccessful = false;
                try {
                    PreparedWorkflow prepared = prepareWorkflow(workflow, session);
                    List<StreamMode> effectiveModes = streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes;
                    Iterator<WorkflowChunk> iterator =
                            prepared.workflow().stream(inputs, prepared.session(), context, effectiveModes);
                    if (bound) {
                        iterator = wrapTenantUnbindIterator(iterator);
                    }
                    isSuccessful = true;
                    return iterator;
                } finally {
                    if (bound && !isSuccessful) {
                        unbindTenantContext();
                    }
                }
            });
        }

        private CompletionStage<Object> runAgent(Object agent, Object inputs, Object session,
                                                 ModelContext context, Map<String, Object> envs) {
            return runAgent(agent, inputs, session, context, envs, null);
        }

        private CompletionStage<Object> runAgent(Object agent, Object inputs, Object session,
                                                 ModelContext context, Map<String, Object> envs,
                                                 TenantContext tenantCtx) {
            return CompletableFuture.supplyAsync(() -> {
                Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
                boolean bound = resolved.isPresent();
                if (bound) {
                    bindTenantContext(resolved.get());
                }
                try {
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
                    if (prepared.agent() instanceof com.openjiuwen.core.singleagent.legacy.agent.BaseAgent legacyAgent) {
                        Object result = await(legacyAgent.invoke(asStringObjectMap(inputs), prepared.agentSession()));
                        if (prepared.agentSessionFacade() != null) {
                            prepared.agentSessionFacade().postRun();
                        }
                        return result;
                    }
                    if (isDuckTypedAgent(prepared.agent())) {
                        Object result = invokeDuckTypedAgent(prepared.agent(), inputs, prepared.agentSession(), context);
                        if (prepared.agentSessionFacade() != null) {
                            prepared.agentSessionFacade().postRun();
                        }
                        return result;
                    }
                    throw unsupportedAgent(prepared.agent());
                } finally {
                    if (bound) {
                        unbindTenantContext();
                    }
                }
            });
        }

        private CompletionStage<Iterator<Object>> runAgentStreaming(Object agent, Object inputs, Object session,
                                                                    ModelContext context,
                                                                    List<StreamMode> streamModes,
                                                                    Map<String, Object> envs) {
            return runAgentStreaming(agent, inputs, session, context, streamModes, envs, null);
        }

        private CompletionStage<Iterator<Object>> runAgentStreaming(Object agent, Object inputs, Object session,
                                                                    ModelContext context,
                                                                    List<StreamMode> streamModes,
                                                                    Map<String, Object> envs,
                                                                    TenantContext tenantCtx) {
            return CompletableFuture.supplyAsync(() -> {
                Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
                boolean bound = resolved.isPresent();
                if (bound) {
                    bindTenantContext(resolved.get());
                }
                boolean isSuccessful = false;
                try {
                    PreparedAgent prepared = prepareAgent(agent, inputs, session);
                    List<StreamMode> effectiveModes = streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes;
                    Iterator<Object> iterator;
                    if (prepared.agent() instanceof RemoteAgent remoteAgent) {
                        iterator = remoteAgent.stream(asStringObjectMap(inputs));
                    } else if (prepared.agent() instanceof BaseAgent baseAgent) {
                        iterator = baseAgent.stream(inputs, prepared.agentSession(), effectiveModes);
                        if (prepared.agentSessionFacade() != null) {
                            iterator = postRunAfterIterator(iterator, prepared.agentSessionFacade());
                        }
                    } else if (prepared.agent() instanceof com.openjiuwen.core.singleagent.legacy.agent.BaseAgent legacyAgent) {
                        iterator = legacyAgent.stream(
                                asStringObjectMap(inputs), prepared.agentSession(), effectiveModes);
                        if (prepared.agentSessionFacade() != null) {
                            iterator = postRunAfterIterator(iterator, prepared.agentSessionFacade());
                        }
                    } else {
                        throw unsupportedAgent(prepared.agent());
                    }
                    if (bound) {
                        iterator = wrapTenantUnbindIterator(iterator);
                    }
                    isSuccessful = true;
                    return iterator;
                } finally {
                    if (bound && !isSuccessful) {
                        unbindTenantContext();
                    }
                }
            });
        }

        private static Iterator<Object> postRunAfterIterator(Iterator<Object> delegate, AgentSession session) {
            return new Iterator<>() {
                private boolean closed;

                @Override
                public boolean hasNext() {
                    boolean hasNext;
                    try {
                        hasNext = delegate != null && delegate.hasNext();
                    } catch (RuntimeException error) {
                        close();
                        throw error;
                    }
                    if (!hasNext) {
                        close();
                    }
                    return hasNext;
                }

                @Override
                public Object next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return delegate.next();
                }

                private void close() {
                    if (!closed) {
                        session.postRun();
                        closed = true;
                    }
                }
            };
        }

        /**
         * Reactive version of {@link #runAgent(Object, Object, Object, ModelContext, Map)}.
         */
        Mono<Object> runAgentAsync(Object agent, Object inputs, Object session,
                                   ModelContext context, Map<String, Object> envs) {
            return ReactiveAdapters.fromCompletionStage(runAgent(agent, inputs, session, context, envs));
        }

        /**
         * Reactive version of {@link #runAgentStreaming(Object, Object, Object, ModelContext, List, Map)}.
         */
        Flux<Object> runAgentStreamingAsync(Object agent, Object inputs, Object session,
                                            ModelContext context, List<StreamMode> streamModes,
                                            Map<String, Object> envs) {
            return ReactiveAdapters.fromAutoCloseableIterator(
                    () -> runAgentStreaming(agent, inputs, session, context, streamModes, envs)
                            .toCompletableFuture().join());
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
            return runAgentTeam(agentTeam, inputs, member, session, context, envs, null);
        }

        private CompletionStage<Object> runAgentTeam(Object agentTeam, Object inputs, boolean member,
                                                     Object session, ModelContext context,
                                                     Map<String, Object> envs, TenantContext tenantCtx) {
            return CompletableFuture.supplyAsync(() -> {
                Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
                boolean bound = resolved.isPresent();
                if (bound) {
                    bindTenantContext(resolved.get());
                }
                try {
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
                } finally {
                    if (bound) {
                        unbindTenantContext();
                    }
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
                AgentSessionApi executionSession = baseTeamExecutionSession(team, session, teamSession);
                TeamRuntime runtime = team.getRuntime();
                teamSession.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null)
                        .toCompletableFuture()
                        .join();
                if (runtime != null) {
                    runtime.bindTeamSession(executionSession);
                }
                try {
                    return await(team.invoke(inputs, executionSession));
                } finally {
                    if (runtime != null) {
                        runtime.unbindTeamSession(executionSession.getSessionId());
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
                AgentSessionApi executionSession = baseTeamExecutionSession(team, session, teamSession);
                TeamRuntime runtime = team.getRuntime();
                teamSession.preRun(inputs instanceof Map<?, ?> values ? copyStringMap(values) : null)
                        .toCompletableFuture()
                        .join();
                if (runtime != null) {
                    runtime.bindTeamSession(executionSession);
                }
                try {
                    Stream<Object> stream = team.stream(inputs, executionSession);
                    List<Object> chunks = new ArrayList<>();
                    if (stream != null) {
                        stream.forEach(chunks::add);
                    }
                    return chunks.iterator();
                } finally {
                    if (runtime != null) {
                        runtime.unbindTeamSession(executionSession.getSessionId());
                    }
                    teamSession.postRun();
                }
            });
        }

        private static AgentSessionApi baseTeamExecutionSession(
                BaseTeam team,
                Object requestedSession,
                AgentTeamSessionAdapter teamSession) {
            if (requestedSession instanceof AgentSessionApi requested) {
                return requested;
            }
            return teamSession.asAgentSessionApi();
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
                    return new TeamAgentRuntimeAdapter(
                            this,
                            TeamAgent.recoverFromSession(sessionView, teamName, spec.toConfiguratorSpec()));
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
                SessionManager.AgentTeamSessionView {
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

            private AgentSessionApi asAgentSessionApi() {
                return session;
            }
        }

        private PreparedWorkflow prepareWorkflow(Object workflow, Object session) {
            Object workflowSession = createWorkflowSession(session);
            if (workflow instanceof String workflowId) {
                Object resolved = await(resourceManager.getWorkflow(workflowId, workflowSession));
                if (!(resolved instanceof Workflow workflowInstance)) {
                    throw ErrorHelper.buildError(
                            StatusCode.RUNNER_RUN_AGENT_ERROR,
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
                return new WorkflowSession();
            }
            if (session instanceof String sessionId) {
                return new WorkflowSession(null, sessionId, null);
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
                if (agent instanceof com.openjiuwen.core.singleagent.legacy.agent.BaseAgent legacyAgent) {
                    agentSession.preRun(inputKwargs(inputs));
                    return new PreparedAgent(legacyAgent, agentSession, agentSession);
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
            if (resolvedAgent instanceof com.openjiuwen.core.singleagent.legacy.agent.BaseAgent legacyAgent) {
                AgentSession agentSession = createLegacyAgentSession(legacyAgent, sessionId);
                agentSession.preRun(inputKwargs(inputs));
                return new PreparedAgent(legacyAgent, agentSession, agentSession);
            }
            if (resolvedAgent instanceof RemoteAgent) {
                inputMap.putIfAbsent(AGENT_CONVERSATION_ID, sessionId);
                syncStringObjectMap(inputs, inputMap);
                return new PreparedAgent(resolvedAgent, null, null);
            }
            if (isDuckTypedAgent(resolvedAgent)) {
                AgentSession agentSession = createDuckTypedAgentSession(resolvedAgent, sessionId);
                agentSession.preRun(inputKwargs(inputs));
                return new PreparedAgent(resolvedAgent, agentSession, agentSession);
            }
            throw unsupportedAgent(resolvedAgent);
        }

        private AgentSession createAgentSession(BaseAgent agent, String sessionId) {
            AgentCard card = agent == null ? null : agent.getCard();
            return AgentSession.createAgentSession(sessionId, null, card);
        }

        private AgentSession createLegacyAgentSession(
                com.openjiuwen.core.singleagent.legacy.agent.BaseAgent agent, String sessionId) {
            AgentCard card = legacyAgentCard(agent);
            return AgentSession.createAgentSession(sessionId, null, card);
        }

        private AgentCard legacyAgentCard(com.openjiuwen.core.singleagent.legacy.agent.BaseAgent agent) {
            Object card = invokeNoArg(agent, "getCard");
            if (card instanceof AgentCard agentCard) {
                return agentCard;
            }
            Object config = agent == null ? null : agent.getAgentConfig();
            Object idValue = invokeNoArg(config, "getId");
            Object descriptionValue = invokeNoArg(config, "getDescription");
            String id = idValue == null ? "" : String.valueOf(idValue);
            String description = descriptionValue == null ? "" : String.valueOf(descriptionValue);
            return new AgentCard(id, id, description);
        }

        private AgentSession createDuckTypedAgentSession(Object agent, String sessionId) {
            Object card = invokeNoArg(agent, "getCard");
            return AgentSession.createAgentSession(sessionId, null, card instanceof AgentCard agentCard ? agentCard : null);
        }

        private static boolean isDuckTypedAgent(Object agent) {
            return findArityMethod(agent, "invoke", 3) != null
                    || findArityMethod(agent, "invoke", 2) != null
                    || findArityMethod(agent, "invoke", 1) != null;
        }

        private static Object invokeDuckTypedAgent(Object agent, Object inputs, AgentSessionApi session,
                                                   ModelContext context) {
            Method method = findArityMethod(agent, "invoke", 3);
            Object[] args = new Object[] {inputs, session, context};
            if (method == null) {
                method = findArityMethod(agent, "invoke", 2);
                args = new Object[] {inputs, session};
            }
            if (method == null) {
                method = findArityMethod(agent, "invoke", 1);
                args = new Object[] {inputs};
            }
            if (method == null) {
                throw unsupportedAgent(agent);
            }
            try {
                method.setAccessible(true);
                Object value = method.invoke(agent, args);
                if (value instanceof CompletionStage<?> stage) {
                    return await(stage);
                }
                return value;
            } catch (ReflectiveOperationException error) {
                Throwable cause = error instanceof java.lang.reflect.InvocationTargetException invocation
                        ? invocation.getCause()
                        : error;
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new CompletionException(cause);
            }
        }

        private static Method findArityMethod(Object target, String name, int arity) {
            if (target == null) {
                return null;
            }
            for (Method method : target.getClass().getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == arity) {
                    return method;
                }
            }
            for (Method method : target.getClass().getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == arity) {
                    return method;
                }
            }
            return null;
        }

        private static Object invokeNoArg(Object target, String methodName) {
            if (target == null) {
                return null;
            }
            try {
                Method method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        private RuntimeException missingAgent(String agentId) {
            return ErrorHelper.buildError(
                    StatusCode.RUNNER_RUN_AGENT_ERROR,
                    "agent", agentId,
                    "reason", "agent not exist");
        }

        private static RuntimeException unsupportedAgent(Object agent) {
            return ErrorHelper.buildError(
                    StatusCode.RUNNER_RUN_AGENT_ERROR,
                    "agent", String.valueOf(agent),
                    "reason", "unsupported agent type: " + agent);
        }

        private void initializeCheckpointer(RunnerConfig config) {
            defaultCheckpointerInstalledFromConfig = false;
            if (config == null || config.getCheckpointerConfig() == null) {
                return;
            }
            CheckpointerFactory.installDefaultCheckpointer(config.getCheckpointerConfig());
            defaultCheckpointerInstalledFromConfig = true;
        }

        private void releaseConfiguredCheckpointerOnStartFailure(Throwable error) {
            try {
                releaseConfiguredCheckpointer();
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
            }
        }

        private void releaseConfiguredCheckpointer() {
            boolean installed = defaultCheckpointerInstalledFromConfig;
            defaultCheckpointerInstalledFromConfig = false;
            if (installed) {
                CheckpointerFactory.releaseDefaultCheckpointer();
            }
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
