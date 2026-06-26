/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Focused validation for {@link CodingMemoryToolOps}.
 *
 * <p>Mirrors Python's module in
 * {@code openjiuwen/core/memory/lite/coding_memory_tool_ops.py}.</p>
 */
public final class CodingMemoryToolOpsTest {

    private CodingMemoryToolOpsTest() {
    }

    public static void main(String[] args) {
        validatesCodingMemoryPath();
        readsWithOffsetAndLimit();
        writesFrontmatterContentAndIndex();
        editsSingleOccurrenceAndRejectsAmbiguousText();
        System.out.println("PASS CodingMemoryToolOpsTest");
    }

    private static void validatesCodingMemoryPath() {
        Workspace workspace = new Workspace(Path.of("workspace-root"));

        require(!CodingMemoryToolOps.validateCodingMemoryPath("bad.txt", workspace).valid(), "suffix");
        require(!CodingMemoryToolOps.validateCodingMemoryPath("../bad.md", workspace).valid(), "traversal");
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("nested/note.md", workspace);

        require(result.valid(), "valid path");
        require(result.value().endsWith(Path.of("coding_memory", "note.md").toString()), "basename resolution");
    }

    private static void readsWithOffsetAndLimit() {
        Workspace workspace = new Workspace(Path.of("workspace-root"));
        FakeSysOperation sysOperation = new FakeSysOperation();
        String resolved = workspace.getNodePath("coding_memory").resolve("readme.md").normalize().toString();
        sysOperation.fs.files.put(resolved, "one\ntwo\nthree");
        CodingMemoryToolContext context = context(workspace, sysOperation);

        Map<String, Object> result = CodingMemoryToolOps
                .codingMemoryReadWithContext(context, "readme.md", 2, 1)
                .join();

        require(Boolean.TRUE.equals(result.get("success")), "read success");
        require("two".equals(result.get("content")), "read slice");
        require(Integer.valueOf(3).equals(result.get("totalLines")), "total lines");
        require(Boolean.TRUE.equals(result.get("truncated")), "truncated");
    }

    private static void writesFrontmatterContentAndIndex() {
        Workspace workspace = new Workspace(Path.of("workspace-root"));
        FakeSysOperation sysOperation = new FakeSysOperation();
        CodingMemoryToolContext context = context(workspace, sysOperation);
        String content = """
                ---
                name: Architecture Note
                description: Useful project note
                type: project
                ---

                Remember this design decision.
                """;

        Map<String, Object> result = CodingMemoryToolOps
                .codingMemoryWriteWithContext(context, "notes/arch.md", content)
                .join();
        String resolved = workspace.getNodePath("coding_memory").resolve("arch.md").normalize().toString();
        String index = workspace.getNodePath("coding_memory").resolve("MEMORY.md").normalize().toString();

        require(Boolean.TRUE.equals(result.get("success")), "write success");
        require("create".equals(result.get("mode")), "write mode");
        require("project".equals(result.get("type")), "frontmatter type");
        require(sysOperation.fs.files.get(resolved).contains("updated_at:"), "updated frontmatter");
        require(sysOperation.fs.files.get(resolved).contains("Remember this design decision."), "body");
        require(sysOperation.fs.files.get(index).contains("[Architecture Note](arch.md)"), "index entry");
    }

    private static void editsSingleOccurrenceAndRejectsAmbiguousText() {
        Workspace workspace = new Workspace(Path.of("workspace-root"));
        FakeSysOperation sysOperation = new FakeSysOperation();
        CodingMemoryToolContext context = context(workspace, sysOperation);
        String resolved = workspace.getNodePath("coding_memory").resolve("edit.md").normalize().toString();
        sysOperation.fs.files.put(resolved, """
                ---
                name: Edit Note
                description: Editable note
                type: reference
                ---

                alpha beta
                """);

        Map<String, Object> success = CodingMemoryToolOps
                .codingMemoryEditWithContext(context, "edit.md", "alpha", "gamma")
                .join();
        require(Boolean.TRUE.equals(success.get("success")), "edit success");
        require(sysOperation.fs.files.get(resolved).contains("gamma beta"), "edited content");

        sysOperation.fs.files.put(resolved, "repeat repeat");
        Map<String, Object> ambiguous = CodingMemoryToolOps
                .codingMemoryEditWithContext(context, "edit.md", "repeat", "once")
                .join();
        require(Boolean.FALSE.equals(ambiguous.get("success")), "ambiguous reject");
        require(String.valueOf(ambiguous.get("error")).contains("appears 2 times"), "ambiguous message");
    }

    private static CodingMemoryToolContext context(Workspace workspace, FakeSysOperation sysOperation) {
        CodingMemoryToolContext context = new CodingMemoryToolContext();
        context.setWorkspace(workspace);
        context.setCodingMemoryDir(workspace.getNodePath("coding_memory").normalize().toString());
        context.setSysOperation(sysOperation);
        return context;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static final class FakeSysOperation {
        private final FakeFs fs = new FakeFs();

        public FakeFs fs() {
            return fs;
        }
    }

    public static final class FakeFs {
        private final Map<String, String> files = new LinkedHashMap<>();

        public FileResult readFile(String path) {
            return new FileResult(new FileData(files.getOrDefault(path, "")));
        }

        public FileResult readFile(String path, int[] lineRange) {
            return readFile(path);
        }

        public FileResult writeFile(String path, String content, boolean createIfNotExist, boolean append) {
            if (append) {
                files.put(path, files.getOrDefault(path, "") + content);
            } else {
                files.put(path, content);
            }
            return new FileResult(new FileData(content));
        }

        public ListResult listFiles(String directory, boolean recursive) {
            List<FileItem> items = new ArrayList<>();
            Path dir = Path.of(directory).normalize();
            for (String path : files.keySet()) {
                Path filePath = Path.of(path).normalize();
                if (dir.equals(filePath.getParent())) {
                    items.add(new FileItem(filePath.getFileName().toString(), false));
                }
            }
            return new ListResult(new ListData(items));
        }
    }

    public record FileResult(FileData data) {
        public FileData getData() {
            return data;
        }
    }

    public record FileData(String content) {
        public String getContent() {
            return content;
        }
    }

    public record ListResult(ListData data) {
        public ListData getData() {
            return data;
        }
    }

    public record ListData(List<FileItem> listItems) {
        public List<FileItem> getListItems() {
            return listItems;
        }
    }

    public record FileItem(String name, boolean directory) {
        public String getName() {
            return name;
        }

        public boolean isDirectory() {
            return directory;
        }
    }
}
