/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.workflow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edge topology snapshot used by workflow ability inference.
 *
 * <p>Mirrors Python's {@code EdgeTopology} dataclass.</p>
 */
public class EdgeTopology {

    private final Map<String, List<String>> sourceMap;
    private final Map<String, List<String>> targetMap;
    private final Map<String, List<String>> sourceStreamMap;
    private final Map<String, List<String>> targetStreamMap;

    public EdgeTopology(Map<String, List<String>> sourceMap,
                        Map<String, List<String>> targetMap,
                        Map<String, List<String>> sourceStreamMap,
                        Map<String, List<String>> targetStreamMap) {
        this.sourceMap = sourceMap;
        this.targetMap = targetMap;
        this.sourceStreamMap = sourceStreamMap;
        this.targetStreamMap = targetStreamMap;
    }

    public Map<String, List<String>> getSourceMap() {
        return sourceMap;
    }

    public Map<String, List<String>> getTargetMap() {
        return targetMap;
    }

    public Map<String, List<String>> getSourceStreamMap() {
        return sourceStreamMap;
    }

    public Map<String, List<String>> getTargetStreamMap() {
        return targetStreamMap;
    }

    public Set<String> allEdgeNodes() {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.addAll(sourceMap.keySet());
        nodes.addAll(targetMap.keySet());
        nodes.addAll(sourceStreamMap.keySet());
        nodes.addAll(targetStreamMap.keySet());
        return nodes;
    }
}
