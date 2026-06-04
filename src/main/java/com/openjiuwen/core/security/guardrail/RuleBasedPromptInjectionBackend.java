/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Rule-based prompt injection detection backend.
 *
 * <p>Mirrors Python's {@code RuleBasedPromptInjectionBackend} in
 * {@code openjiuwen.core.security.guardrail.backends}.</p>
 */
public class RuleBasedPromptInjectionBackend implements GuardrailBackend {

    private static final List<String> DEFAULT_PATTERNS = List.of(
            "ignore.*previous.*instructions",
            "disregard.*prior.*commands",
            "system.*prompt",
            "you.*are.*now",
            "act.*as",
            "forget.*everything"
    );

    private final List<String> patterns;
    private final RiskLevel riskLevel;

    public RuleBasedPromptInjectionBackend() {
        this((RuleBasedBackendConfig) null);
    }

    public RuleBasedPromptInjectionBackend(RuleBasedBackendConfig config) {
        this(
                config != null ? config.getPatterns() : null,
                config != null ? config.getRiskLevel() : RiskLevel.HIGH
        );
    }

    public RuleBasedPromptInjectionBackend(List<String> patterns, RiskLevel riskLevel) {
        this.patterns = patterns != null ? List.copyOf(patterns) : DEFAULT_PATTERNS;
        this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.HIGH;
    }

    @Override
    public RiskAssessment analyze(Map<String, Object> data) {
        return analyze(toContext(data));
    }

    public RiskAssessment analyze(GuardrailContext context) {
        String text = context != null
                ? context.getText().orElse(context.getContent() != null ? String.valueOf(context.getContent()) : "")
                : "";

        for (String pattern : patterns) {
            if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(text).find()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("matched_pattern", pattern);
                return RiskAssessment.builder()
                        .hasRisk(true)
                        .riskLevel(riskLevel)
                        .riskType("prompt_injection")
                        .confidence(1.0)
                        .details(details)
                        .build();
            }
        }

        return RiskAssessment.builder()
                .hasRisk(false)
                .riskLevel(RiskLevel.SAFE)
                .confidence(1.0)
                .build();
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    private GuardrailContext toContext(Map<String, Object> data) {
        if (data == null) {
            return new GuardrailContext(GuardrailContentType.TEXT, "", "");
        }
        Object text = firstNonNull(data.get("text"), data.get("content"), data.get("prompt"), data.get("result"));
        if (text == null && data.get("messages") instanceof List<?> messages && !messages.isEmpty()) {
            Object last = messages.get(messages.size() - 1);
            if (last instanceof Map<?, ?> map) {
                text = map.get("content");
            } else {
                text = last;
            }
        }
        return new GuardrailContext(
                GuardrailContentType.TEXT,
                text != null ? String.valueOf(text) : "",
                String.valueOf(data.getOrDefault("event", "")),
                data
        );
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
