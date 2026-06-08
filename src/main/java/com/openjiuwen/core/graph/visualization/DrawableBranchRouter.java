/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code DrawableBranchRouter} in
 * {@code openjiuwen/core/graph/visualization/drawable_edge.py}.
 */
public class DrawableBranchRouter {

    private List<String> targets = new ArrayList<>();
    private List<String> datas = new ArrayList<>();

    public DrawableBranchRouter() {
    }

    public DrawableBranchRouter(List<String> targets, List<String> datas) {
        setTargets(targets);
        setDatas(datas);
    }

    public List<String> getTargets() {
        return targets;
    }

    public void setTargets(List<String> targets) {
        this.targets = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
    }

    public List<String> getDatas() {
        return datas;
    }

    public void setDatas(List<String> datas) {
        this.datas = datas == null ? new ArrayList<>() : new ArrayList<>(datas);
    }
}
