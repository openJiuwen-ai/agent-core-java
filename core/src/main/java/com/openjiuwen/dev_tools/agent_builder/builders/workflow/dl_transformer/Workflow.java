/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Workflow model.
 * <p>
 * Mirrors Python's {@code Workflow} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/models.py}.
 */
public class Workflow {
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}
