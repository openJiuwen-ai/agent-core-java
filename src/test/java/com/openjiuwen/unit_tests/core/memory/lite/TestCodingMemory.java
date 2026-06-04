/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryTools;
import com.openjiuwen.core.memory.lite.Frontmatter;
import com.openjiuwen.core.memory.lite.Frontmatter.ValidationResult;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Coding Memory — basic functionality.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.memory.lite.test_coding_memory}.
 */
class TestCodingMemory {

    @TempDir
    Path tempDir;

    private Path codingMemoryDir;

    @BeforeEach
    void setUpRuntime() throws Exception {
        codingMemoryDir = tempDir.resolve("coding_memory");
        Files.createDirectories(codingMemoryDir);
        CodingMemoryTools.bindCodingMemoryRuntime(new Workspace(tempDir.toString(), "cn"), null, codingMemoryDir.toString());
    }

    @AfterEach
    void tearDownRuntime() {
        CodingMemoryTools.clearCodingMemoryRuntime();
    }

    // ==================== TestFrontmatter ====================

    @Nested
    class TestFrontmatter {

        @Test
        @Tag("level0")
        void testParseFrontmatterSuccess() {
            /** Test normal frontmatter parsing */
            String content = "---\n" +
                    "name: Developer Role\n" +
                    "description: Senior Python developer\n" +
                    "type: user\n" +
                    "---\n\n" +
                    "User is a senior Python developer.";

            Map<String, String> result = Frontmatter.parseFrontmatter(content);
            assertNotNull(result);
            assertEquals("Developer Role", result.get("name"));
            assertEquals("Senior Python developer", result.get("description"));
            assertEquals("user", result.get("type"));
        }

        @Test
        @Tag("level0")
        void testParseFrontmatterNoFrontmatter() {
            /** Test content without frontmatter */
            String content = "Plain text content, no frontmatter";
            Map<String, String> result = Frontmatter.parseFrontmatter(content);
            assertNull(result);
        }

        @Test
        @Tag("level0")
        void testValidateFrontmatterSuccess() {
            /** Test validation passes */
            Map<String, String> fm = new HashMap<>();
            fm.put("name", "Test Memory");
            fm.put("description", "A test memory");
            fm.put("type", "user");

            ValidationResult result = Frontmatter.validateFrontmatter(fm);
            assertTrue(result.isValid());
            assertEquals("", result.getErrorMessage());
        }

        @Test
        @Tag("level0")
        void testValidateFrontmatterMissingField() {
            /** Test missing required field */
            Map<String, String> fm = new HashMap<>();
            fm.put("name", "Test Memory");
            fm.put("type", "user");

            ValidationResult result = Frontmatter.validateFrontmatter(fm);
            assertFalse(result.isValid());
            assertTrue(result.getErrorMessage().contains("description"));
        }

        @Test
        @Tag("level0")
        void testValidateFrontmatterInvalidType() {
            /** Test invalid type */
            Map<String, String> fm = new HashMap<>();
            fm.put("name", "Test Memory");
            fm.put("description", "A test memory");
            fm.put("type", "invalid_type");

            ValidationResult result = Frontmatter.validateFrontmatter(fm);
            assertFalse(result.isValid());
            assertTrue(result.getErrorMessage().contains("type"));
        }

        @Test
        @Tag("level0")
        void testValidTypesConstant() {
            /** Test VALID_TYPES contains all 4 types */
            assertTrue(Frontmatter.VALID_TYPES.contains("user"));
            assertTrue(Frontmatter.VALID_TYPES.contains("feedback"));
            assertTrue(Frontmatter.VALID_TYPES.contains("project"));
            assertTrue(Frontmatter.VALID_TYPES.contains("reference"));
            assertEquals(4, Frontmatter.VALID_TYPES.size());
        }

        @Test
        @Tag("level0")
        void testEnrichFrontmatter() {
            /** Test enrich frontmatter with timestamps */
            Map<String, String> fm = new HashMap<>();
            fm.put("name", "Test");
            fm.put("description", "Desc");
            fm.put("type", "user");

            Map<String, String> enriched = Frontmatter.enrichFrontmatter(fm, false);
            assertTrue(enriched.containsKey("created_at"));
            assertTrue(enriched.containsKey("updated_at"));
        }

        @Test
        @Tag("level0")
        void testEnrichFrontmatterEdit() {
            /** Test enrich frontmatter for edit (should not set created_at) */
            Map<String, String> fm = new HashMap<>();
            fm.put("name", "Test");
            fm.put("description", "Desc");
            fm.put("type", "user");
            fm.put("created_at", "2026-01-01");

            Map<String, String> enriched = Frontmatter.enrichFrontmatter(fm, true);
            assertEquals("2026-01-01", enriched.get("created_at")); // Should keep original
            assertTrue(enriched.containsKey("updated_at"));
        }

        @Test
        @Tag("level0")
        void testRebuildContentWithFrontmatter() {
            /** Test rebuild content with frontmatter */
            String content = "---\nname: Old\n---\nBody content";
            Map<String, String> fm = new HashMap<>();
            fm.put("name", "New Name");
            fm.put("type", "user");

            String rebuilt = Frontmatter.rebuildContentWithFrontmatter(content, fm);
            assertTrue(rebuilt.contains("New Name"));
            assertTrue(rebuilt.contains("---"));
        }

        @Test
        @Tag("level0")
        void testExtractBody() {
            /** Test extract body after frontmatter */
            String content = "---\nname: Test\n---\n\nBody content here";
            String body = Frontmatter.extractBody(content);
            assertEquals("Body content here", body);
        }

        @Test
        @Tag("level0")
        void testExtractBodyNoFrontmatter() {
            /** Test extract body when no frontmatter */
            String content = "Just body content";
            String body = Frontmatter.extractBody(content);
            assertEquals("Just body content", body);
        }
    }

    // ==================== TestPathValidation ====================
    // Note: Path validation tests require runtime setup, simplified for now

    @Nested
    class TestPathValidation {

        @Test
        @Tag("level0")
        void testValidatePathPlaceholder() {
            String resolved = CodingMemoryTools.validateCodingMemoryPath("user_role.md");
            assertTrue(resolved.endsWith("coding_memory/user_role.md")
                    || resolved.endsWith("coding_memory\\user_role.md"));
        }

        @Test
        @Tag("level0")
        void testValidatePathTraversal() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> CodingMemoryTools.validateCodingMemoryPath("../etc/passwd.md")
            );
            assertTrue(error.getMessage().contains("directory traversal"));
        }

        @Test
        @Tag("level0")
        void testValidatePathAbsolute() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> CodingMemoryTools.validateCodingMemoryPath(tempDir.resolve("absolute.md").toString())
            );
            assertTrue(error.getMessage().contains("directory traversal"));
        }

        @Test
        @Tag("level0")
        void testValidatePathNotMd() {
            IllegalArgumentException error = assertThrows(
                    IllegalArgumentException.class,
                    () -> CodingMemoryTools.validateCodingMemoryPath("user_role.txt")
            );
            assertTrue(error.getMessage().contains(".md"));
        }

        @Test
        @Tag("level0")
        void testSetAndGetCodingMemoryDir() {
            assertNotNull(CodingMemoryTools.getMemoryIndexManager());
            assertEquals(codingMemoryDir.toString(), CodingMemoryTools.getMemoryIndexManager().getCodingMemoryDir());
        }
    }

    // ==================== TestMemoryIndex ====================
    // Note: Memory index tests require runtime setup, simplified for now

    @Nested
    class TestMemoryIndex {

        @Test
        @Tag("level0")
        void testMemoryIndexPlaceholder() {
            Map<String, String> frontmatter = Map.of(
                    "name", "Developer Role",
                    "description", "Senior Python developer",
                    "type", "user"
            );

            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir.toString(), "user_role.md", frontmatter);

            String indexContent = readIndexContent();
            assertTrue(indexContent.contains("Developer Role"));
            assertTrue(indexContent.contains("user_role.md"));
        }

        @Test
        @Tag("level0")
        void testUpsertUpdateExisting() {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir.toString(), "user_role.md", Map.of(
                    "name", "Old Name",
                    "description", "Old desc",
                    "type", "user"
            ));
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir.toString(), "user_role.md", Map.of(
                    "name", "New Name",
                    "description", "New desc",
                    "type", "user"
            ));

            String indexContent = readIndexContent();
            assertTrue(indexContent.contains("New Name"));
            assertFalse(indexContent.contains("Old Name"));
        }

        @Test
        @Tag("level0")
        void testRemoveFromIndex() {
            CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir.toString(), "to_delete.md", Map.of(
                    "name", "To Delete",
                    "description", "Will be deleted",
                    "type", "user"
            ));

            CodingMemoryTools.removeFromMemoryIndex("to_delete.md");

            assertFalse(readIndexContent().contains("To Delete"));
        }

        @Test
        @Tag("level0")
        void testCountMemoryFiles() throws Exception {
            Files.writeString(codingMemoryDir.resolve("file1.md"), "content");
            Files.writeString(codingMemoryDir.resolve("file2.md"), "content");
            Files.writeString(codingMemoryDir.resolve("MEMORY.md"), "index");

            assertEquals(2, CodingMemoryTools.countMemoryFiles());
        }

        @Test
        @Tag("level0")
        void testReadFileSafeSuccess() throws Exception {
            Path file = codingMemoryDir.resolve("test_read.md");
            Files.writeString(file, "测试内容");

            assertEquals("测试内容", CodingMemoryTools.readFileSafe("test_read.md"));
        }

        @Test
        @Tag("level0")
        void testReadFileSafeNotFound() {
            assertEquals("", CodingMemoryTools.readFileSafe("missing.md"));
        }
    }

    private String readIndexContent() {
        try {
            return Files.readString(codingMemoryDir.resolve("MEMORY.md"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
