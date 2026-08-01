/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.multiagent.runtime.TeamRuntime;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool that delegates work to a child agent and emits the child's result as a
 * streaming {@code message} chunk to the team session.
 * <p>
 * Injected by {@link HierarchicalToolsTeam} into the parent agent's
 * {@code AbilityManager} (mirroring Python's
 * {@code HierarchicalTeam._setup_hierarchy} which adds the child
 * {@link AgentCard} to the parent's {@code ability_manager}). The tool name
 * exposed to the LLM is the child agent id (e.g. {@code literature_researcher});
 * invoking it dispatches the message to the child agent via
 * {@link TeamRuntime#send} and then writes a {@code message} chunk to the
 * team session stream so streaming callers see each child agent's output.
 * </p>
 * <p>
 * Unlike {@code HierarchicalMsgBusTeam}'s {@code DelegateTool} which only
 * returns the child's result, this tool also calls
 * {@link AgentGroupSessionApi#writeStream(Object)} with a
 * {@code {"message": <child result text>}} payload. The session's
 * {@code enrichWithTeamMetadata} then adds {@code source_agent_id} (this tool
 * sets {@code currentAgentId = targetId} before writeStream, because
 * {@code TeamRuntime.send}'s finally clears it) and {@code source_team_id}.
 * </p>
 * 
 * @since 0.1.7
 */
public class HierarchicalDelegateTool extends Tool {
    private final String targetId;
    private final TeamRuntime runtime;
    private final String senderId;
    private final String teamId;

    /**
     * Create a delegate tool targeting {@code targetId}.
     * 
     * @param targetId ID of the child agent to delegate to.
     * @param targetCard Child agent card (used for tool description and
     * @param runtime the team runtime for message dispatch.
     * @param senderId the ID of the parent agent that owns this tool.
     * @param teamId the team ID for session metadata.
     *            input params schema shown to the LLM).
     * @since 0.1.7
     */
    public HierarchicalDelegateTool(String targetId, AgentCard targetCard, TeamRuntime runtime, String senderId,
            String teamId) {
        super(buildCard(targetId, targetCard));
        this.targetId = targetId;
        this.runtime = runtime;
        this.senderId = senderId;
        this.teamId = teamId;
    }

    /**
     * Dispatch the message to the child agent via the team runtime, then emit
     * the child's result as a {@code message} chunk to the team session stream.
     * <p>
     * The {@code session} is extracted from {@code kwargs} (set by
     * {@code AbilityManager.invokeTool}) so that nested dispatch preserves the
     * team session's streamWriter. Before {@code writeStream} this tool sets
     * {@code session.currentAgentId = targetId} so the chunk is enriched with
     * the correct {@code source_agent_id} (TeamRuntime.send's finally would
     * otherwise clear it back to null before we get here).
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
        AgentGroupSessionApi session = null;
        if (kwargs != null) {
            Object sessionObj = kwargs.get("session");
            if (sessionObj instanceof AgentGroupSessionApi groupSession) {
                session = groupSession;
            }
        }
        String sessionId = null;
        if (session == null) {
            session = new AgentGroupSessionApi();
            if (teamId != null) {
                session.setTeamId(teamId);
            }
            sessionId = session.getSessionId();
        } else {
            sessionId = session.getSessionId();
        }
        Object message = inputs != null ? inputs : "";
        // Dispatch to the child agent via runtime.send, which passes the team
        // session down so leaf agents that call session.writeStream inside
        // their invoke (mirroring Python's `await session.write_stream(...)`)
        // write directly to the team session stream with correct
        // source_agent_id (TeamRuntime.send sets currentAgentId=targetId).
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

    @SuppressWarnings("unchecked")
    /**
     * buildCard.
     * 
     * @param targetId targetId
     * @param targetCard targetCard
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard buildCard(String targetId, AgentCard targetCard) {
        String toolName = targetId;
        String toolId = targetId + "_delegate_tool";
        String description = "Delegate a task to " + targetId + " for processing.";
        if (targetCard != null && targetCard.getDescription() != null && !targetCard.getDescription().isBlank()) {
            description = targetCard.getDescription();
        }
        Map<String, Object> inputParams;
        Object raw = targetCard != null ? targetCard.getInputParams() : null;
        if (raw instanceof Map<?, ?> map) {
            inputParams = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                inputParams.put(String.valueOf(e.getKey()), e.getValue());
            }
        } else {
            inputParams = defaultInputParams();
        }
        // Use a distinct tool ID (suffixed with "_delegate_tool") to avoid
        // ResourceMgr tagMgr collisions with the agent registered under the
        // same ID. The tool name visible to the LLM remains the child agent ID.
        return ToolCard.builder().id(toolId).name(toolName).description(description).inputParams(inputParams).build();
    }

    /**
     * defaultInputParams.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> defaultInputParams() {
        Map<String, Object> messageProp = new LinkedHashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "The task description to delegate.");
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("message", messageProp);
        Map<String, Object> inputParams = new LinkedHashMap<>();
        inputParams.put("type", "object");
        inputParams.put("properties", properties);
        inputParams.put("required", List.of("message"));
        return inputParams;
    }
}
