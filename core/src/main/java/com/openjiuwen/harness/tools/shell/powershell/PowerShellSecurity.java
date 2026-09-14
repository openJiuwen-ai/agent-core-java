/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.shell.powershell;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tool-layer security for PowerShell commands.
 *
 * <p>Mirrors Python's {@code PowerShellSecurity} in
 * {@code openjiuwen/harness/tools/shell/powershell/_security.py}.
 */
public final class PowerShellSecurity {

    private static final List<PatternLabel> INJECTION_PATTERNS = createInjectionPatterns();
    private static final List<PatternLabel> DESTRUCTIVE_PATTERNS = createDestructivePatterns();

    private PowerShellSecurity() {
    }

    public static SecurityCheck checkInjection(String command) {
        if (command == null || command.isEmpty()) {
            return new SecurityCheck(false);
        }
        for (PatternLabel entry : INJECTION_PATTERNS) {
            if (entry.pattern.matcher(command).find()) {
                return new SecurityCheck(true, "PowerShell injection detected: " + entry.label);
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

    private static List<PatternLabel> createInjectionPatterns() {
        List<PatternLabel> patterns = new ArrayList<>();
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\b(?:invoke-expression|iex)\\b"),
                "Invoke-Expression"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\b(?:powershell|powershell\\.exe|pwsh|pwsh\\.exe)\\b[^\\n]*-encodedcommand\\b"),
                "nested encoded command"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(^|[\\s;(])&\\s*(?:\\(|\\$)"),
                "dynamic call operator"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\[scriptblock\\]::create\\s*\\("),
                "dynamic ScriptBlock creation"
        ));
        return List.copyOf(patterns);
    }

    private static List<PatternLabel> createDestructivePatterns() {
        List<PatternLabel> patterns = new ArrayList<>();
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\bremove-item\\b[^\\n]*-(?:recurse|force)\\b"),
                "May permanently remove files or directories"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\bclear-content\\b"),
                "May remove file contents"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\bset-content\\b"),
                "May overwrite file contents"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\brename-item\\b"),
                "May rename or replace files"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("(?i)\\bmove-item\\b"),
                "May move or overwrite files"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+reset\\s+--hard\\b"),
                "May discard uncommitted changes"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+push\\b[^\\n]*(?:--force|-f)\\b"),
                "May overwrite remote history"
        ));
        patterns.add(new PatternLabel(
                Pattern.compile("\\bgit\\s+commit\\s+--amend\\b"),
                "May rewrite the last commit"
        ));
        return List.copyOf(patterns);
    }

    private record PatternLabel(Pattern pattern, String label) {
    }

    /**
     * Result of a tool-layer security check.
     *
     * <p>Mirrors Python's {@code SecurityCheck} in
     * {@code openjiuwen/harness/tools/shell/powershell/_security.py}.
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
