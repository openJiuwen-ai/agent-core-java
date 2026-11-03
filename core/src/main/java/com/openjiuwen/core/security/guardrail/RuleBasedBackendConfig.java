/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.List;

/**
 * Configuration for the rule-based prompt-injection backend.
 * <p>
 * Mirrors Python's {@code RuleBasedBackendConfig} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public record RuleBasedBackendConfig(List<String> patterns, RiskLevel riskLevel) {

    public RuleBasedBackendConfig() {
        this(null, RiskLevel.HIGH);
    }
}
