/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Call LLM-as-Judge to score a single (instruction, response, feedback) triple.
 * <p>
 * Calls the Judge service (which may be a dedicated judge_server with voting,
 * or a raw vLLM endpoint) to score a single turn.
 * <p>
 * Mirrors Python's {@code JudgeScorer} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.judge.judge_scorer}.
 */
public class JudgeScorer {

    private static final Logger logger = Logger.getLogger(JudgeScorer.class.getName());
    
    private final String judgeUrl;
    private final String judgeModel;
    private final String apiKey;
    private final int numVotes;
    private final int maxRetries;
    private final double retryBackoffSec;
    private final double timeout;
    private final boolean ownedClient;
    private final Object httpClient; // httpx.AsyncClient equivalent
    private final JudgeEvaluatorConfig config;

    /**
     * Initialize judge scorer client.
     *
     * @param judgeUrl Base URL of judge-compatible chat endpoint.
     * @param judgeModel Judge model id.
     * @param apiKey Judge API key.
     * @param timeout Per-request timeout in seconds.
     * @param numVotes Number of judge votes per sample.
     * @param maxRetries Max retries for transient judge failures.
     * @param retryBackoffSec Linear retry backoff base in seconds.
     * @param httpClient Optional shared HTTP client.
     */
    public JudgeScorer(
        String judgeUrl,
        String judgeModel,
        String apiKey,
        double timeout,
        int numVotes,
        int maxRetries,
        double retryBackoffSec,
        Object httpClient
    ) {
        this.judgeUrl = judgeUrl != null ? judgeUrl.replaceAll("/$", "") : "";
        this.judgeModel = judgeModel != null ? judgeModel : "";
        this.apiKey = apiKey != null ? apiKey : "EMPTY";
        this.numVotes = Math.max(1, numVotes);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffSec = Math.max(0.0, retryBackoffSec);
        this.timeout = timeout;
        this.ownedClient = httpClient == null;
        this.httpClient = httpClient; // Placeholder for actual HTTP client
        
        this.config = new JudgeEvaluatorConfig(
            this.judgeUrl,
            this.judgeModel,
            this.apiKey,
            this.numVotes,
            0.1,  // temperature
            4096, // max_completion_tokens
            this.maxRetries,
            this.retryBackoffSec
        );
    }

    /**
     * Close owned HTTP client if created internally.
     */
    public void close() {
        if (ownedClient && httpClient != null) {
            // Close HTTP client if owned
            // Requires actual HTTP client implementation
        }
    }

    /**
     * Score a turn and return normalized reward details.
     *
     * @param responseText Assistant response content to score.
     * @param instructionText User instruction text for this turn.
     * @param followupUserFeedback Next-turn user feedback for delayed scoring.
     * @param sessionId Optional session id used for logging context.
     * @param turnNum Optional turn index used for logging context.
     * @return Dict with normalized score and raw vote details.
     */
    public CompletableFuture<Map<String, Object>> score(
        String responseText,
        String instructionText,
        String followupUserFeedback,
        String sessionId,
        int turnNum
    ) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = evaluateJudgeScores(
                httpClient,
                config,
                responseText,
                instructionText,
                followupUserFeedback,
                sessionId,
                turnNum
            );
            
            // Remove internal fields
            result.remove("model");
            result.remove("session_id");
            result.remove("turn_num");
            
            return result;
        });
    }

    /**
     * Parse judge scores from content string.
     *
     * @param content Raw content string from judge response
     * @return Parsed scores map
     */
    public static Map<String, Object> parseScores(String content) {
        return parseJudgeScores(content);
    }

    // -- Placeholder methods for evaluator integration --

    private Map<String, Object> evaluateJudgeScores(
        Object client,
        JudgeEvaluatorConfig config,
        String responseText,
        String instructionText,
        String followupUserFeedback,
        String sessionId,
        int turnNum
    ) {
        // Requires actual JudgeEvaluator implementation
        // Placeholder returns a basic score result
        Map<String, Object> result = new HashMap<>();
        result.put("score", 0.5);
        result.put("normalized_score", 0.5);
        result.put("votes", new HashMap<String, Object>());
        result.put("raw_response", "");
        return result;
    }

    private static Map<String, Object> parseJudgeScores(String content) {
        // Requires actual scoring parser implementation
        Map<String, Object> scores = new HashMap<>();
        scores.put("score", 0.5);
        scores.put("normalized_score", 0.5);
        return scores;
    }

    // -- Getters --

    public String getJudgeUrl() { return judgeUrl; }
    public String getJudgeModel() { return judgeModel; }
    public String getApiKey() { return apiKey; }
    public int getNumVotes() { return numVotes; }
    public int getMaxRetries() { return maxRetries; }
    public double getRetryBackoffSec() { return retryBackoffSec; }
    public double getTimeout() { return timeout; }
    public JudgeEvaluatorConfig getConfig() { return config; }
}