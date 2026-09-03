/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.optimizer.tool_call.utils;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Beam search algorithm implementation.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.tool_call.utils.beam_search.BeamSearch}.
 * 
 * @since 0.1.7
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
    private final int numRetry;
    private long timeoutMs;

    /**
     * Create beam search instance.
     * 
     * @param method Search method
     * @param beamWidth Beam width
     * @param expandNum Number of expansions per node
     * @param maxDepth Maximum search depth
     * @param numWorkers Number of parallel workers
     * @param verbose Enable verbose logging
     * @param earlyStop Enable early stopping
     * @param checkValid Check validity of nodes
     * @param maxScore Maximum score threshold
     * @param topK Number of top results to return
     * @since 0.1.7
     */
    public BeamSearch(Object method, int beamWidth, int expandNum, int maxDepth, int numWorkers, boolean verbose,
            boolean earlyStop, boolean checkValid, double maxScore, int topK) {
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
        this.numRetry = 1;
        this.timeoutMs = 600000L;
    }

    /**
     * Execute beam search.
     * 
     * @param tool Tool to optimize
     * @return List of result histories
     * @since 0.1.7
     */
    public List<List<Object>> search(Map<String, Object> tool) {
        long startTime = System.currentTimeMillis();
        List<Object> examples = getExamples(tool);

        TreeNode root = null;
        for (int retry = 0; retry < numRetry; retry++) {
            StepResult result = step(tool, examples, null, 0);
            if (checkValid && Double.compare(result.score, -1.0d) == 0) {
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

        for (int depth = 1; depth <= maxDepth; depth++) {
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                return bestNodes.stream().sorted(Comparator.comparingDouble(TreeNode::getScore).reversed()).limit(topK)
                        .map(node -> new ArrayList<>(node.getHistory())).collect(Collectors.toList());
            }

            if (earlyStop && checkEarlyStop(beamList, maxScore, topK)) {
                break;
            }

            beamList = expand(beamList, tool, examples, depth);
            beamList = prune(beamList);
            bestNodes.addAll(beamList);
        }

        return bestNodes.stream().filter(node -> node.getDepth() > 0)
                .sorted(Comparator.comparingDouble(TreeNode::getScore).reversed()).limit(topK)
                .map(node -> new ArrayList<>(node.getHistory())).collect(Collectors.toList());
    }

    /**
     * expand.
     * 
     * @param beamList beamList
     * @param tool tool
     * @param examples examples
     * @param depth depth
     * @return the result
     * @since 0.1.7
     */
    public List<TreeNode> expand(List<TreeNode> beamList, Map<String, Object> tool, List<Object> examples, int depth) {
        List<TreeNode> newBeamList = new ArrayList<>();
        if (numWorkers <= 1) {
            for (TreeNode node : beamList) {
                for (int i = 0; i < expandNum; i++) {
                    newBeamList.add(expandSingleStep(node, tool, examples, depth));
                }
            }
            return newBeamList;
        }

        ExecutorService executor = OpenJiuwenExecutors.newFixedThreadPool("agent-evolving-beam-search", numWorkers,
                false);
        try {
            List<Future<TreeNode>> futures = new ArrayList<>();
            for (TreeNode node : beamList) {
                for (int i = 0; i < expandNum; i++) {
                    futures.add(executor.submit(() -> expandSingleStep(node, tool, examples, depth)));
                }
            }
            for (Future<TreeNode> future : futures) {
                try {
                    newBeamList.add(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return newBeamList;
                } catch (ExecutionException ignored) {
                    if (verbose) {
                        // Python ignores failed futures in worker mode.
                    }
                }
            }
            return newBeamList;
        } finally {
            OpenJiuwenExecutors.shutdown(executor);
        }
    }

    /**
     * prune.
     * 
     * @param beamList beamList
     * @return the result
     * @since 0.1.7
     */
    public List<TreeNode> prune(List<TreeNode> beamList) {
        return beamList.stream().sorted(Comparator.comparingDouble(TreeNode::getScore).reversed()).limit(beamWidth)
                .collect(Collectors.toList());
    }

    /**
     * checkEarlyStop.
     * 
     * @param beamList beamList
     * @param maxScore maxScore
     * @param k k
     * @return the result
     * @since 0.1.7
     */
    public boolean checkEarlyStop(List<TreeNode> beamList, double maxScore, int k) {
        if (beamList.size() < k) {
            return false;
        }
        for (int i = 0; i < k && i < beamList.size(); i++) {
            if (beamList.get(i).getScore() < maxScore) {
                return false;
            }
        }
        return true;
    }

    /**
     * setTimeoutMs.
     * 
     * @param timeoutMs timeoutMs
     * @since 0.1.7
     */
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * expandSingleStep.
     * 
     * @param node node
     * @param tool tool
     * @param examples examples
     * @param depth depth
     * @return the result
     * @since 0.1.7
     */
    private TreeNode expandSingleStep(TreeNode node, Map<String, Object> tool, List<Object> examples, int depth) {
        for (int retry = 0; retry < numRetry; retry++) {
            StepResult result = step(tool, examples, node.getHistory(), depth);
            if (checkValid && Double.compare(result.score, -1.0d) == 0) {
                continue;
            }

            TreeNode newNode = new TreeNode(result.data, result.score, result.results, node.getHistory());
            newNode.setParent(node);
            node.addChild(newNode);
            return newNode;
        }
        throw new RuntimeException("Failed to expand node after retries.");
    }

    /**
     * getExamples.
     * 
     * @param tool tool
     * @return the result
     * @since 0.1.7
     */
    private List<Object> getExamples(Map<String, Object> tool) {
        try {
            java.lang.reflect.Method methodRef = method.getClass().getMethod("getExamples", Map.class);
            Object result = methodRef.invoke(method, tool);
            if (result instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        } catch (Exception ignored) {
            // Ignore.
        }
        return java.util.Collections.emptyList();
    }

    /**
     * step.
     * 
     * @param tool tool
     * @param examples examples
     * @param prevOutputs prevOutputs
     * @param depth depth
     * @return the result
     * @since 0.1.7
     */
    private StepResult step(Map<String, Object> tool, List<Object> examples, List<Object> prevOutputs, int depth) {
        try {
            Object rawResult;
            try {
                java.lang.reflect.Method stepMethod =
                    method.getClass().getMethod("step", Map.class, List.class, List.class, int.class);
                rawResult = stepMethod.invoke(method, tool, examples, prevOutputs, depth);
            } catch (NoSuchMethodException ignored) {
                java.lang.reflect.Method stepMethod =
                    method.getClass().getMethod("step", Map.class, List.class, int.class);
                rawResult = stepMethod.invoke(method, tool, prevOutputs, depth);
            }
            return toStepResult(rawResult);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause != null ? cause : e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute beam-search step.", e);
        }
    }

    /**
     * toStepResult.
     * 
     * @param rawResult rawResult
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    private static StepResult toStepResult(Object rawResult) throws Exception {
        if (rawResult instanceof StepResult stepResult) {
            return stepResult;
        }
        if (rawResult == null) {
            throw new IllegalArgumentException("Step method returned null.");
        }

        Class<?> resultClass = rawResult.getClass();
        Field dataField = resultClass.getField("data");
        Field scoreField = resultClass.getField("score");
        Field resultsField = resultClass.getField("results");
        Object scoreValue = scoreField.get(rawResult);
        if (!(scoreValue instanceof Number number)) {
            throw new IllegalArgumentException("Step result score must be numeric.");
        }

        return new StepResult(dataField.get(rawResult), number.doubleValue(), resultsField.get(rawResult));
    }

    /**
     * Step result container.
     * 
     * @since 0.1.7
     */
    public static class StepResult {
        /**
         * data.
         * 
         * @since 0.1.7
         */
        public final Object data;

        /**
         * score.
         * 
         * @since 0.1.7
         */
        public final double score;

        /**
         * results.
         * 
         * @since 0.1.7
         */
        public final Object results;

        /**
         * StepResult.
         * 
         * @param data data
         * @param score score
         * @param results results
         * @since 0.1.7
         */
        public StepResult(Object data, double score, Object results) {
            this.data = data;
            this.score = score;
            this.results = results;
        }
    }
}
