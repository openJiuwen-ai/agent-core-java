/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous callback contract for agent lifecycle hooks.
 *
 * <p>Mirrors Python's {@code AgentCallback} in
 * {@code openjiuwen/core/single_agent/rail/base.py}.</p>
 */
@FunctionalInterface
public interface AgentCallback {
    CompletionStage<Void> handle(AgentCallbackContext context);
}
