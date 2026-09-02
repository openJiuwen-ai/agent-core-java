/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Legacy reasoner configuration using sub-module configuration.
 * Mirrors Python's {@code ReasonerConfig} dataclass which composes
 * IntentDetectionConfig, PlannerConfig, ProactiveIdentifierConfig, ReflectorConfig.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonerConfig {
    @Builder.Default
    /**
     * IntentDetectionConfig.
     * 
     * @since 0.1.7
     */
    private IntentDetectionConfig intentDetection = new IntentDetectionConfig();

    @Builder.Default
    /**
     * PlannerConfig.
     * 
     * @since 0.1.7
     */
    private PlannerConfig planner = new PlannerConfig();

    @Builder.Default
    /**
     * ProactiveIdentifierConfig.
     * 
     * @since 0.1.7
     */
    private ProactiveIdentifierConfig proactiveIdentifier = new ProactiveIdentifierConfig();

    @Builder.Default
    /**
     * ReflectorConfig.
     * 
     * @since 0.1.7
     */
    private ReflectorConfig reflector = new ReflectorConfig();

    @Builder.Default
    private boolean enableMetrics = true;

    @Builder.Default
    private boolean enableLogging = true;

    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
