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
 * 
 * @since 0.1.7
 * @deprecated Use {@link com.openjiuwen.core.runner.drunner.remote_client.RemoteClient}.
 */
@Deprecated(since = "0.1.14")
public interface RemoteClient {
    /**
     * start.
     * 
     * @since 0.1.7
     */
    void start();

    /**
     * stop.
     * 
     * @since 0.1.7
     */
    void stop();

    /**
     * isStarted.
     * 
     * @return the result
     * @since 0.1.7
     */
    boolean isStarted();

    /**
     * isStopped.
     * 
     * @return the result
     * @since 0.1.7
     */
    default boolean isStopped() {
        return !isStarted();
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param timeoutSeconds timeoutSeconds
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Object invoke(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param timeoutSeconds timeoutSeconds
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) throws Exception;

    /**
     * Reactive version of {@link #invoke(Map, Double)}.
     * 
     * @param inputs request inputs
     * @param timeoutSeconds request timeout in seconds
     * @return Mono emitting the remote invocation result
     * @since 0.1.7
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
     * @since 0.1.7
     */
    default Flux<Object> streamAsync(Map<String, Object> inputs, Double timeoutSeconds) {
        return ReactiveAdapters.fromAutoCloseableIterator(() -> stream(inputs, timeoutSeconds));
    }
}
