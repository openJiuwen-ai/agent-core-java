/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.tiered;

import com.openjiuwen.harness.security.patterns.PathMatcher;
import com.openjiuwen.harness.security.patterns.WildcardMatcher;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pattern matching for tiered parameter rules.
 *
 * <p>Mirrors Python {@code tiered_policy._shell_pattern_matches} and {@code _path_pattern_matches}.
 * Patterns prefixed with {@code re:} are regular expressions (search semantics); otherwise shell
 * patterns fall back to wildcard or exact match, and path patterns use {@link PathMatcher}.
 *
 * @since 0.1.15
 */
public final class RuleMatcher {
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private RuleMatcher() {
    }

    /**
     * Match a shell command against a pattern.
     *
     * @param pattern rule pattern
     * @param command command text
     * @return whether the pattern matches
     * @since 0.1.15
     */
    public static boolean shellMatches(String pattern, String command) {
        if (pattern == null || pattern.isBlank() || command == null || command.isEmpty()) {
            return false;
        }
        String p = pattern.trim();
        if (p.toLowerCase(Locale.ROOT).startsWith("re:")) {
            return regexMatches(p.substring(3).trim(), command);
        }
        if (p.indexOf('*') >= 0 || p.indexOf('?') >= 0 || p.indexOf('[') >= 0) {
            return WildcardMatcher.match(p, command);
        }
        return command.equals(p);
    }

    /**
     * Match a path value against a pattern.
     *
     * @param pattern rule pattern
     * @param value   path value
     * @return whether the pattern matches
     * @since 0.1.15
     */
    public static boolean pathMatches(String pattern, String value) {
        if (pattern == null || pattern.isBlank() || value == null || value.isEmpty()) {
            return false;
        }
        String p = pattern.trim();
        if (p.toLowerCase(Locale.ROOT).startsWith("re:")) {
            String expr = p.substring(3).trim();
            int flags = IS_WINDOWS ? Pattern.CASE_INSENSITIVE : 0;
            try {
                return Pattern.compile(expr, flags).matcher(value.replace("\\", "/")).find();
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }
        return PathMatcher.matchPath(p, value);
    }

    private static boolean regexMatches(String expr, String command) {
        int flags = IS_WINDOWS ? Pattern.CASE_INSENSITIVE : 0;
        String normalized = command.replace("\\", "/");
        Pattern compiled;
        try {
            compiled = Pattern.compile(expr, flags);
        } catch (PatternSyntaxException ex) {
            return regexSplitFallback(expr, flags, command, normalized);
        }
        return compiled.matcher(command).find() || compiled.matcher(normalized).find();
    }

    private static boolean regexSplitFallback(String expr, int flags, String command, String normalized) {
        if (!expr.contains("|")) {
            return false;
        }
        for (String part : expr.split("\\|")) {
            String sub = part.trim();
            if (sub.isEmpty()) {
                continue;
            }
            if (find(sub, flags, command) || find(sub, flags, normalized)) {
                return true;
            }
        }
        return false;
    }

    private static boolean find(String expr, int flags, String target) {
        try {
            return Pattern.compile(expr, flags).matcher(target).find();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }
}
