/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.Map;
import java.util.function.Function;

/**
 * Mirrors Python's {@code WrapHandler} in
 * {@code openjiuwen/core/runner/callback/decorator.py}.
 */
@FunctionalInterface
public interface WrapHandler {

    Object execute(Function<Map<String, Object>, Object> callNext, Map<String, Object> kwargs);
}
