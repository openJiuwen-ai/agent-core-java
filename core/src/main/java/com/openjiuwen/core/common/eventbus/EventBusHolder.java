/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

/**
 * Global singleton holder for the application-wide {@link EventBus}.
 * <p>
 * Components that need to publish or subscribe to events can use
 * {@link #getInstance()} to obtain the shared bus instance.
 * <p>
 * The default implementation is {@link DefaultEventBus}. Call
 * {@link #setInstance(EventBus)} during bootstrap to replace it with a custom
 * implementation (e.g., for testing).
 *
 * @since 0.1.16
 */
public final class EventBusHolder {

    private static volatile EventBus instance = new DefaultEventBus();

    private EventBusHolder() {
    }

    /**
     * Get the global EventBus instance.
     *
     * @return the shared EventBus
     */
    public static EventBus getInstance() {
        return instance;
    }

    /**
     * Replace the global EventBus instance. Intended for testing or bootstrap.
     *
     * @param bus the new EventBus to use
     */
    public static void setInstance(EventBus bus) {
        if (bus != null) {
            instance = bus;
        }
    }
}
