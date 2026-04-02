// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree node for beam search algorithm.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.beam_search.TreeNode}.
 */
public class TreeNode {

    private final Object data;
    private final double score;
    private final Object results;
    private final List<Object> history;
    private TreeNode parent;
    private final List<TreeNode> children;

    /**
     * Create tree node.
     *
     * @param data    Node data
     * @param score   Node score
     * @param results Node results
     * @param history Previous history
     */
    public TreeNode(Object data, double score, Object results, List<Object> history) {
        this.data = data;
        this.score = score;
        this.results = results;
        this.history = new ArrayList<>();
        if (history != null) {
            this.history.addAll(history);
        }
        this.history.add(results);
        this.parent = null;
        this.children = new ArrayList<>();
    }

    /**
     * Create tree node without history.
     *
     * @param data    Node data
     * @param score   Node score
     * @param results Node results
     */
    public TreeNode(Object data, double score, Object results) {
        this(data, score, results, null);
    }

    /**
     * Get depth of this node.
     *
     * @return Depth (0 for root)
     */
    public int getDepth() {
        if (parent == null) {
            return 0;
        }
        return parent.getDepth() + 1;
    }

    /**
     * Add child node.
     *
     * @param child Child node
     */
    public void addChild(TreeNode child) {
        child.parent = this;
        children.add(child);
    }

    // Getters
    public Object getData() {
        return data;
    }

    public double getScore() {
        return score;
    }

    public Object getResults() {
        return results;
    }

    public List<Object> getHistory() {
        return new ArrayList<>(history);
    }

    public TreeNode getParent() {
        return parent;
    }

    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    public List<TreeNode> getChildren() {
        return new ArrayList<>(children);
    }

    @Override
    public String toString() {
        int depth = getDepth();
        StringBuilder sb = new StringBuilder();
        sb.append("    ".repeat(depth))
                .append("it=").append(depth)
                .append(" score=").append(String.format("%.1f", score))
                .append(" data=\"").append(data).append("\"");
        for (TreeNode child : children) {
            sb.append("\n").append(child.toString());
        }
        return sb.toString();
    }
}