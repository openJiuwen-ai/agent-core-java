/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.loop;

import com.openjiuwen.core.graph.visualization.Drawable;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.HasDrawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Workflow-like group used as a loop body and drawable subgraph owner.
 *
 * <p>Mirrors Python's {@code LoopGroup} in
 * {@code openjiuwen/core/workflow/components/flow/loop/loop_comp.py}.</p>
 */
public class LoopGroup implements HasDrawable {

    private final Drawable drawable = new Drawable();
    private final List<LoopBreakComponent> breakComponents = new ArrayList<>();
    private final List<String> startNodes = new ArrayList<>();
    private final List<String> endNodes = new ArrayList<>();
    private final List<String> nodeIds = new ArrayList<>();

    @Override
    public Drawable getDrawable() {
        return drawable;
    }

    public LoopGroup addWorkflowComp(String componentId, ComponentComposable workflowComponent) {
        if (workflowComponent instanceof LoopComponentImpl) {
            throw new IllegalArgumentException("cannot add 'LoopComponent' to a loop group.");
        }
        if (workflowComponent instanceof LoopBreakComponent breakComponent) {
            breakComponents.add(breakComponent);
            if (drawable.getGraph().getNodes().containsKey(componentId)) {
                drawable.setBreakNode(componentId);
            }
        }
        nodeIds.add(componentId);
        return this;
    }

    public LoopGroup startNodes(List<String> nodes) {
        startNodes.clear();
        startNodes.addAll(nodes);
        return this;
    }

    public LoopGroup startComp(String componentId) {
        if (!startNodes.contains(componentId)) {
            startNodes.add(componentId);
        }
        return this;
    }

    public LoopGroup endNodes(List<String> nodes) {
        endNodes.clear();
        endNodes.addAll(nodes);
        return this;
    }

    public LoopGroup endComp(String componentId) {
        if (!endNodes.contains(componentId)) {
            endNodes.add(componentId);
        }
        return this;
    }

    public void checkValidate() {
        if (startNodes.isEmpty() && drawable.getGraph().getStartNodes().isEmpty()) {
            throw new IllegalStateException("missing start_nodes in loop group");
        }
        if (endNodes.isEmpty() && drawable.getGraph().getEndNodes().isEmpty()) {
            throw new IllegalStateException("missing end_nodes in loop group");
        }
        if (drawable.getGraph().getNodes().isEmpty() && nodeIds.isEmpty()) {
            throw new IllegalStateException("loop group is empty (contains no nodes)");
        }
    }

    public List<LoopBreakComponent> getBreakComponents() {
        return Collections.unmodifiableList(breakComponents);
    }

    public List<String> getNodeIds() {
        return Collections.unmodifiableList(nodeIds);
    }
}
