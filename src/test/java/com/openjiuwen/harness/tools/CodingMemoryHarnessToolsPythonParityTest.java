/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.lite.CodingMemoryToolContext;
import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's harness coding-memory tool tests in
 * {@code tests/unit_tests/harness/tools/test_coding_memory.py}.</p>
 */
class CodingMemoryHarnessToolsPythonParityTest {

    @TempDir
    private Path root;

    @Test
    void createCodingMemoryToolsReturnsThreeToolsAndFillsContext() throws Exception {
        CodingMemoryToolContext context = context();
        assertThat(context.getSettings()).isNull();
        context.setCodingMemoryDir("");

        List<Tool> tools = CodingMemoryTools.createCodingMemoryTools(context);

        assertThat(tools).hasSize(3);
        assertThat(tools)
                .extracting(tool -> tool.getCard().getName())
                .containsExactlyInAnyOrder(
                        "coding_memory_read",
                        "coding_memory_write",
                        "coding_memory_edit"
                );
        assertThat(context.getNodeName()).isEqualTo("coding_memory");
        assertThat(context.getSettings()).isNotNull();
        assertThat(context.getCodingMemoryDir()).isEqualTo(root.resolve("coding_memory").toString());
    }

    @Test
    void codingMemoryWriteRequiresFrontmatter() throws Exception {
        ToolOutput result = invoke(tool(context(), "coding_memory_write"), Map.of(
                "path", "notes.md",
                "content", "plain text"
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError() == null ? data(result).get("error") : result.getError())
                .asString()
                .containsIgnoringCase("frontmatter");
    }

    @Test
    void codingMemoryWriteReadEditSuccess() throws Exception {
        CodingMemoryToolContext context = context();
        String content = """
                ---
                name: user_pref
                description: user preference for tests
                type: user
                ---
                always run targeted tests first
                """;

        ToolOutput writeResult = invoke(tool(context, "coding_memory_write"), Map.of(
                "path", "notes/user_pref.md",
                "content", content
        ));
        assertThat(writeResult.isSuccess()).isTrue();

        Path memoryFile = root.resolve("coding_memory").resolve("user_pref.md");
        assertThat(Files.exists(memoryFile)).isTrue();

        ToolOutput readResult = invoke(tool(context, "coding_memory_read"), Map.of("path", "notes/user_pref.md"));
        assertThat(readResult.isSuccess()).isTrue();
        assertThat(data(readResult).get("content")).asString().contains("always run targeted tests first");

        ToolOutput editResult = invoke(tool(context, "coding_memory_edit"), Map.of(
                "path", "notes/user_pref.md",
                "old_text", "always run targeted tests first",
                "new_text", "always run focused unit tests first"
        ));
        assertThat(editResult.isSuccess()).isTrue();

        ToolOutput verifyResult = invoke(tool(context, "coding_memory_read"), Map.of("path", "notes/user_pref.md"));
        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(data(verifyResult).get("content")).asString().contains("always run focused unit tests first");
    }

    private CodingMemoryToolContext context() throws IOException {
        Files.createDirectories(root.resolve("coding_memory"));
        CodingMemoryToolContext context = new CodingMemoryToolContext();
        context.setWorkspace(new Workspace(root));
        context.setSysOperation(new FakeSysOperation());
        return context;
    }

    private static Tool tool(CodingMemoryToolContext context, String name) {
        return CodingMemoryTools.createCodingMemoryTools(context)
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
        public Result readFile(String path) {
            return readFile(path, null);
        }

        public Result readFile(String path, int[] ignoredLineRange) {
            try {
                return new Result(new Data(Files.readString(Path.of(path), StandardCharsets.UTF_8), null));
            } catch (IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }

        public Result writeFile(String path, String content, boolean createIfNotExist, boolean append) {
            try {
                Path target = Path.of(path);
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (append) {
                    Files.writeString(
                            target,
                            content,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.APPEND
                    );
                } else if (createIfNotExist) {
                    Files.writeString(
                            target,
                            content,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    );
                } else {
                    Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                }
                return new Result(new Data("", null));
            } catch (IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }

        public Result writeFile(String path, String content, boolean append) {
            return writeFile(path, content, true, append);
        }

        public ListResult listFiles(String directory, boolean recursive) {
            try {
                Path dir = Path.of(directory);
                if (!Files.isDirectory(dir)) {
                    return new ListResult(new ListData(List.of()));
                }
                List<FileItem> items = new ArrayList<>();
                try (var stream = Files.list(dir)) {
                    stream.forEach(path -> items.add(new FileItem(
                            path.getFileName().toString(),
                            Files.isDirectory(path)
                    )));
                }
                return new ListResult(new ListData(items));
            } catch (IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }
    }

    private record Result(Data data) {
    }

    private record Data(String content, List<FileItem> listItems) {
    }

    private record ListResult(ListData data) {
    }

    private record ListData(List<FileItem> listItems) {
    }

    private record FileItem(String name, boolean directory) {
    }
}
