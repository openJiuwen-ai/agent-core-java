/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.runner.callback.AbortError;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

final class InstanceCallbackFramework implements AgentCallbackManager.CallbackFramework {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceCallbackFramework.class);

    private static final Comparator<Entry> ENTRY_ORDER =
            Comparator.comparingInt(Entry::priority).reversed().thenComparingLong(Entry::sequence);

    private final Map<String, List<Entry>> callbacks = new ConcurrentHashMap<>();
    private final AtomicLong nextSequence = new AtomicLong();

    @Override
    public CompletionStage<Void> register(String event, AgentCallback callback, int priority) {
        if (event == null || callback == null) {
            return CompletableFuture.completedFuture(null);
        }
        // compute serializes remappings per event key, and every published
        // list is an immutable snapshot, so trigger can iterate lock-free.
        callbacks.compute(event, (key, existing) -> {
            List<Entry> updated = new ArrayList<>(existing == null ? List.of() : existing);
            updated.add(new Entry(callback, priority, nextSequence.getAndIncrement()));
            updated.sort(ENTRY_ORDER);
            return List.copyOf(updated);
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unregister(String event, AgentCallback callback) {
        if (event == null || callback == null) {
            return CompletableFuture.completedFuture(null);
        }
        callbacks.computeIfPresent(event, (key, entries) -> {
            List<Entry> updated = new ArrayList<>();
            for (Entry entry : entries) {
                if (entry.callback() != callback) {
                    updated.add(entry);
                }
            }
            return updated.isEmpty() ? null : List.copyOf(updated);
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> unregisterEvent(String event) {
        callbacks.remove(event);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<Object>> listCallbacks(String event) {
        List<Entry> entries = callbacks.get(event);
        if (entries == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.completedFuture(entries.stream()
                .map(entry -> (Object) entry.callback())
                .toList());
    }

    @Override
    public CompletionStage<Void> trigger(String event, AgentCallbackContext context) {
        List<Entry> entries = callbacks.get(event);
        if (entries == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (Entry entry : entries) {
            chain = chain.thenCompose(ignored -> invokeCallback(entry, context));
        }
        return chain;
    }

    private CompletionStage<Void> invokeCallback(Entry entry, AgentCallbackContext context) {
        CompletionStage<Void> stage;
        try {
            stage = entry.callback().handle(context);
        } catch (Throwable error) {
            return handleFailure(entry, error);
        }
        if (stage == null) {
            return CompletableFuture.completedFuture(null);
        }
        return stage.handle((ignored, error) -> {
            if (error == null) {
                return null;
            }
            Throwable normalized = unwrap(error);
            if (shouldPropagate(normalized)) {
                throwUnchecked(normalized);
            }
            logIgnoredFailure(entry, normalized);
            return null;
        });
    }

    private CompletionStage<Void> handleFailure(Entry entry, Throwable error) {
        Throwable normalized = unwrap(error);
        if (shouldPropagate(normalized)) {
            return CompletableFuture.failedFuture(normalized);
        }
        logIgnoredFailure(entry, normalized);
        return CompletableFuture.completedFuture(null);
    }

    private static boolean shouldPropagate(Throwable error) {
        return error instanceof AbortError || error instanceof ToolInterruptException || error instanceof Error;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void throwUnchecked(Throwable error) {
        if (error instanceof Error fatal) {
            throw fatal;
        }
        if (error instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new CompletionException(error);
    }

    private static void logIgnoredFailure(Entry entry, Throwable error) {
        LOGGER.warn("Callback {} failed and will be ignored.", entry.callback(), error);
    }

    private record Entry(AgentCallback callback, int priority, long sequence) {
    }
}
