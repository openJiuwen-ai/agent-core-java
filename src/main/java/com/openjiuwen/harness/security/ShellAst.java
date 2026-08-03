/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shell AST pre-processing for tiered tool permissions.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/security/shell_ast.py}.
 */
public final class ShellAst {

    private static final Pattern COMMAND_SUBSTITUTION = Pattern.compile("`|\\$\\(");
    private static final Pattern PROCESS_SUBSTITUTION = Pattern.compile("[<>]\\(");
    private static final Pattern HEREDOC = Pattern.compile("<<<?");
    private static final Pattern PARAM_EXPANSION = Pattern.compile("\\$\\{");

    private ShellAst() {
    }

    public static ShellAstParseResult parseShellForPermission(String command) {
        String text = command == null ? "" : command.trim();
        if (text.isEmpty()) {
            return new ShellAstParseResult("simple");
        }

        ShellStructureFlags flags = scanShellStructure(text);
        if (flags.hasSubshell() || flags.hasCommandGroup()
                || flags.hasCommandSubstitution() || flags.hasProcessSubstitution()
                || flags.hasParameterExpansion() || flags.hasHeredoc()) {
            return new ShellAstParseResult(
                    "too_complex",
                    List.of(),
                    flags,
                    "unsupported complex shell structure",
                    "fallback"
            );
        }

        ParseSegmentsResult segmentsResult = splitIntoSegments(text);
        if (!segmentsResult.valid()) {
            return new ShellAstParseResult(
                    "parse_unavailable",
                    List.of(),
                    flags,
                    "fallback lexer failed to tokenize command safely",
                    "fallback"
            );
        }

        List<ShellSubcommand> subcommands = new ArrayList<>();
        for (Segment segment : segmentsResult.segments()) {
            if (segment.text().isBlank()) {
                continue;
            }
            List<String> argv;
            try {
                argv = shellSplit(segment.text());
            } catch (IllegalArgumentException ex) {
                if (segmentsResult.segments().size() == 1) {
                    return new ShellAstParseResult(
                            "parse_unavailable",
                            List.of(),
                            flags,
                            "fallback lexer failed to tokenize command safely",
                            "fallback"
                    );
                }
                argv = List.of();
            }
            subcommands.add(new ShellSubcommand(
                    segment.text(),
                    argv,
                    segment.redirects(),
                    new ShellSubcommand.SourceSpan(segment.start(), segment.end()),
                    flags.getOperators()
            ));
        }

        if (subcommands.isEmpty()) {
            return new ShellAstParseResult(
                    "too_complex",
                    List.of(),
                    flags,
                    "fallback could not extract any executable command",
                    "fallback"
            );
        }
        return new ShellAstParseResult("simple", subcommands, flags, null, "fallback");
    }

    public static ShellStructureFlags scanShellStructure(String command) {
        Set<String> operators = new LinkedHashSet<>();
        boolean hasPipeline = false;
        boolean hasCompound = false;
        boolean hasInputRedirect = false;
        boolean hasOutputRedirect = false;

        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (ch == '"' && !inSingle && !isEscaped(command, i)) {
                inDouble = !inDouble;
                continue;
            }
            if (inSingle || inDouble) {
                continue;
            }
            if (ch == '&' && i + 1 < command.length() && command.charAt(i + 1) == '&') {
                hasCompound = true;
                operators.add("&&");
                i++;
                continue;
            }
            if (ch == '|' && i + 1 < command.length() && command.charAt(i + 1) == '|') {
                hasCompound = true;
                operators.add("||");
                i++;
                continue;
            }
            if (ch == '|') {
                hasPipeline = true;
                operators.add("|");
                continue;
            }
            if (ch == ';' || ch == '\n' || ch == '\r') {
                hasCompound = true;
                operators.add(String.valueOf(ch));
                continue;
            }
            if (ch == '>') {
                hasOutputRedirect = true;
                if (i + 1 < command.length() && command.charAt(i + 1) == '>') {
                    operators.add(">>");
                    i++;
                } else {
                    operators.add(">");
                }
                continue;
            }
            if (ch == '<') {
                hasInputRedirect = true;
                if (i + 2 < command.length() && command.charAt(i + 1) == '<' && command.charAt(i + 2) == '<') {
                    operators.add("<<<");
                    i += 2;
                } else if (i + 1 < command.length() && command.charAt(i + 1) == '<') {
                    operators.add("<<");
                    i++;
                } else {
                    operators.add("<");
                }
            }
        }

        boolean hasSubshell = looksLikeSubshell(command);
        boolean hasCommandGroup = looksLikeCommandGroup(command);
        boolean hasCommandSubstitution = COMMAND_SUBSTITUTION.matcher(command).find();
        boolean hasProcessSubstitution = PROCESS_SUBSTITUTION.matcher(command).find();
        boolean hasParameterExpansion = PARAM_EXPANSION.matcher(command).find();
        boolean hasHeredoc = HEREDOC.matcher(command).find();

        if (hasCommandSubstitution) {
            operators.add("$(");
            if (command.contains("`")) {
                operators.add("`");
            }
        }
        if (hasProcessSubstitution) {
            if (command.contains("<(")) {
                operators.add("<(");
            }
            if (command.contains(">(")) {
                operators.add(">(");
            }
        }
        if (hasHeredoc) {
            if (command.contains("<<<")) {
                operators.add("<<<");
            } else if (command.contains("<<")) {
                operators.add("<<");
            }
        }

        return new ShellStructureFlags(
                hasCompound,
                hasPipeline,
                hasSubshell,
                hasCommandGroup,
                hasCommandSubstitution,
                hasProcessSubstitution,
                hasParameterExpansion,
                hasHeredoc,
                hasInputRedirect,
                hasOutputRedirect,
                !operators.isEmpty(),
                new ArrayList<>(operators)
        );
    }

    private static boolean looksLikeSubshell(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("(") && trimmed.endsWith(")");
    }

    private static boolean looksLikeCommandGroup(String command) {
        String trimmed = command.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private static ParseSegmentsResult splitIntoSegments(String command) {
        List<Segment> segments = new ArrayList<>();
        List<String> redirects = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        int segmentStart = firstNonWhitespace(command, 0);
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(ch);
                continue;
            }
            if (ch == '"' && !inSingle && !isEscaped(command, i)) {
                inDouble = !inDouble;
                current.append(ch);
                continue;
            }
            if (inSingle || inDouble) {
                current.append(ch);
                continue;
            }

            String operator = operatorAt(command, i);
            if (operator != null) {
                if (operator.equals(">") || operator.equals(">>") || operator.equals("<") || operator.equals("<<")
                        || operator.equals("<<<")) {
                    redirects.add(operator);
                    current.append(operator);
                    i += operator.length() - 1;
                    continue;
                }

                String text = current.toString().trim();
                if (!text.isEmpty()) {
                    segments.add(new Segment(text, segmentStart, i, List.copyOf(redirects)));
                }
                current.setLength(0);
                redirects = new ArrayList<>();
                i += operator.length() - 1;
                segmentStart = firstNonWhitespace(command, i + 1);
                continue;
            }
            current.append(ch);
        }
        if (inSingle || inDouble) {
            return new ParseSegmentsResult(List.of(), false);
        }
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            segments.add(new Segment(text, segmentStart, command.length(), List.copyOf(redirects)));
        }
        return new ParseSegmentsResult(segments, true);
    }

    private static String operatorAt(String command, int index) {
        if (index + 1 < command.length()) {
            String pair = command.substring(index, index + 2);
            if ("&&".equals(pair) || "||".equals(pair) || ">>".equals(pair) || "<<".equals(pair)) {
                if ("&&".equals(pair) || "||".equals(pair)) {
                    return pair;
                }
                if ("<<".equals(pair) && index + 2 < command.length() && command.charAt(index + 2) == '<') {
                    return "<<<";
                }
                return pair;
            }
        }
        char ch = command.charAt(index);
        if (ch == '|' || ch == ';' || ch == '\n' || ch == '\r' || ch == '>' || ch == '<') {
            return String.valueOf(ch);
        }
        return null;
    }

    private static int firstNonWhitespace(String command, int start) {
        for (int i = start; i < command.length(); i++) {
            if (!Character.isWhitespace(command.charAt(i))) {
                return i;
            }
        }
        return command.length();
    }

    private static boolean isEscaped(String command, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && command.charAt(i) == '\\'; i--) {
            slashCount++;
        }
        return slashCount % 2 == 1;
    }

    private static List<String> shellSplit(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escaping = false;

        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (escaping) {
                current.append(ch);
                escaping = false;
                continue;
            }
            if (ch == '\\' && !inSingle) {
                escaping = true;
                continue;
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                continue;
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
                continue;
            }
            if (!inSingle && !inDouble && Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }

        if (escaping || inSingle || inDouble) {
            throw new IllegalArgumentException("Unbalanced shell quoting");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        return List.copyOf(tokens);
    }

    private record Segment(String text, int start, int end, List<String> redirects) {
    }

    private record ParseSegmentsResult(List<Segment> segments, boolean valid) {
    }
}
