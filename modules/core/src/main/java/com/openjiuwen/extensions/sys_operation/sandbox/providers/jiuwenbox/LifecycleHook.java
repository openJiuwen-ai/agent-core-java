/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import java.util.Map;

/**
 * Functional interface for sandbox lifecycle event callbacks.
 * 
 * @version 1.0
 * @since 0.1.7
 */
@FunctionalInterface
public interface LifecycleHook {
    /**
     * onEvent.
     * 
     * @param eventName eventName
     * @param context context
     * @since 0.1.7
     */
    void onEvent(String eventName, Map<String, Object> context);
}
