/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.runner.base;

import com.openjiuwen.core.foundation.llm.Model;

import java.util.function.Supplier;

/**
 * Provider functional interface for creating Model instances.
 * Mirrors Python's {@code ModelProvider = Callable[..., Model]}.
 */
@FunctionalInterface
public interface ModelProvider extends Supplier<Model> {
}
