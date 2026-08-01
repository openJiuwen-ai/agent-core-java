/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multiagent.runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that delegates work to a sub-agent via {@link TeamRuntime#send}.
 * <p>
 * Injected by {@link HierarchicalMsgBusTeam} into the supervisor agent's
 * {@code AbilityManager}. The tool name exposed to the LLM is
 * {@code delegate_to_{target_id}}; invoking it dispatches the message to
 * the target agent and returns the result.
 * </p>
 * <p>
 * Mirrors Python's {@code P2PAbilityManager} sub-agent dispatch.
 * </p>
 * 
 * @since 0.1.7
 */
public class DelegateTool extends Tool {
    private final String targetId;
    private final TeamRuntime runtime;
    private final String senderId;
    private final String teamId;

    /**
     * Create a delegate tool targeting {@code targetId}.
     * 
     * @param targetId ID of the agent to delegate to.
     * @param targetDescription Optional description of the target agent appended
     * @param runtime the team runtime for message dispatch.
     * @param senderId the ID of the agent that owns this tool.
     * @param teamId the team ID for session metadata.
     *            to the tool description shown to the LLM.
     * @since 0.1.7
     */
    public DelegateTool(String targetId, String targetDescription, TeamRuntime runtime, String senderId,
            String teamId) {
        super(buildCard(targetId, targetDescription));
        this.targetId = targetId;
        this.runtime = runtime;
        this.senderId = senderId;
        this.teamId = teamId;
    }

    /**
     * Get the target agent ID.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTargetId() {
        return targetId;
    }

    /**
     * Dispatch the message to the target agent via the team runtime and
     * return the result.
     * <p>
     * The {@code session} is extracted from {@code kwargs} (set by
     * {@code AbilityManager.invokeTool}) so that nested dispatch preserves
     * the call-depth tracking in {@code TeamRuntime}.
     * </p>
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        // Extract session from kwargs (set by AbilityManager.invokeTool)
        AgentGroupSessionApi session = null;
        String sessionId = null;
        if (kwargs != null) {
            Object sessionObj = kwargs.get("session");
            if (sessionObj instanceof AgentGroupSessionApi) {
                session = (AgentGroupSessionApi) sessionObj;
                sessionId = session.getSessionId();
            }
        }
        if (session == null) {
            session = new AgentGroupSessionApi();
            session.setTeamId(teamId);
            sessionId = session.getSessionId();
        }
        // Pass the full inputs as the message to the target agent
        Object message = inputs != null ? inputs : "";
        return runtime.send(message, targetId, senderId, sessionId, session);
    }

    /**
     * Streaming variant — yields the single {@link #invoke} result.
     * 
     * @param inputs inputs
     * @param kwargs kwargs
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return List.of(invoke(inputs, kwargs)).iterator();
    }

    /**
     * buildCard.
     * 
     * @param targetId targetId
     * @param targetDescription targetDescription
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard buildCard(String targetId, String targetDescription) {
        String toolName = targetId;
        String description = "Delegate a task to " + targetId + " for processing.";
        if (targetDescription != null && !targetDescription.isBlank()) {
            description += " " + targetDescription;
        }
        Map<String, Object> messageProp = new LinkedHashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "The task description to delegate.");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("message", messageProp);
        Map<String, Object> inputParams = new LinkedHashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("message"));
        return ToolCard.builder().id(toolName).name(toolName).description(description).inputParams(inputParams).build();
    }
}
