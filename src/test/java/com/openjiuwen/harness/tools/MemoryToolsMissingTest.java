/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.lite.LiteMemoryToolContextBase;
import com.openjiuwen.core.memory.lite.MemoryToolContext;
import com.openjiuwen.core.memory.lite.MemoryToolOps;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests/unit_tests/harness/tools/test_memory.py}.
 */
class MemoryToolsMissingTest {

    @TempDir
    private Path root;

    @Test
    void createMemoryToolsReturnsFiveTools() throws Exception {
        List<Tool> tools = MemoryTools.createMemoryTools(context());

        assertThat(tools).hasSize(5);
        assertThat(tools)
                .extracting(tool -> tool.getCard().getName())
                .containsExactlyInAnyOrder(
                        "memory_search",
                        "memory_get",
                        "write_memory",
                        "edit_memory",
                        "read_memory"
                );
    }

    @Test
    void writeMemorySuccess() throws Exception {
        MemoryToolContext context = context();
        ToolOutput result = invoke(tool(context, "write_memory"), Map.of(
                "path", "notes/hello.md",
                "content", "# Hello\nbody",
                "append", false
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(Files.exists(root.resolve("memory").resolve("hello.md"))).isTrue();
    }

    @Test
    void readMemorySuccess() throws Exception {
        MemoryToolContext context = context();
        invoke(tool(context, "write_memory"), Map.of(
                "path", "notes/readme.md",
                "content", "line1\nline2\nline3",
                "append", false
        ));

        ToolOutput result = invoke(tool(context, "read_memory"), Map.of(
                "path", "notes/readme.md",
                "offset", 1,
                "limit", 2
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(data(result).get("content")).asString().contains("line1");
    }

    @Test
    void editMemorySuccess() throws Exception {
        MemoryToolContext context = context();
        invoke(tool(context, "write_memory"), Map.of(
                "path", "notes/edit.md",
                "content", "alpha beta",
                "append", false
        ));

        ToolOutput edit = invoke(tool(context, "edit_memory"), Map.of(
                "path", "notes/edit.md",
                "old_text", "beta",
                "new_text", "gamma"
        ));
        ToolOutput read = invoke(tool(context, "read_memory"), Map.of("path", "notes/edit.md"));

        assertThat(edit.isSuccess()).isTrue();
        assertThat(read.isSuccess()).isTrue();
        assertThat(data(read).get("content")).asString().contains("gamma");
    }

    @Test
    void validateMemoryPathInvalidTraversal() throws Exception {
        MemoryToolOps.ValidationResult result = MemoryToolOps.validateMemoryPath(
                "../escape.md",
                context().getWorkspace()
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.value().toLowerCase()).contains("traversal");
    }

    @Test
    void memoryGetDisabledWhenNoManager() throws Exception {
        MemoryToolContext context = new MemoryToolContext();

        ToolOutput result = invoke(tool(context, "memory_get"), Map.of("path", "indexed.md"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(data(result)).containsEntry("disabled", true);
    }

    @Test
    void memorySearchReturnsStructuredResult() throws Exception {
        MemoryToolContext context = context();

        ToolOutput result = invoke(tool(context, "memory_search"), Map.of("query", "anything"));

        assertThat(data(result)).containsKey("results");
        assertThat(data(result).containsKey("results") || data(result).containsKey("disabled")).isTrue();
    }

    private MemoryToolContext context() throws IOException {
        Files.createDirectories(root.resolve("memory"));
        MemoryToolContext context = new MemoryToolContext();
        context.setWorkspace(new Workspace(root));
        context.setSysOperation(new FakeSysOperation());
        context.setManager(new FakeMemoryManager());
        return context;
    }

    private static Tool tool(MemoryToolContext context, String name) {
        return MemoryTools.createMemoryTools(context)
                .stream()
                .filter(candidate -> name.equals(candidate.getCard().getName()))
                .findFirst()
                .orElseThrow();
    }

    private static ToolOutput invoke(Tool tool, Map<String, Object> inputs) throws Exception {
        return (ToolOutput) tool.invoke(inputs, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ToolOutput output) {
        return (Map<String, Object>) output.getData();
    }

    private static final class FakeSysOperation {
        private final FakeFs fs = new FakeFs();

        public FakeFs fs() {
            return fs;
        }
    }

    private static final class FakeFs {
        public FakeReadResult readFile(
                String path,
                String mode,
                Integer head,
                Integer tail,
                BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options
        ) throws IOException {
            return new FakeReadResult(new FakeReadData(read(path)));
        }

        public FakeReadResult readFile(String path) throws IOException {
            return new FakeReadResult(new FakeReadData(read(path)));
        }

        public FakeWriteResult writeFile(
                String path,
                String content,
                String mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                Map<String, Object> options
        ) throws IOException {
            Path target = Path.of(path);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            int previousSize = Files.exists(target) ? (int) Files.size(target) : 0;
            if (append) {
                String prefix = prependNewline && previousSize > 0 ? "\n" : "";
                Files.writeString(
                        target,
                        prefix + content + (appendNewline ? "\n" : ""),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } else {
                Files.writeString(
                        target,
                        content + (appendNewline ? "\n" : ""),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            }
            return new FakeWriteResult(new FakeWriteData(previousSize));
        }

        private String read(String path) throws IOException {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        }
    }

    public record FakeReadResult(FakeReadData data) {
    }

    public record FakeReadData(String content) {
    }

    public record FakeWriteResult(FakeWriteData data) {
    }

    public record FakeWriteData(int size) {
    }

    public static final class FakeMemoryManager implements LiteMemoryToolContextBase.MemoryIndexManagerView {
        @Override
        public boolean isClosed() {
            return false;
        }

        public List<Map<String, Object>> search(String query, Map<String, Object> opts) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", "MEMORY.md");
            row.put("start_line", 1);
            row.put("end_line", 1);
            row.put("text", query);
            return List.of(row);
        }

        public Map<String, Object> status() {
            return Map.of("provider", "fake", "model", "test");
        }
    }
}
