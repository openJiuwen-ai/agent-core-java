/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tree node for beam search algorithm.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.beam_search.TreeNode}.
 * 
 * @since 0.1.7
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
     * @param data Node data
     * @param score Node score
     * @param results Node results
     * @param history Previous history
     * @since 0.1.7
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
     * @param data Node data
     * @param score Node score
     * @param results Node results
     * @since 0.1.7
     */
    public TreeNode(Object data, double score, Object results) {
        this(data, score, results, null);
    }

    /**
     * Get depth of this node.
     * 
     * @return Depth (0 for root)
     * @since 0.1.7
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
     * @since 0.1.7
     */
    public void addChild(TreeNode child) {
        child.parent = this;
        children.add(child);
    }

    // Getters
    /**
     * getData.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getData() {
        return data;
    }

    /**
     * getScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getScore() {
        return score;
    }

    /**
     * getResults.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getResults() {
        return results;
    }

    /**
     * getHistory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Object> getHistory() {
        return new ArrayList<>(history);
    }

    /**
     * getParent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TreeNode getParent() {
        return parent;
    }

    /**
     * setParent.
     * 
     * @param parent parent
     * @since 0.1.7
     */
    public void setParent(TreeNode parent) {
        this.parent = parent;
    }

    /**
     * getChildren.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TreeNode> getChildren() {
        return new ArrayList<>(children);
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        int depth = getDepth();
        StringBuilder sb = new StringBuilder();
        sb.append("    ".repeat(depth)).append("it=").append(depth).append(" score=")
                .append(String.format(Locale.ROOT, "%.1f", score)).append(" data=\"").append(data).append("\"");
        for (TreeNode child : children) {
            sb.append("\n").append(child.toString());
        }
        return sb.toString();
    }
}
