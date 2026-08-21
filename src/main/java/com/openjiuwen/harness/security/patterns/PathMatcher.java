/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security.patterns;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

/**
 * Path matcher with wildcard support and parent-directory walk.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.patterns.PathMatcher}: normalizes
 * separators, full-matches the pattern against the path, then walks parent directories so a
 * prefix-style pattern such as {@code "/etc"} matches {@code "/etc/hosts"}.
 * {@link #containsPath} mirrors {@code patterns.contains_path} for traversal-safe containment.
 *
 * @since 0.1.15
 */
public final class PathMatcher {
    private PathMatcher() {
    }

    /**
     * Match a path against a wildcard pattern, walking parent directories.
     *
     * @param pattern wildcard pattern
     * @param path    path value
     * @return whether any path level matches the pattern
     * @since 0.1.15
     */
    public static boolean matchPath(String pattern, String path) {
        if (pattern == null || pattern.isEmpty() || path == null || path.isEmpty()) {
            return false;
        }
        String normalizedPath = path.replace("\\", "/");
        String normalizedPattern = pattern.replace("\\", "/");
        if (WildcardMatcher.match(normalizedPattern, normalizedPath)) {
            return true;
        }
        Path pathObj = Paths.get(path);
        for (Path parent : parents(pathObj)) {
            String parentStr = parent.toString().replace("\\", "/");
            if (WildcardMatcher.match(normalizedPattern, parentStr)) {
                return true;
            }
            if (WildcardMatcher.match(normalizedPattern, parentStr + "/")) {
                return true;
            }
            if (WildcardMatcher.match(normalizedPattern, parentStr + "/*")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code child} is located under {@code parent} (traversal-safe).
     *
     * @param parent parent path
     * @param child  candidate child path
     * @return true when child is within parent (or equal)
     * @since 0.1.15
     */
    public static boolean containsPath(String parent, String child) {
        try {
            Path p = Paths.get(parent).toAbsolutePath().normalize();
            Path c = Paths.get(child).toAbsolutePath().normalize();
            String rel = p.relativize(c).toString().replace("\\", "/");
            return !rel.startsWith("..") && !"..".equals(rel);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static Iterable<Path> parents(Path path) {
        return () -> new Iterator<>() {
            private Path current = path.getParent();

            @Override
            public boolean hasNext() {
                return current != null;
            }

            @Override
            public Path next() {
                Path result = current;
                current = current.getParent();
                return result;
            }
        };
    }
}
