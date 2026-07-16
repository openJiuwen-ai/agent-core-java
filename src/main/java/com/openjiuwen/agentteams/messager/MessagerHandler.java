/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.agentteams.schema.events.EventMessage;

import java.util.concurrent.CompletableFuture;

/**
 * Callback for handling a received team event message.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface MessagerHandler {
    /**
     * handle.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    CompletableFuture<Void> handle(EventMessage message);
}
