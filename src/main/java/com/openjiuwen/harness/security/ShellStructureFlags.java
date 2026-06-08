/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.List;

/**
 * Mirrors Python's {@code ShellStructureFlags} in
 * {@code openjiuwen/harness/security/shell_ast.py}.
 */
public final class ShellStructureFlags {

    private final boolean hasCompoundOperators;
    private final boolean hasPipeline;
    private final boolean hasSubshell;
    private final boolean hasCommandGroup;
    private final boolean hasCommandSubstitution;
    private final boolean hasProcessSubstitution;
    private final boolean hasParameterExpansion;
    private final boolean hasHeredoc;
    private final boolean hasInputRedirection;
    private final boolean hasOutputRedirection;
    private final boolean hasActualOperatorNodes;
    private final List<String> operators;

    public ShellStructureFlags() {
        this(false, false, false, false, false, false, false, false, false, false, false, List.of());
    }

    public ShellStructureFlags(
            boolean hasCompoundOperators,
            boolean hasPipeline,
            boolean hasSubshell,
            boolean hasCommandGroup,
            boolean hasCommandSubstitution,
            boolean hasProcessSubstitution,
            boolean hasParameterExpansion,
            boolean hasHeredoc,
            boolean hasInputRedirection,
            boolean hasOutputRedirection,
            boolean hasActualOperatorNodes,
            List<String> operators
    ) {
        this.hasCompoundOperators = hasCompoundOperators;
        this.hasPipeline = hasPipeline;
        this.hasSubshell = hasSubshell;
        this.hasCommandGroup = hasCommandGroup;
        this.hasCommandSubstitution = hasCommandSubstitution;
        this.hasProcessSubstitution = hasProcessSubstitution;
        this.hasParameterExpansion = hasParameterExpansion;
        this.hasHeredoc = hasHeredoc;
        this.hasInputRedirection = hasInputRedirection;
        this.hasOutputRedirection = hasOutputRedirection;
        this.hasActualOperatorNodes = hasActualOperatorNodes;
        this.operators = operators == null ? List.of() : List.copyOf(operators);
    }

    public boolean hasCompoundOperators() {
        return hasCompoundOperators;
    }

    public boolean hasPipeline() {
        return hasPipeline;
    }

    public boolean hasSubshell() {
        return hasSubshell;
    }

    public boolean hasCommandGroup() {
        return hasCommandGroup;
    }

    public boolean hasCommandSubstitution() {
        return hasCommandSubstitution;
    }

    public boolean hasProcessSubstitution() {
        return hasProcessSubstitution;
    }

    public boolean hasParameterExpansion() {
        return hasParameterExpansion;
    }

    public boolean hasHeredoc() {
        return hasHeredoc;
    }

    public boolean hasInputRedirection() {
        return hasInputRedirection;
    }

    public boolean hasOutputRedirection() {
        return hasOutputRedirection;
    }

    public boolean hasActualOperatorNodes() {
        return hasActualOperatorNodes;
    }

    public List<String> getOperators() {
        return operators;
    }

    public boolean hasRiskyStructure() {
        return hasCompoundOperators
                || hasPipeline
                || hasSubshell
                || hasCommandGroup
                || hasCommandSubstitution
                || hasProcessSubstitution
                || hasParameterExpansion
                || hasHeredoc
                || hasInputRedirection
                || hasOutputRedirection;
    }
}
