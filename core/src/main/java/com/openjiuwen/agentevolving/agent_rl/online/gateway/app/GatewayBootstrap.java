/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.app;

import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayConfig;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory.GatewayTrajectoryRuntime;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.Forwarder;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.GatewayHttpTransport;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.HttpUpstreamGatewayClient;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.JavaNetGatewayHttpTransport;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.RetryPolicy;
import com.openjiuwen.agentevolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;
import com.openjiuwen.agentevolving.agent_rl.online.judge.JudgeScorer;
import com.openjiuwen.agentevolving.agent_rl.storage.LoRARepository;

import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gateway app bootstrap helpers.
 *
 * <p>Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/app/bootstrap.py}.</p>
 */
public final class GatewayBootstrap {

    private static final Logger LOGGER = Logger.getLogger("online_rl.gateway");

    private GatewayBootstrap() {
    }

    public static GatewayConfig buildConfigFromEnv() {
        String inferenceUrl = env("INFERENCE_URL", env("LLM_URL", "http://127.0.0.1:18000"));
        GatewayConfig config = new GatewayConfig();
        config.setHost(env("GATEWAY_HOST", "127.0.0.1"));
        config.setPort(Integer.parseInt(envRequired("GATEWAY_PORT")));
        config.setLlmUrl(inferenceUrl);
        config.setJudgeUrl(env("JUDGE_URL", inferenceUrl));
        config.setModelId(env("MODEL_ID", env("SERVED_MODEL_NAME", "")));
        config.setJudgeModel(env("JUDGE_MODEL", ""));
        config.setRequestTimeout(Double.parseDouble(env("REQUEST_TIMEOUT", "120")));
        config.setLlmApiKey(env("LLM_API_KEY", ""));
        config.setJudgeApiKey(env("JUDGE_API_KEY", "EMPTY"));
        config.setGatewayApiKey(env("GATEWAY_API_KEY", ""));
        config.setRecordDir(env("RECORD_DIR", "records"));
        config.setLogLevel(env("LOG_LEVEL", "INFO"));
        config.setDumpTokenIds(isTruthy(env("DUMP_TOKEN_IDS", "")));
        config.setLoraRepoRoot(env("LORA_REPO_ROOT", ""));
        config.setRedisUrl(env("REDIS_URL", ""));
        config.setUpstreamMaxRetries(Integer.parseInt(env("UPSTREAM_MAX_RETRIES", "2")));
        config.setUpstreamRetryBackoffSec(Double.parseDouble(env("UPSTREAM_RETRY_BACKOFF_SEC", "0.2")));
        config.setUpstreamRetryMaxBackoffSec(Double.parseDouble(env("UPSTREAM_RETRY_MAX_BACKOFF_SEC", "2.0")));
        config.setDisableGatewayTrajectoryCollection(isTruthy(env("DISABLE_GATEWAY_TRAJECTORY_COLLECTION", "")));
        return config;
    }

    public static GatewayServer buildAppFromConfig(GatewayConfig config) {
        return buildAppFromConfig(config, null, null);
    }

    public static GatewayServer buildAppFromConfig(
            GatewayConfig config,
            Object httpClient,
            Object redisClient
    ) {
        Objects.requireNonNull(config, "config");
        configureRootLogging(config.getLogLevel());

        boolean ownsRedisClient = redisClient == null;
        Object effectiveRedisClient = redisClient;
        if (effectiveRedisClient == null && !isBlank(config.getRedisUrl())) {
            LOGGER.warning(() -> "Redis URL is configured but automatic Redis bootstrap is not available; "
                    + "inject redisClient explicitly: " + config.getRedisUrl());
        }
        if (effectiveRedisClient == null) {
            throw new IllegalArgumentException("gateway requires redis_url or injected redis_client");
        }

        boolean ownsHttpClient = httpClient == null;
        Object effectiveHttpClient = httpClient != null
                ? httpClient
                : HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1L, (long) config.getRequestTimeout())))
                    .build();
        UpstreamGatewayClient upstreamClient = toUpstreamClient(effectiveHttpClient, config);
        Forwarder forwarder = new Forwarder(upstreamClient, config.getModelId());
        GatewayTrajectoryRuntime trajectoryRuntime = new GatewayTrajectoryRuntime(config, effectiveRedisClient);

        JudgeScorer judgeScorer = null;
        if (!isBlank(config.getJudgeUrl())) {
            judgeScorer = new JudgeScorer(
                    config.getJudgeUrl(),
                    isBlank(config.getJudgeModel()) ? config.getModelId() : config.getJudgeModel(),
                    isBlank(config.getJudgeApiKey()) ? "EMPTY" : config.getJudgeApiKey(),
                    config.getRequestTimeout(),
                    1,
                    config.getUpstreamMaxRetries(),
                    config.getUpstreamRetryBackoffSec(),
                    effectiveHttpClient
            );
        }
        trajectoryRuntime.setJudgeScorer(judgeScorer);

        LoRARepository loraRepository = null;
        if (!isBlank(config.getLoraRepoRoot())) {
            try {
                loraRepository = new LoRARepository(config.getLoraRepoRoot());
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.WARNING,
                        "LoRA repo not available at {0}: {1}",
                        new Object[]{config.getLoraRepoRoot(), exception.getMessage()}
                );
            }
        }

        Object finalHttpClient = effectiveHttpClient;
        Object finalRedisClient = effectiveRedisClient;
        AutoCloseable closeResources = () -> {
            if (ownsHttpClient) {
                closeQuietly(finalHttpClient);
            }
            if (ownsRedisClient) {
                closeQuietly(finalRedisClient);
            }
        };

        return new GatewayServer(
                config,
                forwarder,
                upstreamClient,
                trajectoryRuntime,
                closeResources,
                loraRepository
        );
    }

    private static UpstreamGatewayClient toUpstreamClient(Object httpClient, GatewayConfig config) {
        RetryPolicy retryPolicy = new RetryPolicy(
                Math.max(0, config.getUpstreamMaxRetries()),
                Math.max(0.0d, config.getUpstreamRetryBackoffSec()),
                Math.max(0.0d, config.getUpstreamRetryMaxBackoffSec())
        );
        Duration requestTimeout = Duration.ofSeconds(Math.max(1L, (long) config.getRequestTimeout()));
        if (httpClient instanceof UpstreamGatewayClient upstreamGatewayClient) {
            return upstreamGatewayClient;
        }
        if (httpClient instanceof GatewayHttpTransport transport) {
            return new HttpUpstreamGatewayClient(transport, config.getLlmUrl(), retryPolicy, requestTimeout);
        }
        if (httpClient instanceof HttpClient jdkHttpClient) {
            return new HttpUpstreamGatewayClient(
                    new JavaNetGatewayHttpTransport(jdkHttpClient),
                    config.getLlmUrl(),
                    retryPolicy,
                    requestTimeout
            );
        }
        throw new IllegalArgumentException("Unsupported gateway HTTP client: " + httpClient.getClass().getName());
    }

    private static void configureRootLogging(String logLevel) {
        Level level = toJulLevel(logLevel);
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static Level toJulLevel(String logLevel) {
        String normalized = logLevel == null ? "" : logLevel.trim().toUpperCase();
        return switch (normalized) {
            case "CRITICAL", "FATAL", "ERROR" -> Level.SEVERE;
            case "WARN", "WARNING" -> Level.WARNING;
            case "DEBUG" -> Level.FINE;
            default -> Level.INFO;
        };
    }

    private static void closeQuietly(Object resource) {
        if (resource == null) {
            return;
        }
        try {
            if (resource instanceof AutoCloseable closeable) {
                closeable.close();
                return;
            }
            for (String methodName : new String[]{"close", "aclose"}) {
                Method method = findZeroArgMethod(resource.getClass(), methodName);
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(resource);
                    return;
                }
            }
        } catch (Exception exception) {
            LOGGER.log(Level.FINE, "Ignoring close failure for gateway bootstrap resource", exception);
        }
    }

    private static Method findZeroArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private static String env(String key, String defaultValue) {
        return System.getenv().getOrDefault(key, defaultValue);
    }

    private static String envRequired(String key) {
        String value = env(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized);
    }
}
