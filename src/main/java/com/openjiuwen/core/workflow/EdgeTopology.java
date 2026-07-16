/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edge topology snapshot used by workflow ability inference.
 * <p>
 * Mirrors Python's {@code EdgeTopology} dataclass.
 * </p>
 * 
 * @since 0.1.7
 */
public class EdgeTopology {
    private final Map<String, List<String>> sourceMap;
    private final Map<String, List<String>> targetMap;
    private final Map<String, List<String>> sourceStreamMap;
    private final Map<String, List<String>> targetStreamMap;

    /**
     * EdgeTopology.
     * 
     * @param sourceMap sourceMap
     * @param targetMap targetMap
     * @param sourceStreamMap sourceStreamMap
     * @param targetStreamMap targetStreamMap
     * @since 0.1.7
     */
    public EdgeTopology(Map<String, List<String>> sourceMap, Map<String, List<String>> targetMap,
            Map<String, List<String>> sourceStreamMap, Map<String, List<String>> targetStreamMap) {
        this.sourceMap = sourceMap;
        this.targetMap = targetMap;
        this.sourceStreamMap = sourceStreamMap;
        this.targetStreamMap = targetStreamMap;
    }

    /**
     * getSourceMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getSourceMap() {
        return sourceMap;
    }

    /**
     * getTargetMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getTargetMap() {
        return targetMap;
    }

    /**
     * getSourceStreamMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getSourceStreamMap() {
        return sourceStreamMap;
    }

    /**
     * getTargetStreamMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, List<String>> getTargetStreamMap() {
        return targetStreamMap;
    }

    /**
     * allEdgeNodes.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> allEdgeNodes() {
        Set<String> nodes = new LinkedHashSet<>();
        nodes.addAll(sourceMap.keySet());
        nodes.addAll(targetMap.keySet());
        nodes.addAll(sourceStreamMap.keySet());
        nodes.addAll(targetStreamMap.keySet());
        return nodes;
    }
}
