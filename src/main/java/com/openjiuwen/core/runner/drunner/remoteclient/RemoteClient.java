/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.reactive.ReactiveAdapters;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.Map;

/**
 * Remote-client abstraction.
 */
public interface RemoteClient {

    void start();

    void stop();

    boolean isStarted();

    default boolean isStopped() {
        return !isStarted();
    }

    Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;

    Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;

    /**
     * Reactive version of {@link #invoke(Map, Double)}.
     *
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return Mono emitting the remote invocation result
     */
    default Mono<Object> invokeAsync(Map<String, Object> inputs, Double timeoutSeconds) {
        return ReactiveAdapters.fromCallable(() -> invoke(inputs, timeoutSeconds));
    }

    /**
     * Reactive version of {@link #stream(Map, Double)}.
     *
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return Flux emitting remote stream chunks
     */
    default Flux<Object> streamAsync(Map<String, Object> inputs, Double timeoutSeconds) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(inputs, timeoutSeconds));
    }
}
