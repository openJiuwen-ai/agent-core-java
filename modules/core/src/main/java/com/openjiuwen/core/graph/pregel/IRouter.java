/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.List;

/**
 * Router interface for dispatching messages after a node executes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.IRouter}.
 * 
 * @since 0.1.7
 */
public interface IRouter {
    /**
     * dispatch.
     * 
     * @param sourceNode sourceNode
     * @return the result
     * @since 0.1.7
     */
    List<Message> dispatch(String sourceNode);
}
