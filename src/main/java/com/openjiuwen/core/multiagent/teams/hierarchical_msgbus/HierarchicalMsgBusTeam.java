/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchical_msgbus;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.AgentSessionApi;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Stream;

/**
 * Legacy hierarchical msg-bus team stub.
 *
 * @deprecated Prefer {@link HierarchicalTeam} or {@code com.openjiuwen.agentteams}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class HierarchicalMsgBusTeam extends BaseTeam {

    public HierarchicalMsgBusTeam(TeamCard card, HierarchicalMsgBusTeamConfig config) {
        super(card);
    }

    @Override
    public CompletionStage<Object> invoke(Object message, AgentSessionApi session) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public Stream<Object> stream(Object message, AgentSessionApi session) {
        return Stream.empty();
    }
}
