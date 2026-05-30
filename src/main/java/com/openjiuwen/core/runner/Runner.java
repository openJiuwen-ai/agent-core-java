/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.agent_teams.RuntimeManager;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.core.runner.callback.CallbackFramework;
import com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.mq.LocalMessageQueue;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

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
    private static final RuntimeManager TEAM_RUNTIME_MANAGER = new RuntimeManager();

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

    /**
     * Get the distributed message queue for cross-process communication.
     */
    public static MessageQueueBase distPubsub() {
        return GLOBAL_RUNNER.getDistPubsub();
    }

    /**
     * Get the reply topic subscription for distributed mode.
     */
    public static ReplyTopicSubscription systemReplySub() {
        return GLOBAL_RUNNER.getSystemReplySub();
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
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, null);
    }

    /**
     * Execute a workflow with given inputs and environment overrides.
     */
    public static Object runWorkflow(Object workflow, Object inputs, Object session, ModelContext context,
                                     Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflow(workflow, inputs, session, context, envs);
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
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes, null);
    }

    /**
     * Execute a workflow with streaming output support and environment overrides.
     */
    public static Iterator<WorkflowChunk> runWorkflowStreaming(Object workflow, Object inputs, Object session,
                                                         ModelContext context, List<StreamMode> streamModes,
                                                         Map<String, Object> envs) {
        return GLOBAL_RUNNER.runWorkflowStreaming(workflow, inputs, session, context, streamModes, envs);
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
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context, null);
    }

    /**
     * Execute a single agent with given inputs and environment overrides.
     */
    public static Object runAgent(Object agent, Object inputs, Object session, ModelContext context,
                                  Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgent(agent, inputs, session, context, envs);
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
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, null);
    }

    /**
     * Execute a single agent with streaming output support and environment overrides.
     */
    public static Iterator<Object> runAgentStreaming(Object agent, Object inputs, Object session,
                                                      ModelContext context, List<StreamMode> streamModes,
                                                      Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentStreaming(agent, inputs, session, context, streamModes, envs);
    }

    /**
     * Execute a Runner-owned {@code TeamAgentSpec} runtime with streaming output.
     *
     * <p>Mirrors Python's {@code Runner.run_agent_team_streaming} enough for the
     * Java agent-team E2E examples: activate or resume the team runtime, emit a
     * {@code team.runtime_ready} acknowledgement, then stream the active
     * {@link TeamAgent}.</p>
     */
    public static Iterator<Object> runAgentTeamStreaming(Object agentTeam, Object inputs, Object session) {
        RuntimeManager.TeamRuntimeActivation activation =
                TEAM_RUNTIME_MANAGER.activate(agentTeam, session, inputs).join();
        Map<String, Object> readyPayload = new LinkedHashMap<>();
        readyPayload.put("event_type", "team.runtime_ready");
        readyPayload.put("team_name", TEAM_RUNTIME_MANAGER.getActiveTeamName().orElse(null));
        readyPayload.put("session_id", TEAM_RUNTIME_MANAGER.getActiveSessionId().orElse(null));
        readyPayload.put("activation_kind", activation.getActivationKind());
        List<Object> prefix = new ArrayList<>();
        prefix.add(new OutputSchema("message", 0, readyPayload));

        Supplier<Iterator<?>> body = Collections::emptyIterator;
        Object active = activation.getAgent();
        Object activeSession = activation.getSession();
        if (active instanceof TeamAgent teamAgent && activeSession instanceof Session typedSession) {
            body = () -> teamAgent.stream(inputs, typedSession, List.of(StreamMode.OUTPUT));
        }
        return concat(prefix.iterator(), body);
    }

    /**
     * Deliver same-session user input to the active Runner-owned team runtime.
     *
     * <p>Mirrors Python's {@code Runner.interact_agent_team}.</p>
     */
    public static boolean interactAgentTeam(String userInput, String teamName, String sessionId) {
        if (!matchesActiveTeam(teamName, sessionId)) {
            return false;
        }
        Object active = TEAM_RUNTIME_MANAGER.getActiveAgent().orElse(null);
        if (active instanceof TeamAgent teamAgent) {
            teamAgent.receiveUserInput(userInput);
            return true;
        }
        return false;
    }

    /**
     * Pause the active Runner-owned team runtime.
     *
     * <p>Mirrors Python's {@code Runner.pause_agent_team}.</p>
     */
    public static boolean pauseAgentTeam(String teamName, String sessionId) {
        if (!matchesActiveTeam(teamName, sessionId)) {
            return false;
        }
        TEAM_RUNTIME_MANAGER.pause();
        return true;
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
        return GLOBAL_RUNNER.runAgentGroup(agentGroup, inputs, session, context, null);
    }

    /**
     * Execute a group of agents with given inputs and environment overrides.
     */
    public static Object runAgentGroup(Object agentGroup, Object inputs, Object session, ModelContext context,
                                       Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentGroup(agentGroup, inputs, session, context, envs);
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
        return GLOBAL_RUNNER.runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes, null);
    }

    /**
     * Execute a group of agents with streaming output support and environment overrides.
     */
    public static Iterator<Object> runAgentGroupStreaming(Object agentGroup, Object inputs, Object session,
                                                           ModelContext context, List<StreamMode> streamModes,
                                                           Map<String, Object> envs) {
        return GLOBAL_RUNNER.runAgentGroupStreaming(agentGroup, inputs, session, context, streamModes, envs);
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

    private static boolean matchesActiveTeam(String teamName, String sessionId) {
        return TEAM_RUNTIME_MANAGER.getActiveTeamName().map(teamName::equals).orElse(false)
                && TEAM_RUNTIME_MANAGER.getActiveSessionId().map(sessionId::equals).orElse(false);
    }

    private static Iterator<Object> concat(Iterator<?> first, Supplier<Iterator<?>> secondSupplier) {
        return new Iterator<>() {
            private Iterator<?> current = first;
            private boolean usingFirst = true;

            @Override
            public boolean hasNext() {
                if (current.hasNext()) {
                    return true;
                }
                if (usingFirst) {
                    usingFirst = false;
                    current = secondSupplier.get();
                    return current.hasNext();
                }
                return false;
            }

            @Override
            public Object next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }
}
