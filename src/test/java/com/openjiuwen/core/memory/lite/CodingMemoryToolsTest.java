/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

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
 * <p>Mirrors Python's coding-memory tool tests in
 * {@code tests/unit_tests/core/memory/lite/test_coding_memory_tools.py}.</p>
 */
class CodingMemoryToolsTest {

    @TempDir
    private Path root;

    @Test
    void codingMemoryWriteSuccess() throws Exception {
        Map<String, Object> result = write("test.md", frontmatter("test_memory", "test memory", "project")
                + "memory body");

        assertThat(result).containsEntry("success", true).containsEntry("type", "project");
    }

    @Test
    void codingMemoryWriteInvalidFrontmatter() throws Exception {
        Map<String, Object> result = write("test_invalid_fm.md", "content without frontmatter");

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("frontmatter");
    }

    @Test
    void codingMemoryWriteInvalidPath() throws Exception {
        Map<String, Object> result = write("test.txt", frontmatter("test", "test", "project") + "content");

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains(".md");
    }

    @Test
    void codingMemoryWriteTypeUser() throws Exception {
        assertThat(write("user_test.md", frontmatter("user_memory", "user memory", "user") + "body"))
                .containsEntry("success", true)
                .containsEntry("type", "user");
    }

    @Test
    void codingMemoryWriteTypeFeedback() throws Exception {
        assertThat(write("feedback_test.md", frontmatter("feedback_memory", "feedback memory", "feedback") + "body"))
                .containsEntry("success", true)
                .containsEntry("type", "feedback");
    }

    @Test
    void codingMemoryWriteTypeReference() throws Exception {
        assertThat(write("reference_test.md", frontmatter("reference_memory", "reference memory", "reference") + "body"))
                .containsEntry("success", true)
                .containsEntry("type", "reference");
    }

    @Test
    void codingMemoryWriteInvalidType() throws Exception {
        Map<String, Object> result = write("invalid.md", frontmatter("invalid_type", "invalid type", "knowledge")
                + "body");

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("type must be one of");
    }

    @Test
    void codingMemoryReadFullContent() throws Exception {
        CodingMemoryToolContext context = context();
        CodingMemoryToolOps.codingMemoryWriteWithContext(
                context,
                "read_test.md",
                frontmatter("test_read", "read test", "project") + "read body"
        ).get();

        Map<String, Object> result = CodingMemoryToolOps.codingMemoryReadWithContext(
                context,
                "read_test.md",
                null,
                null
        ).get();

        assertThat(result).containsEntry("success", true);
        assertThat(result.get("content")).asString().contains("read body");
        assertThat((Integer) result.get("totalLines")).isPositive();
    }

    @Test
    void codingMemoryReadWithOffsetLimit() throws Exception {
        CodingMemoryToolContext context = context();
        Map<String, Object> writeResult = CodingMemoryToolOps.codingMemoryWriteWithContext(
                context,
                "offset_test.md",
                frontmatter("test_offset", "offset test", "project") + "line1\nline2\nline3\nline4\nline5"
        ).get();

        Map<String, Object> readResult = CodingMemoryToolOps.codingMemoryReadWithContext(
                context,
                "offset_test.md",
                3,
                2
        ).get();

        assertThat(writeResult).containsEntry("success", true);
        assertThat(readResult).containsEntry("success", true);
    }

    @Test
    void codingMemoryReadNonexistent() throws Exception {
        Map<String, Object> result = CodingMemoryToolOps.codingMemoryReadWithContext(
                context(),
                "nonexistent.md",
                null,
                null
        ).get();

        assertThat(result).containsEntry("success", false);
    }

    @Test
    void codingMemoryEditSuccess() throws Exception {
        CodingMemoryToolContext context = context();
        CodingMemoryToolOps.codingMemoryWriteWithContext(
                context,
                "edit_test.md",
                frontmatter("test_edit", "edit test", "project") + "old body"
        ).get();

        Map<String, Object> result = CodingMemoryToolOps.codingMemoryEditWithContext(
                context,
                "edit_test.md",
                "old body",
                "new body"
        ).get();
        Map<String, Object> readResult = CodingMemoryToolOps.codingMemoryReadWithContext(
                context,
                "edit_test.md",
                null,
                null
        ).get();

        assertThat(result).containsEntry("success", true);
        assertThat(readResult.get("content")).asString().contains("new body");
    }

    @Test
    void codingMemoryEditOldTextNotFound() throws Exception {
        CodingMemoryToolContext context = context();
        CodingMemoryToolOps.codingMemoryWriteWithContext(
                context,
                "not_found_test.md",
                frontmatter("test_not_found", "not found test", "project") + "real body"
        ).get();

        Map<String, Object> result = CodingMemoryToolOps.codingMemoryEditWithContext(
                context,
                "not_found_test.md",
                "missing text",
                "new text"
        ).get();

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("old_text not found");
    }

    @Test
    void codingMemoryEditMultipleMatches() throws Exception {
        CodingMemoryToolContext context = context();
        CodingMemoryToolOps.codingMemoryWriteWithContext(
                context,
                "multi_test.md",
                frontmatter("test_multi", "multi test", "project") + "same text\nsame text"
        ).get();

        Map<String, Object> result = CodingMemoryToolOps.codingMemoryEditWithContext(
                context,
                "multi_test.md",
                "same text",
                "replacement"
        ).get();

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("appears");
    }

    @Test
    void codingMemoryEditEmptyOldText() throws Exception {
        Map<String, Object> result = CodingMemoryToolOps.codingMemoryEditWithContext(
                context(),
                "test.md",
                "",
                "new"
        ).get();

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error")).asString().contains("old_text cannot be empty");
    }

    @Test
    void upsertMemoryIndex() throws Exception {
        CodingMemoryToolContext context = context();

        CodingMemoryToolOps.codingMemoryWriteWithContext(
                context,
                "test.md",
                frontmatter("test", "index test", "project") + "indexed body"
        ).get();

        String index = Files.readString(root.resolve("coding_memory").resolve("MEMORY.md"), StandardCharsets.UTF_8);
        assertThat(index).contains("test.md");
    }

    @Test
    void validateCodingMemoryPathValid() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("valid.md", workspace());

        assertThat(result.valid()).isTrue();
        assertThat(result.value()).endsWith("valid.md");
    }

    @Test
    void validateCodingMemoryPathInvalidExt() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("invalid.txt", workspace());

        assertThat(result.valid()).isFalse();
        assertThat(result.value()).contains(".md");
    }

    @Test
    void validateCodingMemoryPathTraversal() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("../escape.md", workspace());

        assertThat(result.valid()).isFalse();
        assertThat(result.value()).contains("traversal");
    }

    @Test
    void validateCodingMemoryPathAbsolute() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("/absolute.md", workspace());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void parseFrontmatterValid() {
        Map<String, String> result = FrontmatterUtils.parseFrontmatter(
                frontmatter("test_memory", "test memory", "project") + "body"
        );

        assertThat(result)
                .containsEntry("name", "test_memory")
                .containsEntry("description", "test memory")
                .containsEntry("type", "project");
    }

    @Test
    void parseFrontmatterNoFrontmatter() {
        assertThat(FrontmatterUtils.parseFrontmatter("content without frontmatter")).isNull();
    }

    @Test
    void parseFrontmatterIncomplete() {
        Map<String, String> result = FrontmatterUtils.parseFrontmatter("""
                ---
                name: test
                ---
                body""");

        assertThat(result).containsEntry("name", "test");
    }

    @Test
    void validateFrontmatterValid() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "test", "description", "desc", "type", "project")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isEmpty();
    }

    @Test
    void validateFrontmatterMissingName() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("description", "desc", "type", "project")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("name");
    }

    @Test
    void validateFrontmatterMissingDescription() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "test", "type", "project")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("description");
    }

    @Test
    void validateFrontmatterMissingType() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "test", "description", "desc")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("type");
    }

    @Test
    void validateFrontmatterInvalidType() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "test", "description", "desc", "type", "invalid")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("type must be one of");
    }

    @Test
    void validateFrontmatterAllValidTypes() {
        for (String type : List.of("user", "feedback", "project", "reference")) {
            FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                    Map.of("name", "test", "description", "desc", "type", type)
            );
            assertThat(result.valid()).as("type %s should be valid", type).isTrue();
        }
    }

    private Map<String, Object> write(String path, String content) throws ExecutionException, InterruptedException {
        return CodingMemoryToolOps.codingMemoryWriteWithContext(context(), path, content).get();
    }

    private CodingMemoryToolContext context() {
        Path codingMemory = root.resolve("coding_memory");
        CodingMemoryToolContext context = new CodingMemoryToolContext(codingMemory.toString());
        context.setWorkspace(workspace());
        context.setSysOperation(new FakeSysOperation());
        return context;
    }

    private Workspace workspace() {
        return new Workspace(root);
    }

    private static String frontmatter(String name, String description, String type) {
        return """
                ---
                name: %s
                description: %s
                type: %s
                ---
                """.formatted(name, description, type);
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

        public Result listFiles(String directory, boolean recursive) {
            try {
                Path dir = Path.of(directory);
                if (!Files.isDirectory(dir)) {
                    return new Result(new Data("", List.of()));
                }
                List<FileItem> items = new ArrayList<>();
                try (var stream = Files.list(dir)) {
                    stream.forEach(path -> items.add(new FileItem(
                            path.getFileName().toString(),
                            Files.isDirectory(path)
                    )));
                }
                return new Result(new Data("", items));
            } catch (IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
        }
    }

    private record Result(Data data) {
    }

    private record Data(String content, List<FileItem> listItems) {
    }

    private record FileItem(String name, boolean directory) {
    }
}
