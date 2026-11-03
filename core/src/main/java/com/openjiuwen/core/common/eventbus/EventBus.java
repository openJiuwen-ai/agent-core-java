/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lightweight in-process event bus for publishing and subscribing to typed events.
 * <p>
 * Subscribers register a {@link Consumer} for a specific event type. When
 * {@link #publish(Object)} is called, matching subscribers are invoked synchronously.
 * Subscriber failures are logged and do not stop remaining subscribers.
 *
 * @since 0.1.15
 */
public interface EventBus {

    /**
     * Subscribe to events of the given type.
     *
     * @param eventType the event class to subscribe to
     * @param subscriber the callback invoked for matching events
     * @param <T> the event type
     * @return a handle that can cancel the subscription
     */
    <T> Subscription subscribe(Class<T> eventType, Consumer<T> subscriber);

    /**
     * Unsubscribe using a previously obtained subscription handle.
     *
     * @param subscription the subscription to remove
     */
    void unsubscribe(Subscription subscription);

    /**
     * Publish an event to subscribers of its runtime type (and compatible supertypes).
     *
     * @param event the event to publish
     */
    void publish(Object event);

    /**
     * Returns subscribers registered for the exact event type.
     *
     * @param eventType the event class
     * @return subscribers for the type, or an empty list
     */
    List<Consumer<?>> getSubscribers(Class<?> eventType);
}
