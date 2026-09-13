/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.base;

import java.util.function.Supplier;

/**
 * Provider functional interface for creating AgentGroup instances.
 * Mirrors Python's {@code AgentGroupProvider = Callable[..., BaseGroup]}.
 * 
 * @since 0.1.7
 */
@FunctionalInterface
public interface AgentGroupProvider<T> extends Supplier<T> {
}
