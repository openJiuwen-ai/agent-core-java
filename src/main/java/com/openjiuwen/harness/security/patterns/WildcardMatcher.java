/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.patterns;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Wildcard ({@code *} / {@code ?}) matcher backed by a restrictive character class.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.patterns.match_wildcard}. The
 * {@code *} and {@code ?} wildcards only match a restricted character set that excludes
 * shell metacharacters ({@code ; | & ` < > $} …), so a pattern such as {@code "git status *"}
 * cannot match {@code "git status; rm -rf /"}. A trailing {@code " *"} is made optional so
 * {@code "ls *"} matches both {@code "ls"} and {@code "ls -la"}.
 *
 * @since 0.1.15
 */
public final class WildcardMatcher {
    private static final String WILDCARD_CHAR_CLASS = "[-a-zA-Z0-9 ._/:\"']";
    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private WildcardMatcher() {
    }

    /**
     * Full-match {@code value} against a wildcard {@code pattern}.
     *
     * @param pattern trusted wildcard pattern from config
     * @param value   value from tool input
     * @return whether the value matches the pattern
     * @since 0.1.15
     */
    public static boolean match(String pattern, String value) {
        if (pattern == null || pattern.isEmpty() || value == null || value.isEmpty()) {
            return false;
        }
        String val = value.replace("\\", "/");
        String pat = pattern.replace("\\", "/");
        String escaped = escapeRegex(pat);
        escaped = escaped.replace("?", WILDCARD_CHAR_CLASS);
        if (escaped.endsWith(" *")) {
            escaped = escaped.substring(0, escaped.length() - 2)
                    + "(" + WILDCARD_CHAR_CLASS + "*)?";
        } else {
            escaped = escaped.replace("*", WILDCARD_CHAR_CLASS + "*");
        }
        try {
            int flags = IS_WINDOWS ? Pattern.CASE_INSENSITIVE : 0;
            return Pattern.compile(escaped, flags).matcher(val).matches();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }

    private static String escapeRegex(String pat) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pat.length(); i++) {
            char c = pat.charAt(i);
            if (isRegexMeta(c)) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean isRegexMeta(char c) {
        return c == '.' || c == '+' || c == '^' || c == '$' || c == '{' || c == '}'
                || c == '(' || c == ')' || c == '|' || c == '[' || c == ']' || c == '\\';
    }
}
