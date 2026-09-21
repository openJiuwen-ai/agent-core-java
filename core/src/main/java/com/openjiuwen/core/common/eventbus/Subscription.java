/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

/**
 * Handle returned by {@link EventBus#subscribe(Class, java.util.function.Consumer)}.
 *
 * @since 0.1.15
 */
public interface Subscription {
    /**
     * Cancels this subscription. Additional calls are ignored.
     */
    void cancel();
}
