/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.CommitScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Commit scope helper parity tests.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/auto_harness/infra/test_commit_scope.py}.</p>
 */
class CommitScopeTest {

    @Test
    void deriveTestFilesForPythonModule() {
        List<String> result = CommitScope.deriveTestFiles(List.of(
                "openjiuwen/auto_harness/schema.py"
        ));

        assertEquals(List.of(
                "tests/unit_tests/**/test_schema.py",
                "tests/system_tests/**/test_schema.py"
        ), result);
    }

    @Test
    void deriveTestFilesSkipsInitAndTests() {
        List<String> result = CommitScope.deriveTestFiles(List.of(
                "openjiuwen/auto_harness/__init__.py",
                "tests/unit_tests/auto_harness/test_schema.py"
        ));

        assertEquals(List.of(), result);
    }

    @Test
    void isDerivedTestFileMatchesSameBasename() {
        assertTrue(CommitScope.isDerivedTestFile(
                List.of("openjiuwen/auto_harness/schema.py"),
                "tests/unit_tests/auto_harness/test_schema.py"
        ));
        assertFalse(CommitScope.isDerivedTestFile(
                List.of("openjiuwen/auto_harness/schema.py"),
                "tests/unit_tests/auto_harness/test_other.py"
        ));
    }

    @Test
    void extractVerifyRelatedFilesFindsReferencedTests() {
        Map<String, Object> ciResult = Map.of(
                "errors", "FAILED tests/unit_tests/auto_harness/test_schema.py::test_x",
                "gates", List.of()
        );

        assertEquals(List.of(
                "tests/unit_tests/auto_harness/test_schema.py"
        ), CommitScope.extractVerifyRelatedFiles(ciResult));
    }

    @Test
    void deriveLegacyRelatedTestFilesRequiresEditAndReference() {
        assertEquals(List.of("tests/unit_tests/auto_harness/test_schema.py"), CommitScope.deriveLegacyRelatedTestFiles(
                List.of("tests/unit_tests/auto_harness/test_schema.py", "tests/unit_tests/auto_harness/test_other.py"),
                List.of("tests/unit_tests/auto_harness/test_schema.py")
        ));
    }

    @Test
    void isAllowedDocumentationFileLimitsDocsLayout() {
        assertTrue(CommitScope.isAllowedDocumentationFile("docs/en/guide.md"));
        assertFalse(CommitScope.isAllowedDocumentationFile("docs/auto-harness-agent-design.md"));
        assertFalse(CommitScope.isAllowedDocumentationFile("README.md"));
    }
}
