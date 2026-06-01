/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.List;

/**
 * Configuration for rule-based prompt injection detection.
 *
 * <p>Mirrors Python's {@code RuleBasedBackendConfig} in
 * {@code openjiuwen.core.security.guardrail.backends}.</p>
 */
public class RuleBasedBackendConfig {

    private final List<String> patterns;
    private final RiskLevel riskLevel;

    public RuleBasedBackendConfig() {
        this(null, RiskLevel.HIGH);
    }

    public RuleBasedBackendConfig(List<String> patterns) {
        this(patterns, RiskLevel.HIGH);
    }

    public RuleBasedBackendConfig(List<String> patterns, RiskLevel riskLevel) {
        this.patterns = patterns;
        this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.HIGH;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }
}
