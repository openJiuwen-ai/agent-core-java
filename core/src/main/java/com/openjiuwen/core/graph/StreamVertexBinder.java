/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import java.util.Map;

/**
 * Session surface that lets a run publish its own vertices to the stream layer.
 *
 * <p>Vertices carry mutable per-run state, so a run works on copies that belong
 * to it alone. Stream actors, however, are created from the graph topology
 * before the run starts. A session implementing this interface receives the
 * run's vertex set right before execution, so stream consumers resolve the same
 * vertex the graph is executing.
 */
public interface StreamVertexBinder {

    /**
     * Points the run's stream actors at the vertices owned by this run.
     *
     * @param runNodes node id to the vertex executing in this run
     */
    void bindStreamVertices(Map<String, Vertex> runNodes);
}
