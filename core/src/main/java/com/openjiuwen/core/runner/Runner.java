/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.monitor.TeamMonitor;
import com.openjiuwen.agentteams.runtime.TeamRuntimeManager;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.defaults.LoggingDefaults;
import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.core.multiagent.BaseTeam;
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
import com.openjiuwen.core.session.AgentGroupSession;
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
        return runAgentTeamStreaming(agentTeam, inputs, false, false, null, null, null, null);
    }

    public static Iterator<Object> runAgentTeamStreaming(String agentTeam, Object inputs, Object session) {
        return joinOrThrow(runAgentTeamStreaming(agentTeam, inputs, true, false, session, null, null, null));
    }

    public static Iterator<Object> runAgentTeamStreaming(TeamAgentSpec agentTeam, Map<String, String> inputs,
                                                         String conversationId) {
        return runAgentTeamStreaming(
                agentTeam, inputs, false, false, conversationId, null, null, null)
                .toCompletableFuture()
                .join();
    }

    public static Iterator<Object> runAgentTeamStreaming(TeamAgent agentTeam, Map<String, String> inputs,
                                                         String conversationId) {
        return runAgentTeamStreaming(
                agentTeam, inputs, false, true, conversationId, null, null, null)
                .toCompletableFuture()
                .join();
    }

    public static CompletionStage<Iterator<Object>> runAgentTeamStreaming(Object agentTeam, Object inputs,
                                                                          Object session,
                                                                          ModelContext context,
                                                                          List<StreamMode> streamModes,
                                                                          Map<String, Object> envs) {
        return runAgentTeamStreaming(agentTeam, inputs, false, false, session, context, streamModes, envs);
    }

    public static CompletionStage<Iterator<Object>> runAgentTeamStreaming(Object agentTeam, Object inputs,
                                                                          boolean base, boolean member,
                                                                          Object session,
                                                                          ModelContext context,
                                                                          List<StreamMode> streamModes,
                                                                          Map<String, Object> envs) {
        if (base) {
            return GLOBAL_RUNNER.runBaseTeamStreaming(agentTeam, inputs, session, context, streamModes, envs);
        }
        return GLOBAL_RUNNER.runAgentTeamStreaming(
                agentTeam, inputs, member, session, context, streamModes, envs);
    }

    /**
     * Destroy a registered agent team.
     *
     * @param teamName team name
     * @param isForceEnabled whether other members should be force-shut down
     * @return {@code true} when the registered team was cleaned
     * @since 0.1.13
     */
    public static boolean destroyAgentTeam(String teamName, boolean isForceEnabled) {
        return GLOBAL_RUNNER.destroyAgentTeam(teamName, isForceEnabled);
    }

    /**
     * Inspect the monitor of a registered agent team.
     *
     * @param teamName team name
     * @return an {@link Optional} containing the team monitor, or empty when no team is registered
     * @since 0.1.13
     */
    public static Optional<TeamMonitor> getAgentTeamMonitor(String teamName) {
        return GLOBAL_RUNNER.getAgentTeamMonitor(teamName);
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
        private MessageQueueBase distributedMessageQueue;
        private ReplyTopicSubscription systemReplySub;
        private Object rootTaskGroup;
        private TeamRuntimeManager teamRuntimeManager;
        private boolean defaultCheckpointerInstalledFromConfig;
        private TenantWorkspaceResolver workspaceResolver;
        private final Map<String, TeamAgent> teamMonitors = new LinkedHashMap<>();

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
                        Object result = baseAgent.invoke(inputs, prepared.agentSession());
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
                    if (prepared.agent() instanceof DeepAgent deepAgent) {
                        Object result = deepAgent.invoke(asStringObjectMap(inputs), prepared.agentSession());
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
                    } else if (prepared.agent() instanceof DeepAgent deepAgent) {
                        iterator = deepAgent.stream(asStringObjectMap(inputs), prepared.agentSession(), effectiveModes);
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
            return CompletableFuture.runAsync(
                    () -> CheckpointerFactory.getCheckpointer().release(sessionId));
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
                    TeamRuntimeManager.Activation activation = getTeamRuntimeManager()
                            .activate(agentTeam, resolveAgentSessionId(inputs, session));
                    try {
                        teamMonitors.put(activation.teamName(), activation.agent());
                        return invokeTeamAgent(activation.agent(), inputs, session);
                    } catch (InterruptedException interrupted) {
                        throw new CompletionException(interrupted);
                    } finally {
                        getTeamRuntimeManager().finalizeRound(activation);
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
                Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                if (member) {
                    return await(runTeamMemberStreaming(agentTeam, inputs, session, streamModes));
                }
                TeamRuntimeManager.Activation activation = getTeamRuntimeManager()
                        .activate(agentTeam, resolveAgentSessionId(inputs, session));
                boolean isSuccessful = false;
                try {
                    teamMonitors.put(activation.teamName(), activation.agent());
                    Iterator<Object> stream = streamTeamAgent(
                            activation.agent(), inputs, session, streamModes);
                    isSuccessful = true;
                    return wrapAgentTeamIterator(stream, activation);
                } catch (InterruptedException interrupted) {
                    throw new CompletionException(interrupted);
                } finally {
                    if (!isSuccessful) {
                        getTeamRuntimeManager().finalizeRound(activation);
                    }
                }
            });
        }

        private CompletionStage<Object> runBaseTeam(Object baseTeam, Object inputs, Object session,
                                                    ModelContext context, Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                BaseTeam team = await(prepareBaseTeam(baseTeam));
                AgentSessionApi executionSession = baseTeamExecutionSession(team, session);
                com.openjiuwen.core.multiagent.team_runtime.TeamRuntime runtime = team.getRuntime();
                if (runtime != null) {
                    runtime.bindTeamSession(executionSession);
                }
                try {
                    return await(team.invoke(inputs, executionSession));
                } finally {
                    if (runtime != null) {
                        runtime.unbindTeamSession(executionSession.getSessionId());
                    }
                }
            });
        }

        private CompletionStage<Iterator<Object>> runBaseTeamStreaming(Object baseTeam, Object inputs, Object session,
                                                                       ModelContext context,
                                                                       List<StreamMode> streamModes,
                                                                       Map<String, Object> envs) {
            return CompletableFuture.supplyAsync(() -> {
                BaseTeam team = await(prepareBaseTeam(baseTeam));
                AgentSessionApi executionSession = baseTeamExecutionSession(team, session);
                com.openjiuwen.core.multiagent.team_runtime.TeamRuntime runtime = team.getRuntime();
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
                }
            });
        }

        private static AgentSessionApi baseTeamExecutionSession(BaseTeam team, Object requestedSession) {
            if (requestedSession instanceof AgentGroupSession groupSession) {
                groupSession.setTeamId(team.getCard().getId());
                return groupSession;
            }
            if (requestedSession instanceof AgentSessionApi requested) {
                return requested;
            }
            String sessionId = requestedSession instanceof String requestedId
                    ? requestedId
                    : team.getTeamId();
            AgentGroupSession groupSession = AgentGroupSession.create(sessionId, null);
            groupSession.setTeamId(team.getCard().getId());
            return groupSession;
        }

        private boolean destroyAgentTeam(String teamName, boolean isForceEnabled) {
            return getTeamRuntimeManager().destroyTeam(teamName, isForceEnabled);
        }

        private Optional<TeamMonitor> getAgentTeamMonitor(String teamName) {
            TeamAgent agent = teamMonitors.get(teamName);
            return Optional.ofNullable(agent).map(TeamMonitor::createMonitor);
        }

        private CompletionStage<Object> runTeamMember(Object agent, Object inputs, Object session) {
            if (!(agent instanceof TeamAgent teamAgent)) {
                return failedFuture(ErrorHelper.buildError(
                        StatusCode.AGENT_TEAM_CONFIG_INVALID,
                        "reason",
                        "run_agent_team(member=True) accepts TeamAgent; got " + typeName(agent)
                ));
            }
            return CompletableFuture.supplyAsync(() -> {
                if (session instanceof AgentSessionApi sessionApi) {
                    teamAgent.stream(asObjectMap(inputs), sessionApi);
                } else if (session instanceof String sessionId && sessionId != null && !sessionId.isBlank()) {
                    teamAgent.stream(asObjectMap(inputs), sessionId);
                } else {
                    teamAgent.stream(asObjectMap(inputs), teamAgent.sessionId());
                }
                return null;
            });
        }

        private CompletionStage<Iterator<Object>> runTeamMemberStreaming(Object agent, Object inputs,
                                                                         Object session,
                                                                         List<StreamMode> streamModes) {
            if (!(agent instanceof TeamAgent teamAgent)) {
                return failedFuture(ErrorHelper.buildError(
                        StatusCode.AGENT_TEAM_CONFIG_INVALID,
                        "reason",
                        "run_agent_team(member=True) accepts TeamAgent; got " + typeName(agent)
                ));
            }
            return CompletableFuture.supplyAsync(() -> {
                Object streamSession = session instanceof AgentSessionApi sessionApi ? sessionApi
                        : session instanceof String sessionId && sessionId != null && !sessionId.isBlank()
                        ? sessionId : teamAgent.sessionId();
                Iterator<Object> stream = teamAgent.stream(asObjectMap(inputs), streamSession);
                return stream == null ? List.<Object>of().iterator() : stream;
            });
        }

        private TeamRuntimeManager getTeamRuntimeManager() {
            if (teamRuntimeManager == null) {
                teamRuntimeManager = new TeamRuntimeManager();
            }
            return teamRuntimeManager;
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

        private static String resolveAgentSessionId(Object inputs, Object session) {
            if (inputs instanceof Map<?, ?> inputMap) {
                Object conversationId = inputMap.get(AGENT_CONVERSATION_ID);
                if (conversationId instanceof String value && !value.isBlank()) {
                    return value;
                }
            }
            if (session instanceof AgentSessionApi sessionApi) {
                String sessionId = sessionApi.getSessionId();
                if (sessionId != null && !sessionId.isBlank()) {
                    return sessionId;
                }
            }
            if (session instanceof String sessionId && !sessionId.isBlank()) {
                return sessionId;
            }
            return DEFAULT_AGENT_SESSION_ID;
        }

        private static Map<String, Object> asObjectMap(Object inputs) {
            if (inputs instanceof Map<?, ?> rawMap) {
                return copyStringMap(rawMap);
            }
            Map<String, Object> wrapped = new LinkedHashMap<>();
            wrapped.put("query", String.valueOf(inputs));
            return wrapped;
        }

        /**
         * Drive one full team round with the given inputs and return the last emitted chunk.
         *
         * <p>Mirrors Python's {@code LeaderTeammateAgentTeam.invoke} path: dispatch the
         * query and consume the coordination stream until the round completes.</p>
         */
        private static Object invokeTeamAgent(TeamAgent agent, Object inputs, Object session)
                throws InterruptedException {
            Iterator<Object> stream = streamTeamAgent(agent, inputs, session, null);
            Object last = null;
            while (stream != null && stream.hasNext()) {
                last = stream.next();
            }
            return last;
        }

        /**
         * Start a streaming coordination round and return the chunk iterator.
         */
        private static Iterator<Object> streamTeamAgent(
                TeamAgent agent,
                Object inputs,
                Object session,
                List<StreamMode> streamModes) throws InterruptedException {
            Map<String, Object> teamInputs = asObjectMap(inputs);
            if (session instanceof AgentSessionApi sessionApi) {
                return agent.stream(teamInputs, sessionApi);
            }
            if (session instanceof String sessionId && !sessionId.isBlank()) {
                return agent.stream(teamInputs, sessionId);
            }
            return agent.stream(teamInputs, agent.sessionId());
        }

        /**
         * Finalize the team round when the stream is fully consumed or fails.
         *
         * <p>A persistent team stays registered (paused) so it can be resumed by
         * name on the same session; a temporary team is stopped and removed.</p>
         */
        private Iterator<Object> wrapAgentTeamIterator(
                Iterator<Object> delegate,
                TeamRuntimeManager.Activation activation) {
            return new AgentTeamStreamingIterator(delegate, activation);
        }

        private final class AgentTeamStreamingIterator implements Iterator<Object>, AutoCloseable {
            private final Iterator<Object> delegate;
            private final TeamRuntimeManager.Activation activation;
            private boolean isFinalized;

            private AgentTeamStreamingIterator(
                    Iterator<Object> delegate,
                    TeamRuntimeManager.Activation activation) {
                this.delegate = delegate;
                this.activation = activation;
            }

            @Override
            public boolean hasNext() {
                boolean isSuccessful = false;
                try {
                    boolean hasNext = delegate.hasNext();
                    isSuccessful = true;
                    if (!hasNext) {
                        finalizeTeam();
                    }
                    return hasNext;
                } finally {
                    if (!isSuccessful) {
                        finalizeTeam();
                    }
                }
            }

            @Override
            public Object next() {
                boolean isSuccessful = false;
                try {
                    Object next = delegate.next();
                    isSuccessful = true;
                    return next;
                } finally {
                    if (!isSuccessful) {
                        finalizeTeam();
                    }
                }
            }

            @Override
            public void close() throws Exception {
                if (delegate instanceof AutoCloseable closeable) {
                    closeable.close();
                }
                finalizeTeam();
            }

            private void finalizeTeam() {
                if (isFinalized) {
                    return;
                }
                getTeamRuntimeManager().finalizeRound(activation);
                isFinalized = true;
            }
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
