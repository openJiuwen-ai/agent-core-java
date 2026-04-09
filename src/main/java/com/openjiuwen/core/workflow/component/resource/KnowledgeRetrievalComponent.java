/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.Graph;
import com.openjiuwen.core.workflow.ComponentComposable;

/**
 * Knowledge Retrieval workflow component (composable wrapper).
 * <p>
 * Creates a {@link KnowledgeRetrievalExecutable} for graph execution.
 * <p>
 * Mirrors Python's {@code KnowledgeRetrievalComponent}.
 */
public class KnowledgeRetrievalComponent implements ComponentComposable {

    private final KnowledgeRetrievalCompConfig config;

    public KnowledgeRetrievalComponent(KnowledgeRetrievalCompConfig config) {
        this.config = config;
    }

    @Override
    public void addComponent(Graph graph, String nodeId, boolean waitForAll) {
        graph.addNode(nodeId, toExecutable(), waitForAll);
    }

    @Override
    public Executable<?, ?> toExecutable() {
        return new KnowledgeRetrievalExecutable(config);
    }
}
