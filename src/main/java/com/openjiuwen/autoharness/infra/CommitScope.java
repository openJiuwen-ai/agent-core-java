/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.infra;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CommitScope.
 * 
 * @since 0.1.7
 */
public final class CommitScope {
    private static final Pattern TEST_FILE_RE = Pattern.compile("(tests/(?:unit_tests|system_tests)/[^\\s:'\"]+\\.py)");

    /**
     * List.of.
     * 
     * @since 0.1.7
     */
    private static final List<String> ALLOWED_EDIT_PREFIXES =
        List.of("openjiuwen/harness/", "openjiuwen/core/", "tests/", "examples/", "docs/en/", "docs/zh/");

    /**
     * CommitScope.
     * 
     * @since 0.1.7
     */
    private CommitScope() {
    }

    /**
     * isDocumentationFile.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static boolean isDocumentationFile(String path) {
        String normalized = normalizePath(path);
        return normalized.startsWith("docs/") && normalized.endsWith(".md");
    }

    /**
     * isAllowedDocumentationFile.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static boolean isAllowedDocumentationFile(String path) {
        return isDocumentationFile(path) && isAllowedRepoEditPath(path);
    }

    /**
     * deriveTestFiles.
     * 
     * @param taskFiles taskFiles
     * @return the result
     * @since 0.1.7
     */
    public static List<String> deriveTestFiles(List<String> taskFiles) {
        Set<String> derived = new LinkedHashSet<>();
        for (String path : taskFiles) {
            String normalized = normalizePath(path);
            if (!isNonTestSourceFile(normalized)) {
                continue;
            }
            String fileName = Path.of(normalized).getFileName().toString();
            int lastDot = fileName.lastIndexOf('.');
            String stem = lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
            String testName = "test_" + stem + ".py";
            derived.add("tests/unit_tests" + testName);
        }
        return new ArrayList<>(derived);
    }

    /**
     * isDerivedTestFile.
     * 
     * @param sourceFiles sourceFiles
     * @param candidate candidate
     * @return the result
     * @since 0.1.7
     */
    public static boolean isDerivedTestFile(List<String> sourceFiles, String candidate) {
        String normalizedCandidate = normalizePath(candidate);
        if (!normalizedCandidate.startsWith("tests/")) {
            return false;
        }
        String candidateName = Path.of(normalizedCandidate).getFileName().toString();
        if (!candidateName.startsWith("test_")) {
            return false;
        }

        for (String path : sourceFiles) {
            String normalized = normalizePath(path);
            if (!isNonTestSourceFile(normalized)) {
                continue;
            }
            String fileName = Path.of(normalized).getFileName().toString();
            int lastDot = fileName.lastIndexOf('.');
            String stem = lastDot >= 0 ? fileName.substring(0, lastDot) : fileName;
            if (candidateName.equals("test_" + stem + ".py")) {
                return true;
            }
        }
        return false;
    }

    /**
     * extractVerifyRelatedFiles.
     * 
     * @param ciResult ciResult
     * @param fixLogs fixLogs
     * @return the result
     * @since 0.1.7
     */
    public static List<String> extractVerifyRelatedFiles(CIGateResult ciResult, String fixLogs) {
        List<String> texts = new ArrayList<>();
        if (ciResult != null) {
            texts.add(ciResult.getErrors() == null ? "" : ciResult.getErrors());
            texts.addAll(ciResult.getGateOutputs());
        }
        if (fixLogs != null) {
            texts.add(fixLogs);
        }

        Set<String> files = new LinkedHashSet<>();
        for (String text : texts) {
            Matcher matcher = TEST_FILE_RE.matcher(text == null ? "" : text);
            while (matcher.find()) {
                files.add(matcher.group(1));
            }
        }
        return new ArrayList<>(files);
    }

    /**
     * deriveLegacyRelatedTestFiles.
     * 
     * @param editedFiles editedFiles
     * @param verifyRelatedFiles verifyRelatedFiles
     * @return the result
     * @since 0.1.7
     */
    public static List<String> deriveLegacyRelatedTestFiles(List<String> editedFiles, List<String> verifyRelatedFiles) {
        Set<String> verifySet = new LinkedHashSet<>();
        for (String path : verifyRelatedFiles) {
            String normalized = normalizePath(path);
            if (!normalized.isBlank()) {
                verifySet.add(normalized);
            }
        }

        Set<String> related = new LinkedHashSet<>();
        for (String path : editedFiles) {
            String normalized = normalizePath(path);
            if (normalized.startsWith("tests/") && verifySet.contains(normalized)) {
                related.add(normalized);
            }
        }
        return new ArrayList<>(related);
    }

    /**
     * isAllowedRepoEditPath.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    public static boolean isAllowedRepoEditPath(String path) {
        String normalized = normalizePath(path);
        for (String prefix : ALLOWED_EDIT_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.trim().replace('\\', '/');
    }

    /**
     * isNonTestSourceFile.
     * 
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static boolean isNonTestSourceFile(String path) {
        if (path.isBlank() || !path.endsWith(".py")) {
            return false;
        }
        if (path.startsWith("tests/")) {
            return false;
        }
        return !"__init__.py".equals(Path.of(path).getFileName().toString());
    }
}
