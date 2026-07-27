/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;

/**
 * Legacy hierarchical msg-bus team; prefer the snake_case package version.
 *
 * @since 1.0
 * @deprecated Use {@code com.openjiuwen.agent_teams} package instead.
 */
@Deprecated
public class HierarchicalMsgBusTeam extends BaseTeam {
  /** Auto-generated for codecheck compliance. */
  public HierarchicalMsgBusTeam(TeamCard card, HierarchicalMsgBusTeamConfig config) {
     super(card);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public java.util.concurrent.CompletionStage<Object> invoke(Object message, com.openjiuwen.core.session.Session session) {
    return java.util.concurrent.CompletableFuture.completedFuture(null);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public java.util.stream.Stream<Object> stream(Object message, com.openjiuwen.core.session.Session session) {
    return java.util.stream.Stream.empty();
  }
}
