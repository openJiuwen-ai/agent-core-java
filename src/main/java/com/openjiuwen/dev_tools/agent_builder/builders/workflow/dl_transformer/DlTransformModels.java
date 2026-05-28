/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.*;

/**
 * DL transformer models — data structures for workflow DSL transformation.
 * <p>
 * Mirrors Python's {@code models} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.models}.
 */
public class DlTransformModels {

    /** DL node representation. */
    public static class DlNode {
        private String id;
        private String type;
        private Map<String, Object> properties = new LinkedHashMap<>();

        public DlNode(String id, String type) {
            this.id = id;
            this.type = type;
        }

        public String getId() { return id; }
        public String getType() { return type; }
        public Map<String, Object> getProperties() { return properties; }
        public void setProperty(String key, Object value) { properties.put(key, value); }
    }

    /** DL edge representation. */
    public static class DlEdge {
        private final String source;
        private final String target;
        private final String label;

        public DlEdge(String source, String target, String label) {
            this.source = source;
            this.target = target;
            this.label = label;
        }

        public String getSource() { return source; }
        public String getTarget() { return target; }
        public String getLabel() { return label; }
    }

    /** DL graph representation. */
    public static class DlGraph {
        private final List<DlNode> nodes = new ArrayList<>();
        private final List<DlEdge> edges = new ArrayList<>();

        public List<DlNode> getNodes() { return nodes; }
        public List<DlEdge> getEdges() { return edges; }
        public void addNode(DlNode node) { nodes.add(node); }
        public void addEdge(DlEdge edge) { edges.add(edge); }
    }
}
