/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

/**
 * Opaque handle returned by {@link EventBus#subscribe(Class, java.util.function.Consumer)}
 * that can be used to unsubscribe.
 *
 * @since 0.1.16
 */
public interface Subscription {
    /**
     * Cancel this subscription. After cancellation the subscriber will no longer
     * receive events. Calling this method more than once is a no-op.
     */
    void cancel();
}
