// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.runner;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.Iterator;
import java.util.List;

/**
 * Runner singleton class that proxies all calls to the global runner instance.
 * <p>
 * This class provides a singleton interface for accessing the global runner instance.
 * All method calls and property accesses are automatically proxied to GLOBAL_RUNNER.
 * <p>
 * Example:
 * <pre>
 *     Runner.start();
 *     ResourceMgr mgr = Runner.resourceMgr();
 *     Runner.runAgent(agent, inputs, null, null);
 * </pre>
 */
public final class Runner {

    /** The global runner instance. */
    private static final RunnerImpl GLOBAL_RUNNER = new RunnerImpl("global", RunnerConfig.DEFAULT);

    private Runner() {
        // Utility class
    }

    // ========== Properties ==========

    /**
     * Get the resource manager for workflow, agent, agent_group, tool, model, prompt...
     */
    public static ResourceMgr resourceMgr() {
        return GLOBAL_RUNNER.getResourceMgr();
    }

    /**
     * Get the local message queue for publish/subscribe communication.
     */
    public static LocalMessageQueue pubsub() {
        return GLOBAL_RUNNER.getPubsub();
    }

    /**
     * Get the callback framework.
     */
    public static CallbackFramework callbackFramework() {
        return GLOBAL_RUNNER.getCallbackFramework();
    }

    // ========== Config ==========

    /**
     * Set the runner configuration with provided config object.
     */
    public static void setConfig(RunnerConfig config) {
        GLOBAL_RUNNER.setConfig(config);
    }

    /**
     * Retrieve the current runner configuration.
     */
    public static RunnerConfig getConfig() {
        return GLOBAL_RUNNER.getConfig();
    }

    // ========== Lifecycle ==========

    /**
     * Start the runner and its associated components, such as message queue.
     */
    public static boolean start() {
        return GLOBAL_RUNNER.start();
    }

    /**
     * Stop the runner and clean up resources.
     */
    public static boolean stop() {
        return GLOBAL_RUNNER.stop();
    }

    // ========== Workflow ==========

    /**
     * Execute a workflow with given inputs.
     *
     * @param workflow Workflow name (String) or Workflow instance
     * @param inputs   Input data for the workflow
     * @param session  Session ID or session instance
     * @param context  Model context
     * @return Workflow execution result
     */
    public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context);
    }

    /**
     * Execute a workflow with streaming output support.
     *
     * @param workflow    Workflow name (String) or Workflow instance
     * @param inputs      Input data for the workflow
     * @param session     Session ID or session instance
     * @param context     Model context
     * @param streamModes Types of streaming data to output
     * @return Iterator of streaming chunks
     */
    public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
                                                         ModelContext context, List<StreamMode> streamModes) {
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes);
    }

    // ========== Agent ==========

    /**
     * Execute a single agent with given inputs.
     *
     * @param agent   Agent name (String) or agent instance
     * @param inputs  Input data for the agent
     * @param session Session ID or session instance
     * @param context Model context
     * @return Agent execution result
     */
    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context) {
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context);
    }

    /**
     * Execute a single agent with streaming output support.
     *
     * @param agent       Agent name (String) or agent instance
     * @param inputs      Input data for the agent
     * @param session     Session ID or session instance
     * @param context     Model context
     * @param streamModes Types of streaming data to output
     * @return Iterator of streaming chunks
     */
    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session,
                                                      ModelContext context, List<StreamMode> streamModes) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes);
    }

    // ========== Agent Group ==========

    /**
     * Execute a group of agents with given inputs.
     *
     * @param agentGroup Agent group name (String) or group instance
     * @param inputs     Input data for the agent group
     * @param session    Session ID or session instance
     * @param context    Model context
     * @return Agent group execution result
     */
    public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context) {
        return GLOBAL_RUNNER.runAgentGroup(agentGroup, inputs, session, context);
    }

    /**
     * Execute a group of agents with streaming output support.
     *
     * @param agentGroup  Agent group name (String) or group instance
     * @param inputs      Input data for the agent group
     * @param session     Session ID or session instance
     * @param context     Model context
     * @param streamModes Types of streaming data to output
     * @return Iterator of streaming chunks
     */
    public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
                                                           ModelContext context, List<StreamMode> streamModes) {
        return GLOBAL_RUNNER.runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes);
    }

    // ========== Release ==========

    /**
     * Release resources associated with a session.
     *
     * @param sessionId ID of the session to clean up
     */
    public static void release(String sessionId) {
        GLOBAL_RUNNER.release(sessionId);
    }
}
