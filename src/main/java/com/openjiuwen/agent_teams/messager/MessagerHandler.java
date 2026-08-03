/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.concurrent.CompletionStage;

/**
 * Minimal handler abstraction for messager delivery callbacks.
 * <p>
 * Mirrors Python's {@code MessagerHandler} callable alias in
 * {@code openjiuwen/agent_teams/messager/messager.py}.
 */
@FunctionalInterface
public interface MessagerHandler {

    CompletionStage<Void> handle(EventMessage message);
}
