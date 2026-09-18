/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Commit scope helpers for auto-harness.
 * <p>
 * Mirrors Python's module helpers in
 * {@code openjiuwen/auto_harness/infra/commit_scope.py}.
 */
public final class CommitScope {

    private static final Pattern TEST_FILE_PATTERN = Pattern.compile("(tests/(?:unit_tests|system_tests)/[^\\s:'\"]+\\.py)");

    private CommitScope() {
    }

    public static boolean isDocumentationFile(String path) {
        String normalized = normalize(path);
        return normalized.startsWith("docs/") && normalized.endsWith(".md");
    }

    public static boolean isAllowedDocumentationFile(String path) {
        return isDocumentationFile(path) && EditScope.isAllowedRepoEditPath(path);
    }

    public static List<String> deriveTestFiles(List<String> taskFiles) {
        List<String> derived = new ArrayList<>();
        for (String path : taskFiles) {
            String normalized = normalize(path);
            if (normalized.isEmpty() || !normalized.endsWith(".py")) {
                continue;
            }
            if (normalized.startsWith("tests/")) {
                continue;
            }
            if ("__init__.py".equals(fileName(normalized))) {
                continue;
            }
            String stem = fileStem(normalized);
            String testName = "test_" + stem + ".py";
            derived.add("tests/unit_tests/**/" + testName);
            derived.add("tests/system_tests/**/" + testName);
        }
        return deduplicate(derived);
    }

    public static boolean isDerivedTestFile(List<String> sourceFiles, String candidate) {
        String normalizedCandidate = normalize(candidate);
        if (!normalizedCandidate.startsWith("tests/")) {
            return false;
        }
        String candidateName = fileName(normalizedCandidate);
        if (!candidateName.startsWith("test_")) {
            return false;
        }
        for (String path : sourceFiles) {
            String normalized = normalize(path);
            if (!isNonTestSourceFile(normalized)) {
                continue;
            }
            if (candidateName.equals("test_" + fileStem(normalized) + ".py")) {
                return true;
            }
        }
        return false;
    }

    public static List<String> extractVerifyRelatedFiles(Map<String, Object> ciResult) {
        return extractVerifyRelatedFiles(ciResult, null);
    }

    public static List<String> extractVerifyRelatedFiles(Map<String, Object> ciResult, String fixLogs) {
        List<String> texts = new ArrayList<>();
        if (ciResult != null) {
            texts.add(String.valueOf(ciResult.getOrDefault("errors", "")));
            Object gates = ciResult.get("gates");
            if (gates instanceof List<?> gateList) {
                for (Object gate : gateList) {
                    if (gate instanceof Map<?, ?> gateMap) {
                        Object output = gateMap.containsKey("output") ? gateMap.get("output") : "";
                        texts.add(String.valueOf(output));
                    }
                }
            }
        }
        if (fixLogs != null) {
            texts.add(fixLogs);
        }

        List<String> files = new ArrayList<>();
        for (String text : texts) {
            Matcher matcher = TEST_FILE_PATTERN.matcher(text);
            while (matcher.find()) {
                files.add(matcher.group(1));
            }
        }
        return deduplicate(files);
    }

    public static List<String> deriveLegacyRelatedTestFiles(List<String> editedFiles, List<String> verifyRelatedFiles) {
        Set<String> verifySet = new LinkedHashSet<>();
        for (String path : verifyRelatedFiles) {
            String normalized = normalize(path);
            if (!normalized.isEmpty()) {
                verifySet.add(normalized);
            }
        }
        List<String> related = new ArrayList<>();
        for (String path : editedFiles) {
            String normalized = normalize(path);
            if (normalized.startsWith("tests/") && verifySet.contains(normalized)) {
                related.add(normalized);
            }
        }
        return deduplicate(related);
    }

    private static boolean isNonTestSourceFile(String path) {
        if (path.isEmpty() || !path.endsWith(".py")) {
            return false;
        }
        if (path.startsWith("tests/")) {
            return false;
        }
        return !"__init__.py".equals(fileName(path));
    }

    private static List<String> deduplicate(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static String normalize(String path) {
        return path == null ? "" : path.trim().replace("\\", "/");
    }

    private static String fileName(String normalizedPath) {
        return Path.of(normalizedPath).getFileName().toString();
    }

    private static String fileStem(String normalizedPath) {
        String fileName = fileName(normalizedPath);
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }
}
