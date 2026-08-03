/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import com.openjiuwen.harness.workspace.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestFrontmatter}, {@code TestPathValidation}, {@code TestMemoryIndex},
 * and {@code TestFileHelpers} in
 * {@code tests/unit_tests/core/memory/lite/test_coding_memory.py}.
 */
class CodingMemoryBasicTest {

    @TempDir
    private Path root;

    @Test
    void parseFrontmatterSuccess() {
        String content = """
                ---
                name: Developer Role
                description: Senior Python developer
                type: user
                ---

                用户是高级 Python 开发者.
                """;

        Map<String, String> result = FrontmatterUtils.parseFrontmatter(content);

        assertThat(result)
                .isNotNull()
                .containsEntry("name", "Developer Role")
                .containsEntry("description", "Senior Python developer")
                .containsEntry("type", "user");
    }

    @Test
    void parseFrontmatterNoFrontmatter() {
        assertThat(FrontmatterUtils.parseFrontmatter("纯文本内容，没有 frontmatter")).isNull();
    }

    @Test
    void validateFrontmatterSuccess() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "Test Memory", "description", "A test memory", "type", "user"));

        assertThat(result.valid()).isTrue();
        assertThat(result.message()).isEmpty();
    }

    @Test
    void validateFrontmatterMissingField() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "Test Memory", "type", "user"));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("description");
    }

    @Test
    void validateFrontmatterInvalidType() {
        FrontmatterUtils.ValidationResult result = FrontmatterUtils.validateFrontmatter(
                Map.of("name", "Test Memory", "description", "A test memory", "type", "invalid_type"));

        assertThat(result.valid()).isFalse();
        assertThat(result.message()).contains("type");
    }

    @Test
    @SuppressWarnings("unchecked")
    void validTypesConstant() throws ReflectiveOperationException {
        Field field = FrontmatterUtils.class.getDeclaredField("VALID_TYPES");
        field.setAccessible(true);

        Set<String> validTypes = (Set<String>) field.get(null);

        assertThat(validTypes).contains("user", "feedback", "project", "reference").hasSize(4);
    }

    @Test
    void validatePathSuccess() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("user_role.md", workspace());

        assertThat(result.valid()).isTrue();
        assertThat(result.value()).endsWith("user_role.md");
    }

    @Test
    void validatePathTraversal() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("../etc/passwd.md", workspace());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void validatePathAbsolute() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("/etc/passwd.md", workspace());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void validatePathNotMd() {
        CodingMemoryToolOps.ValidationResult result =
                CodingMemoryToolOps.validateCodingMemoryPath("user_role.txt", workspace());

        assertThat(result.valid()).isFalse();
    }

    @Test
    void upsertNewEntry() throws ReflectiveOperationException {
        CodingMemoryToolContext context = context();
        Path codingMemoryDir = root.resolve("coding_memory");
        Map<String, String> frontmatter =
                Map.of("name", "Developer Role", "description", "Senior Python developer", "type", "user");

        invokeUpsertMemoryIndex(context, codingMemoryDir, "user_role.md", frontmatter);

        String index = invokeReadFileSafe(context, codingMemoryDir.resolve("MEMORY.md"));
        assertThat(index).contains("Developer Role", "user_role.md");
    }

    @Test
    void upsertUpdateExisting() throws ReflectiveOperationException {
        CodingMemoryToolContext context = context();
        Path codingMemoryDir = root.resolve("coding_memory");

        invokeUpsertMemoryIndex(context, codingMemoryDir, "user_role.md",
                Map.of("name", "Old Name", "description", "Old desc", "type", "user"));
        invokeUpsertMemoryIndex(context, codingMemoryDir, "user_role.md",
                Map.of("name", "New Name", "description", "New desc", "type", "user"));

        String index = invokeReadFileSafe(context, codingMemoryDir.resolve("MEMORY.md"));
        assertThat(index).contains("New Name").doesNotContain("Old Name");
    }

    @Test
    void readFileSafeSuccess() throws ReflectiveOperationException {
        CodingMemoryToolContext context = context();
        Path file = root.resolve("coding_memory").resolve("test.txt");
        ((FakeSysOperation) context.getSysOperation()).fs().writeFile(file.toString(), "测试内容", true, false);

        String content = invokeReadFileSafe(context, file);

        assertThat(content).contains("测试内容");
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

    private static void invokeUpsertMemoryIndex(CodingMemoryToolContext context,
                                                Path memoryDir,
                                                String filename,
                                                Map<String, String> frontmatter)
            throws ReflectiveOperationException {
        Method method = CodingMemoryToolOps.class.getDeclaredMethod(
                "upsertMemoryIndex",
                CodingMemoryToolContext.class,
                String.class,
                String.class,
                Map.class);
        method.setAccessible(true);
        method.invoke(null, context, memoryDir.toString(), filename, frontmatter);
    }

    private static String invokeReadFileSafe(CodingMemoryToolContext context, Path file)
            throws ReflectiveOperationException {
        Method method = CodingMemoryToolOps.class.getDeclaredMethod(
                "readFileSafe",
                CodingMemoryToolContext.class,
                String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, context, file.toString());
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
                Path target = Path.of(path);
                if (!Files.exists(target)) {
                    return new Result(new Data("", null));
                }
                return new Result(new Data(Files.readString(target, StandardCharsets.UTF_8), null));
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
                            StandardOpenOption.APPEND);
                } else if (createIfNotExist) {
                    Files.writeString(
                            target,
                            content,
                            StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.writeString(target, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                }
                return new Result(new Data("", null));
            } catch (IOException ex) {
                throw new IllegalStateException(ex.getMessage(), ex);
            }
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
                            Files.isDirectory(path))));
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
