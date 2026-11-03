/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

/**
 * Mirrors Python's {@code Router} type alias in
 * {@code openjiuwen/core/graph/base.py}.
 *
 * <p>The Python alias accepts sync or async callables that return a single
 * hashable route or a list of hashable routes. Java callers may return either a
 * scalar, a collection, or a future-like object from this functional interface.</p>
 */
@FunctionalInterface
public interface Router {

    /**
     * Route using Python-style variadic arguments.
     *
     * @param args router arguments
     * @return route id, route id collection, or asynchronous route result
     */
    Object route(Object... args);
}
