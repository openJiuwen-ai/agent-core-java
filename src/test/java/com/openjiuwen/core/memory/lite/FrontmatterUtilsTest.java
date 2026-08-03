/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for coding-memory conflict handling and frontmatter utilities.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/core/memory/lite/test_conflict_resolution.py}.</p>
 *
 * <p>Mirrors Python's {@code WriteMode}, {@code WriteResult}, and frontmatter helpers in
 * {@code openjiuwen/core/memory/lite/conflict_types.py} and
 * {@code openjiuwen/core/memory/lite/frontmatter.py}.</p>
 */
class FrontmatterUtilsTest {

    @Test
    void writeModeValues() {
        assertEquals("create", WriteMode.CREATE.value());
        assertEquals("append", WriteMode.APPEND.value());
        assertEquals("skip", WriteMode.SKIP.value());
    }

    @Test
    void writeResultBasic() {
        WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.CREATE,
                false, List.of(), null, null, null);

        assertTrue(result.isSuccess());
        assertEquals("/test/path.md", result.getPath());
        assertEquals(WriteMode.CREATE, result.getMode());
        assertFalse(result.isConflictDetected());
        assertEquals(List.of(), result.getConflictingFiles());
        assertNull(result.getNote());
        assertNull(result.getError());
    }

    @Test
    void writeResultWithConflict() {
        WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.CREATE,
                true, List.of("old1.md", "old2.md"), "Conflicts detected", null, null);

        assertTrue(result.isConflictDetected());
        assertEquals(List.of("old1.md", "old2.md"), result.getConflictingFiles());
        assertEquals("Conflicts detected", result.getNote());
    }

    @Test
    void writeResultToDictCreate() {
        WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.CREATE,
                false, List.of(), null, null, null);

        assertEquals(Map.of(
                "success", true,
                "path", "/test/path.md",
                "mode", "create"
        ), result.toDict());
    }

    @Test
    void writeResultToDictWithConflict() {
        WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.APPEND,
                true, List.of("old.md"), "Has conflicts", null, null);

        Map<String, Object> data = result.toDict();

        assertEquals(true, data.get("success"));
        assertEquals("/test/path.md", data.get("path"));
        assertEquals("append", data.get("mode"));
        assertEquals(true, data.get("conflict_detected"));
        assertEquals(List.of("old.md"), data.get("conflicting_files"));
        assertEquals("Has conflicts", data.get("note"));
    }

    @Test
    void writeResultToDictSkip() {
        WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.SKIP,
                false, List.of(), "Content is redundant", null, null);

        Map<String, Object> data = result.toDict();

        assertEquals("skip", data.get("mode"));
        assertEquals("Content is redundant", data.get("note"));
    }

    @Test
    void writeResultToDictWithError() {
        WriteResult result = new WriteResult(false, "/test/path.md", WriteMode.CREATE,
                false, List.of(), null, "Invalid frontmatter", null);

        Map<String, Object> data = result.toDict();

        assertEquals(false, data.get("success"));
        assertEquals("Invalid frontmatter", data.get("error"));
    }

    @Test
    void enrichFrontmatterCreate() {
        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("name", "Test Memory");
        frontmatter.put("description", "Test description");
        frontmatter.put("type", "user");

        Map<String, String> result = FrontmatterUtils.enrichFrontmatter(frontmatter, false);

        assertTrue(result.containsKey("created_at"));
        assertTrue(result.containsKey("updated_at"));
        assertEquals(result.get("created_at"), result.get("updated_at"));
        assertEquals("Test Memory", result.get("name"));
        assertEquals("Test description", result.get("description"));
        assertEquals("user", result.get("type"));
    }

    @Test
    void enrichFrontmatterCreatePreservesExistingCreatedAt() {
        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("name", "Test Memory");
        frontmatter.put("description", "Test description");
        frontmatter.put("type", "user");
        frontmatter.put("created_at", "2026-01-01");

        Map<String, String> result = FrontmatterUtils.enrichFrontmatter(frontmatter, false);

        assertEquals("2026-01-01", result.get("created_at"));
        assertFalse("2026-01-01".equals(result.get("updated_at")));
    }

    @Test
    void enrichFrontmatterEdit() {
        Map<String, String> frontmatter = new LinkedHashMap<>();
        frontmatter.put("name", "Test Memory");
        frontmatter.put("description", "Test description");
        frontmatter.put("type", "user");
        frontmatter.put("created_at", "2026-01-01");

        Map<String, String> result = FrontmatterUtils.enrichFrontmatter(frontmatter, true);

        assertEquals("2026-01-01", result.get("created_at"));
        assertTrue(result.containsKey("updated_at"));
        assertFalse("2026-01-01".equals(result.get("updated_at")));
    }

    @Test
    void extractBodyWithFrontmatterViaRebuild() {
        String content = """
                ---
                name: Test Memory
                description: Test description
                type: user
                ---

                This is the body content.
                It can have multiple lines.
                """;

        String rebuilt = FrontmatterUtils.rebuildContentWithFrontmatter(content, Map.of("name", "Updated"));

        assertTrue(rebuilt.contains("This is the body content."));
        assertTrue(rebuilt.contains("It can have multiple lines."));
        assertFalse(rebuilt.substring(rebuilt.indexOf("---", 3) + 3).contains("description: Test description"));
    }

    @Test
    void extractBodyWithoutFrontmatterViaRebuild() {
        String content = "This is pure body content without frontmatter.";

        String rebuilt = FrontmatterUtils.rebuildContentWithFrontmatter(content, Map.of("name", "Updated"));

        assertTrue(rebuilt.endsWith(content));
    }

    @Test
    void extractBodyEmptyAfterFrontmatterViaRebuild() {
        String content = """
                ---
                name: Test Memory
                description: Test description
                type: user
                ---""";

        String rebuilt = FrontmatterUtils.rebuildContentWithFrontmatter(content, Map.of("name", "Updated"));

        assertEquals("""
                ---
                name: Updated
                ---""", rebuilt);
    }

    @Test
    void extractBodyWhitespaceHandlingViaRebuild() {
        String content = """
                ---
                name: Test
                ---

                  Trimmed content  """;

        String rebuilt = FrontmatterUtils.rebuildContentWithFrontmatter(content, Map.of("name", "Updated"));

        assertTrue(rebuilt.endsWith("Trimmed content"));
    }

    @Test
    void rebuildContentWithFrontmatter() {
        String originalContent = """
                ---
                name: Old Name
                description: Old description
                type: user
                ---

                Body content here.""";
        Map<String, String> newFrontmatter = new LinkedHashMap<>();
        newFrontmatter.put("name", "New Name");
        newFrontmatter.put("description", "New description");
        newFrontmatter.put("type", "feedback");
        newFrontmatter.put("updated_at", "2026-04-14");

        String result = FrontmatterUtils.rebuildContentWithFrontmatter(originalContent, newFrontmatter);

        assertTrue(result.contains("name: New Name"));
        assertTrue(result.contains("description: New description"));
        assertTrue(result.contains("type: feedback"));
        assertTrue(result.contains("updated_at: 2026-04-14"));
        assertFalse(result.contains("Old Name"));
        assertFalse(result.contains("Old description"));
        assertTrue(result.contains("Body content here."));
    }

    @Test
    void rebuildContentPreservesBodyFormatting() {
        String originalContent = """
                ---
                name: Test
                ---

                # Heading

                - List item 1
                - List item 2

                Paragraph with **bold** text.""";

        String result = FrontmatterUtils.rebuildContentWithFrontmatter(originalContent, Map.of("name", "Updated"));

        assertTrue(result.contains("# Heading"));
        assertTrue(result.contains("- List item 1"));
        assertTrue(result.contains("**bold**"));
    }

    @Test
    void rebuildContentEmptyBody() {
        String originalContent = """
                ---
                name: Test
                ---""";

        String result = FrontmatterUtils.rebuildContentWithFrontmatter(
                originalContent,
                linkedMap("name", "Updated", "updated_at", "2026-04-14"));

        assertEquals("""
                ---
                name: Updated
                updated_at: 2026-04-14
                ---""", result);
    }

    @Test
    void fullWorkflowCreate() {
        String content = """
                ---
                name: User Preference
                description: User likes dark mode
                type: user
                ---

                User prefers dark mode for all applications.""";

        Map<String, String> frontmatter = FrontmatterUtils.parseFrontmatter(content);
        assertNotNull(frontmatter);

        FrontmatterUtils.ValidationResult validation = FrontmatterUtils.validateFrontmatter(frontmatter);
        assertTrue(validation.valid());
        assertEquals("", validation.message());

        frontmatter = FrontmatterUtils.enrichFrontmatter(frontmatter, false);
        assertTrue(frontmatter.containsKey("created_at"));
        assertTrue(frontmatter.containsKey("updated_at"));

        String newContent = FrontmatterUtils.rebuildContentWithFrontmatter(content, frontmatter);

        assertTrue(newContent.contains("created_at:"));
        assertTrue(newContent.contains("updated_at:"));
        assertTrue(newContent.contains("User prefers dark mode"));
    }

    @Test
    void fullWorkflowEdit() {
        String content = """
                ---
                name: User Preference
                description: User likes dark mode
                type: user
                created_at: 2026-01-01
                updated_at: 2026-01-01
                ---

                User prefers dark mode for all applications.""";

        Map<String, String> frontmatter = FrontmatterUtils.parseFrontmatter(content);
        frontmatter = FrontmatterUtils.enrichFrontmatter(frontmatter, true);

        assertEquals("2026-01-01", frontmatter.get("created_at"));
        assertFalse("2026-01-01".equals(frontmatter.get("updated_at")));

        String newContent = FrontmatterUtils.rebuildContentWithFrontmatter(content, frontmatter);
        String today = LocalDate.now().toString();

        assertTrue(newContent.contains("created_at: 2026-01-01"));
        assertTrue(newContent.contains("updated_at: " + today));
    }

    private static Map<String, String> linkedMap(String key1, String value1, String key2, String value2) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(key1, value1);
        result.put(key2, value2);
        return result;
    }
}
