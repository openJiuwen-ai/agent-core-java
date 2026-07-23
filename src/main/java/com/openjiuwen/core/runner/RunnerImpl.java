/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.reactive.ReactiveAdapters;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageQueueFactory;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnProcesses;
import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Runner implementation class.
 * <p>
 * Manages the lifecycle of agents, workflows, agent groups, and their execution.
 * Provides resource management, message queuing, and callback framework capabilities.
 * 
 * @since 0.1.7
 */
public class RunnerImpl {
    private static final Logger logger = LoggerFactory.getLogger(RunnerImpl.class);

    private static final String DEFAULT_RUNNER_ID = "global";
    private static final String DEFAULT_AGENT_SESSION_ID = "default_session";
    private static final String AGENT_CONVERSATION_ID = "conversation_id";

    private final String runnerId;
    private final ResourceMgr resourceManager;
    private final LocalMessageQueue messageQueue;
    private final CallbackFramework callbackFramework;

    private TenantWorkspaceResolver workspaceResolver;

    /** Distributed message queue (null if not in distributed mode). */
    private volatile MessageQueueBase distributeMessageQueue;

    /** Reply topic subscription for distributed mode (null if not in distributed mode). */
    private volatile ReplyTopicSubscription systemReplySub;

    /**
     * RunnerImpl.
     * 
     * @since 0.1.7
     */
    public RunnerImpl() {
        this(DEFAULT_RUNNER_ID, null);
    }

    /**
     * RunnerImpl.
     * 
     * @param runnerId runnerId
     * @param config config
     * @since 0.1.7
     */
    public RunnerImpl(String runnerId, RunnerConfig config) {
        this.runnerId = runnerId != null ? runnerId : DEFAULT_RUNNER_ID;
        this.resourceManager = new ResourceMgr();
        this.messageQueue = new LocalMessageQueue();
        this.callbackFramework = new CallbackFramework();

        if (config != null) {
            RunnerConfig.setRunnerConfig(config);
        } else {
            RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT);
        }
    }

    /**
     * Get the resource manager for workflow, agent, agent_group, tool, model, prompt...
     * 
     * @return the result
     * @since 0.1.7
     */
    public ResourceMgr getResourceMgr() {
        return resourceManager;
    }

    /**
     * Get the local message queue for publish/subscribe communication.
     * 
     * @return the result
     * @since 0.1.7
     */
    public LocalMessageQueue getPubsub() {
        return messageQueue;
    }

    /**
     * Get the callback framework.
     * 
     * @return the result
     * @since 0.1.7
     */
    public CallbackFramework getCallbackFramework() {
        return callbackFramework;
    }

    /**
     * Get the distributed message queue for cross-process communication.
     * 
     * @return the result
     * @since 0.1.7
     */
    public MessageQueueBase getDistPubsub() {
        return distributeMessageQueue;
    }

    /**
     * Get the reply topic subscription for distributed mode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public ReplyTopicSubscription getSystemReplySub() {
        return systemReplySub;
    }

    /**
     * Set the runner configuration.
     * 
     * @param config config
     * @since 0.1.7
     */
    public void setConfig(RunnerConfig config) {
        logger.info("set runner {} config {}", runnerId, config);
        RunnerConfig.setRunnerConfig(config);
    }

    /**
     * Retrieve the current runner configuration.
     * 
     * @return the result
     * @since 0.1.7
     */
    public RunnerConfig getConfig() {
        return RunnerConfig.getRunnerConfig();
    }

    /**
     * Start the runner and its associated components, such as message queue.
     * 
     * @return true if started successfully
     * @since 0.1.7
     */
    public boolean start() {
        boolean result = true;
        logger.info("Begin to start runner, runnerId={}", runnerId);

        // Initialize checkpointer if configured
        Map<String, Object> checkpointerConfig = RunnerConfig.getRunnerConfig().getCheckpointerConfig();
        if (checkpointerConfig != null) {
            String type = (String) checkpointerConfig.getOrDefault("type", "in_memory");
            @SuppressWarnings("unchecked")
            Map<String, Object> conf = (Map<String, Object>) checkpointerConfig.getOrDefault("conf", Map.of());
            logger.info("Begin to initializing checkpointer with type: {}, runnerId={}", type, runnerId);
            try {
                var checkpointer = CheckpointerFactory.create(type, conf);
                CheckpointerFactory.setDefaultCheckpointer(checkpointer);
                logger.info("Succeed to initializing checkpointer with type: {}, runnerId={}", type, runnerId);
            } catch (Exception e) {
                logger.error("Failed to initializing checkpointer with type: {}, runnerId={}", type, runnerId, e);
                throw e;
            }
        }

        if (RunnerConfig.getRunnerConfig().isEnableTenantIsolation()) {
            String dataRoot = RunnerConfig.getRunnerConfig().getTenantDataRoot();
            if (dataRoot == null || dataRoot.isEmpty()) {
                dataRoot = System.getProperty("user.dir");
            }
            workspaceResolver = new TenantWorkspaceResolver(dataRoot);
            logger.info("Tenant isolation enabled, dataRoot={}", dataRoot);
        }

        if (RunnerConfig.getRunnerConfig().isDistributedMode()) {
            // Start distributed message queue
            distributeMessageQueue = MessageQueueFactory
                    .create(RunnerConfig.getRunnerConfig().getDistributedConfig().getMessageQueueConfig());
            distributeMessageQueue.start();

            // Start reply topic subscription
            String replyTopic = RunnerConfig.getRunnerConfig().replyTopicTemplate().replace("{instance_id}",
                    RunnerConfig.getRunnerConfig().getInstanceId());
            systemReplySub = new ReplyTopicSubscription(distributeMessageQueue, replyTopic);
            systemReplySub.activate();
        }

        result = messageQueue.start();
        logger.info("Succeed to start runner, runnerId={}", runnerId);
        return result;
    }

    /**
     * Stop the runner and clean up resources.
     * 
     * @return true if stopped successfully
     * @since 0.1.7
     */
    public boolean stop() {
        logger.info("Begin to stop runner, runnerId={}", runnerId);
        try {
            if (RunnerConfig.getRunnerConfig().isDistributedMode()) {
                // Stop ReplyTopicSubscription and clean up collectors
                if (systemReplySub != null) {
                    systemReplySub.deactivate();
                    systemReplySub = null;
                }
                // Stop distributed MQ
                if (distributeMessageQueue != null) {
                    distributeMessageQueue.stop();
                    distributeMessageQueue = null;
                }
            }

            messageQueue.stop();
            logger.info("Succeed to stop runner, runnerId={}", runnerId);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to stop runner, runnerId={}", runnerId, e);
            return false;
        } finally {
            resourceManager.release();
        }
    }

    // ========== Workflow Execution ==========

    /**
     * Execute a workflow with given inputs.
     * 
     * @param workflow Workflow ID or Workflow instance
     * @param inputs Input data for the workflow
     * @param session Session identifier or session object
     * @param context Model context
     * @param envs envs
     * @return Workflow execution result
     * @since 0.1.7
     */
    public Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        Workflow workflowInstance;
        Object workflowSession;

        if (workflow instanceof String workflowId) {
            Object wf = resourceManager.getWorkflow(workflowId);
            if (wf == null) {
                throw ErrorHelper.buildError(StatusCode.RUNNER_RUN_AGENT_ERROR, "agent", workflowId, "reason",
                        "workflow not exist");
            }
            workflowInstance = (Workflow) wf;
        } else if (workflow instanceof Workflow w) {
            workflowInstance = w;
        } else {
            throw new IllegalArgumentException("workflow must be a String (workflow ID) or Workflow instance");
        }

        workflowSession = createWorkflowSession(session);
        return workflowInstance.invoke(inputs, workflowSession, context);
    }

    /**
     * Execute a workflow with streaming output support.
     * 
     * @param workflow Workflow ID or Workflow instance
     * @param inputs Input data for the workflow
     * @param session Session identifier or session object
     * @param context Model context
     * @param streamModes Types of streaming data to output
     * @param envs envs
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs) {
        Workflow workflowInstance;
        Object workflowSession;

        if (workflow instanceof String workflowId) {
            Object wf = resourceManager.getWorkflow(workflowId);
            if (wf == null) {
                throw ErrorHelper.buildError(StatusCode.RUNNER_RUN_AGENT_ERROR, "agent", workflowId, "reason",
                        "workflow not exist");
            }
            workflowInstance = (Workflow) wf;
        } else if (workflow instanceof Workflow w) {
            workflowInstance = w;
        } else {
            throw new IllegalArgumentException("workflow must be a String (workflow ID) or Workflow instance");
        }

        workflowSession = createWorkflowSession(session);
        return workflowInstance.stream(inputs, workflowSession, context, streamModes);
    }

    // ========== Agent Execution ==========

    /**
     * Execute a single agent with given inputs.
     * <p>
     * Note: BaseAgent/LegacyBaseAgent are not yet available in Java.
     * This method supports String agent IDs for resource-managed agents,
     * and Object agent instances for direct invocation.
     * 
     * @param agent Agent ID (String) or agent instance (Object)
     * @param inputs Input data for the agent
     * @param session Session identifier
     * @param context Model context
     * @param envs envs
     * @return Agent execution result
     * @since 0.1.7
     */
    public Object runAgent(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        Object agentInstance = prepareAgent(agent);
        AgentSessionApi agentSession = prepareAgentSession(agentInstance, inputs, session);
        Object result = invokeAgent(agentInstance, inputs, agentSession, context);
        agentSession.postRun();
        return result;
    }

    /**
     * Execute a single agent with streaming output support.
     * 
     * @param agent Agent ID (String) or agent instance (Object)
     * @param inputs Input data for the agent
     * @param session Session identifier
     * @param context Model context
     * @param streamModes Types of streaming data to output
     * @param envs envs
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs) {
        Object agentInstance = prepareAgent(agent);
        AgentSessionApi agentSession = prepareAgentSession(agentInstance, inputs, session, streamModes);
        Iterator<Object> iterator = streamAgent(agentInstance, inputs, agentSession, context, streamModes);
        return wrapStreamingIterator(iterator, agentSession);
    }

    /**
     * spawnAgent.
     * 
     * @param agentConfig agentConfig
     * @param inputs inputs
     * @param session session
     * @param spawnConfig spawnConfig
     * @return the result
     * @since 0.1.7
     */
    public SpawnedProcessHandle spawnAgent(SpawnAgentConfig agentConfig, Object inputs, Object session,
            SpawnConfig spawnConfig) {
        if (agentConfig == null) {
            throw new IllegalArgumentException("Runner.spawnAgent requires SpawnAgentConfig");
        }
        Map<String, Object> normalizedInputs = normalizeSpawnInputs(inputs);
        Object sessionId = normalizedInputs.getOrDefault(AGENT_CONVERSATION_ID,
                session instanceof String ? String.valueOf(session) : DEFAULT_AGENT_SESSION_ID);
        agentConfig.setSessionId(String.valueOf(sessionId));
        SpawnedProcessHandle handle =
            SpawnProcesses.spawnProcess(agentConfig.toPayload(), normalizedInputs, spawnConfig);
        if (spawnConfig != null) {
            handle.startHealthCheck();
        }
        return handle;
    }

    // ========== Agent Group Execution ==========

    /**
     * Execute a group of agents with given inputs.
     * 
     * @param agentGroup Agent group ID (String) or group instance (Object)
     * @param inputs Input data for the agent group
     * @param session Session identifier
     * @param context Model context
     * @param envs envs
     * @return Agent group execution result
     * @since 0.1.7
     */
    public Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        Object groupInstance = prepareAgentGroup(agentGroup);
        AgentGroupSessionApi groupSession = prepareAgentGroupSession(inputs, session);
        resolveTeamId(agentGroup, groupSession);
        groupSession.preRun(inputs);
        Object result = invokeAgentGroup(groupInstance, inputs, groupSession, context);
        groupSession.postRun();
        return result;
    }

    /**
     * Execute a group of agents with streaming output support.
     * 
     * @param agentGroup Agent group ID (String) or group instance (Object)
     * @param inputs Input data for the agent group
     * @param session Session identifier
     * @param context Model context
     * @param streamModes Types of streaming data to output
     * @param envs envs
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs) {
        Object groupInstance = prepareAgentGroup(agentGroup);
        AgentGroupSessionApi groupSession = prepareAgentGroupSession(inputs, session, streamModes);
        resolveTeamId(agentGroup, groupSession);
        groupSession.preRun(inputs);
        Iterator<Object> iterator = streamAgentGroup(groupInstance, inputs, groupSession, context);
        return wrapStreamingIterator(iterator, groupSession);
    }

    private void bindTenantContext(TenantContext ctx) {
        if (ctx != null && ctx.isTenantAware()) {
            TenantContextHolder.setCurrentTenant(ctx);
            if (workspaceResolver != null) {
                workspaceResolver.initializeTenantSpace(ctx);
                Path tenantWorkspace = workspaceResolver.resolveWorkspaceRoot(ctx);
                CwdContext.setWorkspace(tenantWorkspace.toString());
                CwdContext.setOriginalCwd(tenantWorkspace.toString());
                CwdContext.setTenantRoot(workspaceResolver.resolveTenantRoot(ctx).toString());
            }
        }
    }

    private void unbindTenantContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    private Optional<TenantContext> resolveTenantContext(Object session, TenantContext explicitTenantCtx) {
        if (explicitTenantCtx != null) {
            return Optional.of(explicitTenantCtx);
        }
        if (session instanceof AgentSessionApi apiSession) {
            TenantContext sessionCtx = apiSession.getTenantContext();
            if (sessionCtx != null && sessionCtx.isTenantAware()) {
                return Optional.of(sessionCtx);
            }
        }
        return Optional.empty();
    }

    /**
     * Execute a workflow with tenant context binding.
     *
     * @param workflow workflow instance or identifier
     * @param inputs workflow inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return workflow execution result
     * @since 0.1.7
     */
    public Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        bindTenantContext(resolveTenantContext(session, tenantCtx).orElse(null));
        try {
            return runWorkflow(workflow, inputs, session, context, envs);
        } finally {
            unbindTenantContext();
        }
    }

    /**
     * Execute a workflow with streaming output support and tenant context binding.
     *
     * @param workflow workflow instance or identifier
     * @param inputs workflow inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param streamModes stream output modes
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        bindTenantContext(resolveTenantContext(session, tenantCtx).orElse(null));
        boolean isSuccessful = false;
        try {
            Iterator<WorkflowChunk> inner = runWorkflowStreaming(workflow, inputs, session, context, streamModes, envs);
            isSuccessful = true;
            return wrapTenantUnbindIterator(inner);
        } finally {
            if (!isSuccessful) {
                unbindTenantContext();
            }
        }
    }

    /**
     * Execute an agent with tenant context binding.
     *
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return agent execution result
     * @since 0.1.7
     */
    public Object runAgent(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        bindTenantContext(resolveTenantContext(session, tenantCtx).orElse(null));
        try {
            return runAgent(agent, inputs, session, context, envs);
        } finally {
            unbindTenantContext();
        }
    }

    /**
     * Execute an agent with streaming output support and tenant context binding.
     *
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param streamModes stream output modes
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        bindTenantContext(resolveTenantContext(session, tenantCtx).orElse(null));
        boolean isSuccessful = false;
        try {
            Iterator<Object> inner = runAgentStreaming(agent, inputs, session, context, streamModes, envs);
            isSuccessful = true;
            return wrapTenantUnbindIterator(inner);
        } finally {
            if (!isSuccessful) {
                unbindTenantContext();
            }
        }
    }

    /**
     * Execute a group of agents with tenant context binding.
     *
     * @param agentGroup agent group instance or identifier
     * @param inputs input data for the agent group
     * @param session session object, nullable
     * @param context model context, nullable
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return agent group execution result
     * @since 0.1.7
     */
    public Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        bindTenantContext(resolveTenantContext(session, tenantCtx).orElse(null));
        try {
            return runAgentGroup(agentGroup, inputs, session, context, envs);
        } finally {
            unbindTenantContext();
        }
    }

    /**
     * Execute a group of agents with streaming output support and tenant context binding.
     *
     * @param agentGroup agent group instance or identifier
     * @param inputs input data for the agent group
     * @param session session object, nullable
     * @param context model context, nullable
     * @param streamModes stream output modes
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        bindTenantContext(resolveTenantContext(session, tenantCtx).orElse(null));
        boolean isSuccessful = false;
        try {
            Iterator<Object> inner = runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes, envs);
            isSuccessful = true;
            return wrapTenantUnbindIterator(inner);
        } finally {
            if (!isSuccessful) {
                unbindTenantContext();
            }
        }
    }

    /**
     * Reactive version of {@link #runAgent(Object, Object, Object, ModelContext, Map)} with tenant context binding.
     *
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return Mono emitting the agent result
     * @since 0.1.7
     */
    public Mono<Object> runAgentAsync(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
        if (resolved.isEmpty()) {
            return runAgentAsync(agent, inputs, session, context, envs);
        }
        TenantContext captured = resolved.get();
        return ReactiveAdapters.fromCallable(() -> {
            bindTenantContext(captured);
            try {
                return runAgent(agent, inputs, session, context, envs);
            } finally {
                unbindTenantContext();
            }
        });
    }

    /**
     * Reactive version of {@link #runAgentStreaming(Object, Object, Object, ModelContext, List, Map)}
     * with tenant context binding.
     *
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param streamModes stream output modes
     * @param envs environment values, nullable
     * @param tenantCtx tenant context, nullable
     * @return Flux emitting stream chunks
     * @since 0.1.7
     */
    public Flux<Object> runAgentStreamingAsync(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        Optional<TenantContext> resolved = resolveTenantContext(session, tenantCtx);
        if (resolved.isEmpty()) {
            return runAgentStreamingAsync(agent, inputs, session, context, streamModes, envs);
        }
        TenantContext captured = resolved.get();
        return deferWithSessionCleanup(sessRef -> {
            bindTenantContext(captured);
            Object agentInstance = prepareAgent(agent);
            AgentSessionApi sess = prepareAgentSession(agentInstance, inputs, session, streamModes);
            sessRef.set(sess);
            return ReactiveAdapters.fromAutoCloseableIterator(
                () -> streamAgent(agentInstance, inputs, sess, context, streamModes));
        }, (AgentSessionApi sess) -> {
            sess.postRun();
            unbindTenantContext();
        });
    }

    /**
     * Release resources associated with a session.
     * 
     * @param sessionId ID of the session to clean up
     * @since 0.1.7
     */
    public void release(String sessionId) {
        CheckpointerFactory.getCheckpointer(null).release(sessionId);
    }

    /**
     * Generate workflow key from ID and version (matches Python's generate_workflow_key).
     * 
     * @param workflowId workflowId
     * @param workflowVersion workflowVersion
     * @return the result
     * @since 0.1.7
     */
    public static String generateWorkflowKey(String workflowId, String workflowVersion) {
        return workflowId + "_" + (workflowVersion != null ? workflowVersion : "");
    }

    /**
     * createWorkflowSession.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Object createWorkflowSession(Object session) {
        if (session == null) {
            return new WorkflowSessionApi((BaseSession) null, null, null);
        }
        if (session instanceof String sessionId) {
            return new WorkflowSessionApi((BaseSession) null, sessionId, null);
        }
        if (session instanceof AgentSessionApi agentSessionApi) {
            return agentSessionApi.getInner().createWorkflowSession();
        }
        if (session instanceof WorkflowSessionApi || session instanceof BaseSession) {
            return session;
        }
        throw new IllegalArgumentException("Unsupported workflow session type: " + session.getClass().getSimpleName());
    }

    /**
     * prepareAgentGroup.
     * 
     * @param agentGroup agentGroup
     * @return the result
     * @since 0.1.7
     */
    private Object prepareAgentGroup(Object agentGroup) {
        if (agentGroup instanceof String groupId) {
            Object groupInstance = resourceManager.getAgentGroup(groupId, null, TagMatchStrategy.ALL);
            if (groupInstance == null) {
                throw ErrorHelper.buildError(StatusCode.RUNNER_RUN_AGENT_ERROR, "agent", groupId, "reason",
                        "agent group not exist");
            }
            return groupInstance;
        }
        return agentGroup;
    }

    /**
     * resolveTeamId.
     * 
     * @param agentGroup agentGroup
     * @param groupSession groupSession
     * @since 0.1.7
     */
    private void resolveTeamId(Object agentGroup, AgentGroupSessionApi groupSession) {
        if (agentGroup instanceof String groupId) {
            groupSession.setTeamId(groupId);
        } else {
            Object card = tryReadProperty(agentGroup, "getTeamCard", "teamCard");
            if (card == null) {
                card = tryReadProperty(agentGroup, "getCard", "card");
            }
            if (card != null) {
                Object id = tryReadProperty(card, "getId", "id");
                if (id instanceof String tid) {
                    groupSession.setTeamId(tid);
                }
            }
        }
    }

    /**
     * Invoke an agent instance. Uses reflection to call invoke method since
     * BaseAgent/LegacyBaseAgent are not yet available in Java.
     * 
     * @param agentInstance agentInstance
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private Object invokeAgent(Object agentInstance, Object inputs, Object session, ModelContext context) {
        try {
            return invokeFirstCompatibleMethod(
                    agentInstance, "invoke", List.of(new Object[]{inputs, session, context},
                            new Object[]{inputs, session}, new Object[]{inputs}),
                    "Agent does not support invoke method");
        } catch (RuntimeException e) {
            throw wrapRunnerRuntime("Failed to invoke agent", e);
        }
    }

    /**
     * Stream from an agent instance via reflection.
     * 
     * @param agentInstance agentInstance
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private Iterator<Object> streamAgent(Object agentInstance, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes) {
        try {
            return (Iterator<Object>) invokeFirstCompatibleMethod(agentInstance, "stream",
                    List.of(new Object[]{inputs, session, streamModes}, new Object[]{inputs, session, context},
                            new Object[]{inputs, session}, new Object[]{inputs}),
                    "Agent does not support stream method");
        } catch (RuntimeException e) {
            throw wrapRunnerRuntime("Failed to stream agent", e);
        }
    }

    /**
     * Invoke an agent group instance via reflection.
     * 
     * @param groupInstance groupInstance
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private Object invokeAgentGroup(Object groupInstance, Object inputs, Object session, ModelContext context) {
        try {
            return invokeFirstCompatibleMethod(
                    groupInstance, "invoke", List.of(new Object[]{inputs, session, context},
                            new Object[]{inputs, session}, new Object[]{inputs}),
                    "Agent group does not support invoke method");
        } catch (RuntimeException e) {
            throw wrapRunnerRuntime("Failed to invoke agent group", e);
        }
    }

    /**
     * Stream from an agent group instance via reflection.
     * 
     * @param groupInstance groupInstance
     * @param inputs inputs
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private Iterator<Object> streamAgentGroup(Object groupInstance, Object inputs, Object session,
            ModelContext context) {
        try {
            return (Iterator<Object>) invokeFirstCompatibleMethod(
                    groupInstance, "stream", List.of(new Object[]{inputs, session, context},
                            new Object[]{inputs, session}, new Object[]{inputs}),
                    "Agent group does not support stream method");
        } catch (RuntimeException e) {
            throw wrapRunnerRuntime("Failed to stream agent group", e);
        }
    }

    /**
     * wrapRunnerRuntime.
     * 
     * @param message message
     * @param error error
     * @return the result
     * @since 0.1.7
     */
    private RuntimeException wrapRunnerRuntime(String message, RuntimeException error) {
        BaseError baseError = findBaseError(error);
        if (baseError != null) {
            return baseError;
        }
        return new RuntimeException(message, error);
    }

    /**
     * findBaseError.
     * 
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private BaseError findBaseError(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof BaseError baseError) {
                return baseError;
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * normalizeSpawnInputs.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> normalizeSpawnInputs(Object inputs) {
        if (inputs instanceof Map<?, ?> rawInputs) {
            java.util.LinkedHashMap<String, Object> normalized = new java.util.LinkedHashMap<>();
            rawInputs.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return Map.of("data", inputs);
    }

    /**
     * prepareAgent.
     * 
     * @param agent agent
     * @return the result
     * @since 0.1.7
     */
    private Object prepareAgent(Object agent) {
        if (agent instanceof String agentId) {
            Object agentInstance = resourceManager.getAgent(agentId);
            if (agentInstance == null) {
                throw ErrorHelper.buildError(StatusCode.RUNNER_RUN_AGENT_ERROR, "agent_id", agentId, "reason",
                        "agent not exist");
            }
            return agentInstance;
        }
        return agent;
    }

    /**
     * prepareAgentSession.
     * 
     * @param agentInstance agentInstance
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private AgentSessionApi prepareAgentSession(Object agentInstance, Object inputs, Object session) {
        return prepareAgentSession(agentInstance, inputs, session, null);
    }

    /**
     * prepareAgentGroupSession.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private AgentGroupSessionApi prepareAgentGroupSession(Object inputs, Object session) {
        return prepareAgentGroupSession(inputs, session, null);
    }

    /**
     * prepareAgentGroupSession.
     * 
     * @param inputs inputs
     * @param session session
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
    private AgentGroupSessionApi prepareAgentGroupSession(Object inputs, Object session, List<StreamMode> streamModes) {
        AgentGroupSessionApi groupSession;
        if (session instanceof AgentGroupSessionApi existingGroupSession) {
            groupSession = existingGroupSession;
            // 复用 session 时重置 preRun/postRun CAS 守卫，否则第二轮静默跳过 checkpointer 钩子。
            groupSession.resetRunState();
        } else if (session instanceof AgentSessionApi existingSession) {
            groupSession = AgentGroupSessionApi.create(existingSession.getSessionId(), null);
        } else {
            String sessionId = resolveAgentSessionId(inputs, session);
            groupSession = AgentGroupSessionApi.create(sessionId, null);
        }
        if (inputs instanceof Map<?, ?> inputMap) {
            Object teamId = inputMap.get("team_id");
            if (teamId instanceof String tid) {
                groupSession.setTeamId(tid);
            }
        }
        return groupSession;
    }

    /**
     * prepareAgentSession.
     * 
     * @param agentInstance agentInstance
     * @param inputs inputs
     * @param session session
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
    private AgentSessionApi prepareAgentSession(Object agentInstance, Object inputs, Object session,
            List<StreamMode> streamModes) {
        AgentSessionApi agentSession;
        if (session instanceof AgentSessionApi existingSession) {
            agentSession = existingSession;
            // 复用 session 时重置 preRun/postRun CAS 守卫，否则第二轮静默跳过 checkpointer 钩子。
            agentSession.resetRunState();
        } else {
            String sessionId = resolveAgentSessionId(inputs, session);
            agentSession = AgentSessionApi.create(sessionId, extractAgentEnvs(agentInstance),
                    extractAgentCard(agentInstance), streamModes);
        }
        agentSession.preRun(inputs);
        return agentSession;
    }

    /**
     * resolveAgentSessionId.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private String resolveAgentSessionId(Object inputs, Object session) {
        if (inputs instanceof Map<?, ?> inputMap) {
            Object conversationId = inputMap.get(AGENT_CONVERSATION_ID);
            if (conversationId instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        if (session instanceof String sessionId && !sessionId.isBlank()) {
            return sessionId;
        }
        if (session instanceof AgentSessionApi sessionApi) {
            return sessionApi.getSessionId();
        }
        return DEFAULT_AGENT_SESSION_ID;
    }

    @SuppressWarnings("unchecked")
    /**
     * extractAgentEnvs.
     * 
     * @param agentInstance agentInstance
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> extractAgentEnvs(Object agentInstance) {
        Object config = tryReadProperty(agentInstance, "getConfig", "config");
        if (config == null) {
            return null;
        }
        Object envs = tryReadProperty(config, "getEnvs", "envs");
        if (envs instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    /**
     * extractAgentCard.
     * 
     * @param agentInstance agentInstance
     * @return the result
     * @since 0.1.7
     */
    private Object extractAgentCard(Object agentInstance) {
        return tryReadProperty(agentInstance, "getCard", "card");
    }

    /**
     * tryReadProperty.
     * 
     * @param target target
     * @param getterName getterName
     * @param fieldName fieldName
     * @return the result
     * @since 0.1.7
     */
    private Object tryReadProperty(Object target, String getterName, String fieldName) {
        if (target == null) {
            return null;
        }
        try {
            Method getter = target.getClass().getMethod(getterName);
            return getter.invoke(target);
        } catch (NoSuchMethodException ignored) {
            // fall through
        } catch (IllegalAccessException | InvocationTargetException e) {
            return null;
        }
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return null;
        }
    }

    /**
     * invokeFirstCompatibleMethod.
     * 
     * @param target target
     * @param methodName methodName
     * @param argVariants argVariants
     * @param unsupportedMessage unsupportedMessage
     * @return the result
     * @since 0.1.7
     */
    private Object invokeFirstCompatibleMethod(Object target, String methodName, List<Object[]> argVariants,
            String unsupportedMessage) {
        RuntimeException lastFailure = null;
        for (Object[] args : argVariants) {
            Method method = findCompatibleMethod(target.getClass(), methodName, args);
            if (method == null) {
                continue;
            }
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException e) {
                lastFailure = new RuntimeException("Cannot access " + methodName + " method", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(cause != null ? cause : e);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new RuntimeException(unsupportedMessage);
    }

    /**
     * findCompatibleMethod.
     * 
     * @param targetClass targetClass
     * @param methodName methodName
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private Method findCompatibleMethod(Class<?> targetClass, String methodName, Object[] args) {
        Method bestMethod = null;
        int bestScore = Integer.MIN_VALUE;
        for (Method method : targetClass.getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            int score = scoreMethod(method.getParameterTypes(), args);
            if (score > bestScore) {
                bestScore = score;
                bestMethod = method;
            }
        }
        if (bestMethod != null) {
            // The method may be public on a package-private or non-exported
            // throws IllegalAccessException. setAccessible(true) bypasses the
            // enclosing-class access check the same way getDeclaredMethods
            // already does below.
            bestMethod.setAccessible(true);
            return bestMethod;
        }
        for (Method method : targetClass.getDeclaredMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) {
                continue;
            }
            int score = scoreMethod(method.getParameterTypes(), args);
            if (score > bestScore) {
                method.setAccessible(true);
                bestScore = score;
                bestMethod = method;
            }
        }
        return bestMethod;
    }

    /**
     * scoreMethod.
     * 
     * @param parameterTypes parameterTypes
     * @param args args
     * @return the result
     * @since 0.1.7
     */
    private int scoreMethod(Class<?>[] parameterTypes, Object[] args) {
        int score = 0;
        for (int i = 0; i < parameterTypes.length; i++) {
            int argScore = scoreArgument(parameterTypes[i], args[i]);
            if (argScore < 0) {
                return -1;
            }
            score += argScore;
        }
        return score;
    }

    /**
     * scoreArgument.
     * 
     * @param parameterType parameterType
     * @param arg arg
     * @return the result
     * @since 0.1.7
     */
    private int scoreArgument(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return parameterType.isPrimitive() ? -1 : 1;
        }
        Class<?> wrappedType = wrapPrimitive(parameterType);
        Class<?> argType = arg.getClass();
        if (wrappedType.equals(argType)) {
            return 10;
        }
        if (wrappedType.isAssignableFrom(argType)) {
            if (Objects.equals(wrappedType, Object.class)) {
                return 2;
            }
            if (wrappedType.isInterface()) {
                return 6;
            }
            return 8;
        }
        return -1;
    }

    /**
     * wrapPrimitive.
     * 
     * @param type type
     * @return the result
     * @since 0.1.7
     */
    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    /**
     * wrapStreamingIterator.
     * 
     * @param delegate delegate
     * @param agentSession agentSession
     * @return the result
     * @since 0.1.7
     */
    private Iterator<Object> wrapStreamingIterator(Iterator<Object> delegate, AgentSessionApi agentSession) {
        class CloseableStreamingIterator implements Iterator<Object>, AutoCloseable {
            private boolean postRunDone;
            @Override
            public boolean hasNext() {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    completePostRun();
                }
                return hasNext;
            }

            @Override
            public Object next() {
                try {
                    Object next = delegate.next();
                    if (!delegate.hasNext()) {
                        completePostRun();
                    }
                    return next;
                } catch (NoSuchElementException e) {
                    completePostRun();
                    throw e;
                }
            }

            private void completePostRun() {
                if (!postRunDone) {
                    agentSession.postRun();
                    postRunDone = true;
                }
            }

            @Override
            public void close() throws Exception {
                try {
                    if (delegate instanceof AutoCloseable closeable) {
                        closeable.close();
                    }
                } finally {
                    completePostRun();
                }
            }
        }
        return new CloseableStreamingIterator();
    }

    @SuppressWarnings("unchecked")
    private <T> Iterator<T> wrapTenantUnbindIterator(Iterator<T> delegate) {
        class TenantUnbindIterator implements Iterator<T>, AutoCloseable {
            private boolean isUnbound;

            @Override
            public boolean hasNext() {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    unbind();
                }
                return hasNext;
            }

            @Override
            public T next() {
                try {
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

    /**
     * Reactive version of {@link #runAgent(Object, Object, Object, ModelContext, Map)}.
     * 
     * @param agent agent instance or identifier
     * @param inputs agent inputs
     * @param session session object, nullable
     * @param context model context, nullable
     * @param envs environment values, nullable
     * @return Mono emitting the agent result
     * @since 0.1.7
     */
    public Mono<Object> runAgentAsync(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        return ReactiveAdapters.fromCallable(() -> runAgent(agent, inputs, session, context, envs));
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
     * @since 0.1.7
     */
    public Flux<Object> runAgentStreamingAsync(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs) {
        return deferWithSessionCleanup(sessRef -> {
            Object agentInstance = prepareAgent(agent);
            AgentSessionApi sess = prepareAgentSession(agentInstance, inputs, session, streamModes);
            sessRef.set(sess);
            return ReactiveAdapters
                    .fromAutoCloseableIterator(() -> streamAgent(agentInstance, inputs, sess, context, streamModes));
        }, AgentSessionApi::postRun);
    }

    /**
     * 流式响应式方法公共脚手架：阻塞的 setup 在 boundedElastic 线程执行；
     * doFinally 保证 COMPLETE / ERROR / CANCEL 三种终态都触发 cleaner；
     * AtomicReference 填补 setup 完成到内层 Flux 首次订阅之间的取消空窗。
     * 
     * @param body body
     * @param cleaner cleaner
     * @return the result
     * @since 0.1.7
     */
    private <T, S> Flux<T> deferWithSessionCleanup(
            java.util.function.Function<java.util.concurrent.atomic.AtomicReference<S>, Flux<T>> body,
            java.util.function.Consumer<S> cleaner) {
        java.util.concurrent.atomic.AtomicReference<S> sessRef = new java.util.concurrent.atomic.AtomicReference<>();
        return Flux.defer(() -> body.apply(sessRef)).doFinally(signal -> {
            S s = sessRef.getAndSet(null);
            if (s != null) {
                cleaner.accept(s);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
