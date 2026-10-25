/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed IntelliRouter configuration extracted from {@link ModelClientConfig}.
 *
 * <p>Mirrors Python's {@code IntelliRouterClientConfig} in
 * {@code openjiuwen/core/foundation/llm/model_clients/intelli_router_model_client.py}.</p>
 */
public final class IntelliRouterClientConfig {

    private static final String DEPLOYMENTS_KEY = "intelli_router_deployments";
    private static final String STRATEGY_KEY = "intelli_router_strategy";
    private static final String NUM_RETRIES_KEY = "intelli_router_num_retries";
    private static final String TIMEOUT_KEY = "intelli_router_timeout";
    private static final String STRATEGY_KWARGS_KEY = "intelli_router_strategy_kwargs";
    private static final String ENABLE_HEALTH_CHECK_KEY = "intelli_router_enable_health_check";
    private static final String HEALTH_CHECK_INTERVAL_KEY = "intelli_router_health_check_interval";

    private final List<Map<String, Object>> deployments;
    private final String strategy;
    private final int numRetries;
    private final double timeout;
    private final Map<String, Object> strategyKwargs;
    private final boolean enableHealthCheck;
    private final double healthCheckInterval;
    private final boolean verifySsl;
    private final Double connectTimeoutSeconds;
    private final Double streamIdleSeconds;

    public IntelliRouterClientConfig(
            List<Map<String, Object>> deployments,
            String strategy,
            int numRetries,
            double timeout,
            Map<String, Object> strategyKwargs,
            boolean enableHealthCheck,
            double healthCheckInterval,
            boolean verifySsl) {
        this(deployments, strategy, numRetries, timeout, strategyKwargs, enableHealthCheck,
                healthCheckInterval, verifySsl, null, null);
    }

    IntelliRouterClientConfig(
            List<Map<String, Object>> deployments,
            String strategy,
            int numRetries,
            double timeout,
            Map<String, Object> strategyKwargs,
            boolean enableHealthCheck,
            double healthCheckInterval,
            boolean verifySsl,
            Double connectTimeoutSeconds,
            Double streamIdleSeconds) {
        this.deployments = copyMapList(deployments);
        this.strategy = strategy == null ? "simple-shuffle" : strategy;
        this.numRetries = numRetries;
        this.timeout = timeout;
        this.strategyKwargs = copyMap(strategyKwargs);
        this.enableHealthCheck = enableHealthCheck;
        this.healthCheckInterval = healthCheckInterval;
        this.verifySsl = verifySsl;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.streamIdleSeconds = streamIdleSeconds;
    }

    /**
     * Extract IntelliRouter-specific fields from Python-compatible extra fields. The top-level
     * {@code connectTimeoutSeconds} / {@code streamIdleSeconds} of the model client config are
     * carried through so router drivers can enforce the unified tiered timeout contract instead
     * of the fields being silently ignored on this channel.
     *
     * @param config model client config carrying {@code intelli_router_*} entries
     * @return typed IntelliRouter config
     */
    public static IntelliRouterClientConfig fromModelClientConfig(ModelClientConfig config) {
        Map<String, Object> extra = config == null || config.getExtraFields() == null
                ? Map.of()
                : config.getExtraFields();
        return new IntelliRouterClientConfig(
                asMapList(extra.get(DEPLOYMENTS_KEY)),
                asString(extra.get(STRATEGY_KEY), "simple-shuffle"),
                asInt(extra.get(NUM_RETRIES_KEY), 3),
                asDouble(extra.get(TIMEOUT_KEY), 30.0D),
                asMap(extra.get(STRATEGY_KWARGS_KEY)),
                asBoolean(extra.get(ENABLE_HEALTH_CHECK_KEY), false),
                asDouble(extra.get(HEALTH_CHECK_INTERVAL_KEY), 300.0D),
                config == null || config.isVerifySsl(),
                config == null ? null : config.getConnectTimeoutSeconds(),
                config == null ? null : config.getStreamIdleSeconds()
        );
    }

    public List<Map<String, Object>> getDeployments() {
        return copyMapList(deployments);
    }

    public String getStrategy() {
        return strategy;
    }

    public int getNumRetries() {
        return numRetries;
    }

    public double getTimeout() {
        return timeout;
    }

    public Map<String, Object> getStrategyKwargs() {
        return copyMap(strategyKwargs);
    }

    public boolean isEnableHealthCheck() {
        return enableHealthCheck;
    }

    public double getHealthCheckInterval() {
        return healthCheckInterval;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * Returns the top-level connect timeout carried from the model client config.
     *
     * @return connect timeout in seconds, or null when not configured
     */
    public Double getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    /**
     * Returns the top-level stream idle timeout carried from the model client config.
     *
     * @return stream idle timeout in seconds, or null when not configured
     */
    public Double getStreamIdleSeconds() {
        return streamIdleSeconds;
    }

    private static List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                result.add(copyRawMap(rawMap));
            }
        }
        return result;
    }

    private static Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return copyRawMap(map);
    }

    private static String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static List<Map<String, Object>> copyMapList(List<Map<String, Object>> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> item : value) {
            result.add(copyMap(item));
        }
        return result;
    }

    private static Map<String, Object> copyMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(value);
    }

    private static Map<String, Object> copyRawMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }
}
