/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.agent;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Permanent denylist and dynamic R2-approved write-scope policy.
 *
 * @since 0.1.12
 */
public final class FeaturePathPolicy {
    private static final Set<String> DENIED_TOP_LEVEL = Set.of(
            ".git", ".ssh", ".gnupg", ".codex", ".claude", ".agents",
            ".github", ".gitcode", ".gitlab", ".gitee", ".idea");
    private static final Set<String> DENIED_FILE_NAMES = Set.of(
            ".env", "apiconfig.json", "gitcode-config.json", "git-identity.inc",
            "settings.xml", "credentials", "credentials.json");
    private static final Set<String> DENIED_WRITE_FILE_NAMES = Set.of(
            "pom.xml", "mvnw", "mvnw.cmd", ".gitmodules", ".gitlab-ci.yml",
            ".gitcode-ci.yml", "jenkinsfile", "codeowners");
    private static final Set<String> DENIED_WRITE_DIRECTORIES = Set.of(".mvn");
    private static final List<String> DENIED_PREFIXES = List.of(
            "resources/skills/", "examples/gitcode_issue_evolver/",
            "examples/gitcode_feature_evolver/", "deploy/", "scripts/deploy/");

    private FeaturePathPolicy() {
    }

    /** Normalize a repository-relative path without resolving it on disk. */
    public static String normalize(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            throw new IllegalArgumentException("repository path is required");
        }
        String slashPath = supplied.replace('\\', '/');
        while (slashPath.startsWith("./")) {
            slashPath = slashPath.substring(2);
        }
        try {
            Path path = Path.of(slashPath);
            if (path.isAbsolute()) {
                throw new IllegalArgumentException("absolute repository paths are not allowed");
            }
            for (Path segment : path) {
                if (".".equals(segment.toString()) || "..".equals(segment.toString())) {
                    throw new IllegalArgumentException("repository path traversal is not allowed");
                }
            }
            String normalized = path.normalize().toString().replace('\\', '/');
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("repository path is required");
            }
            return normalized;
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("repository path is invalid", ex);
        }
    }

    /** Report whether a path is permanently unavailable for reads. */
    public static boolean isSensitiveRead(String supplied) {
        String path = normalize(supplied);
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        return containsDirectory(lower, DENIED_TOP_LEVEL) || DENIED_FILE_NAMES.contains(name);
    }

    /** Report whether a path is permanently unavailable for writes. */
    public static boolean isDeniedWrite(String supplied) {
        String path = normalize(supplied);
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.contains("/") ? lower.substring(lower.lastIndexOf('/') + 1) : lower;
        if (isSensitiveRead(path) || "agents.md".equals(name)
                || DENIED_WRITE_FILE_NAMES.contains(name)
                || containsDirectory(lower, DENIED_WRITE_DIRECTORIES)) {
            return true;
        }
        for (String prefix : DENIED_PREFIXES) {
            if (containsDirectoryPath(lower, prefix)) {
                return true;
            }
        }
        return lower.endsWith(".key") || lower.endsWith(".pem")
                || lower.endsWith(".p12") || lower.endsWith(".jks");
    }

    private static boolean containsDirectory(String path, Set<String> deniedDirectories) {
        for (String segment : path.split("/")) {
            if (deniedDirectories.contains(segment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsDirectoryPath(String path, String deniedPrefix) {
        String denied = deniedPrefix.endsWith("/")
                ? deniedPrefix.substring(0, deniedPrefix.length() - 1) : deniedPrefix;
        return path.equals(denied) || path.startsWith(denied + "/")
                || path.contains("/" + denied + "/") || path.endsWith("/" + denied);
    }

    /**
     * Normalize trusted exact-file or trailing-slash directory scopes.
     *
     * @param scopes controller-approved paths
     * @return immutable normalized scope list
     */
    public static List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            boolean directory = scope != null && (scope.endsWith("/") || scope.endsWith("\\"));
            String path = normalize(scope);
            if (isDeniedWrite(path)) {
                throw new IllegalArgumentException("approved scope intersects the permanent denylist");
            }
            normalized.add(directory ? path + "/" : path);
        }
        return List.copyOf(normalized);
    }

    /** Report whether a path is inside one normalized dynamic write scope. */
    public static boolean isAllowedWrite(String supplied, List<String> normalizedScopes) {
        String path = normalize(supplied);
        if (isDeniedWrite(path)) {
            return false;
        }
        for (String scope : normalizedScopes) {
            if (scope.endsWith("/") ? path.startsWith(scope) : path.equals(scope)) {
                return true;
            }
        }
        return false;
    }

    /** Return every dirty path that violates the dynamic and permanent policies. */
    public static List<String> violations(List<String> dirtyPaths, List<String> scopes) {
        List<String> normalizedScopes = normalizeScopes(scopes);
        List<String> violations = new ArrayList<>();
        for (String dirty : dirtyPaths == null ? List.<String>of() : dirtyPaths) {
            if (!isAllowedWrite(dirty, normalizedScopes)) {
                violations.add(normalize(dirty));
            }
        }
        return List.copyOf(violations);
    }
}
