/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public class HandoffTeam used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class HandoffTeam extends BaseTeam {
    /**
     * HandoffTeam.
     * 
     * @param card card
     * @param config config
     * @since 0.1.7
     */
    public HandoffTeam(TeamCard card, HandoffTeamConfig config) {
        super(card, config != null ? config : new HandoffTeamConfig());
    }

    /**
     * HandoffTeam.
     * 
     * @param card card
     * @since 0.1.7
     */
    public HandoffTeam(TeamCard card) {
        this(card, null);
    }

    /**
     * invoke.
     * 
     * @param message message
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object message, AgentGroupSessionApi session) {
        return runChain(message, session, null).finalResult();
    }

    /**
     * stream.
     * 
     * @param message message
     * @param session session
     * @return Iterator<Object>
     * @since 0.1.7
     */
    @Override
    public java.util.Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        List<Object> chunks = new ArrayList<>();
        runChain(message, session, chunks::add);
        // Each hop's output (intermediate handoff messages + the terminal
        // answer) is already emitted to the sink inside runChain; just hand
        // back the collected chunks.
        return chunks.iterator();
    }

    /**
     * Run the handoff chain, optionally emitting each hop's output as an
     * {@link OutputSchema} chunk (enriched with {@code source_team_id}) to
     * {@code chunkSink}.
     * <p>
     * Mirrors Python's {@code ContainerAgent._invoke_target_with_stream}:
     * every agent's output (triage's transfer message, the target agent's
     * final answer) is emitted via {@code team_session.write_stream} so
     * streaming callers see each routed hop. When {@code chunkSink} is null
     * (the {@link #invoke} path), no chunks are emitted and only the final
     * result is returned.
     * </p>
     * 
     * @param message user input.
     * @param session team session (may be null).
     * @param chunkSink receives each hop's {@link OutputSchema} chunk; null to
     * @return the terminal chain result carrying the final answer (and its
     *         suppress streaming emission.
     *         streaming chunk form when emitting).
     * @since 0.1.7
     */
    private ChainResult runChain(Object message, AgentGroupSessionApi session,
            java.util.function.Consumer<Object> chunkSink) {
        HandoffTeamConfig config = getTeamConfig() instanceof HandoffTeamConfig handoffConfig
                ? handoffConfig
                : HandoffTeamConfig.class.cast(getTeamConfig());
        String startAgentId = resolveStartAgentId(config);
        injectHandoffTools(config);
        HandoffOrchestrator orchestrator =
            HandoffOrchestrator.restoreFromSession(session != null ? session : new AgentGroupSessionApi(), startAgentId,
                    listAgents(), config.getHandoff());
        Object currentInput = message;
        List<Map<String, Object>> history = new ArrayList<>();
        String teamId = getTeamCard() != null ? getTeamCard().getId() : null;

        while (true) {
            // Each hop gets a fresh per-agent session so the LLM context of a
            // prior agent (e.g. triage's transfer_to_xxx tool message) does not
            // leak into the next agent's handoff-signal scan. Mirrors Python's
            // ContainerAgent creating a separate agent_session per hop.
            AgentGroupSessionApi hopSession = new AgentGroupSessionApi(session != null ? session.getSessionId() : null);
            if (teamId != null) {
                hopSession.setTeamId(teamId);
            }
            Object result = send(currentInput, orchestrator.getCurrentAgentId(), getTeamCard().getId(),
                    hopSession.getSessionId(), hopSession);
            history.add(Map.of("agent", orchestrator.getCurrentAgentId(), "output", result));
            if (session != null) {
                session.updateState(Map.of(HandoffOrchestrator.HANDOFF_HISTORY_KEY, history));
            }
            if (chunkSink != null) {
                chunkSink.accept(toChunk(result, teamId));
            }
            HandoffSignal signal = HandoffSignal.extract(result, hopSession);
            if (signal == null) {
                orchestrator.complete(result);
                return new ChainResult(result);
            }
            boolean isAllowed = orchestrator.requestHandoff(signal.target());
            if (!isAllowed) {
                throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg",
                        "Handoff to '" + signal.target() + "' is not allowed");
            }
            if (session != null) {
                orchestrator.saveToSession(session);
            }
            currentInput = signal.message() != null ? signal.message() : currentInput;
        }
    }

    /**
     * Convert a hop result into an {@link OutputSchema} chunk enriched with
     * {@code source_team_id}, mirroring Python's
     * {@code AgentGroupSessionApi.enrichWithTeamMetadata}.
     * 
     * @param result result
     * @param teamId teamId
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private static OutputSchema toChunk(Object result, String teamId) {
        Map<String, Object> payload;
        if (result instanceof Map<?, ?> map) {
            payload = new LinkedHashMap<>((Map<String, Object>) map);
        } else {
            payload = new LinkedHashMap<>();
            payload.put("output", result == null ? "" : result.toString());
            payload.put("result_type", "answer");
        }
        if (teamId != null && !payload.containsKey("source_team_id")) {
            payload.put("source_team_id", teamId);
        }
        if (!payload.containsKey("result_type")) {
            payload.put("result_type", "answer");
        }
        return new OutputSchema("message", 0, payload);
    }

    /**
     * Terminal result of a handoff chain run.
     */
    private static final class ChainResult {
        private final Object finalResult;

        /**
         * ChainResult.
         * 
         * @param finalResult finalResult
         * @since 0.1.7
         */
        private ChainResult(Object finalResult) {
            this.finalResult = finalResult;
        }

        Object finalResult() {
            return finalResult;
        }
    }

    /**
     * resolveStartAgentId.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private String resolveStartAgentId(HandoffTeamConfig config) {
        AgentCard start = config.getHandoff() != null ? config.getHandoff().getStartAgent() : null;
        if (start != null && start.getId() != null && !start.getId().isBlank()) {
            return start.getId();
        }
        List<String> agents = listAgents();
        if (agents.isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg",
                    "No agents registered in handoff team");
        }
        return agents.get(0);
    }

    /**
     * Inject {@code transfer_to_{target}} tools into each registered agent's
     * {@code AbilityManager} based on the configured handoff routes.
     * <p>
     * Mirrors Python's {@code ContainerAgent._inject_tools_once}: for each
     * agent in the team, a {@link HandoffTool} is created for every allowed
     * handoff target, added to the agent's ability manager, and registered
     * with {@code Runner.resourceMgr()} under the agent's ID tag so the LLM's
     * {@code transfer_to_xxx} tool calls resolve correctly.
     * </p>
     * <p>
     * Idempotent: agents that have already been processed are skipped via a
     * transient tag on the agent's {@code AbilityManager}.
     * </p>
     * 
     * @param config config
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private void injectHandoffTools(HandoffTeamConfig config) {
        HandoffConfig handoff = config.getHandoff();
        List<String> agentIds = listAgents();
        if (agentIds.isEmpty()) {
            return;
        }
        Map<String, Set<String>> routeGraph =
            HandoffOrchestrator.buildRouteGraph(agentIds, handoff != null ? handoff.getRoutes() : null);
        for (String agentId : agentIds) {
            BaseAgent agent;
            try {
                agent = getRuntime().getAgentInstance(agentId);
            } catch (RuntimeException ex) {
                Loggers.MULTI_AGENT.warning("[HandoffTeam:" + getTeamCard().getId() + "] skip tool injection for '"
                        + agentId + "': " + ex.getMessage());
                continue;
            }
            if (agent == null) {
                continue;
            }
            Set<String> allowedTargets = new LinkedHashSet<>(routeGraph.getOrDefault(agentId, new LinkedHashSet<>()));
            if (allowedTargets.isEmpty()) {
                continue;
            }
            for (String targetId : allowedTargets) {
                String toolName = "transfer_to_" + targetId;
                if (agent.getAbilityManager().get(toolName) != null) {
                    continue;
                }
                AgentCard targetCard = getRuntime().getAgentCard(targetId);
                String targetDescription = targetCard != null ? targetCard.getDescription() : "";
                HandoffTool tool = new HandoffTool(targetId, targetDescription);
                agent.getAbilityManager().add(tool.getCard());
                Object existing = Runner.resourceMgr().getTool(tool.getCard().getId(), agentId, TagMatchStrategy.ALL);
                if (existing == null) {
                    Runner.resourceMgr().addTool(tool, agentId);
                }
                Loggers.MULTI_AGENT.info(
                        "[HandoffTeam:" + getTeamCard().getId() + "] injected '" + toolName + "' -> '" + agentId + "'");
            }
        }
    }
}
