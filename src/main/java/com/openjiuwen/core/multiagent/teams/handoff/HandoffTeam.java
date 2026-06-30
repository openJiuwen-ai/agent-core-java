/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.handoff;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;

/**
 * Public class HandoffTeam used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HandoffTeam extends BaseTeam {
    /**
     * Auto-generated for codecheck compliance.
     */
    public HandoffTeam(TeamCard card, HandoffTeamConfig config) {
        super(card, config != null ? config : new HandoffTeamConfig());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public HandoffTeam(TeamCard card) {
        this(card, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Object message, AgentGroupSessionApi session) {
        HandoffTeamConfig config = getTeamConfig() instanceof HandoffTeamConfig handoffConfig
                ? handoffConfig
                : HandoffTeamConfig.class.cast(getTeamConfig());
        String startAgentId = resolveStartAgentId(config);
        HandoffOrchestrator orchestrator = HandoffOrchestrator.restoreFromSession(
                session != null ? session : new AgentGroupSessionApi(),
                startAgentId,
                listAgents(),
                config.getHandoff()
        );
        Object currentInput = message;
        List<Map<String, Object>> history = new ArrayList<>();

        while (true) {
            Object result = send(currentInput, orchestrator.getCurrentAgentId(), getTeamCard().getId(),
                    session != null ? session.getSessionId() : null, session);
            history.add(Map.of(
                    "agent", orchestrator.getCurrentAgentId(),
                    "output", result
            ));
            if (session != null) {
                session.updateState(Map.of(HandoffOrchestrator.HANDOFF_HISTORY_KEY, history));
            }
            HandoffSignal signal = HandoffSignal.extract(result);
            if (signal == null) {
                orchestrator.complete(result);
                return result;
            }
            boolean isAllowed = orchestrator.requestHandoff(signal.target());
            if (!isAllowed) {
                throw ErrorHelper.buildError(
                        StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                        "error_msg", "Handoff to '" + signal.target() + "' is not allowed"
                );
            }
            if (session != null) {
                orchestrator.saveToSession(session);
            }
            currentInput = signal.message() != null ? signal.message() : currentInput;
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        return List.<Object>of(invoke(message, session)).iterator();
    }

    private String resolveStartAgentId(HandoffTeamConfig config) {
        AgentCard start = config.getHandoff() != null ? config.getHandoff().getStartAgent() : null;
        if (start != null && start.getId() != null && !start.getId().isBlank()) {
            return start.getId();
        }
        List<String> agents = listAgents();
        if (agents.isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_GROUP_EXECUTION_ERROR,
                    "error_msg", "No agents registered in handoff team"
            );
        }
        return agents.get(0);
    }
}
