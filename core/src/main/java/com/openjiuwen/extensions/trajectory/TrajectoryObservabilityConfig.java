/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

/**
 * Configuration for the trajectory observability runtime.
 *
 * @param isEnabled whether trajectory span production is active
 * @param serviceName {@code service.name} resource attribute value
 * @param sampleRate root-trace sampling ratio in {@code [0.0, 1.0]}
 * @since 0.1.18
 */
public record TrajectoryObservabilityConfig(boolean isEnabled, String serviceName,
                                            double sampleRate) {
    /** Default service name used when the host does not configure one. */
    public static final String DEFAULT_SERVICE_NAME = "jiuwenswarm-agent";

    /** Default root-trace sample ratio. */
    public static final double DEFAULT_SAMPLE_RATE = 1.0d;

    /**
     * Disabled configuration used by hosts that turn trajectory export off.
     *
     * @return a configuration with {@code isEnabled() == false}
     */
    public static TrajectoryObservabilityConfig disabled() {
        return new TrajectoryObservabilityConfig(false, DEFAULT_SERVICE_NAME,
                DEFAULT_SAMPLE_RATE);
    }

    /**
     * Compact constructor validating ranges and defaults.
     */
    public TrajectoryObservabilityConfig {
        if (serviceName == null || serviceName.isEmpty()) {
            serviceName = DEFAULT_SERVICE_NAME;
        }
        if (sampleRate < 0.0d || sampleRate > 1.0d) {
            throw new IllegalArgumentException("sampleRate must be within [0.0, 1.0]");
        }
    }
}
