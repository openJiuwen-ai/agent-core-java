/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Mirrors Python's {@code PregelNode} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public class PregelNode {

    private final String name;
    private final Function<Object, Object> func;
    private final List<IRouter> routers;

    public PregelNode(String name, Function<Object, Object> func, List<IRouter> routers) {
        this.name = name;
        this.func = func;
        this.routers = routers == null ? new ArrayList<>() : new ArrayList<>(routers);
    }

    public String getName() {
        return name;
    }

    public Function<Object, Object> getFunc() {
        return func;
    }

    public List<IRouter> getRouters() {
        return routers;
    }
}
