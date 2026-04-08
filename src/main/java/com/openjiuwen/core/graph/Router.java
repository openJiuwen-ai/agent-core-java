/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import java.util.function.Function;

/**
 * Functional interface for a graph router that determines conditional edge targets.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.base.Router} type alias.
 * <p>
 * A router receives some context and returns either a single target node ID (Hashable)
 * or a list of target node IDs.
 */
@FunctionalInterface
public interface Router extends Function<Object, Object> {
    // Inherits apply(Object) -> Object from Function
}
