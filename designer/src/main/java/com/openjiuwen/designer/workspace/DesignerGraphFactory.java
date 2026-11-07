/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.workspace;

import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerSchema;

import java.util.Map;
import java.util.UUID;

/**
 * Creates empty designer graph documents.
 *
 * @since 2026-11-06
 */
public final class DesignerGraphFactory {

    /**
     * Create an empty graph.
     *
     * @param projectId owning project
     * @param title display title
     * @param description graph description
     * @param metadata initial metadata
     * @return empty graph
     */
    public DesignerGraph empty(String projectId, String title, String description,
            Map<String, Object> metadata) {
        long now = System.currentTimeMillis();
        return new DesignerGraph(DesignerSchema.GRAPH_SCHEMA_VERSION, newGraphId(), projectId,
                title, description, "manual", java.util.List.of(), java.util.List.of(),
                metadata, now, now);
    }

    /**
     * Mint a graph id.
     *
     * @return graph id
     */
    public String newGraphId() {
        return "graph_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
