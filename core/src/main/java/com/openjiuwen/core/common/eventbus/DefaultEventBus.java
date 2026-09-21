/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.eventbus;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Default in-process {@link EventBus} backed by concurrent subscriber lists.
 *
 * @since 0.1.15
 */
public class DefaultEventBus implements EventBus {
    private final Map<Class<?>, List<SubscriberEntry<?>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public <T> Subscription subscribe(Class<T> eventType, Consumer<T> subscriber) {
        if (eventType == null || subscriber == null) {
            throw new IllegalArgumentException("eventType and subscriber must not be null");
        }
        SubscriberEntry<T> entry = new SubscriberEntry<>(subscriber);
        subscribers.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>()).add(entry);
        return () -> {
            List<SubscriberEntry<?>> list = subscribers.get(eventType);
            if (list != null) {
                list.remove(entry);
            }
        };
    }

    @Override
    public void unsubscribe(Subscription subscription) {
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public void publish(Object event) {
        if (event == null) {
            return;
        }
        Class<?> eventType = event.getClass();
        dispatchTo(eventType, event);
        for (Map.Entry<Class<?>, List<SubscriberEntry<?>>> entry : subscribers.entrySet()) {
            if (!entry.getKey().equals(eventType) && entry.getKey().isAssignableFrom(eventType)) {
                dispatchTo(entry.getKey(), event);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void dispatchTo(Class<?> type, Object event) {
        List<SubscriberEntry<?>> list = subscribers.get(type);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SubscriberEntry entry : list) {
            try {
                entry.subscriber().accept(event);
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException
                    | ClassCastException | CompletionException | UnsupportedOperationException exception) {
                Loggers.AGENT.warning("EventBus subscriber error for event type {}: {}",
                        type.getSimpleName(), exception.getMessage(), exception);
            }
        }
    }

    @Override
    public List<Consumer<?>> getSubscribers(Class<?> eventType) {
        List<SubscriberEntry<?>> entries = subscribers.get(eventType);
        if (entries == null) {
            return List.of();
        }
        List<Consumer<?>> result = new ArrayList<>();
        for (SubscriberEntry<?> entry : entries) {
            result.add(entry.subscriber());
        }
        return result;
    }

    private record SubscriberEntry<T>(Consumer<T> subscriber) {
    }
}
