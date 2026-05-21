/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.*;

/**
 * OutputConverter for DL transformer.
 * <p>
 * Mirrors Python's {@code OutputConverter} in
 * {@code openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer.converters}.
 */
public class OutputConverter {

    private final Map<String, Object> nodeData;
    private DlTransformModels.DlNode node;

    public OutputConverter(Map<String, Object> nodeData, Map<String, Object> context) {
        this.nodeData = nodeData;
    }

    public void convert() {
        String id = (String) nodeData.getOrDefault("id", "unknown");
        this.node = new DlTransformModels.DlNode(id, (String) nodeData.getOrDefault("type", ""));
    }

    public DlTransformModels.DlNode getNode() { return node; }
    public Map<String, Object> getNodeData() { return nodeData; }
}

