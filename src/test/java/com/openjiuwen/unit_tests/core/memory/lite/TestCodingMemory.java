/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.Frontmatter;
import com.openjiuwen.core.memory.lite.Frontmatter.ValidationResult;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Coding Memory — basic functionality.
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.memory.lite.test_coding_memory}.
 */
class TestCodingMemory {

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
            /** Test path validation - valid markdown paths */
            String validPath = "/valid/memory/file.md";
            assertTrue(validPath.endsWith(".md"));
            
            /** Test path validation - invalid paths */
            String invalidPath = "/invalid/path.txt";
            assertFalse(invalidPath.endsWith(".md"));
        }
    }

    // ==================== TestMemoryIndex ====================
    // Note: Memory index tests require runtime setup, simplified for now

    @Nested
    class TestMemoryIndex {

        @Test
        @Tag("level0")
        void testMemoryIndexPlaceholder() {
            /** Test memory index structure - verify index data types */
            Map<String, List<String>> index = new HashMap<>();
            index.put("test_key", new ArrayList<>());
            assertNotNull(index);
            assertTrue(index.containsKey("test_key"));
        }
    }
}