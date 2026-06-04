/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.memory.lite;

import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.core.memory.lite.CodingMemoryTools;
import com.openjiuwen.core.memory.lite.Frontmatter;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CodingMemoryTools.
 *
 * <p>Mirrors Python's {@code TestCodingMemoryTools} in
 * {@code tests/unit_tests/core/memory/lite/test_coding_memory_tools.py}.
 */
class TestCodingMemoryTools {

    @TempDir
    Path tempDir;

    private Path codingMemoryDir;
    private Workspace workspace;
    private Object sysOperation;

    @BeforeEach
    void setUp() throws Exception {
        codingMemoryDir = tempDir.resolve("coding_memory");
        Files.createDirectories(codingMemoryDir);
        workspace = new Workspace(tempDir.toString(), "cn");
        sysOperation = new Object();
        CodingMemoryTools.bindCodingMemoryRuntime(workspace, sysOperation, codingMemoryDir.toString());
    }

    @AfterEach
    void tearDown() {
        CodingMemoryTools.clearCodingMemoryRuntime();
    }

    @Test
    void testConstantsExist() {
        assertEquals("coding_memory", CodingMemoryTools.CODING_MEMORY_DIR);
        assertTrue(CodingMemoryTools.MAX_INDEX_LINES > 0);
    }

    @Test
    void testDefaultContextNullBeforeBinding() {
        CodingMemoryTools.clearCodingMemoryRuntime();
        assertNull(CodingMemoryTools.getCodingMemoryContext());
        assertNull(CodingMemoryTools.getMemoryIndexManager());
    }

    @Test
    void testBindCodingMemoryRuntimeExposesContext() {
        assertNotNull(CodingMemoryTools.getCodingMemoryContext());
        assertEquals(codingMemoryDir.toString(), CodingMemoryTools.getCodingMemoryContext().getCodingMemoryDir());
        assertSame(workspace, CodingMemoryTools.getCodingMemoryWorkspace());
        assertSame(sysOperation, CodingMemoryTools.getCodingMemorySysOperation());
        assertEquals(codingMemoryDir.toString(), CodingMemoryTools.getMemoryIndexManager().getCodingMemoryDir());
    }

    @Test
    void testClearCodingMemoryRuntimeClearsContext() {
        CodingMemoryTools.clearCodingMemoryRuntime();
        assertNull(CodingMemoryTools.getCodingMemoryContext());
        assertNull(CodingMemoryTools.getCodingMemoryWorkspace());
        assertNull(CodingMemoryTools.getCodingMemorySysOperation());
        assertNull(CodingMemoryTools.getMemoryIndexManager());
    }

    @Test
    void testCodingMemoryDirectoryName() {
        assertEquals("coding_memory", CodingMemoryTools.CODING_MEMORY_DIR);
    }

    @Test
    void testMaxIndexLinesReasonable() {
        assertTrue(CodingMemoryTools.MAX_INDEX_LINES >= 10);
        assertTrue(CodingMemoryTools.MAX_INDEX_LINES <= 1000);
    }

    @Test
    void testCodingMemoryWriteSuccess() {
        Map<String, Object> result = CodingMemoryToolContext.write("test.md", content("project", "这是记忆内容"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("project", result.get("type"));
        assertEquals("create", result.get("mode"));
    }

    @Test
    void testCodingMemoryWriteInvalidFrontmatter() {
        Map<String, Object> result = CodingMemoryToolContext.write("test_invalid_fm.md", "这是没有 frontmatter 的内容");
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("frontmatter"));
    }

    @Test
    void testCodingMemoryWriteInvalidPath() {
        Map<String, Object> result = CodingMemoryToolContext.write("test.txt", content("project", "content"));
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains(".md"));
    }

    @Test
    void testCodingMemoryWriteTypeUser() {
        Map<String, Object> result = CodingMemoryToolContext.write("user_test.md", content("user", "用户内容"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("user", result.get("type"));
    }

    @Test
    void testCodingMemoryWriteTypeFeedback() {
        Map<String, Object> result = CodingMemoryToolContext.write("feedback_test.md", content("feedback", "反馈内容"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("feedback", result.get("type"));
    }

    @Test
    void testCodingMemoryWriteTypeReference() {
        Map<String, Object> result = CodingMemoryToolContext.write("reference_test.md", content("reference", "参考内容"));
        assertTrue((Boolean) result.get("success"));
        assertEquals("reference", result.get("type"));
    }

    @Test
    void testCodingMemoryWriteInvalidType() {
        Map<String, Object> result = CodingMemoryToolContext.write("invalid.md", content("knowledge", "无效类型内容"));
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("type must be one of"));
    }

    @Test
    void testCodingMemoryReadFullContent() {
        CodingMemoryToolContext.write("read_test.md", content("project", "这是测试内容"));
        Map<String, Object> result = CodingMemoryToolContext.read("read_test.md", null, null);
        assertTrue((Boolean) result.get("success"));
        assertTrue(result.get("content").toString().contains("这是测试内容"));
        assertTrue(((Integer) result.get("totalLines")) > 0);
    }

    @Test
    void testCodingMemoryReadWithOffsetLimit() {
        String content = "---\n" +
                "name: test_offset\n" +
                "description: 测试偏移\n" +
                "type: project\n" +
                "---\n" +
                "第一行\n第二行\n第三行\n第四行\n第五行";
        CodingMemoryToolContext.write("offset_test.md", content);

        Map<String, Object> result = CodingMemoryToolContext.read("offset_test.md", 3, 2);
        assertTrue((Boolean) result.get("success"));
        assertEquals(3, result.get("start_line"));
        assertEquals(4, result.get("end_line"));
        assertTrue((Boolean) result.get("truncated"));
    }

    @Test
    void testCodingMemoryReadNonexistent() {
        Map<String, Object> result = CodingMemoryToolContext.read("nonexistent.md", null, null);
        assertFalse((Boolean) result.get("success"));
    }

    @Test
    void testCodingMemoryEditSuccess() {
        CodingMemoryToolContext.write("edit_test.md", content("project", "原内容"));
        Map<String, Object> result = CodingMemoryToolContext.staticEdit("edit_test.md", "原内容", "修改后内容");
        assertTrue((Boolean) result.get("success"));
        assertTrue(CodingMemoryToolContext.read("edit_test.md", null, null).get("content").toString().contains("修改后内容"));
    }

    @Test
    void testCodingMemoryEditOldTextNotFound() {
        CodingMemoryToolContext.write("not_found_test.md", content("project", "实际内容"));
        Map<String, Object> result = CodingMemoryToolContext.staticEdit("not_found_test.md", "不存在的文本", "新文本");
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("old_text not found"));
    }

    @Test
    void testCodingMemoryEditMultipleMatches() {
        CodingMemoryToolContext.write("multi_test.md", content("project", "相同文本\n相同文本"));
        Map<String, Object> result = CodingMemoryToolContext.staticEdit("multi_test.md", "相同文本", "替换文本");
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("appears"));
    }

    @Test
    void testCodingMemoryEditEmptyOldText() {
        Map<String, Object> result = CodingMemoryToolContext.staticEdit("test.md", "", "new");
        assertFalse((Boolean) result.get("success"));
        assertTrue(result.get("error").toString().contains("old_text cannot be empty"));
    }

    @Test
    void testUpsertMemoryIndex() {
        CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir.toString(), "test.md",
                Map.of("name", "test", "description", "测试"));
        assertTrue(indexContent().contains("test.md"));
        assertTrue(indexContent().contains("测试"));
    }

    @Test
    void testRemoveFromMemoryIndex() {
        CodingMemoryToolContext.upsertMemoryIndex(codingMemoryDir.toString(), "to_remove.md",
                Map.of("name", "remove", "description", "删除"));
        CodingMemoryTools.removeFromMemoryIndex("to_remove.md");
        assertFalse(indexContent().contains("to_remove.md"));
    }

    @Test
    void testReadHeadWithContextLimit() throws Exception {
        Path file = codingMemoryDir.resolve("head_test.md");
        Files.writeString(file, "---\nname: head_test\ndescription: 测试头部\ntype: project\n---\n第一行\n第二行\n第三行\n第四行");
        String result = CodingMemoryTools.codingMemoryReadWithContext("head_test.md");
        assertTrue(result.contains("head_test"));
        assertTrue(result.contains("第一行"));
    }

    @Test
    void testCountMemoryFiles() throws Exception {
        Files.writeString(codingMemoryDir.resolve("file1.md"), "content");
        Files.writeString(codingMemoryDir.resolve("file2.md"), "content");
        Files.writeString(codingMemoryDir.resolve("MEMORY.md"), "index");
        assertEquals(2, CodingMemoryTools.countMemoryFiles());
    }

    @Test
    void testValidateCodingMemoryPathValid() {
        String resolved = CodingMemoryTools.validateCodingMemoryPath("valid.md");
        assertTrue(resolved.endsWith("valid.md"));
    }

    @Test
    void testValidateCodingMemoryPathInvalidExt() {
        try {
            CodingMemoryTools.validateCodingMemoryPath("invalid.txt");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains(".md"));
            return;
        }
        throw new AssertionError("Expected invalid extension");
    }

    @Test
    void testValidateCodingMemoryPathTraversal() {
        try {
            CodingMemoryTools.validateCodingMemoryPath("../escape.md");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("directory traversal"));
            return;
        }
        throw new AssertionError("Expected traversal rejection");
    }

    @Test
    void testValidateCodingMemoryPathAbsolute() {
        try {
            CodingMemoryTools.validateCodingMemoryPath(tempDir.resolve("absolute.md").toString());
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("directory traversal"));
            return;
        }
        throw new AssertionError("Expected absolute path rejection");
    }

    @Test
    void testParseFrontmatterValid() {
        Map<String, String> result = Frontmatter.parseFrontmatter(content("project", "这是内容"));
        assertNotNull(result);
        assertEquals("test_memory", result.get("name"));
        assertEquals("测试记忆", result.get("description"));
        assertEquals("project", result.get("type"));
    }

    @Test
    void testParseFrontmatterNoFrontmatter() {
        assertNull(Frontmatter.parseFrontmatter("这是没有 frontmatter 的内容"));
    }

    @Test
    void testParseFrontmatterIncomplete() {
        Map<String, String> result = Frontmatter.parseFrontmatter("---\nname: test\n---\n这是内容");
        assertNotNull(result);
        assertEquals("test", result.get("name"));
    }

    @Test
    void testValidateFrontmatterValid() {
        Frontmatter.ValidationResult result = Frontmatter.validateFrontmatter(
                Map.of("name", "test", "description", "测试", "type", "project"));
        assertTrue(result.isValid());
        assertEquals("", result.getErrorMessage());
    }

    @Test
    void testValidateFrontmatterMissingName() {
        Frontmatter.ValidationResult result = Frontmatter.validateFrontmatter(
                Map.of("description", "测试", "type", "project"));
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("name"));
    }

    @Test
    void testValidateFrontmatterMissingDescription() {
        Frontmatter.ValidationResult result = Frontmatter.validateFrontmatter(
                Map.of("name", "test", "type", "project"));
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("description"));
    }

    @Test
    void testValidateFrontmatterMissingType() {
        Frontmatter.ValidationResult result = Frontmatter.validateFrontmatter(
                Map.of("name", "test", "description", "测试"));
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("type"));
    }

    @Test
    void testValidateFrontmatterInvalidType() {
        Frontmatter.ValidationResult result = Frontmatter.validateFrontmatter(
                Map.of("name", "test", "description", "测试", "type", "invalid"));
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("type must be one of"));
    }

    @Test
    void testValidateFrontmatterAllValidTypes() {
        for (String type : List.of("user", "feedback", "project", "reference")) {
            Frontmatter.ValidationResult result = Frontmatter.validateFrontmatter(
                    Map.of("name", "test", "description", "测试", "type", type));
            assertTrue(result.isValid(), type);
        }
    }

    @Test
    void testReadFileSafeMissingReturnsEmptyString() {
        assertEquals("", CodingMemoryTools.readFileSafe("missing.md"));
    }

    @Test
    void testReadFileSafeExistingReturnsContent() throws Exception {
        Files.writeString(codingMemoryDir.resolve("safe.md"), "safe-content");
        assertEquals("safe-content", CodingMemoryTools.readFileSafe("safe.md"));
    }

    private String content(String type, String body) {
        return "---\n" +
                "name: test_memory\n" +
                "description: 测试记忆\n" +
                "type: " + type + "\n" +
                "---\n" +
                body;
    }

    private String indexContent() {
        try {
            return Files.readString(codingMemoryDir.resolve("MEMORY.md"));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
