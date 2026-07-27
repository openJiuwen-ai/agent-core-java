/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import java.util.Map;

/**
 * Functional interface for sandbox lifecycle event callbacks.
 *
 * @since 2026-01-01
 * @version 1.0
 */
@FunctionalInterface
public interface LifecycleHook {
    /**
     * Called when a sandbox lifecycle event occurs.
     *
     * @param eventName the lifecycle event name (e.g., "before_create", "after_create", "before_recreate", etc.)
     * @param context the context map providing event-specific information
     */
    void onEvent(String eventName, Map<String, Object> context);
}
