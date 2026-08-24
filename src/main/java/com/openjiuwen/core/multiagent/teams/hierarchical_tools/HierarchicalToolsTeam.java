/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.multiagent.runtime.TeamRuntime;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.AgentProvider;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Agents-as-Tools hierarchical multi-agent team.
 *
 * <p>Mirrors Python's {@code HierarchicalTeam}: child agents are registered as
 * abilities (tools) of their parent agent's {@code AbilityManager}. At
 * {@code invoke}/{@code stream} time the team walks the recorded
 * {@code parent_agent_id} mapping and adds each child {@link AgentCard} to its
 * parent's ability manager so the LLM can dispatch tool calls to child agents
 * through the standard {@code AbilityManager.executeSingleToolCall} path.</p>
 *
 * @since 1.0
 */
public class HierarchicalToolsTeam extends BaseTeam {
    private final TeamRuntime localRuntime;

    /**
     * Auto-generated for codecheck compliance.
     *
     * @param card team card
     * @param config team config
     */
    public HierarchicalToolsTeam(TeamCard card, HierarchicalToolsTeamConfig config) {
        super(card);
        this.localRuntime = new TeamRuntime(getCard() != null ? getCard().getId() : "default");
        if (config != null) {
            configure(config);
        }
    }

    @Override
    public com.openjiuwen.core.multiagent.team_runtime.TeamRuntime getRuntime() {
        return super.getRuntime();
    }

    /**
     * Register an agent without a parent (the root agent).
     *
     * @param card agent card
     * @param provider agent provider
     * @return this team
     */
    @Override
    public BaseTeam addAgent(AgentCard card, java.util.function.Function<AgentCard, ?> provider) {
        super.addAgent(card, provider);
        localRuntime.registerAgent(card, () -> {
            Object result = provider.apply(card);
            if (result instanceof BaseAgent baseAgent) {
                return baseAgent;
            }
            throw new IllegalStateException("Provider must return a BaseAgent instance");
        });
        return this;
    }

    /**
     * Register an agent with an optional parent agent id.
     *
     * <p>Mirrors Python {@code HierarchicalTeam.add_agent(card, provider,
     * parent_agent_id=...)}: when {@code parentAgentId} is provided, the child
     * card is queued so that at {@code invoke}/{@code stream} time it will be
     * added to the parent agent's {@code AbilityManager} as an
     * {@code AgentCard} ability (so the LLM can call the child as a tool).</p>
     *
     * @param card agent card
     * @param provider agent provider function
     * @param parentAgentId optional parent agent id
     * @return this team
     */
    public BaseTeam addAgent(AgentCard card, java.util.function.Function<AgentCard, ?> provider,
                              String parentAgentId) {
        super.addAgent(card, provider);
        localRuntime.registerAgent(card, () -> {
            Object result = provider.apply(card);
            if (result instanceof BaseAgent baseAgent) {
                return baseAgent;
            }
            throw new IllegalStateException("Provider must return a BaseAgent instance");
        });
        if (parentAgentId != null && !parentAgentId.isBlank()) {
            HierarchicalToolsTeamConfig config = toolsConfig();
            if (config.getParentByAgent() == null) {
                config.setParentByAgent(new java.util.LinkedHashMap<>());
            }
            config.getParentByAgent().put(card.getId(), parentAgentId);
            Loggers.MULTI_AGENT.debug(
                    "[HierarchicalToolsTeam:" + getCard().getId()
                            + "] queued " + card.getId() + " as child of " + parentAgentId);
        }
        return this;
    }

    /**
     * Run the team from the root agent.
     *
     * @param message input message
     * @param session agent session
     * @return final result from the root agent
     */
    @Override
    public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        HierarchicalToolsTeamConfig config = toolsConfig();
        if (config.getRootAgent() == null || config.getRootAgent().getId() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg", "root_agent is required");
        }
        injectChildCards(config);
        Object result = localRuntime.send(message, config.getRootAgent().getId(), getCard().getId(),
                session != null ? session.getSessionId() : null, null);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Run the team from the root agent with streaming output.
     *
     * @param message input message
     * @param session agent session
     * @return stream over streaming chunks
     */
    @Override
    public Stream<Object> stream(Object message, AgentSessionApi session) {
        HierarchicalToolsTeamConfig config = toolsConfig();
        if (config.getRootAgent() == null || config.getRootAgent().getId() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg", "root_agent is required");
        }
        injectChildCards(config);
        Object result = localRuntime.send(message, config.getRootAgent().getId(), getCard().getId(),
                session != null ? session.getSessionId() : null, null);

        List<Object> chunks = new ArrayList<>();
        chunks.add(toAnswerChunk(result, getCard() != null ? getCard().getId() : null));
        return chunks.stream();
    }

    /**
     * Register each child as a {@link HierarchicalDelegateTool} into its parent
     * agent's {@code AbilityManager}.
     *
     * <p>Mirrors Python {@code HierarchicalTeam._setup_hierarchy}: for each
     * (child, parent) pair recorded via
     * {@link #addAgent(AgentCard, AgentProvider, String)}, look up the parent
     * agent instance and inject a {@link HierarchicalDelegateTool} wrapping the
     * child. The tool's {@code ToolCard} mirrors the child {@link AgentCard}'s
     * description and input params so the LLM sees the same tool schema Python
     * exposes. On invoke the tool dispatches to the child via
     * {@link com.openjiuwen.core.multiagent.runtime.TeamRuntime#send} and
     * writes a {@code message} chunk to the team session stream, enabling
     * streaming output for {@link #stream}.</p>
     *
     * <p>Idempotent: already-registered tools are skipped.</p>
     */
    private void injectChildCards(HierarchicalToolsTeamConfig config) {
        Map<String, String> parentByAgent = config.getParentByAgent();
        if (parentByAgent == null || parentByAgent.isEmpty()) {
            return;
        }
        String teamId = getCard() != null ? getCard().getId() : null;
        TeamRuntime runtime = localRuntime;
        for (Map.Entry<String, String> entry : parentByAgent.entrySet()) {
            String childId = entry.getKey();
            String parentId = entry.getValue();
            if (parentId == null || parentId.isBlank()) {
                continue;
            }
            BaseAgent parent;
            try {
                BaseAgent agentInstance = runtime.getAgentInstance(parentId);
                if (agentInstance != null) {
                    parent = agentInstance;
                } else {
                    continue;
                }
            } catch (RuntimeException ex) {
                Loggers.MULTI_AGENT.warning(
                        "[HierarchicalToolsTeam:" + teamId
                                + "] skip tool injection for '" + childId
                                + "': parent '" + parentId + "' not available: "
                                + ex.getMessage());
                continue;
            }
            if (parent == null) {
                continue;
            }
            if (parent.getAbilityManager().get(childId) != null) {
                continue;
            }
            AgentCard childCard = runtime.getAgentCard(childId);
            if (childCard == null) {
                Loggers.MULTI_AGENT.warning(
                        "[HierarchicalToolsTeam:" + teamId
                                + "] skip tool injection for '" + childId
                                + "': child card not registered in runtime");
                continue;
            }
            HierarchicalDelegateTool tool = new HierarchicalDelegateTool(
                    childId, childCard, runtime, parentId, teamId);
            parent.getAbilityManager().add(tool.getCard());
            Object existing = Runner.resourceMgr().getTool(
                    tool.getCard().getId(), parentId, TagMatchStrategy.ALL);
            if (existing == null) {
                Runner.resourceMgr().addTool(tool, parentId);
            }
            Loggers.MULTI_AGENT.info(
                    "[HierarchicalToolsTeam:" + teamId + "] registered " + childId
                            + " -> " + parentId + ".ability_manager");
        }
    }

    /**
     * Wrap the root agent's final result as an {@code answer} chunk.
     *
     * <p>Mirrors Python's terminal {@code team_session.write_stream} of the
     * root agent's {@code {output, result_type=answer}} return value. The
     * payload shape is {@code {output: {output, result_type}, source_team_id}}
     * to match {@code HierarchicalTeamCaseSupport.defaultStreamChunks}' third
     * chunk.</p>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> toAnswerChunk(Object result, String teamId) {
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
        return wrapper;
    }

    private HierarchicalToolsTeamConfig toolsConfig() {
        com.openjiuwen.core.multiagent.TeamConfig cfg = getConfig();
        if (cfg instanceof HierarchicalToolsTeamConfig toolsConfig) {
            return toolsConfig;
        }
        // Wrap the base config if not already the specific type
        HierarchicalToolsTeamConfig specific = new HierarchicalToolsTeamConfig();
        if (cfg != null) {
            specific.setMaxAgents(cfg.getMaxAgents());
            specific.setMaxConcurrentMessages(cfg.getMaxConcurrentMessages());
            specific.setMessageTimeout(cfg.getMessageTimeout());
        }
        return specific;
    }
}
