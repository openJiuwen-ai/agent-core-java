/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Mirrors Python's {@code AsyncCallbackFramework} usage in
 * {@code openjiuwen/core/runner/callback/decorator.py}.
 */
public interface DecoratorFramework {

    CallbackInfo registerSync(String event,
                              Function<Map<String, Object>, Object> callback,
                              int priority,
                              boolean once,
                              String namespace,
                              Set<String> tags,
                              List<EventFilter> filters,
                              Function<Map<String, Object>, Object> rollbackHandler,
                              Function<Map<String, Object>, Object> errorHandler,
                              int maxRetries,
                              double retryDelay,
                              Double timeout,
                              String callbackType);

    void trigger(String event, Object[] args, Map<String, Object> kwargs);

    Object triggerTransform(String event, Object[] args, Map<String, Object> kwargs);

    Map<String, List<CallbackInfo>> getCallbacks();
}
