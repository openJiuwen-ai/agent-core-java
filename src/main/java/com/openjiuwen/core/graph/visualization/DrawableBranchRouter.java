/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.List;

/**
 * Represents a branch router's drawable information for visualization.
 * <p>
 * Contains target node names and their associated display labels.
 * Mirrors Python's {@code openjiuwen.core.graph.visualization.drawable_edge.DrawableBranchRouter}.
 * </p>
 * 
 * @since 0.1.7
 */
public class DrawableBranchRouter {
    private final List<String> targets;
    private final List<String> datas;

    /**
     * DrawableBranchRouter.
     * 
     * @param targets targets
     * @param datas datas
     * @since 0.1.7
     */
    public DrawableBranchRouter(List<String> targets, List<String> datas) {
        this.targets = targets;
        this.datas = datas;
    }

    /**
     * getTargets.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getTargets() {
        return targets;
    }

    /**
     * getDatas.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getDatas() {
        return datas;
    }
}
