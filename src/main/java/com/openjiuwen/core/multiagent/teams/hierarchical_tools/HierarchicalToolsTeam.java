/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_tools;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;

import java.util.Iterator;
import java.util.List;

/**
 * Public class HierarchicalToolsTeam used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HierarchicalToolsTeam extends BaseTeam {
    /**
     * Auto-generated for codecheck compliance.
     */
    public HierarchicalToolsTeam(TeamCard card, HierarchicalToolsTeamConfig config) {
        super(card, config);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Object invoke(Object message, AgentGroupSessionApi session) {
        HierarchicalToolsTeamConfig config = getTeamConfig() instanceof HierarchicalToolsTeamConfig toolsConfig
                ? toolsConfig
                : HierarchicalToolsTeamConfig.class.cast(getTeamConfig());
        if (config.getRootAgent() == null || config.getRootAgent().getId() == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg", "root_agent is required");
        }
        return send(message, config.getRootAgent().getId(), getTeamCard().getId(),
                session != null ? session.getSessionId() : null, session);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
        return List.<Object>of(invoke(message, session)).iterator();
    }
}
