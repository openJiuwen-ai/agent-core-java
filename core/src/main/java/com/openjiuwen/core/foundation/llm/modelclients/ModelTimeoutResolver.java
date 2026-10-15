/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Resolves the three independent timeout layers of a model HTTP call (FEAT-058).
 *
 * <p>Three independent resolution chains, never mixed:</p>
 * <ul>
 * <li>connect: {@code connectTimeoutSeconds} field, then system property
 * {@code openjiuwen.llm.http.connect-timeout-seconds}, then default 10s.</li>
 * <li>stream idle: per-call override, then {@code streamIdleSeconds} field, then default 600s.</li>
 * <li>response-header wait: explicit legacy {@code timeout} field, then the configured stream
 * idle value (never the per-call override).</li>
 * </ul>
 *
 * @since 2026-10-09
 */
public final class ModelTimeoutResolver {
    /**
     * Default connect timeout in seconds.
     */
    public static final double DEFAULT_CONNECT_TIMEOUT_SECONDS = 10.0D;

    /**
     * Default stream idle timeout in seconds.
     */
    public static final double DEFAULT_STREAM_IDLE_SECONDS = 600.0D;

    /**
     * Legacy single timeout field default, kept as an alias of the config-class constant.
     */
    public static final double LEGACY_TIMEOUT_DEFAULT_SECONDS =
            ModelClientConfig.DEFAULT_LEGACY_TIMEOUT_SECONDS;

    private static final String CONNECT_TIMEOUT_SECONDS_PROPERTY =
            "openjiuwen.llm.http.connect-timeout-seconds";

    private ModelTimeoutResolver() {
    }

    /**
     * Validates a configured connect timeout value.
     *
     * @param seconds candidate value
     * @return the same value when valid
     * @throws IllegalArgumentException when the value is non-positive, NaN, or outside 5-15s
     */
    public static double validateConnectTimeoutSeconds(double seconds) {
        return ModelClientConfig.validateConnectTimeoutSeconds(seconds);
    }

    /**
     * Validates a configured stream idle timeout value.
     *
     * @param seconds candidate value
     * @return the same value when valid
     * @throws IllegalArgumentException when the value is non-positive, NaN, or outside 300-900s
     */
    public static double validateStreamIdleSeconds(double seconds) {
        return ModelClientConfig.validateStreamIdleSeconds(seconds);
    }

    /**
     * Resolves the connect timeout: config field, then system property, then default.
     *
     * @param config model client config, may be null
     * @return effective connect timeout in seconds
     */
    public static double resolveConnectTimeoutSeconds(ModelClientConfig config) {
        if (config != null && config.getConnectTimeoutSeconds() != null) {
            return config.getConnectTimeoutSeconds();
        }
        String raw = System.getProperty(CONNECT_TIMEOUT_SECONDS_PROPERTY);
        if (raw != null && !raw.isBlank()) {
            try {
                double parsed = Double.parseDouble(raw.trim());
                if (!Double.isNaN(parsed) && parsed > 0.0D) {
                    return parsed;
                }
                Loggers.LLM.warning("Invalid {} value {}, falling back to default 10s",
                        CONNECT_TIMEOUT_SECONDS_PROPERTY, raw);
            } catch (NumberFormatException exception) {
                Loggers.LLM.warning("Unparseable {} value {}, falling back to default 10s",
                        CONNECT_TIMEOUT_SECONDS_PROPERTY, raw);
            }
        }
        return DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    /**
     * Resolves the configured stream idle timeout (no per-call involved).
     *
     * @param config model client config, may be null
     * @return effective stream idle timeout in seconds
     */
    public static double resolveStreamIdleSeconds(ModelClientConfig config) {
        if (config != null && config.getStreamIdleSeconds() != null) {
            return config.getStreamIdleSeconds();
        }
        return DEFAULT_STREAM_IDLE_SECONDS;
    }

    /**
     * Resolves the per-call idle timeout: per-call override wins, otherwise the configured value.
     *
     * @param perCallTimeout per-call override in seconds, null means inherit
     * @param config model client config, may be null
     * @return effective idle timeout in seconds for this call
     * @throws IllegalArgumentException when the per-call value is non-positive or NaN
     */
    public static double resolvePerCallIdleSeconds(Float perCallTimeout, ModelClientConfig config) {
        if (perCallTimeout == null) {
            return resolveStreamIdleSeconds(config);
        }
        float value = perCallTimeout;
        if (Float.isNaN(value) || value <= 0.0F) {
            throw new IllegalArgumentException(
                    "per-call idle timeout must be greater than 0, got: " + value);
        }
        return value;
    }

    /**
     * Resolves the response-header wait window: explicit legacy timeout wins, otherwise the
     * configured stream idle value. The per-call override never participates in this chain.
     *
     * @param config model client config, may be null
     * @return effective response-header wait timeout in seconds
     */
    public static double resolveHeaderWaitSeconds(ModelClientConfig config) {
        if (config != null && config.isTimeoutExplicit()) {
            return config.getTimeout();
        }
        return resolveStreamIdleSeconds(config);
    }

    /**
     * Logs a one-time migration warning when the legacy timeout field is explicitly set.
     * Intended to be called once at client construction time, never per request.
     *
     * @param config model client config, may be null
     */
    public static void warnIfLegacyTimeoutExplicit(ModelClientConfig config) {
        if (config != null && config.isTimeoutExplicit()) {
            Loggers.LLM.warning(
                    "Legacy ModelClientConfig.timeout is deprecated and now only overrides the"
                            + " response-header wait window; migrate to connectTimeoutSeconds"
                            + " and streamIdleSeconds");
        }
    }

    /**
     * Converts seconds to a clamped millisecond duration (at least 1ms).
     *
     * @param seconds timeout in seconds
     * @return duration, never zero or negative
     */
    public static Duration toDuration(double seconds) {
        long millis = Math.max(1L, BigDecimal.valueOf(seconds)
                .multiply(BigDecimal.valueOf(1000L)).longValue());
        return Duration.ofMillis(millis);
    }

    /**
     * Resolves the per-call idle timeout as a Float for {@code ModelInvokeOptions}: the
     * declared per-call value wins, otherwise the configured stream idle value applies.
     *
     * @param declaredPerCall per-call declaration in seconds, null means undeclared
     * @param config model client config, may be null
     * @return effective idle timeout in seconds, never null
     */
    public static Float resolvePerCallIdleTimeout(Double declaredPerCall, ModelClientConfig config) {
        Float perCall = declaredPerCall == null ? null : declaredPerCall.floatValue();
        return (float) resolvePerCallIdleSeconds(perCall, config);
    }
}
