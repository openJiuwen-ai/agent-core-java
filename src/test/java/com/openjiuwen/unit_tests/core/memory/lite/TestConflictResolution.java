/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.WriteMode;
import com.openjiuwen.core.memory.lite.WriteResult;
import com.openjiuwen.core.memory.lite.Frontmatter;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Coding Memory conflict handling.
 * <p>
 * Scope:
 * - conflict_types.py data models
 * - frontmatter.py (enrich_frontmatter, rebuild_content_with_frontmatter, extract_body)
 * <p>
 * Mirrors Python's {@code tests.unit_tests.core.memory.lite.test_conflict_resolution}.
 */
class TestConflictResolution {

    // ==================== TestConflictTypes ====================

    @Nested
    class TestConflictTypes {

        @Test
        @Tag("level0")
        void testWriteModeValues() {
            /** Test WriteMode enum values */
            assertEquals("create", WriteMode.CREATE.getValue());
            assertEquals("append", WriteMode.APPEND.getValue());
            assertEquals("skip", WriteMode.SKIP.getValue());
        }

        @Test
        @Tag("level0")
        void testWriteResultBasic() {
            /** Test WriteResult basic functionality */
            WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.CREATE);
            assertTrue(result.isSuccess());
            assertEquals("/test/path.md", result.getPath());
            assertEquals(WriteMode.CREATE, result.getMode());
            assertFalse(result.isConflictDetected());
            assertTrue(result.getConflictingFiles().isEmpty());
            assertNull(result.getNote());
            assertNull(result.getError());
        }

        @Test
        @Tag("level0")
        void testWriteResultWithConflict() {
            /** Test WriteResult with conflict information */
            WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.CREATE);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("old1.md", "old2.md"));
            result.setNote("Conflicts detected");
            assertTrue(result.isConflictDetected());
            assertEquals(Arrays.asList("old1.md", "old2.md"), result.getConflictingFiles());
            assertEquals("Conflicts detected", result.getNote());
        }

        @Test
        @Tag("level0")
        void testWriteResultToDictCreate() {
            /** Test toDict for CREATE mode */
            WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.CREATE);
            Map<String, Object> d = result.toDict();
            assertEquals(true, d.get("success"));
            assertEquals("/test/path.md", d.get("path"));
            assertEquals("create", d.get("mode"));
        }

        @Test
        @Tag("level0")
        void testWriteResultToDictWithConflict() {
            /** Test toDict with conflict information */
            WriteResult result = new WriteResult(true, "/test/path.md", WriteMode.APPEND);
            result.setConflictDetected(true);
            result.setConflictingFiles(Arrays.asList("old.md"));
            Map<String, Object> d = result.toDict();
            assertEquals("append", d.get("mode"));
            assertTrue((Boolean) d.get("conflict_detected"));
            assertEquals(Arrays.asList("old.md"), d.get("conflicting_files"));
        }
    }

    // ==================== TestFrontmatterFunctions ====================

    @Nested
    class TestFrontmatterFunctions {

        @Test
        @Tag("level0")
        void testEnrichFrontmatterCreate() {
            /** Test enrich frontmatter for create */
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
            /** Test enrich frontmatter for edit */
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
            String content = "---\nname: Old\n---\nBody";
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
            /** Test extract body */
            String content = "---\nname: Test\n---\n\nBody content";
            String body = Frontmatter.extractBody(content);
            assertEquals("Body content", body);
        }

        @Test
        @Tag("level0")
        void testExtractBodyNoFrontmatter() {
            /** Test extract body when no frontmatter */
            String content = "Just body";
            String body = Frontmatter.extractBody(content);
            assertEquals("Just body", body);
        }
    }
}
