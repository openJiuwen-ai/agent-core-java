/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the Pregel execution graph.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.PregelNode}.
 * Each node has a callable function and a list of routers that determine
 * where messages are sent after execution.
 * 
 * @since 0.1.7
 */
public class PregelNode {
    private final String name;
    private final Object func;
    private final List<IRouter> routers;

    /**
     * PregelNode.
     * 
     * @param name name
     * @param func func
     * @param routers routers
     * @since 0.1.7
     */
    public PregelNode(String name, Object func, List<IRouter> routers) {
        this.name = name;
        this.func = func;
        this.routers = routers != null ? new ArrayList<>(routers) : new ArrayList<>();
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * getFunc.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getFunc() {
        return func;
    }

    /**
     * getRouters.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<IRouter> getRouters() {
        return routers;
    }
}
