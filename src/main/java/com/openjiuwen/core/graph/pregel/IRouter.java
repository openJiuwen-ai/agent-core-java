/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.List;

/**
 * Router interface for dispatching messages after a node executes.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.IRouter}.
 */
public interface IRouter {

    /**
     * Dispatch messages from the given source node.
     *
     * @param sourceNode the name of the node that just completed
     * @return list of messages to send
     */
    List<Message> dispatch(String sourceNode);
}
