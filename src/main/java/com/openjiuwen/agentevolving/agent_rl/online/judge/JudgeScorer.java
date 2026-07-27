/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.judge;

import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.GatewayHttpTransport;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.HttpUpstreamGatewayClient;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.JavaNetGatewayHttpTransport;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.RetryPolicy;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.core.common.VirtualThreadSupport;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Call LLM-as-Judge to score a single (instruction, response, feedback) triple.
 * <p>
 * Mirrors Python's {@code JudgeScorer} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/judge/judge_scorer.py}.
 */
public class JudgeScorer implements com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory.JudgeScorer {

    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("judge-scorer-io");

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

    public JudgeScorer(String judgeUrl,
                       String judgeModel,
                       String apiKey,
                       double timeout,
                       int numVotes,
                       int maxRetries,
                       double retryBackoffSec,
                       Object httpClient) {
        this.judgeUrl = judgeUrl == null ? "" : judgeUrl.replaceAll("/+$", "");
        this.judgeModel = judgeModel == null ? "" : judgeModel;
        this.apiKey = apiKey == null ? "EMPTY" : apiKey;
        this.numVotes = Math.max(1, numVotes);
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoffSec = Math.max(0.0d, retryBackoffSec);
        this.timeout = timeout;
        this.ownedClient = httpClient == null;
        this.httpClient = httpClient != null
                ? httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1L, (long) timeout))).build();
        this.config = new JudgeEvaluatorConfig(
                this.judgeUrl,
                this.judgeModel,
                this.apiKey,
                this.numVotes,
                0.1d,
                4096,
                this.maxRetries,
                this.retryBackoffSec
        );
        this.evaluator = new JudgeEvaluator(toUpstreamClient(this.httpClient));
    }

    public void close() {
        if (ownedClient && httpClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to close judge HTTP client", exception);
            }
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> score(String responseText,
                                                        String instructionText,
                                                        String followupUserFeedback,
                                                        String sessionId,
                                                        int turnNum) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> result = new LinkedHashMap<>(evaluator.evaluateJudgeScores(
                    config,
                    responseText,
                    instructionText,
                    followupUserFeedback,
                    sessionId,
                    turnNum
            ).toMap());
            result.remove("model");
            result.remove("session_id");
            result.remove("turn_num");
            return result;
        }, IO_EXECUTOR);
    }

    public static Map<String, Object> parseScores(String content) {
        Map<String, Object> parsed = JudgeScoring.parseJudgeScores(content, false);
        return parsed == null ? new HashMap<>() : parsed;
    }

    private UpstreamGatewayClient toUpstreamClient(Object client) {
        RetryPolicy retryPolicy = new RetryPolicy(maxRetries, retryBackoffSec, Math.max(retryBackoffSec, 2.0d));
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
}
