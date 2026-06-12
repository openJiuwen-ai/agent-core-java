/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import java.util.concurrent.CompletionStage;

/**
 * Handler callback invoked with a coordination event.
 *
 * <p>Mirrors Python's {@code EventCallback} alias in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/base.py}.</p>
 */
@FunctionalInterface
public interface EventCallback extends com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.EventCallback {

    @Override
    CompletionStage<Void> handle(CoordinationEvent event);
}
