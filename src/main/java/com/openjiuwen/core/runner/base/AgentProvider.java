/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.base;

import java.util.function.Supplier;

/**
 * Provider functional interface for creating Agent instances.
 * Mirrors Python's {@code AgentProvider = Callable[..., BaseAgent]}.
 */
@FunctionalInterface
public interface AgentProvider<T> extends Supplier<T> {
}
