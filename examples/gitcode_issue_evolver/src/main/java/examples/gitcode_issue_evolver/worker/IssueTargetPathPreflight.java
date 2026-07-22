/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_issue_evolver.worker;

import examples.gitcode_issue_evolver.gitcode.GitCodeIssue;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts explicit in-scope repository files and verifies that they exist in the pristine baseline Worktree.
 *
 * @since 0.1.12
 */
final class IssueTargetPathPreflight {
    private static final Pattern EXPLICIT_PATH = Pattern.compile(
            "(?i)(?<![A-Za-z0-9._-])((?:src/(?:main|test)/"
                    + "[^\\s`'\"<>|),;:]*\\.[A-Za-z0-9_-]+"
                    + "|pom\\.xml|README[^/\\s`'\"<>|),;:]*\\.md))");
    private static final Pattern NEGATION_BEFORE_PATH = Pattern.compile(
            "(?i)(?:(?:do\\s+not|don't|must\\s+not|never)\\s+"
                    + "(?:modify|edit|change|touch)|without\\s+"
                    + "(?:modifying|editing|changing|touching)"
                    + "|(?:不(?:要|得|需)?|无需|禁止|严禁)\\s*"
                    + "(?:修改|更改|编辑|改动|变更|触碰))");
    private static final Pattern NEGATION_AFTER_PATH = Pattern.compile(
            "(?i)^[\\s]*(?:(?:must|should)\\s+not\\s+be\\s+"
                    + "(?:modified|edited|changed|touched)"
                    + "|(?:不得|不应|不要|无需|禁止|严禁)\\s*(?:被)?"
                    + "(?:修改|更改|编辑|改动|变更|触碰))");
    private static final String CLAUSE_BOUNDARIES = "\r\n,，。！？;；";

    private IssueTargetPathPreflight() {
    }

    /**
     * Extract normalized repository-relative file paths from untrusted Issue fields.
     *
     * @param issue untrusted Issue data
     * @return deduplicated safe paths in encounter order
     */
    static List<String> extractExplicitPaths(GitCodeIssue issue) {
        GitCodeIssue requiredIssue = Objects.requireNonNull(issue, "issue must not be null");
        Set<String> paths = new LinkedHashSet<>();
        List<String> sections = new ArrayList<>();
        sections.add(requiredIssue.title());
        sections.add(requiredIssue.description());
        sections.addAll(requiredIssue.comments());
        for (String section : sections) {
            extractSection(section, paths);
        }
        return List.copyOf(paths);
    }

    /**
     * Verify explicit Issue targets against the pristine sparse Worktree before any Agent is created.
     *
     * @param issue untrusted Issue data
     * @param worktreeRoot prepared baseline Worktree root
     * @return immutable target validation
     */
    static Validation validate(GitCodeIssue issue, Path worktreeRoot) {
        Path root = Objects.requireNonNull(worktreeRoot, "worktreeRoot must not be null")
                .toAbsolutePath()
                .normalize();
        List<String> explicitPaths = extractExplicitPaths(issue);
        List<String> missingPaths = new ArrayList<>();
        for (String path : explicitPaths) {
            Path target = root.resolve(path).normalize();
            if (!target.startsWith(root)
                    || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                missingPaths.add(path);
            }
        }
        return new Validation(missingPaths.isEmpty(), explicitPaths, missingPaths);
    }

    private static void extractSection(String text, Set<String> paths) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalizedText = text.replace('\\', '/');
        Matcher matcher = EXPLICIT_PATH.matcher(normalizedText);
        while (matcher.find()) {
            if (isNegated(normalizedText, matcher.start(1), matcher.end(1))) {
                continue;
            }
            String normalized = normalizeSafePath(matcher.group(1));
            if (!normalized.isBlank()) {
                paths.add(normalized);
            }
        }
    }

    private static boolean isNegated(String text, int pathStart, int pathEnd) {
        int clauseStart = findClauseStart(text, pathStart);
        int clauseEnd = findClauseEnd(text, pathEnd);
        String beforePath = text.substring(clauseStart, pathStart);
        String afterPath = text.substring(pathEnd, clauseEnd);
        return NEGATION_BEFORE_PATH.matcher(beforePath).find()
                || NEGATION_AFTER_PATH.matcher(afterPath).find();
    }

    private static int findClauseStart(String text, int pathStart) {
        for (int index = pathStart - 1; index >= 0; index--) {
            if (isClauseBoundary(text, index)) {
                return index + 1;
            }
        }
        return 0;
    }

    private static int findClauseEnd(String text, int pathEnd) {
        for (int index = pathEnd; index < text.length(); index++) {
            if (isClauseBoundary(text, index)) {
                return index;
            }
        }
        return text.length();
    }

    private static boolean isClauseBoundary(String text, int index) {
        char character = text.charAt(index);
        if (CLAUSE_BOUNDARIES.indexOf(character) >= 0) {
            return true;
        }
        return character == '.'
                && (index + 1 == text.length() || Character.isWhitespace(text.charAt(index + 1)));
    }

    private static String normalizeSafePath(String value) {
        String normalized = value == null ? "" : value.strip().replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/")) {
            return "";
        }
        List<String> segments = List.of(normalized.split("/"));
        if (segments.contains("..") || segments.contains(".")) {
            return "";
        }
        Path path;
        try {
            path = Path.of(normalized).normalize();
        } catch (InvalidPathException ex) {
            return "";
        }
        if (path.isAbsolute()) {
            return "";
        }
        return path.toString().replace('\\', '/');
    }

    /**
     * Baseline target-path validation.
     *
     * @param available whether every explicit target exists as a regular file
     * @param explicitPaths all extracted safe paths
     * @param missingPaths paths absent from the pristine baseline
     */
    record Validation(boolean available, List<String> explicitPaths, List<String> missingPaths) {
        Validation {
            explicitPaths = List.copyOf(explicitPaths);
            missingPaths = List.copyOf(missingPaths);
        }
    }
}
