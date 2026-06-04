/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.app;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.GatewayTrajectoryRuntime;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.JudgeScorer;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.HttpUpstreamGatewayClient;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.JavaNetGatewayHttpTransport;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.RetryPolicy;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeEvaluator;
import com.openjiuwen.agent_evolving.agent_rl.online.judge.JudgeEvaluatorConfig;
import com.openjiuwen.agent_evolving.agent_rl.online.judge.ScoreResponse;
import com.openjiuwen.agent_evolving.agent_rl.storage.LoRARepository;
import com.openjiuwen.agent_evolving.agent_rl.storage.RedisTrajectoryStoreBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.Map;

/**
 * Gateway app assembly from config and environment.
 * <p>
 * Mirrors Python's {@code build_app_from_config} and environment bootstrap in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.app.bootstrap}.
 */
public final class GatewayBootstrap {

    private static final Logger LOG = LoggerFactory.getLogger("online_rl.gateway");

    private GatewayBootstrap() {
    }

    public static GatewayConfig buildConfigFromEnv() {
        return buildConfigFromEnv(System.getenv());
    }

    public static GatewayConfig buildConfigFromEnv(Map<String, String> env) {
        String inferenceUrl = env(env, "INFERENCE_URL", env(env, "LLM_URL", "http://127.0.0.1:18000"));
        GatewayConfig config = new GatewayConfig();
        config.setHost(env(env, "GATEWAY_HOST", "127.0.0.1"));
        config.setPort(Integer.parseInt(envRequired(env, "GATEWAY_PORT")));
        config.setLlmUrl(inferenceUrl);
        config.setJudgeUrl(env(env, "JUDGE_URL", inferenceUrl));
        config.setModelId(env(env, "MODEL_ID", env(env, "SERVED_MODEL_NAME", "")));
        config.setJudgeModel(env(env, "JUDGE_MODEL", ""));
        config.setRequestTimeout(Double.parseDouble(env(env, "REQUEST_TIMEOUT", "120")));
        config.setLlmApiKey(env(env, "LLM_API_KEY", ""));
        config.setJudgeApiKey(env(env, "JUDGE_API_KEY", "EMPTY"));
        config.setGatewayApiKey(env(env, "GATEWAY_API_KEY", ""));
        config.setRecordDir(env(env, "RECORD_DIR", "records"));
        config.setLogLevel(env(env, "LOG_LEVEL", "INFO"));
        config.setDumpTokenIds(asBoolean(env(env, "DUMP_TOKEN_IDS", "")));
        config.setLoraRepoRoot(env(env, "LORA_REPO_ROOT", ""));
        config.setRedisUrl(env(env, "REDIS_URL", ""));
        config.setUpstreamMaxRetries(Integer.parseInt(env(env, "UPSTREAM_MAX_RETRIES", "2")));
        config.setUpstreamRetryBackoffSec(Double.parseDouble(env(env, "UPSTREAM_RETRY_BACKOFF_SEC", "0.2")));
        config.setUpstreamRetryMaxBackoffSec(Double.parseDouble(env(env, "UPSTREAM_RETRY_MAX_BACKOFF_SEC", "2.0")));
        config.setDisableGatewayTrajectoryCollection(asBoolean(env(env, "DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "")));
        return config;
    }

    public static GatewayApplication buildAppFromConfig(GatewayConfig config,
                                                        RedisTrajectoryStoreBackend redisBackend) {
        GatewayHttpTransport httpTransport = new JavaNetGatewayHttpTransport(HttpClient.newHttpClient());
        return buildAppFromConfig(config, httpTransport, redisBackend);
    }

    public static GatewayApplication buildAppFromConfig(GatewayConfig config,
                                                        GatewayHttpTransport httpTransport,
                                                        RedisTrajectoryStoreBackend redisBackend) {
        if (redisBackend == null) {
            throw new IllegalArgumentException("gateway requires redis_url or injected redis_client");
        }

        RetryPolicy retryPolicy = new RetryPolicy(
                Math.max(0, config.getUpstreamMaxRetries()),
                Math.max(0.0, config.getUpstreamRetryBackoffSec()),
                Math.max(0.0, config.getUpstreamRetryMaxBackoffSec())
        );
        UpstreamGatewayClient upstreamClient = new HttpUpstreamGatewayClient(httpTransport, config.getLlmUrl(), retryPolicy);
        Forwarder forwarder = new Forwarder(upstreamClient, config.getModelId());
        GatewayTrajectoryRuntime trajectoryRuntime = new GatewayTrajectoryRuntime(config, redisBackend);
        trajectoryRuntime.setJudgeScorer(buildJudgeScorer(config, retryPolicy, httpTransport));

        LoRARepository loraRepository = buildLoraRepository(config);

        return new GatewayApplication(
                config,
                forwarder,
                upstreamClient,
                trajectoryRuntime,
                loraRepository,
                closeHook(httpTransport)
        );
    }

    private static LoRARepository buildLoraRepository(GatewayConfig config) {
        if (config.getLoraRepoRoot() == null || config.getLoraRepoRoot().isBlank()) {
            return null;
        }
        try {
            return new LoRARepository(config.getLoraRepoRoot());
        } catch (RuntimeException exception) {
            LOG.warn("LoRA repo not available at {}", config.getLoraRepoRoot());
            return null;
        }
    }

    private static AutoCloseable closeHook(GatewayHttpTransport httpTransport) {
        return () -> {
            if (httpTransport instanceof AutoCloseable closeable) {
                closeable.close();
            }
        };
    }

    private static JudgeScorer buildJudgeScorer(GatewayConfig config,
                                                RetryPolicy retryPolicy,
                                                GatewayHttpTransport httpTransport) {
        if (config.getJudgeUrl() == null || config.getJudgeUrl().isBlank()) {
            return null;
        }
        HttpUpstreamGatewayClient judgeClient = new HttpUpstreamGatewayClient(httpTransport, config.getJudgeUrl(), retryPolicy);
        JudgeEvaluator evaluator = new JudgeEvaluator(judgeClient);
        JudgeEvaluatorConfig evaluatorConfig = new JudgeEvaluatorConfig(
                config.getJudgeUrl(),
                !config.getJudgeModel().isBlank() ? config.getJudgeModel() : config.getModelId()
        );
        evaluatorConfig.setApiKey(!config.getJudgeApiKey().isBlank() ? config.getJudgeApiKey() : "EMPTY");
        evaluatorConfig.setNumVotes(1);
        evaluatorConfig.setTemperature(0.1);
        evaluatorConfig.setMaxCompletionTokens(4096);
        evaluatorConfig.setMaxRetries(config.getUpstreamMaxRetries());
        evaluatorConfig.setRetryBackoffSec(config.getUpstreamRetryBackoffSec());
        return (responseText, instructionText, followupUserFeedback, sessionId, turnNum) -> {
            ScoreResponse response = evaluator.evaluateJudgeScores(
                    evaluatorConfig,
                    responseText,
                    instructionText,
                    followupUserFeedback,
                    sessionId,
                    turnNum
            );
            return response.toMap();
        };
    }

    private static String env(Map<String, String> env, String key, String defaultValue) {
        String value = env != null ? env.get(key) : null;
        return value != null ? value : defaultValue;
    }

    private static String envRequired(Map<String, String> env, String key) {
        String value = env(env, key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static boolean asBoolean(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized);
    }
}
