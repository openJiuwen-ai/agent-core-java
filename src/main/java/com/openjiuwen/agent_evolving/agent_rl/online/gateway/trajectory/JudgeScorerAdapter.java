/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Adapter that wraps the full JudgeScorer implementation for the gateway trajectory flow.
 * <p>
 * Adapts the comprehensive {@link com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeScorer}
 * to the minimal interface required by {@link JudgeDispatcher}.
 * </p>
 *
 * <p>Mirrors Python's {@code JudgeScorer} usage in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.judge_dispatcher}.</p>
 */
public class JudgeScorerAdapter implements JudgeScorer {

    private final com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeScorer delegate;

    /**
     * Create an adapter wrapping the given JudgeScorer implementation.
     *
     * @param delegate the full JudgeScorer implementation to delegate to
     */
    public JudgeScorerAdapter(com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeScorer delegate) {
        this.delegate = delegate;
    }

    /**
     * Create an adapter with a new JudgeScorer using the specified configuration.
     *
     * @param judgeUrl     Base URL of judge-compatible chat endpoint
     * @param judgeModel   Judge model id
     * @param apiKey       Judge API key
     * @param timeout      Per-request timeout in seconds
     * @param numVotes     Number of judge votes per sample
     */
    public JudgeScorerAdapter(String judgeUrl, String judgeModel, String apiKey,
                              double timeout, int numVotes) {
        this.delegate = new com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeScorer(
                judgeUrl, judgeModel, apiKey, timeout, numVotes, 2, 0.2, null
        );
    }

    @Override
    public Map<String, Object> score(String responseText, String instructionText,
                                      String followupUserFeedback, String sessionId, int turnNum) {
        try {
            CompletableFuture<Map<String, Object>> future = delegate.score(
                    responseText, instructionText, followupUserFeedback, sessionId, turnNum
            );
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of("error", "Scoring interrupted", "score", 0.0);
        } catch (ExecutionException e) {
            return Map.of("error", e.getCause().getMessage(), "score", 0.0);
        }
    }

    /**
     * Get the underlying delegate JudgeScorer.
     *
     * @return the delegate
     */
    public com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeScorer getDelegate() {
        return delegate;
    }

    /**
     * Close the underlying HTTP client if owned.
     */
    public void close() {
        delegate.close();
    }
}
