/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.bash;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tool-layer security: injection detection and destructive-command warnings.
 *
 * <p>Mirrors Python's {@code check_injection} and
 * {@code get_destructive_warning} in
 * {@code openjiuwen/harness/tools/shell/bash/_security.py}.
 */
public final class BashSecurity {

    private static final List<PatternLabel> INJECTION_PATTERNS = createInjectionPatterns();
    private static final List<PatternLabel> DESTRUCTIVE_PATTERNS = createDestructivePatterns();
    private static final List<PatternLabel> SAFETY_BLOCK_PATTERNS = createSafetyBlockPatterns();

    private BashSecurity() {
    }

    public static SecurityCheck checkInjection(String command) {
        if (command == null || command.isEmpty()) {
            return new SecurityCheck(false);
        }
        for (PatternLabel entry : INJECTION_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return new SecurityCheck(true, "Shell injection detected: " + entry.label);
            }
        }
        return new SecurityCheck(false);
    }

    public static String getDestructiveWarning(String command) {
        if (command == null || command.isEmpty()) {
            return null;
        }
        for (PatternLabel entry : DESTRUCTIVE_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return entry.label;
            }
        }
        return null;
    }

    public static SecurityCheck checkSafety(String command) {
        if (command == null || command.isEmpty()) {
            return new SecurityCheck(false);
        }
        for (PatternLabel entry : SAFETY_BLOCK_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return new SecurityCheck(true, "Command blocked by safety policy: " + entry.label);
            }
        }
        return new SecurityCheck(false);
    }

    private static List<PatternLabel> createInjectionPatterns() {
        List<PatternLabel> patterns = new ArrayList<>();
        patterns.add(new PatternLabel(
                Pattern.compile("(?<!')`[^`]+`"),
                "backtick command substitution"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\$\\("),
                "$() command substitution"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("[<>]\\("),
                "process substitution <() or >()"
        ));
        return List.copyOf(patterns);
    }

    private static List<PatternLabel> createDestructivePatterns() {
        List<PatternLabel> patterns = new ArrayList<>();
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+reset\\s+--hard\\b"),
                "May discard uncommitted changes"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+push\\b[^\\n]*(?:--force|-f)\\b"),
                "May overwrite remote history"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+clean\\s+-[a-zA-Z]*f"),
                "May permanently delete untracked files"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+checkout\\s+--\\s+\\."),
                "May discard all unstaged changes"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+stash\\s+(?:drop|clear)\\b"),
                "May permanently discard stashed changes"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+branch\\s+-D\\b"),
                "May force-delete a branch"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+commit\\s+--amend\\b"),
                "May rewrite the last commit"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+(?:push|commit|merge)\\b[^\\n]*--no-verify\\b"),
                "May skip safety hooks"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bDROP\\s+(?:TABLE|DATABASE)\\b", Pattern.CASE_INSENSITIVE),
                "May drop database objects"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bTRUNCATE\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
                "May truncate database table"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bkubectl\\s+delete\\b"),
                "May delete Kubernetes resources"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bterraform\\s+destroy\\b"),
                "May destroy Terraform infrastructure"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bsudo\\b"),
                "sudo may require a password in non-interactive mode; configure NOPASSWD or run as root"
        ));
        return List.copyOf(patterns);
    }

    private static List<PatternLabel> createSafetyBlockPatterns() {
        List<PatternLabel> patterns = new ArrayList<>();
        patterns.add(new PatternLabel(Pattern.compile("\\brm\\s+-[a-zA-Z]*r[a-zA-Z]*f\\b"), "rm -rf"));
        patterns.add(new PatternLabel(Pattern.compile("\\bshutdown\\b", Pattern.CASE_INSENSITIVE), "shutdown"));
        patterns.add(new PatternLabel(Pattern.compile("\\breboot\\b", Pattern.CASE_INSENSITIVE), "reboot"));
        patterns.add(new PatternLabel(Pattern.compile("\\bdiskpart\\b", Pattern.CASE_INSENSITIVE), "diskpart"));
        patterns.add(new PatternLabel(Pattern.compile("\\bmkfs(?:\\.[a-z0-9]+)?\\b", Pattern.CASE_INSENSITIVE), "mkfs"));
        patterns.add(new PatternLabel(Pattern.compile("\\breg\\s+delete\\b", Pattern.CASE_INSENSITIVE), "reg delete"));
        patterns.add(new PatternLabel(
                Pattern.compile("(^|\\s)Remove-Item(?=\\s)(?=.*(^|\\s)-Recurse(\\s|$))(?=.*(^|\\s)-Force(\\s|$))", Pattern.CASE_INSENSITIVE),
                "Remove-Item -Recurse -Force"
        ));
        return List.copyOf(patterns);
    }

    private record PatternLabel(Pattern pattern, String label) {
    }

    /**
     * Result of a tool-layer security check.
     *
     * <p>Mirrors Python's {@code SecurityCheck} in
     * {@code openjiuwen/harness/tools/shell/bash/_security.py}.
     */
    public record SecurityCheck(boolean blocked, String reason, String warning) {

        public SecurityCheck(boolean blocked) {
            this(blocked, null, null);
        }

        public SecurityCheck(boolean blocked, String reason) {
            this(blocked, reason, null);
        }

        public boolean isBlocked() {
            return blocked;
        }

        public String getReason() {
            return reason;
        }

        public String getWarning() {
            return warning;
        }
    }
}
