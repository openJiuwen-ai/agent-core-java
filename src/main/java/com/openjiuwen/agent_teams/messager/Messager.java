/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.messager;

import com.openjiuwen.agent_teams.schema.events.EventMessage;

/**
 * Minimal messager transport abstraction.
 *
 * <p>Mirrors Python's {@code Messager} in
 * {@code openjiuwen.agent_teams.messager.messager}.</p>
 */
public interface Messager {

    void start();

    void stop();

    void publish(String topicId, EventMessage message);

    void subscribe(String topicId, MessagerHandler handler);

    void unsubscribe(String topicId);

    void send(String agentId, EventMessage message);

    void registerDirectMessageHandler(MessagerHandler handler);

    void unregisterDirectMessageHandler();
}
