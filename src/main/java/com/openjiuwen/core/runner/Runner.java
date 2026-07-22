/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.spawn.SpawnAgentConfig;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.runner.spawn.SpawnedProcessHandle;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowChunk;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runner singleton class that proxies all calls to the global runner instance.
 * <p>
 * This class provides a singleton interface for accessing the global runner instance.
 * All method calls and property accesses are automatically proxied to GLOBAL_RUNNER.
 * <p>
 * Example:
 * 
 * <pre>
 * Runner.start();
 * ResourceMgr mgr = Runner.resourceMgr();
 * Runner.runAgent(agent, inputs, null, null);
 * </pre>
 * 
 * @since 0.1.7
 */
public final class Runner {
    private static final RunnerImpl GLOBAL_RUNNER = new RunnerImpl("global", RunnerConfig.DEFAULT);

    /**
     * Runner.
     * 
     * @since 0.1.7
     */
    private Runner() {
        // Utility class
    }

    /**
     * Get the resource manager for workflow, agent, agent_group, tool, model, prompt...
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ResourceMgr resourceMgr() {
        return GLOBAL_RUNNER.getResourceMgr();
    }

    /**
     * Get the local message queue for publish/subscribe communication.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static LocalMessageQueue pubsub() {
        return GLOBAL_RUNNER.getPubsub();
    }

    /**
     * Get the callback framework.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static CallbackFramework callbackFramework() {
        return GLOBAL_RUNNER.getCallbackFramework();
    }

    /**
     * Get the distributed message queue for cross-process communication.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static MessageQueueBase distPubsub() {
        return GLOBAL_RUNNER.getDistPubsub();
    }

    /**
     * Get the reply topic subscription for distributed mode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static ReplyTopicSubscription systemReplySub() {
        return GLOBAL_RUNNER.getSystemReplySub();
    }

    /**
     * Set the runner configuration with provided config object.
     * 
     * @param config config
     * @since 0.1.7
     */
    public static void setConfig(RunnerConfig config) {
        GLOBAL_RUNNER.setConfig(config);
    }

    /**
     * Retrieve the current runner configuration.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static RunnerConfig getConfig() {
        return GLOBAL_RUNNER.getConfig();
    }

    /**
     * Start the runner and its associated components, such as message queue.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static boolean start() {
        return GLOBAL_RUNNER.start();
    }

    /**
     * Stop the runner and clean up resources.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static boolean stop() {
        return GLOBAL_RUNNER.stop();
    }

    /**
     * Execute a workflow with given inputs.
     * 
     * @param workflow Workflow name (String) or Workflow instance
     * @param inputs Input data for the workflow
     * @param session Session ID or session instance
     * @param context Model context
     * @return Workflow execution result
     * @since 0.1.7
     */
    public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, null);
    }

    /**
     * Execute a workflow with given inputs and environment overrides.
     * 
     * @param workflow workflow
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, envs);
    }

    /**
     * Execute a workflow with streaming output support.
     * 
     * @param workflow Workflow name (String) or Workflow instance
     * @param inputs Input data for the workflow
     * @param session Session ID or session instance
     * @param context Model context
     * @param streamModes Types of streaming data to output
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes) {
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes, null);
    }

    /**
     * Execute a workflow with streaming output support and environment overrides.
     * 
     * @param workflow workflow
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param streamModes streamModes
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes, envs);
    }

    /**
     * Execute a single agent with given inputs.
     * 
     * @param agent Agent name (String) or agent instance
     * @param inputs Input data for the agent
     * @param session Session ID or session instance
     * @param context Model context
     * @return Agent execution result
     * @since 0.1.7
     */
    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context) {
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context, null);
    }

    /**
     * Execute a single agent with given inputs and environment overrides.
     * 
     * @param agent agent
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context, envs);
    }

    /**
     * Execute a single agent with streaming output support.
     * 
     * @param agent Agent name (String) or agent instance
     * @param inputs Input data for the agent
     * @param session Session ID or session instance
     * @param context Model context
     * @param streamModes Types of streaming data to output
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, null);
    }

    /**
     * Execute a single agent with streaming output support and environment overrides.
     * 
     * @param agent agent
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param streamModes streamModes
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, envs);
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
    public static SpawnedProcessHandle spawnAgent(SpawnAgentConfig agentConfig, Object inputs, Object session,
            SpawnConfig spawnConfig) {
        return GLOBAL_RUNNER.spawnAgent(agentConfig, inputs, session, spawnConfig);
    }

    // ========== Agent Group ==========

    /**
     * Execute a group of agents with given inputs.
     * 
     * @param agentGroup Agent group name (String) or group instance
     * @param inputs Input data for the agent group
     * @param session Session ID or session instance
     * @param context Model context
     * @return Agent group execution result
     * @since 0.1.7
     */
    public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context) {
        return GLOBAL_RUNNER.runAgentGroup(agentGroup, inputs, session, context, null);
    }

    /**
     * Execute a group of agents with given inputs and environment overrides.
     * 
     * @param agentGroup agentGroup
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentGroup(agentGroup, inputs, session, context, envs);
    }

    /**
     * Execute a group of agents with streaming output support.
     * 
     * @param agentGroup Agent group name (String) or group instance
     * @param inputs Input data for the agent group
     * @param session Session ID or session instance
     * @param context Model context
     * @param streamModes Types of streaming data to output
     * @return Iterator of streaming chunks
     * @since 0.1.7
     */
    public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes) {
        return GLOBAL_RUNNER.runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes, null);
    }

    /**
     * Execute a group of agents with streaming output support and environment overrides.
     * 
     * @param agentGroup agentGroup
     * @param inputs inputs
     * @param session session
     * @param context context
     * @param streamModes streamModes
     * @param envs envs
     * @return the result
     * @since 0.1.7
     */
    public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes, envs);
    }

    /**
     * Release resources associated with a session.
     * 
     * @param sessionId ID of the session to clean up
     * @since 0.1.7
     */
    public static void release(String sessionId) {
        GLOBAL_RUNNER.release(sessionId);
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
    public static Mono<Object> runAgentAsync(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs) {
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
     * @since 0.1.7
     */
    public static Flux<Object> runAgentStreamingAsync(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentStreamingAsync(agent, inputs, session, context, streamModes, envs);
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
    public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, envs, tenantCtx);
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
    public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes, envs, tenantCtx);
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
    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context, envs, tenantCtx);
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
    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, envs, tenantCtx);
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
    public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runAgentGroup(agentGroup, inputs, session, context, envs, tenantCtx);
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
    public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
            ModelContext context, List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes, envs, tenantCtx);
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
    public static Mono<Object> runAgentAsync(Object agent, Object inputs, Object session, ModelContext context,
            Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runAgentAsync(agent, inputs, session, context, envs, tenantCtx);
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
    public static Flux<Object> runAgentStreamingAsync(Object agent, Object inputs, Object session, ModelContext context,
            List<StreamMode> streamModes, Map<String, Object> envs, TenantContext tenantCtx) {
        return GLOBAL_RUNNER.runAgentStreamingAsync(agent, inputs, session, context, streamModes, envs, tenantCtx);
    }
}
