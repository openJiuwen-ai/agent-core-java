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
 * <p>Mirrors Python's functions in
 * {@code openjiuwen.harness.tools.shell.bash._security}.
 *
 * <p>These checks complement sys_operation's built-in safety (dangerous-pattern
 * blocking, allowlist). Injection checks block execution; destructive warnings
 * are purely informational and do not prevent execution.
 */
public final class BashSecurityUtils {

    private static final Pattern BACKTICK_RE = Pattern.compile("(?<!')`[^`]+`");
    private static final Pattern DOLLAR_PAREN_RE = Pattern.compile("\\$\\(");
    private static final Pattern PROC_SUBST_RE = Pattern.compile("[<>]\\(");

    private static final List<PatternEntry> INJECTION_PATTERNS = new ArrayList<>();
    private static final List<PatternEntry> DANGEROUS_COMMAND_PATTERNS = new ArrayList<>();

    static {
        INJECTION_PATTERNS.add(new PatternEntry(BACKTICK_RE, "backtick command substitution"));
        INJECTION_PATTERNS.add(new PatternEntry(DOLLAR_PAREN_RE, "$() command substitution"));
        INJECTION_PATTERNS.add(new PatternEntry(PROC_SUBST_RE, "process substitution <() or >()"));
    }

    static {
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\brm\\s+-rf\\b", Pattern.CASE_INSENSITIVE),
                "rm -rf"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bdel\\s+/[a-z]*[fsq][a-z]*\\b", Pattern.CASE_INSENSITIVE),
                "del /f /s /q"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\brd\\s+/s\\s+/q\\b", Pattern.CASE_INSENSITIVE),
                "rd /s /q"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bformat\\s+[a-z]:", Pattern.CASE_INSENSITIVE),
                "format drive"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bshutdown\\b", Pattern.CASE_INSENSITIVE),
                "shutdown"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\breboot\\b", Pattern.CASE_INSENSITIVE),
                "reboot"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bdiskpart\\b", Pattern.CASE_INSENSITIVE),
                "diskpart"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bmkfs\\b", Pattern.CASE_INSENSITIVE),
                "mkfs"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\breg\\s+delete\\b", Pattern.CASE_INSENSITIVE),
                "reg delete"));
        DANGEROUS_COMMAND_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bremove-item\\b[^\\n\\r]*-recurse[^\\n\\r]*-force", Pattern.CASE_INSENSITIVE),
                "Remove-Item -Recurse -Force"));
    }

    private static final List<PatternEntry> DESTRUCTIVE_PATTERNS = new ArrayList<>();

    static {
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+reset\\s+--hard\\b"),
                "May discard uncommitted changes"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+push\\b[^\n]*(?:--force|-f)\\b"),
                "May overwrite remote history"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+clean\\s+-[a-zA-Z]*f"),
                "May permanently delete untracked files"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+checkout\\s+--\\s+\\."),
                "May discard all unstaged changes"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+stash\\s+(?:drop|clear)\\b"),
                "May permanently discard stashed changes"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+branch\\s+-D\\b"),
                "May force-delete a branch"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+commit\\s+--amend\\b"),
                "May rewrite the last commit"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bgit\\s+(?:push|commit|merge)\\b[^\n]*--no-verify\\b"),
                "May skip safety hooks"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bDROP\\s+(?:TABLE|DATABASE)\\b", Pattern.CASE_INSENSITIVE),
                "May drop database objects"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bTRUNCATE\\s+TABLE\\b", Pattern.CASE_INSENSITIVE),
                "May truncate database table"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bkubectl\\s+delete\\b"),
                "May delete Kubernetes resources"));
        DESTRUCTIVE_PATTERNS.add(new PatternEntry(
                Pattern.compile("\\bterraform\\s+destroy\\b"),
                "May destroy Terraform infrastructure"));
    }

    private BashSecurityUtils() {
    }

    /**
     * Detect shell injection patterns that could bypass static analysis.
     *
     * @param command Shell command string
     * @return SecurityCheck with blocked=true if any injection pattern is found
     */
    public static SecurityCheck checkInjection(String command) {
        for (PatternEntry entry : INJECTION_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return new SecurityCheck(true, "Shell injection detected: " + entry.label);
            }
        }
        return new SecurityCheck(false);
    }

    /**
     * Block commands that Python's local sys_operation refuses for safety.
     *
     * @param command Shell command string
     * @return SecurityCheck with blocked=true if a dangerous pattern is found
     */
    public static SecurityCheck checkCommandSafety(String command) {
        for (PatternEntry entry : DANGEROUS_COMMAND_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return new SecurityCheck(true, "command rejected for safety: " + entry.label);
            }
        }
        return new SecurityCheck(false);
    }

    /**
     * Return a human-readable warning if the command looks destructive.
     *
     * <p>This is purely informational — it does not block execution.
     *
     * @param command Shell command string
     * @return Warning string if destructive pattern matched, null otherwise
     */
    public static String getDestructiveWarning(String command) {
        for (PatternEntry entry : DESTRUCTIVE_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return entry.warning;
            }
        }
        return null;
    }

    private static final class PatternEntry {
        final Pattern pattern;
        final String label;
        final String warning;

        PatternEntry(Pattern pattern, String label) {
            this.pattern = pattern;
            this.label = label;
            this.warning = label;
        }
    }
}
