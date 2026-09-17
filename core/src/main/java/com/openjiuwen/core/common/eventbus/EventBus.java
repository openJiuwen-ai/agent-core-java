/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lightweight in-process event bus for publishing and subscribing to typed events.
 * <p>
 * Subscribers register a {@link Consumer} for a specific event type (class).
 * When {@link #publish(Object)} is called, all subscribers registered for the
 * event's runtime type are invoked synchronously. If a subscriber throws, the
 * exception is logged and the remaining subscribers still run.
 * <p>
 * Thread-safe: subscribers can be registered and unregistered concurrently.
 *
 * @param <E> the base event type
 * @since 0.1.16
 */
public interface EventBus {

    /**
     * Subscribe to events of the given type.
     *
     * @param eventType the event class to subscribe to
     * @param subscriber the callback invoked when an event of matching type is published
     * @param <T> the event type
     * @return a subscription handle that can be used to unsubscribe
     */
    <T> Subscription subscribe(Class<T> eventType, Consumer<T> subscriber);

    /**
     * Unsubscribe using a previously obtained subscription handle.
     *
     * @param subscription the subscription to remove
     */
    void unsubscribe(Subscription subscription);

    /**
     * Publish an event to all subscribers of the event's runtime type.
     *
     * @param event the event to publish
     */
    void publish(Object event);

    /**
     * Get all subscribers for a given event type (for testing/inspection).
     *
     * @param eventType the event class
     * @return list of subscribers (may be empty)
     */
    List<Consumer<?>> getSubscribers(Class<?> eventType);
}
