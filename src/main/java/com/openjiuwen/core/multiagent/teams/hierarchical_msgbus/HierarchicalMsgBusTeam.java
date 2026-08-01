/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Public class HierarchicalMsgBusTeam used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class HierarchicalMsgBusTeam extends BaseTeam {
    /**
     * HierarchicalMsgBusTeam.
     * 
     * @param card card
     * @param config config
     * @since 0.1.7
     */
    public HierarchicalMsgBusTeam(TeamCard card, HierarchicalMsgBusTeamConfig config) {
        super(card, config);
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
        HierarchicalMsgBusTeamConfig config = getTeamConfig() instanceof HierarchicalMsgBusTeamConfig msgBusConfig
                ? msgBusConfig
                : HierarchicalMsgBusTeamConfig.class.cast(getTeamConfig());
        if (config.getSupervisorAgent() == null || config.getSupervisorAgent().getId() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg",
                    "supervisor_agent is required");
        }
        injectDelegateTools(config);
        return send(message, config.getSupervisorAgent().getId(), getTeamCard().getId(),
                session != null ? session.getSessionId() : null, session);
    }

    /**
     * stream.
     * 
     * @param message message
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        return List.<Object>of(invoke(message, session)).iterator();
    }

    /**
     * Inject {@code delegate_to_{target}} tools into each registered supervisor
     * agent's {@code AbilityManager} based on the configured hierarchy.
     * <p>
     * Mirrors {@code HandoffTeam.injectHandoffTools}: for each supervisor
     * agent in the hierarchy, a {@link DelegateTool} is created for every
     * child agent, added to the supervisor's ability manager, and registered
     * with {@code Runner.resourceMgr()} under the supervisor's ID tag.
     * </p>
     * <p>
     * Idempotent: agents that have already been processed are skipped.
     * </p>
     * 
     * @param config config
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    private void injectDelegateTools(HierarchicalMsgBusTeamConfig config) {
        Map<String, List<String>> hierarchy = config.getHierarchy();
        if (hierarchy == null || hierarchy.isEmpty()) {
            return;
        }
        String teamId = getTeamCard() != null ? getTeamCard().getId() : null;
        for (Map.Entry<String, List<String>> entry : hierarchy.entrySet()) {
            String supervisorId = entry.getKey();
            List<String> children = entry.getValue();
            if (children == null || children.isEmpty()) {
                continue;
            }
            BaseAgent supervisor;
            try {
                supervisor = getRuntime().getAgentInstance(supervisorId);
            } catch (RuntimeException ex) {
                Loggers.MULTI_AGENT.warning("[HierarchicalMsgBusTeam:" + teamId + "] skip tool injection for '"
                        + supervisorId + "': " + ex.getMessage());
                continue;
            }
            if (supervisor == null) {
                continue;
            }
            for (String childId : children) {
                String toolName = childId;
                AgentCard childCard = getRuntime().getAgentCard(childId);
                String childDescription = childCard != null ? childCard.getDescription() : "";
                DelegateTool tool = new DelegateTool(childId, childDescription, getRuntime(), supervisorId, teamId);
                supervisor.getAbilityManager().add(tool.getCard());
                // Always register with refresh=true so stale tool instances from
                // prior test runs are replaced. Without refresh, addTool silently
                // skips when the toolId already exists, leaving the old instance.
                Runner.resourceMgr().addTool(tool, supervisorId, true);
                Loggers.MULTI_AGENT.info("[HierarchicalMsgBusTeam:" + teamId + "] injected '" + toolName + "' -> '"
                        + supervisorId + "'");
            }
        }
    }
}
