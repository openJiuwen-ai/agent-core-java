/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

/**
 * Process-wide holder for the shared {@link EventBus}.
 *
 * @since 0.1.15
 */
public final class EventBusHolder {
    private static volatile EventBus instance = new DefaultEventBus();

    private EventBusHolder() {
    }

    /**
     * Returns the shared EventBus.
     *
     * @return the shared EventBus
     */
    public static EventBus getInstance() {
        return instance;
    }

    /**
     * Replaces the shared EventBus. Intended for bootstrap and tests.
     *
     * @param bus the EventBus to install; null is ignored
     */
    public static void setInstance(EventBus bus) {
        if (bus != null) {
            instance = bus;
        }
    }
}
