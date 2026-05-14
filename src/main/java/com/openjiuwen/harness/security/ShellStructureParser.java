/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative shell-structure parser used for permission checks.
 *
 * <p>Mirrors Python's shell permission parsing flow in
 * {@code openjiuwen.harness.security.shell_ast}.
 */
public final class ShellStructureParser {

    private ShellStructureParser() {
    }

    public static ShellStructureAnalysis analyze(String command) {
        String text = command == null ? "" : command.trim();
        if (text.isEmpty()) {
            return new ShellStructureAnalysis(
                    ShellStructureAnalysis.Kind.SIMPLE,
                    false, false, false, false, false, false, false, false,
                    List.of(), null
            );
        }

        boolean hasPipeline = text.contains("|");
        boolean hasCompound = text.contains("&&") || text.contains("||") || text.contains(";")
                || text.contains("\n") || text.contains("\r");
        boolean hasInputRedirect = text.contains("<");
        boolean hasOutputRedirect = text.contains(">") || text.contains(">>");
        boolean hasCommandSubstitution = text.contains("`") || text.contains("$(");
        boolean hasProcessSubstitution = text.contains("<(") || text.contains(">(");
        boolean hasParameterExpansion = text.contains("${");
        boolean hasHeredoc = text.contains("<<") || text.contains("<<<");

        List<String> operators = new ArrayList<>();
        for (String token : List.of("&&", "||", ";", "|", ">>", ">", "<", "$(", "`", "<(", ">(", "<<", "<<<")) {
            if (text.contains(token) && !operators.contains(token)) {
                operators.add(token);
            }
        }

        boolean risky = hasPipeline || hasCompound || hasInputRedirect || hasOutputRedirect
                || hasCommandSubstitution || hasProcessSubstitution || hasParameterExpansion || hasHeredoc;
        ShellStructureAnalysis.Kind kind = risky
                ? ShellStructureAnalysis.Kind.PARSE_UNAVAILABLE
                : ShellStructureAnalysis.Kind.SIMPLE;
        String reason = risky ? "fallback detected shell structure" : null;

        return new ShellStructureAnalysis(
                kind,
                hasCompound,
                hasPipeline,
                hasCommandSubstitution,
                hasProcessSubstitution,
                hasParameterExpansion,
                hasHeredoc,
                hasInputRedirect,
                hasOutputRedirect,
                operators,
                reason
        );
    }
}
