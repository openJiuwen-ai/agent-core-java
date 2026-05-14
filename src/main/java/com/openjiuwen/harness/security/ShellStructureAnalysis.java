/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight shell-structure analysis result for permission checks.
 *
 * <p>Mirrors Python's shell AST result model in
 * {@code openjiuwen.harness.security.shell_ast}.
 */
public class ShellStructureAnalysis {

    public enum Kind {
        SIMPLE,
        PARSE_UNAVAILABLE,
        TOO_COMPLEX
    }

    private final Kind kind;
    private final boolean hasCompoundOperators;
    private final boolean hasPipeline;
    private final boolean hasCommandSubstitution;
    private final boolean hasProcessSubstitution;
    private final boolean hasParameterExpansion;
    private final boolean hasHeredoc;
    private final boolean hasInputRedirection;
    private final boolean hasOutputRedirection;
    private final List<String> operators;
    private final String reason;

    public ShellStructureAnalysis(
            Kind kind,
            boolean hasCompoundOperators,
            boolean hasPipeline,
            boolean hasCommandSubstitution,
            boolean hasProcessSubstitution,
            boolean hasParameterExpansion,
            boolean hasHeredoc,
            boolean hasInputRedirection,
            boolean hasOutputRedirection,
            List<String> operators,
            String reason
    ) {
        this.kind = kind;
        this.hasCompoundOperators = hasCompoundOperators;
        this.hasPipeline = hasPipeline;
        this.hasCommandSubstitution = hasCommandSubstitution;
        this.hasProcessSubstitution = hasProcessSubstitution;
        this.hasParameterExpansion = hasParameterExpansion;
        this.hasHeredoc = hasHeredoc;
        this.hasInputRedirection = hasInputRedirection;
        this.hasOutputRedirection = hasOutputRedirection;
        this.operators = operators != null ? operators : List.of();
        this.reason = reason;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean hasRiskyStructure() {
        return hasCompoundOperators
                || hasPipeline
                || hasCommandSubstitution
                || hasProcessSubstitution
                || hasParameterExpansion
                || hasHeredoc
                || hasInputRedirection
                || hasOutputRedirection;
    }

    public List<String> getOperators() {
        return new ArrayList<>(operators);
    }

    public String getReason() {
        return reason;
    }
}
