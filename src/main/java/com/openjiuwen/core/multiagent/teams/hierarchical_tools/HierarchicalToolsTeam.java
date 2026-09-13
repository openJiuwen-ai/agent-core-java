/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.AgentProvider;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agents-as-Tools hierarchical multi-agent team.
 * <p>
 * Mirrors Python's {@code HierarchicalTeam}: child agents are registered as
 * abilities (tools) of their parent agent's {@code AbilityManager}. At
 * {@code invoke}/{@code stream} time the team walks the recorded
 * {@code parent_agent_id} mapping and adds each child {@link AgentCard} to its
 * parent's ability manager so the LLM can dispatch tool calls to child agents
 * through the standard {@code AbilityManager.executeSingleToolCall} path.
 * </p>
 * 
 * @since 0.1.7
 */
public class HierarchicalToolsTeam extends BaseTeam {
    /**
     * HierarchicalToolsTeam.
     * 
     * @param card card
     * @param config config
     * @since 0.1.7
     */
    public HierarchicalToolsTeam(TeamCard card, HierarchicalToolsTeamConfig config) {
        super(card, config);
    }

    /**
     * Register an agent without a parent (the root agent).
     * 
     * @param card agent card
     * @param provider agent provider
     * @return this team
     * @since 0.1.7
     */
    @Override
    public BaseTeam addAgent(AgentCard card, AgentProvider<? extends BaseAgent> provider) {
        return addAgent(card, provider, null);
    }

    /**
     * Register an agent with an optional parent agent id.
     * <p>
     * Mirrors Python {@code HierarchicalTeam.add_agent(card, provider,
     * parent_agent_id=...)}: when {@code parentAgentId} is provided, the child
     * card is queued so that at {@code invoke}/{@code stream} time it will be
     * added to the parent agent's {@code AbilityManager} as an
     * {@code AgentCard} ability (so the LLM can call the child as a tool).
     * </p>
     * 
     * @param card agent card
     * @param provider agent provider
     * @param parentAgentId optional parent agent id
     * @return this team
     * @since 0.1.7
     */
    public BaseTeam addAgent(AgentCard card, AgentProvider<? extends BaseAgent> provider, String parentAgentId) {
        super.addAgent(card, provider);
        if (parentAgentId != null && !parentAgentId.isBlank()) {
            HierarchicalToolsTeamConfig config = toolsConfig();
            if (config.getParentByAgent() == null) {
                config.setParentByAgent(new java.util.LinkedHashMap<>());
            }
            config.getParentByAgent().put(card.getId(), parentAgentId);
            Loggers.MULTI_AGENT.debug("[HierarchicalToolsTeam:" + getTeamCard().getId() + "] queued " + card.getId()
                    + " as child of " + parentAgentId);
        }
        return this;
    }

    /**
     * Run the team from the root agent.
     * 
     * @param message input message
     * @param session agent group session
     * @return final result from the root agent
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object message, AgentGroupSessionApi session) {
        HierarchicalToolsTeamConfig config = toolsConfig();
        if (config.getRootAgent() == null || config.getRootAgent().getId() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg", "root_agent is required");
        }
        injectChildCards(config);
        return send(message, config.getRootAgent().getId(), getTeamCard().getId(),
                session != null ? session.getSessionId() : null, session);
    }

    /**
     * Run the team from the root agent with streaming output.
     * <p>
     * Mirrors Python {@code HierarchicalTeam.stream}: runs the root agent
     * (which drives the ReAct loop and calls child agents via the injected
     * {@link HierarchicalDelegateTool}s), collects the {@code message} chunks
     * each delegate tool writes to the team session stream, then appends a
     * final {@code answer} chunk wrapping the root agent's return value.
     * </p>
     * <p>
     * The root agent is invoked via {@code invoke} (not its personal
     * {@code stream}), so no ReAct intermediate {@code llm_reasoning} chunks
     * leak into the team stream — only the delegate-tool {@code message}
     * chunks plus the terminal {@code answer} chunk are yielded, matching the
     * Python contract's 3-chunk expectation.
     * </p>
     * 
     * @param message input message
     * @param session agent group session
     * @return iterator over streaming chunks
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        HierarchicalToolsTeamConfig config = toolsConfig();
        if (config.getRootAgent() == null || config.getRootAgent().getId() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg", "root_agent is required");
        }
        AgentGroupSessionApi streamSession = session != null ? session : new AgentGroupSessionApi();
        if (getTeamCard() != null) {
            streamSession.setTeamId(getTeamCard().getId());
        }
        injectChildCards(config);
        Object result = send(message, config.getRootAgent().getId(), getTeamCard().getId(),
                streamSession.getSessionId(), streamSession);

        // Signal end-of-stream so streamIterator() terminates instead of
        // blocking forever waiting for more chunks. send() already completed,
        // so no more writeStream calls will happen.
        streamSession.getInner().streamWriterManager().getStreamEmitter().close();

        // Collect message chunks written by HierarchicalDelegateTool into the
        // team session stream.
        List<Object> chunks = new ArrayList<>();
        Iterator<Object> sessionStream = streamSession.getInner().streamWriterManager().streamIterator();
        while (sessionStream.hasNext()) {
            chunks.add(sessionStream.next());
        }
        // Append the terminal answer chunk wrapping the root agent's result.
        chunks.add(toAnswerChunk(result, getTeamCard() != null ? getTeamCard().getId() : null));
        return chunks.iterator();
    }

    /**
     * Register each child as a {@link HierarchicalDelegateTool} into its parent
     * agent's {@code AbilityManager}.
     * <p>
     * Mirrors Python {@code HierarchicalTeam._setup_hierarchy}: for each
     * (child, parent) pair recorded via
     * {@link #addAgent(AgentCard, AgentProvider, String)}, look up the parent
     * agent instance and inject a {@link HierarchicalDelegateTool} wrapping the
     * child. The tool's {@code ToolCard} mirrors the child {@link AgentCard}'s
     * description and input params so the LLM sees the same tool schema Python
     * exposes. On invoke the tool dispatches to the child via
     * {@link com.openjiuwen.core.multiagent.runtime.TeamRuntime#send} and
     * writes a {@code message} chunk to the team session stream, enabling
     * streaming output for {@link #stream}.
     * </p>
     * <p>
     * Idempotent: already-registered tools are skipped.
     * </p>
     * 
     * @param config config
     * @since 0.1.7
     */
    private void injectChildCards(HierarchicalToolsTeamConfig config) {
        Map<String, String> parentByAgent = config.getParentByAgent();
        if (parentByAgent == null || parentByAgent.isEmpty()) {
            return;
        }
        String teamId = getTeamCard() != null ? getTeamCard().getId() : null;
        for (Map.Entry<String, String> entry : parentByAgent.entrySet()) {
            String childId = entry.getKey();
            String parentId = entry.getValue();
            if (parentId == null || parentId.isBlank()) {
                continue;
            }
            BaseAgent parent;
            try {
                parent = getRuntime().getAgentInstance(parentId);
            } catch (RuntimeException ex) {
                Loggers.MULTI_AGENT.warning("[HierarchicalToolsTeam:" + teamId + "] skip tool injection for '" + childId
                        + "': parent '" + parentId + "' not available: " + ex.getMessage());
                continue;
            }
            if (parent == null) {
                continue;
            }
            if (parent.getAbilityManager().get(childId) != null) {
                continue;
            }
            AgentCard childCard = getRuntime().getAgentCard(childId);
            if (childCard == null) {
                Loggers.MULTI_AGENT.warning("[HierarchicalToolsTeam:" + teamId + "] skip tool injection for '" + childId
                        + "': child card not registered in runtime");
                continue;
            }
            HierarchicalDelegateTool tool =
                new HierarchicalDelegateTool(childId, childCard, getRuntime(), parentId, teamId);
            parent.getAbilityManager().add(tool.getCard());
            Object existing = Runner.resourceMgr().getTool(tool.getCard().getId(), parentId, TagMatchStrategy.ALL);
            if (existing == null) {
                Runner.resourceMgr().addTool(tool, parentId);
            }
            Loggers.MULTI_AGENT.info("[HierarchicalToolsTeam:" + teamId + "] registered " + childId + " -> " + parentId
                    + ".ability_manager");
        }
    }

    /**
     * Wrap the root agent's final result as an {@code answer} chunk.
     * <p>
     * Mirrors Python's terminal {@code team_session.write_stream} of the
     * root agent's {@code {output, result_type=answer}} return value. The
     * payload shape is {@code {output: {output, result_type}, source_team_id}}
     * to match {@code HierarchicalTeamCaseSupport.defaultStreamChunks}' third
     * chunk.
     * </p>
     * 
     * @param result result
     * @param teamId teamId
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private static OutputSchema toAnswerChunk(Object result, String teamId) {
        Map<String, Object> answerPayload;
        if (result instanceof Map<?, ?> map) {
            answerPayload = new LinkedHashMap<>((Map<String, Object>) map);
        } else {
            answerPayload = new LinkedHashMap<>();
            answerPayload.put("output", result == null ? "" : String.valueOf(result));
            answerPayload.put("result_type", "answer");
        }
        if (!answerPayload.containsKey("result_type")) {
            answerPayload.put("result_type", "answer");
        }
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("output", answerPayload);
        if (teamId != null) {
            wrapper.put("source_team_id", teamId);
        }
        return new OutputSchema("message", 0, wrapper);
    }

    /**
     * toolsConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    private HierarchicalToolsTeamConfig toolsConfig() {
        return getTeamConfig() instanceof HierarchicalToolsTeamConfig toolsConfig
                ? toolsConfig
                : HierarchicalToolsTeamConfig.class.cast(getTeamConfig());
    }
}
