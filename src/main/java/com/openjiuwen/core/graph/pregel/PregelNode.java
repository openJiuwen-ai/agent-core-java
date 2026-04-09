/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Represents a node in the Pregel execution graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.PregelNode}.
 * Each node has a callable function and a list of routers that determine
 * where messages are sent after execution.
 */
public class PregelNode {

    private final String name;
    private final Object func;
    private final List<IRouter> routers;

    public PregelNode(String name, Object func, List<IRouter> routers) {
        this.name = name;
        this.func = func;
        this.routers = routers != null ? new ArrayList<>(routers) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public Object getFunc() {
        return func;
    }

    public List<IRouter> getRouters() {
        return routers;
    }
}
