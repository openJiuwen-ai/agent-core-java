/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rule-based prompt-injection detection backend.
 * <p>
 * Mirrors Python's {@code RuleBasedPromptInjectionBackend} in
 * {@code openjiuwen/core/security/guardrail/backends.py}.
 */
public class RuleBasedPromptInjectionBackend extends GuardrailBackend {

    private final List<String> patterns;
    private final RiskLevel riskLevel;

    public RuleBasedPromptInjectionBackend() {
        this((RuleBasedBackendConfig) null);
    }

    public RuleBasedPromptInjectionBackend(RuleBasedBackendConfig config) {
        this(
                config != null ? config.patterns() : null,
                config != null && config.riskLevel() != null ? config.riskLevel() : RiskLevel.HIGH
        );
    }

    public RuleBasedPromptInjectionBackend(List<String> patterns, RiskLevel riskLevel) {
        this.patterns = patterns == null ? defaultPatterns() : List.copyOf(patterns);
        this.riskLevel = riskLevel == null ? RiskLevel.HIGH : riskLevel;
    }

    @Override
    public RiskAssessment analyze(GuardrailContext ctx) {
        String text = contextText(ctx);
        for (String pattern : patterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(text).find()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("matched_pattern", pattern);
                return new RiskAssessment(true, riskLevel, "prompt_injection", 1.0d, details);
            }
        }
        return new RiskAssessment(false, RiskLevel.SAFE, null, 1.0d, Map.of());
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    private static List<String> defaultPatterns() {
        return List.of(
                "ignore.*previous.*instructions",
                "disregard.*prior.*commands",
                "system.*prompt",
                "you.*are.*now",
                "act.*as",
                "forget.*everything"
        );
    }
}
