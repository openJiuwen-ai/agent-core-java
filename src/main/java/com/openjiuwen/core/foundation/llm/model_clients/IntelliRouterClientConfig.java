/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

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

    public IntelliRouterClientConfig(
            List<Map<String, Object>> deployments,
            String strategy,
            int numRetries,
            double timeout,
            Map<String, Object> strategyKwargs,
            boolean enableHealthCheck,
            double healthCheckInterval,
            boolean verifySsl) {
        this.deployments = copyMapList(deployments);
        this.strategy = strategy == null ? "simple-shuffle" : strategy;
        this.numRetries = numRetries;
        this.timeout = timeout;
        this.strategyKwargs = copyMap(strategyKwargs);
        this.enableHealthCheck = enableHealthCheck;
        this.healthCheckInterval = healthCheckInterval;
        this.verifySsl = verifySsl;
    }

    /**
     * Extract IntelliRouter-specific fields from Python-compatible extra fields.
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
                config == null || config.isVerifySsl()
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
