/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

/**
 * Minimal session contract required by atomic graph nodes.
 *
 * <p>Mirrors Python's {@code BaseSession.state()} usage in
 * {@code openjiuwen/core/graph/atomic_node.py}.</p>
 */
public interface GraphSession {

    /**
     * Return the current session state object.
     *
     * @return state object, expected to be a workflow commit state
     */
    Object state();
}
