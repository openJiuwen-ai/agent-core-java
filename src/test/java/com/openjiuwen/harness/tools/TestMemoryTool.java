/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolOps;
import com.openjiuwen.harness.tools.memory.EditMemoryTool;
import com.openjiuwen.harness.tools.memory.MemoryGetTool;
import com.openjiuwen.harness.tools.memory.MemorySearchTool;
import com.openjiuwen.harness.tools.memory.MemoryTools;
import com.openjiuwen.harness.tools.memory.WriteMemoryTool;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for harness memory tools.
 *
 * <p>Mirrors Python's {@code test_memory.py} in
 * {@code tests.unit_tests.harness.tools}.
 */
class TestMemoryTool {

    private Path tempDir;
    private MemoryToolContext ctx;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("memory-tools-");
        Files.createDirectories(tempDir.resolve("memory"));
        ctx = new MemoryToolContext();
        ctx.setWorkspace(new Workspace(tempDir.toString(), "en"));
        ctx.setSysOperation(new FakeSysOperation());
    }

    @AfterEach
    void cleanup() throws IOException {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(tempDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Nested
    class TestCreateMemoryTools {

        @Test
        void createMemoryToolsReturns5Tools() {
            List<Object> tools = MemoryTools.createMemoryTools(ctx, "en", "agent-1");

            assertEquals(5, tools.size());
            assertTrue(tools.stream().allMatch(Tool.class::isInstance));
        }

        @Test
        void createMemoryToolsReturnsCorrectNames() {
            Set<String> names = MemoryTools.createMemoryTools(ctx, "en", "agent-1").stream()
                    .map(Tool.class::cast)
                    .map(tool -> tool.getCard().getName())
                    .collect(Collectors.toSet());

            assertEquals(Set.of("memory_search", "memory_get", "write_memory", "edit_memory", "read_memory"), names);
        }
    }

    @Nested
    class TestWriteMemoryTool {

        @Test
        void writeMemorySuccess() throws Exception {
            ToolOutput output = invokeWrite("notes/hello.md", "# Hello\nbody", false);

            assertTrue(output.isSuccess(), output.getError());
            assertTrue((Boolean) data(output).get("success"));
            assertTrue(Files.exists(tempDir.resolve("memory").resolve("hello.md")));
        }

        @Test
        void writeMemoryWithPath() throws Exception {
            ToolOutput output = invokeWrite("notes/path.md", "body", false);

            assertTrue(String.valueOf(data(output).get("path")).endsWith("memory" + sep() + "path.md"));
        }

        @Test
        void writeMemoryAppendFalse() throws Exception {
            invokeWrite("notes/overwrite.md", "old", false);
            invokeWrite("notes/overwrite.md", "new", false);

            assertEquals("new", Files.readString(tempDir.resolve("memory").resolve("overwrite.md")));
        }

        @Test
        void writeMemoryAppendTrue() throws Exception {
            invokeWrite("notes/append.md", "one", false);
            invokeWrite("notes/append.md", "\ntwo", true);

            assertEquals("one\ntwo", Files.readString(tempDir.resolve("memory").resolve("append.md")));
        }

        @Test
        void writeMemoryCreatesDirectory() throws Exception {
            invokeWrite("2026-05-28.md", "daily", false);

            assertTrue(Files.exists(tempDir.resolve("memory").resolve("daily_memory").resolve("2026-05-28.md")));
        }
    }

    @Nested
    class TestReadMemoryTool {

        @Test
        void readMemorySuccess() throws Exception {
            invokeWrite("notes/readme.md", "line1\nline2\nline3", false);

            ToolOutput output = invokeRead("notes/readme.md", Map.of("offset", 1, "limit", 2));

            assertTrue(output.isSuccess(), output.getError());
            assertTrue((Boolean) data(output).get("success"));
        }

        @Test
        void readMemoryReturnsContent() throws Exception {
            invokeWrite("notes/readme.md", "line1\nline2\nline3", false);

            ToolOutput output = invokeRead("notes/readme.md", Map.of("offset", 1, "limit", 2));

            assertEquals("line1\nline2", data(output).get("content"));
            assertEquals(1, data(output).get("start_line"));
            assertEquals(2, data(output).get("end_line"));
        }

        @Test
        void readMemoryPathNotFound() throws Exception {
            ToolOutput output = invokeRead("missing.md", Map.of());

            assertFalse(output.isSuccess());
            assertFalse((Boolean) data(output).get("success"));
            assertNotNull(data(output).get("error"));
        }
    }

    @Nested
    class TestEditMemoryTool {

        @Test
        void editMemorySuccess() throws Exception {
            invokeWrite("notes/edit.md", "alpha beta", false);

            ToolOutput output = invokeEdit("notes/edit.md", "beta", "gamma");

            assertTrue(output.isSuccess(), output.getError());
            assertTrue((Boolean) data(output).get("success"));
            assertEquals("alpha gamma", Files.readString(tempDir.resolve("memory").resolve("edit.md")));
        }

        @Test
        void editMemoryWithPath() throws Exception {
            invokeWrite("notes/edit-path.md", "alpha beta", false);

            ToolOutput output = invokeEdit("notes/edit-path.md", "beta", "gamma");

            assertTrue(String.valueOf(data(output).get("path")).endsWith("memory" + sep() + "edit-path.md"));
        }

        @Test
        void editMemoryWithOldString() throws Exception {
            invokeWrite("notes/edit-old.md", "alpha beta", false);

            ToolOutput output = invokeEdit("notes/edit-old.md", "alpha", "omega");

            assertEquals("alpha", data(output).get("replaced"));
            assertEquals("omega beta", Files.readString(tempDir.resolve("memory").resolve("edit-old.md")));
        }

        @Test
        void editMemoryWithNewString() throws Exception {
            invokeWrite("notes/edit-new.md", "alpha beta", false);

            ToolOutput output = invokeEdit("notes/edit-new.md", "beta", "gamma");

            assertEquals("gamma", data(output).get("new_text"));
        }

        @Test
        void editMemoryNotFound() throws Exception {
            ToolOutput output = invokeEdit("notes/missing.md", "beta", "gamma");

            assertFalse((Boolean) data(output).get("success"));
            assertTrue(String.valueOf(data(output).get("error")).contains("not found"));
        }
    }

    @Nested
    class TestMemoryGetTool {

        @Test
        void memoryGetSuccess() throws Exception {
            ctx.setManager(new FakeMemoryManager());

            ToolOutput output = invokeGet("notes/indexed.md", Map.of());

            assertTrue(output.isSuccess());
            assertFalse((Boolean) data(output).get("disabled"));
        }

        @Test
        void memoryGetReturnsContent() throws Exception {
            ctx.setManager(new FakeMemoryManager());

            ToolOutput output = invokeGet("notes/indexed.md", Map.of("from_line", 1, "lines", 1));

            assertEquals("indexed content", data(output).get("text"));
        }
    }

    @Nested
    class TestMemorySearchTool {

        @Test
        void memorySearchSuccess() throws Exception {
            ctx.setManager(new FakeMemoryManager());

            ToolOutput output = invokeSearch("anything", Map.of());

            assertTrue(output.isSuccess());
            assertFalse((Boolean) data(output).get("disabled"));
        }

        @Test
        void memorySearchReturnsResults() throws Exception {
            ctx.setManager(new FakeMemoryManager());

            ToolOutput output = invokeSearch("anything", Map.of());

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) data(output).get("results");
            assertEquals("indexed.md", results.get(0).get("path"));
            assertEquals("indexed.md#L1-L2", results.get(0).get("citation"));
        }

        @Test
        void memorySearchWithPattern() throws Exception {
            ctx.setManager(new FakeMemoryManager());

            ToolOutput output = invokeSearch("indexed", Map.of("max_results", 3, "min_score", 0.25));

            assertEquals("indexed", data(output).get("query"));
            assertEquals(3, data(output).get("max_results"));
            assertEquals(0.25, data(output).get("min_score"));
        }
    }

    @Nested
    class TestValidateMemoryPath {

        @Test
        void validateMemoryPathSuccess() {
            MemoryToolOps.ValidationResult result =
                    MemoryToolOps.validateMemoryPath("notes/safe.md", (Workspace) ctx.getWorkspace());

            assertTrue(result.isValid());
            assertTrue(result.getMessage().endsWith("memory" + sep() + "safe.md"));
        }

        @Test
        void validateMemoryPathRejectsTraversal() {
            MemoryToolOps.ValidationResult result =
                    MemoryToolOps.validateMemoryPath("../escape.md", (Workspace) ctx.getWorkspace());

            assertFalse(result.isValid());
            assertTrue(result.getMessage().toLowerCase().contains("traversal"));
        }

        @Test
        void validateMemoryPathRejectsAbsolute() {
            MemoryToolOps.ValidationResult result =
                    MemoryToolOps.validateMemoryPath(tempDir.resolve("escape.md").toString(), (Workspace) ctx.getWorkspace());

            assertFalse(result.isValid());
            assertTrue(result.getMessage().toLowerCase().contains("invalid path"));
        }
    }

    private ToolOutput invokeWrite(String path, String content, boolean append) throws Exception {
        return (ToolOutput) new WriteMemoryTool("en", "agent-1", ctx)
                .invoke(Map.of("path", path, "content", content, "append", append), Map.of());
    }

    private ToolOutput invokeRead(String path, Map<String, Object> options) throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>(options);
        inputs.put("path", path);
        return (ToolOutput) new ReadMemoryTool("en", "agent-1", ctx)
                .invoke(inputs, Map.of());
    }

    private ToolOutput invokeEdit(String path, String oldText, String newText) throws Exception {
        return (ToolOutput) new EditMemoryTool("en", "agent-1", ctx)
                .invoke(Map.of("path", path, "old_text", oldText, "new_text", newText), Map.of());
    }

    private ToolOutput invokeGet(String path, Map<String, Object> options) throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>(options);
        inputs.put("path", path);
        return (ToolOutput) new MemoryGetTool("en", "agent-1", ctx)
                .invoke(inputs, Map.of());
    }

    private ToolOutput invokeSearch(String query, Map<String, Object> options) throws Exception {
        Map<String, Object> inputs = new LinkedHashMap<>(options);
        inputs.put("query", query);
        return (ToolOutput) new MemorySearchTool("en", "agent-1", ctx)
                .invoke(inputs, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private String sep() {
        return java.io.File.separator;
    }

    public static class FakeSysOperation {
        private final FakeFs fs = new FakeFs();

        public FakeFs fs() {
            return fs;
        }
    }

    public static class FakeFs {

        public FakeWriteResult writeFile(String path, String content, boolean createDirs, boolean append)
                throws IOException {
            Path file = Path.of(path);
            if (createDirs && file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            if (append) {
                Files.writeString(file, content, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } else {
                Files.writeString(file, content, StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
            }
            return new FakeWriteResult(Files.size(file));
        }

        public FakeReadResult readFile(String path) throws IOException {
            Path file = Path.of(path);
            if (!Files.exists(file)) {
                throw new IOException("file not found: " + path);
            }
            return new FakeReadResult(Files.readString(file, StandardCharsets.UTF_8));
        }

        public FakeReadResult readFile(String path, int[] lineRange) throws IOException {
            return readFile(path);
        }
    }

    public record FakeWriteResult(FakeWriteData data) {
        public FakeWriteResult(long size) {
            this(new FakeWriteData(size));
        }

        public FakeWriteData getData() {
            return data;
        }
    }

    public record FakeWriteData(long size) {
        public long getSize() {
            return size;
        }
    }

    public record FakeReadResult(FakeReadData data) {
        public FakeReadResult(String content) {
            this(new FakeReadData(content));
        }

        public FakeReadData getData() {
            return data;
        }
    }

    public record FakeReadData(String content) {
        public String getContent() {
            return content;
        }
    }

    public static class FakeMemoryManager {
        public boolean isClosed() {
            return false;
        }

        public List<Map<String, Object>> search(String query, Map<String, Object> opts) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", "indexed.md");
            result.put("text", "indexed content");
            result.put("start_line", 1);
            result.put("end_line", 2);
            return new ArrayList<>(List.of(result));
        }

        public Map<String, Object> readFile(String path, Integer fromLine, Integer lines) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("path", path);
            result.put("text", "indexed content");
            return result;
        }

        public Map<String, Object> status() {
            return Map.of("provider", "fake", "model", "memory-test");
        }
    }
}
