/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.HttpUpstreamGatewayClient;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.JavaNetGatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.RetryPolicy;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final Object httpClient;
    private final JudgeEvaluatorConfig config;
    private final JudgeEvaluator evaluator;

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
        this.httpClient = httpClient != null
            ? httpClient
            : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1L, (long) timeout)))
                .build();
        
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
        this.evaluator = new JudgeEvaluator(toUpstreamClient(this.httpClient));
    }

    /**
     * Close owned HTTP client if created internally.
     */
    public void close() {
        if (ownedClient && httpClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to close judge HTTP client", exception);
            }
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
            Map<String, Object> result = new LinkedHashMap<>(evaluator.evaluateJudgeScores(
                config,
                responseText,
                instructionText,
                followupUserFeedback,
                sessionId,
                turnNum
            ).toMap());
            
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

    private static Map<String, Object> parseJudgeScores(String content) {
        Map<String, Object> scores = JudgeScoring.parseJudgeScores(content, false);
        return scores != null ? scores : new HashMap<>();
    }

    private UpstreamGatewayClient toUpstreamClient(Object client) {
        RetryPolicy retryPolicy = new RetryPolicy(maxRetries, retryBackoffSec, Math.max(retryBackoffSec, 2.0));
        Duration requestTimeout = Duration.ofSeconds(Math.max(1L, (long) timeout));
        if (client instanceof UpstreamGatewayClient upstreamGatewayClient) {
            return upstreamGatewayClient;
        }
        if (client instanceof GatewayHttpTransport transport) {
            return new HttpUpstreamGatewayClient(transport, judgeUrl, retryPolicy, requestTimeout);
        }
        if (client instanceof HttpClient jdkClient) {
            return new HttpUpstreamGatewayClient(new JavaNetGatewayHttpTransport(jdkClient), judgeUrl, retryPolicy, requestTimeout);
        }
        throw new IllegalArgumentException("Unsupported judge HTTP client: " + client.getClass().getName());
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
