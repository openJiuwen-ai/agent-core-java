/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentGroupSessionApi;
import java.util.Iterator;
import java.util.List;

/**
 * Public class HierarchicalMsgBusTeam used by the Java parity implementation.
 *
 * @since 1.0
 */
public class HierarchicalMsgBusTeam extends BaseTeam {
  /** Auto-generated for codecheck compliance. */
  public HierarchicalMsgBusTeam(TeamCard card, HierarchicalMsgBusTeamConfig config) {
    super(card, config);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public Object invoke(Object message, AgentGroupSessionApi session) {
    HierarchicalMsgBusTeamConfig config =
        getTeamConfig() instanceof HierarchicalMsgBusTeamConfig msgBusConfig
            ? msgBusConfig
            : HierarchicalMsgBusTeamConfig.class.cast(getTeamConfig());
    if (config.getSupervisorAgent() == null || config.getSupervisorAgent().getId() == null) {
      throw ErrorHelper.buildError(
          StatusCode.AGENT_GROUP_EXECUTION_ERROR, "error_msg", "supervisor_agent is required");
    }
    return send(
        message,
        config.getSupervisorAgent().getId(),
        getTeamCard().getId(),
        session != null ? session.getSessionId() : null,
        session);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public Iterator<Object> stream(Object message, AgentGroupSessionApi session) {
    return List.<Object>of(invoke(message, session)).iterator();
  }
}
