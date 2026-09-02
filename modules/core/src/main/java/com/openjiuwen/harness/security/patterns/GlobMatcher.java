/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.patterns;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Simple glob matcher for {@code file_guard} path rules.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.file_guard._match_glob}. Supports
 * {@code **} (crosses {@code /}), {@code *} (does not cross {@code /}), and {@code ?}.
 *
 * @since 0.1.15
 */
public final class GlobMatcher {
    private GlobMatcher() {
    }

    /**
     * Match a posix path against a glob pattern.
     *
     * @param pattern   glob pattern
     * @param pathPosix posix-normalized path
     * @return whether the path matches the glob pattern
     * @since 0.1.15
     */
    public static boolean match(String pattern, String pathPosix) {
        if (pattern == null || pathPosix == null) {
            return false;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            if (pattern.startsWith("**/", i)) {
                out.append("(?:.*/)?");
                i += 3;
            } else if (pattern.startsWith("**", i)) {
                out.append(".*");
                i += 2;
            } else if (pattern.charAt(i) == '*') {
                out.append("[^/]*");
                i += 1;
            } else if (pattern.charAt(i) == '?') {
                out.append("[^/]");
                i += 1;
            } else {
                out.append(Pattern.quote(String.valueOf(pattern.charAt(i))));
                i += 1;
            }
        }
        try {
            return Pattern.compile(out.toString()).matcher(pathPosix).matches();
        } catch (PatternSyntaxException ex) {
            return false;
        }
    }
}
