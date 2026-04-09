/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonerConfig {

    @Builder.Default
    private IntentDetectionConfig intentDetection = new IntentDetectionConfig();

    @Builder.Default
    private PlannerConfig planner = new PlannerConfig();

    @Builder.Default
    private ProactiveIdentifierConfig proactiveIdentifier = new ProactiveIdentifierConfig();

    @Builder.Default
    private ReflectorConfig reflector = new ReflectorConfig();

    @Builder.Default
    private boolean enableMetrics = true;

    @Builder.Default
    private boolean enableLogging = true;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
