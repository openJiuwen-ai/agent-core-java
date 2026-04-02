// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.agent_evolving.optimizer.tool_call.utils;

import com.openjiuwen.core.common.logging.Loggers;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Beam search algorithm implementation.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.beam_search.BeamSearch}.
 */
public class BeamSearch {

    private final Object method;
    private final int beamWidth;
    private final int expandNum;
    private final int maxDepth;
    private final int numWorkers;
    private final boolean verbose;
    private final boolean earlyStop;
    private final boolean checkValid;
    private final double maxScore;
    private final int topK;
    private final long timeoutMs;

    /**
     * Create beam search instance.
     *
     * @param method     Search method
     * @param beamWidth  Beam width
     * @param expandNum  Number of expansions per node
     * @param maxDepth   Maximum search depth
     * @param numWorkers Number of parallel workers
     * @param verbose    Enable verbose logging
     * @param earlyStop  Enable early stopping
     * @param checkValid Check validity of nodes
     * @param maxScore   Maximum score threshold
     * @param topK       Number of top results to return
     */
    public BeamSearch(Object method, int beamWidth, int expandNum, int maxDepth,
                      int numWorkers, boolean verbose, boolean earlyStop,
                      boolean checkValid, double maxScore, int topK) {
        this.method = method;
        this.beamWidth = beamWidth;
        this.expandNum = expandNum;
        this.maxDepth = maxDepth;
        this.numWorkers = numWorkers;
        this.verbose = verbose;
        this.earlyStop = earlyStop;
        this.checkValid = checkValid;
        this.maxScore = maxScore;
        this.topK = topK;
        this.timeoutMs = 600000; // 10 minutes
    }

    /**
     * Execute beam search.
     *
     * @param tool Tool to optimize
     * @return List of result histories
     */
    public List<List<Object>> search(Map<String, Object> tool) {
        long startTime = System.currentTimeMillis();
        List<Object> examples = getExamples(tool);

        // Initial root node generation/evaluation
        TreeNode root = null;
        for (int retry = 0; retry < 1; retry++) {
            StepResult result = step(tool, examples, null, 0);

            if (checkValid && result.score < 0) {
                continue;
            }

            root = new TreeNode(result.data, result.score, result.results);
            break;
        }

        if (root == null) {
            throw new RuntimeException("Failed to generate a valid root node after retries.");
        }

        List<TreeNode> beamList = new ArrayList<>();
        beamList.add(root);
        List<TreeNode> bestNodes = new ArrayList<>();
        bestNodes.add(root);

        // Expand and prune
        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return getTopKHistories(bestNodes);
            }

            if (earlyStop && checkEarlyStop(beamList)) {
                break;
            }

            beamList = expand(beamList, tool, examples, depth);
            beamList = prune(beamList);
            bestNodes.addAll(beamList);
        }

        return getTopKHistories(bestNodes);
    }

    private List<Object> getExamples(Map<String, Object> tool) {
        // Try to call method.getExamples(tool) if available
        try {
            java.lang.reflect.Method m = method.getClass().getMethod("getExamples", Map.class);
            Object result = m.invoke(method, tool);
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) result;
                return list;
            }
        } catch (Exception e) {
            // Method not available
        }
        return null;
    }

    private StepResult step(Map<String, Object> tool, List<Object> examples,
                           List<Object> prevOutputs, int depth) {
        // Simplified - actual implementation would call method.step()
        return new StepResult(new HashMap<>(), 0.0, new HashMap<>());
    }

    private List<TreeNode> expand(List<TreeNode> beamList, Map<String, Object> tool,
                                  List<Object> examples, int depth) {
        List<TreeNode> newBeamList = new ArrayList<>();

        for (TreeNode node : beamList) {
            for (int i = 0; i < expandNum; i++) {
                StepResult result = step(tool, examples, node.getHistory(), depth);

                if (checkValid && result.score < 0) {
                    continue;
                }

                TreeNode newNode = new TreeNode(
                        result.data,
                        result.score,
                        result.results,
                        node.getHistory()
                );
                newNode.setParent(node);
                node.addChild(newNode);
                newBeamList.add(newNode);
            }
        }

        return newBeamList;
    }

    private List<TreeNode> prune(List<TreeNode> beamList) {
        return beamList.stream()
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(beamWidth)
                .collect(Collectors.toList());
    }

    private boolean checkEarlyStop(List<TreeNode> beamList) {
        if (beamList.size() < topK) {
            return false;
        }
        for (int i = 0; i < topK && i < beamList.size(); i++) {
            if (beamList.get(i).getScore() < maxScore) {
                return false;
            }
        }
        return true;
    }

    private List<List<Object>> getTopKHistories(List<TreeNode> nodes) {
        return nodes.stream()
                .filter(n -> n.getDepth() > 0)
                .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
                .limit(topK)
                .map(TreeNode::getHistory)
                .collect(Collectors.toList());
    }

    /**
     * Step result container.
     */
    public static class StepResult {
        public final Object data;
        public final double score;
        public final Object results;

        public StepResult(Object data, double score, Object results) {
            this.data = data;
            this.score = score;
            this.results = results;
        }
    }
}