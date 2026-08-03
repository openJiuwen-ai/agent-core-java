/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.concurrent.CompletionStage;

/**
 * Minimal messager transport abstraction.
 * <p>
 * Mirrors Python's {@code Messager} in
 * {@code openjiuwen/agent_teams/messager/messager.py}.
 */
public interface Messager {

    CompletionStage<Void> start();

    CompletionStage<Void> stop();

    CompletionStage<Void> publish(String topicId, EventMessage message);

    CompletionStage<Void> subscribe(String topicId, MessagerHandler handler);

    CompletionStage<Void> unsubscribe(String topicId);

    CompletionStage<Void> send(String agentId, EventMessage message);

    CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler);

    CompletionStage<Void> unregisterDirectMessageHandler();
}
