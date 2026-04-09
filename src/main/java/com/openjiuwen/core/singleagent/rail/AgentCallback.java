/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.function.Consumer;

/**
 * Functional interface for agent callback.
 */
@FunctionalInterface
public interface AgentCallback extends Consumer<AgentCallbackContext> {
}
