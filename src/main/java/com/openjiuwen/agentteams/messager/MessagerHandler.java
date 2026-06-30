/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import java.util.concurrent.CompletableFuture;

/**
 * Callback for handling a received team event message.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface MessagerHandler {
    CompletableFuture<Void> handle(EventMessage message);
}
