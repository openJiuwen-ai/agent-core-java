/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.shellast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Default {@link ShellAstBackend} with no external dependency.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.shell_ast._parse_with_conservative_fallback}
 * and {@code _scan_shell_structure}. It scans for risky structural markers (pipes,
 * redirections, command/process substitution, heredocs, parameter expansion) and
 * degrades to {@code parse_unavailable} when any are present, so the tiered policy
 * layer can fail closed. Otherwise it tokenizes the simple command with
 * {@link ShellLexer}.
 *
 * @since 0.1.15
 */
public final class ConservativeShellScanner implements ShellAstBackend {
    /**
     * Shared singleton instance.
     *
     * @since 0.1.15
     */
    public static final ConservativeShellScanner INSTANCE = new ConservativeShellScanner();

    private static final Pattern COMMAND_SUBSTITUTION = Pattern.compile("`|\\$\\(");
    private static final Pattern PROCESS_SUBSTITUTION = Pattern.compile("[<>]\\(");
    private static final Pattern PARAMETER_EXPANSION = Pattern.compile("\\$\\{");
    private static final Pattern HEREDOC = Pattern.compile("<<<?");

    private static final String[] OPERATOR_MARKERS = {
            "&&", "||", ";", "|", ">>", ">", "<", "$(", "`", "<(", ">(", "<<", "<<<"
    };

    private ConservativeShellScanner() {
    }

    @Override
    public ShellAstParseResult parse(String command) {
        String text = command == null ? "" : command.trim();
        if (text.isEmpty()) {
            return ShellAstParseResult.builder().kind("simple").backend("fallback").build();
        }
        ShellStructureFlags flags = scan(text);
        if (flags.hasRiskyStructure()) {
            return ShellAstParseResult.builder()
                    .kind("parse_unavailable")
                    .flags(flags)
                    .reason("tree-sitter backend unavailable and fallback detected shell structure")
                    .backend("fallback")
                    .build();
        }
        List<String> argv;
        try {
            argv = ShellLexer.split(text);
        } catch (IllegalArgumentException ex) {
            return ShellAstParseResult.builder()
                    .kind("parse_unavailable")
                    .flags(flags)
                    .reason("fallback lexer failed to tokenize command safely")
                    .backend("fallback")
                    .build();
        }
        ShellSubcommand subcommand = ShellSubcommand.builder().text(text).argv(argv).build();
        return ShellAstParseResult.builder()
                .kind("simple")
                .subcommands(List.of(subcommand))
                .flags(flags)
                .backend("fallback")
                .build();
    }

    /**
     * Scan structural markers from a command.
     *
     * @param command command text
     * @return scanned flags
     * @since 0.1.15
     */
    static ShellStructureFlags scan(String command) {
        boolean hasPipeline = command.contains("|");
        boolean hasCompound = command.contains("&&") || command.contains("||")
                || command.contains(";") || command.contains("\n") || command.contains("\r");
        boolean hasInputRedirection = command.contains("<");
        boolean hasOutputRedirection = command.contains(">");
        boolean hasCommandSubstitution = COMMAND_SUBSTITUTION.matcher(command).find();
        boolean hasProcessSubstitution = PROCESS_SUBSTITUTION.matcher(command).find();
        boolean hasParameterExpansion = PARAMETER_EXPANSION.matcher(command).find();
        boolean hasHeredoc = HEREDOC.matcher(command).find();
        List<String> operators = collectOperators(command);
        return ShellStructureFlags.builder()
                .isCompoundOperators(hasCompound)
                .isPipeline(hasPipeline)
                .isCommandSubstitution(hasCommandSubstitution)
                .isProcessSubstitution(hasProcessSubstitution)
                .isParameterExpansion(hasParameterExpansion)
                .isHeredoc(hasHeredoc)
                .isInputRedirection(hasInputRedirection)
                .isOutputRedirection(hasOutputRedirection)
                .operators(operators)
                .build();
    }

    private static List<String> collectOperators(String command) {
        List<String> markers = new ArrayList<>();
        for (String token : OPERATOR_MARKERS) {
            if (command.contains(token) && !markers.contains(token)) {
                markers.add(token);
            }
        }
        return markers;
    }
}
