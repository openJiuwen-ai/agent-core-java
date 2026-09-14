/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.TeamConfig;
import com.openjiuwen.core.multiagent.runtime.TeamRuntime;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentGroupSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Hierarchical message-bus team: a supervisor agent coordinates child agents
 * through injected {@link DelegateTool}s over the local team runtime.
 *
 * <p>For each {@code supervisor -> children} entry in the configured hierarchy,
 * a {@link DelegateTool} is registered into the supervisor's
 * {@code AbilityManager} so the supervisor LLM can dispatch tool calls to its
 * children. Invoking the team sends the message to the supervisor, which drives
 * the delegation loop.</p>
 */
public class HierarchicalMsgBusTeam extends BaseTeam {

    private final TeamRuntime localRuntime;

    /**
     * Create a hierarchical message-bus team.
     *
     * @param card team card
     * @param config team config carrying the supervisor agent and hierarchy
     */
    public HierarchicalMsgBusTeam(TeamCard card, HierarchicalMsgBusTeamConfig config) {
        super(card, config);
        this.localRuntime = new TeamRuntime(card != null ? card.getId() : "default");
    }

    /**
     * Register an agent into both the base team runtime and the local runtime
     * used for point-to-point delegation dispatch.
     *
     * @param card agent card
     * @param provider agent provider
     * @return this team
     */
    @Override
    public BaseTeam addAgent(AgentCard card, Function<AgentCard, ?> provider) {
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
     * Run the team from the supervisor agent.
     *
     * @param message input message
     * @param session agent session
     * @return final result from the supervisor agent
     */
    @Override
    public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        HierarchicalMsgBusTeamConfig config = msgBusConfig();
        String supervisorId = requireSupervisorId(config);
        injectDelegateTools(config);
        Object result = localRuntime.send(message, supervisorId, getCard().getId(),
                session != null ? session.getSessionId() : null, toGroupSession(session));
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Run the team from the supervisor agent with streaming output.
     *
     * @param message input message
     * @param session agent session
     * @return stream over streaming chunks
     */
    @Override
    public Stream<Object> stream(Object message, AgentSessionApi session) {
        HierarchicalMsgBusTeamConfig config = msgBusConfig();
        String supervisorId = requireSupervisorId(config);
        AgentGroupSession groupSession = toGroupSession(session);
        injectDelegateTools(config);
        Object result = localRuntime.send(message, supervisorId, getCard().getId(),
                groupSession != null ? groupSession.getSessionId() : null, groupSession);

        // Buffered stream: send completes synchronously, then drain session chunks.
        // Callers receive a finite Stream after the run, not mid-execution live chunks.
        List<Object> chunks = new ArrayList<>();
        if (groupSession != null) {
            groupSession.closeStream();
            Iterator<Object> sessionStream = groupSession.streamIterator();
            while (sessionStream.hasNext()) {
                chunks.add(sessionStream.next());
            }
        }
        chunks.add(toAnswerChunk(result, getCard() != null ? getCard().getId() : null));
        return chunks.stream();
    }

    /**
     * Inject a {@link DelegateTool} into each supervisor agent's
     * {@code AbilityManager} for every child in the configured hierarchy, and
     * register the tool with {@code Runner.resourceMgr()} under the supervisor's
     * ID tag so the standard tool-execution path can resolve it.
     *
     * <p>Idempotent: supervisors that already expose a same-named ability are
     * skipped.</p>
     *
     * @param config team config carrying the hierarchy
     */
    private void injectDelegateTools(HierarchicalMsgBusTeamConfig config) {
        Map<String, List<String>> hierarchy = config.getHierarchy();
        if (hierarchy == null || hierarchy.isEmpty()) {
            return;
        }
        String teamId = getCard() != null ? getCard().getId() : null;
        for (Map.Entry<String, List<String>> entry : hierarchy.entrySet()) {
            String supervisorId = entry.getKey();
            List<String> children = entry.getValue();
            if (children == null || children.isEmpty()) {
                continue;
            }
            if (!localRuntime.hasAgent(supervisorId)) {
                Loggers.MULTI_AGENT.warning("[HierarchicalMsgBusTeam:" + teamId
                        + "] skip tool injection for '" + supervisorId + "': agent not registered");
                continue;
            }
            BaseAgent supervisor = localRuntime.getAgentInstance(supervisorId);
            if (supervisor == null) {
                continue;
            }
            for (String childId : children) {
                injectChildTool(supervisor, supervisorId, childId, teamId);
            }
        }
    }

    /**
     * Inject a single child delegate tool into the supervisor, skipping when the
     * supervisor already exposes a same-named ability.
     *
     * @param supervisor the supervisor agent instance
     * @param supervisorId the supervisor agent id
     * @param childId the child agent id to inject
     * @param teamId the team id used for logging and session metadata
     */
    private void injectChildTool(BaseAgent supervisor, String supervisorId, String childId, String teamId) {
        if (supervisor.getAbilityManager().get(childId).isPresent()) {
            return;
        }
        AgentCard childCard = localRuntime.getAgentCard(childId);
        String childDescription = childCard != null ? childCard.getDescription() : "";
        DelegateTool tool = new DelegateTool(childId, childDescription, localRuntime, supervisorId, teamId);
        supervisor.getAbilityManager().add(tool.getCard());
        Object existing = Runner.resourceMgr().getTool(tool.getCard().getId(), supervisorId, TagMatchStrategy.ALL);
        if (existing == null) {
            Runner.resourceMgr().addTool(tool, supervisorId);
        }
        Loggers.MULTI_AGENT.info("[HierarchicalMsgBusTeam:" + teamId + "] injected '" + childId + "' -> '"
                + supervisorId + "'");
    }

    /**
     * Validate and return the configured supervisor agent id.
     *
     * @param config team config
     * @return the supervisor agent id
     */
    private static String requireSupervisorId(HierarchicalMsgBusTeamConfig config) {
        if (config.getSupervisorAgent() == null || config.getSupervisorAgent().getId() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg",
                    "supervisor_agent is required");
        }
        return config.getSupervisorAgent().getId();
    }

    /**
     * Wrap the supervisor agent's final result as a terminal {@code answer}
     * {@link OutputSchema} chunk.
     *
     * @param result the supervisor agent result
     * @param teamId the team id
     * @return the answer chunk
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
     * Cast the API session to the concrete {@link AgentGroupSession} expected by
     * the local {@link TeamRuntime#send} dispatch, returning {@code null} when the
     * caller did not provide a group session.
     *
     * @param session the API session
     * @return the group session, or {@code null}
     */
    private static AgentGroupSession toGroupSession(AgentSessionApi session) {
        return session instanceof AgentGroupSession groupSession ? groupSession : null;
    }

    private HierarchicalMsgBusTeamConfig msgBusConfig() {
        TeamConfig config = getConfig();
        if (config instanceof HierarchicalMsgBusTeamConfig msgBusConfig) {
            return msgBusConfig;
        }
        return new HierarchicalMsgBusTeamConfig();
    }
}
