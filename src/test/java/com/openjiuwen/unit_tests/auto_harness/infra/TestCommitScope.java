/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for commit scope helpers.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.auto_harness.infra.test_commit_scope}.
 */
@DisplayName("Commit Scope Tests")
class TestCommitScope {

    // Helper methods that mirror Python's commit_scope module

    /**
     * Derives test file patterns from source files.
     * Mirrors Python's derive_test_files.
     */
    static List<String> deriveTestFiles(List<String> sourceFiles) {
        List<String> result = new java.util.ArrayList<>();
        for (String file : sourceFiles) {
            // Skip __init__.py and files already in tests/
            if (file.contains("__init__.py") || file.contains("tests/")) {
                continue;
            }
            // Extract module name
            String moduleName = extractModuleName(file);
            if (moduleName != null && !moduleName.isEmpty()) {
                result.add("tests/unit_tests/**/test_" + moduleName + ".py");
                result.add("tests/system_tests/**/test_" + moduleName + ".py");
            }
        }
        return result;
    }

    /**
     * Checks if a test file is derived from source files.
     * Mirrors Python's is_derived_test_file.
     */
    static boolean isDerivedTestFile(List<String> sourceFiles, String testFile) {
        String testModuleName = extractTestModuleName(testFile);
        for (String source : sourceFiles) {
            String sourceModuleName = extractModuleName(source);
            if (sourceModuleName != null && sourceModuleName.equals(testModuleName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts verify-related files from CI result.
     * Mirrors Python's extract_verify_related_files.
     */
    static List<String> extractVerifyRelatedFiles(java.util.Map<String, Object> ciResult) {
        List<String> files = new java.util.ArrayList<>();
        Object errors = ciResult.get("errors");
        if (errors instanceof String) {
            String errorStr = (String) errors;
            // Parse FAILED tests/unit_tests/.../test_xxx.py::test_name
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "tests/unit_tests/[^\\s]+\\.py"
            );
            java.util.regex.Matcher matcher = pattern.matcher(errorStr);
            while (matcher.find()) {
                files.add(matcher.group());
            }
        }
        return files;
    }

    /**
     * Derives legacy related test files.
     * Mirrors Python's derive_legacy_related_test_files.
     */
    static List<String> deriveLegacyRelatedTestFiles(List<String> edited, List<String> referenced) {
        List<String> result = new java.util.ArrayList<>();
        for (String file : edited) {
            if (referenced.contains(file)) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Checks if file is allowed documentation file.
     * Mirrors Python's is_allowed_documentation_file.
     */
    static boolean isAllowedDocumentationFile(String filePath) {
        // Only docs/en/*.md or docs/zh/*.md are allowed
        return filePath.startsWith("docs/en/") || filePath.startsWith("docs/zh/");
    }

    private static String extractModuleName(String filePath) {
        // Extract module name from path like "openjiuwen/auto_harness/schema.py"
        int lastSlash = filePath.lastIndexOf('/');
        int dotIndex = filePath.lastIndexOf(".py");
        if (lastSlash >= 0 && dotIndex > lastSlash) {
            return filePath.substring(lastSlash + 1, dotIndex);
        }
        return null;
    }

    private static String extractTestModuleName(String testFile) {
        // Extract module name from test file like "tests/unit_tests/auto_harness/test_schema.py"
        int lastSlash = testFile.lastIndexOf('/');
        int dotIndex = testFile.lastIndexOf(".py");
        if (lastSlash >= 0 && dotIndex > lastSlash) {
            String fileName = testFile.substring(lastSlash + 1, dotIndex);
            // Remove "test_" prefix
            if (fileName.startsWith("test_")) {
                return fileName.substring(5);
            }
        }
        return null;
    }

    @Nested
    @DisplayName("Derive Test Files Tests")
    class TestDeriveTestFiles {

        @Test
        @DisplayName("derive test files for python module")
        void testDeriveTestFilesForPythonModule() {
            List<String> result = deriveTestFiles(
                Arrays.asList("openjiuwen/auto_harness/schema.py")
            );
            assertEquals(Arrays.asList(
                "tests/unit_tests/**/test_schema.py",
                "tests/system_tests/**/test_schema.py"
            ), result);
        }

        @Test
        @DisplayName("derive test files skips init and tests")
        void testDeriveTestFilesSkipsInitAndTests() {
            List<String> result = deriveTestFiles(
                Arrays.asList(
                    "openjiuwen/auto_harness/__init__.py",
                    "tests/unit_tests/auto_harness/test_schema.py"
                )
            );
            assertEquals(Collections.emptyList(), result);
        }
    }

    @Nested
    @DisplayName("Is Derived Test File Tests")
    class TestIsDerivedTestFile {

        @Test
        @DisplayName("is derived test file matches same basename")
        void testIsDerivedTestFileMatchesSameBasename() {
            assertTrue(isDerivedTestFile(
                Arrays.asList("openjiuwen/auto_harness/schema.py"),
                "tests/unit_tests/auto_harness/test_schema.py"
            ));
            assertFalse(isDerivedTestFile(
                Arrays.asList("openjiuwen/auto_harness/schema.py"),
                "tests/unit_tests/auto_harness/test_other.py"
            ));
        }
    }

    @Nested
    @DisplayName("Extract Verify Related Files Tests")
    class TestExtractVerifyRelatedFiles {

        @Test
        @DisplayName("extract verify related files finds test paths")
        void testExtractVerifyRelatedFilesFindsTestPaths() {
            java.util.Map<String, Object> ciResult = new java.util.HashMap<>();
            ciResult.put("errors", "FAILED tests/unit_tests/auto_harness/test_schema.py::test_x");
            ciResult.put("gates", Collections.emptyList());

            List<String> result = extractVerifyRelatedFiles(ciResult);
            assertEquals(Arrays.asList("tests/unit_tests/auto_harness/test_schema.py"), result);
        }
    }

    @Nested
    @DisplayName("Derive Legacy Related Test Files Tests")
    class TestDeriveLegacyRelatedTestFiles {

        @Test
        @DisplayName("derive legacy related test files requires edit and reference")
        void testDeriveLegacyRelatedTestFilesRequiresEditAndReference() {
            List<String> result = deriveLegacyRelatedTestFiles(
                Arrays.asList(
                    "tests/unit_tests/auto_harness/test_schema.py",
                    "tests/unit_tests/auto_harness/test_other.py"
                ),
                Arrays.asList(
                    "tests/unit_tests/auto_harness/test_schema.py"
                )
            );
            assertEquals(Arrays.asList("tests/unit_tests/auto_harness/test_schema.py"), result);
        }
    }

    @Nested
    @DisplayName("Is Allowed Documentation File Tests")
    class TestIsAllowedDocumentationFile {

        @Test
        @DisplayName("is allowed documentation file limits docs layout")
        void testIsAllowedDocumentationFileLimitsDocsLayout() {
            assertTrue(isAllowedDocumentationFile("docs/en/guide.md"));
            assertFalse(isAllowedDocumentationFile("docs/auto-harness-agent-design.md"));
            assertFalse(isAllowedDocumentationFile("README.md"));
        }
    }
}
