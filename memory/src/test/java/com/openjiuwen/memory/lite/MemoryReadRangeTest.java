/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.lite;

import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.harness.workspace.Workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for memory file reads with an offset/limit window.
 *
 * <p>Asserts that {@code (offset, limit)} is applied exactly once: the whole file is read,
 * then sliced in Java. The fake file systems deliberately honor every read signature used by
 * older implementations, so re-introducing a line range on the file-system call makes these
 * tests fail again.</p>
 */
class MemoryReadRangeTest {

    private static final String THREE_LINES = "line1\nline2\nline3";

    private static final String TEN_LINES = "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10";

    @TempDir
    private Path root;

    @Test
    void readMemoryAppliesOffsetOnce() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", THREE_LINES, new FakeFs(THREE_LINES));

        Map<String, Object> result = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", 2, 1)
                .toCompletableFuture()
                .join();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("content", "line2");
        assertThat(result).containsEntry("totalLines", 3);
        assertThat(result).containsEntry("start_line", 2);
        assertThat(result).containsEntry("end_line", 2);
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    void readMemoryReturnsRequestedMiddleWindow() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", TEN_LINES, new FakeFs(TEN_LINES));

        Map<String, Object> result = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", 5, 3)
                .toCompletableFuture()
                .join();

        assertThat(result).containsEntry("content", "line5\nline6\nline7");
        assertThat(result).containsEntry("totalLines", 10);
        assertThat(result).containsEntry("start_line", 5);
        assertThat(result).containsEntry("end_line", 7);
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    void readMemoryOffsetWithoutLimitReadsToEnd() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", TEN_LINES, new FakeFs(TEN_LINES));

        Map<String, Object> result = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", 5, null)
                .toCompletableFuture()
                .join();

        assertThat(result).containsEntry("content", "line5\nline6\nline7\nline8\nline9\nline10");
        assertThat(result).containsEntry("totalLines", 10);
        assertThat(result).containsEntry("start_line", 5);
        assertThat(result).containsEntry("end_line", 10);
        assertThat(result).containsEntry("truncated", false);
    }

    @Test
    void readMemoryWithoutRangeReturnsWholeFile() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", TEN_LINES, new FakeFs(TEN_LINES));

        Map<String, Object> result = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", null, null)
                .toCompletableFuture()
                .join();

        assertThat(result).containsEntry("content", TEN_LINES);
        assertThat(result).containsEntry("totalLines", 10);
        assertThat(result).containsEntry("start_line", 1);
        assertThat(result).containsEntry("end_line", 10);
        assertThat(result).containsEntry("truncated", false);
    }

    @Test
    void readMemoryOffsetBeyondEndReturnsEmptyRange() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", THREE_LINES, new FakeFs(THREE_LINES));

        Map<String, Object> result = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", 10, 2)
                .toCompletableFuture()
                .join();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("content", "");
        assertThat(result).containsEntry("totalLines", 3);
        assertThat(result).containsEntry("start_line", 0);
        assertThat(result).containsEntry("end_line", 0);
        assertThat(result).containsEntry("truncated", false);
    }

    @Test
    void codingMemoryAppliesOffsetOnce() throws IOException {
        CodingMemoryToolContext context =
                codingContext("readme.md", THREE_LINES, new FakeFs(THREE_LINES));

        Map<String, Object> result = CodingMemoryToolOps
                .codingMemoryReadWithContext(context, "readme.md", 2, 1)
                .join();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("content", "line2");
        assertThat(result).containsEntry("totalLines", 3);
        assertThat(result).containsEntry("start_line", 2);
        assertThat(result).containsEntry("end_line", 2);
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    void codingMemoryOffsetBeyondEndReturnsEmptyRange() throws IOException {
        CodingMemoryToolContext context =
                codingContext("readme.md", THREE_LINES, new FakeFs(THREE_LINES));

        Map<String, Object> result = CodingMemoryToolOps
                .codingMemoryReadWithContext(context, "readme.md", 10, 2)
                .join();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("content", "");
        assertThat(result).containsEntry("totalLines", 3);
        assertThat(result).containsEntry("start_line", 0);
        assertThat(result).containsEntry("end_line", 0);
        assertThat(result).containsEntry("truncated", false);
    }

    @Test
    void readMemoryUsesRealLocalFsOperation() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", THREE_LINES, localFs());

        Map<String, Object> result = MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", 2, 1)
                .toCompletableFuture()
                .join();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("content", "line2");
        assertThat(result).containsEntry("totalLines", 3);
        assertThat(result).containsEntry("start_line", 2);
        assertThat(result).containsEntry("end_line", 2);
    }

    @Test
    void codingMemoryUsesRealLocalFsOperation() throws IOException {
        CodingMemoryToolContext context = codingContext("readme.md", THREE_LINES, localFs());

        Map<String, Object> result = CodingMemoryToolOps
                .codingMemoryReadWithContext(context, "readme.md", 2, 1)
                .join();

        assertThat(result).containsEntry("success", true);
        assertThat(result).containsEntry("content", "line2");
        assertThat(result).containsEntry("totalLines", 3);
        assertThat(result).containsEntry("start_line", 2);
        assertThat(result).containsEntry("end_line", 2);
    }

    private LocalFsOperation localFs() {
        return new LocalFsOperation("fs", OperationMode.LOCAL, "local fs",
                LocalWorkConfig.builder().workDir(root.toString()).build());
    }

    private MemoryToolContext memoryContext(String name, String content, Object fs) throws IOException {
        Path memoryDir = root.resolve("memory");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve(name), content);
        MemoryToolContext context = new MemoryToolContext();
        context.setWorkspace(workspace());
        context.setSysOperation(new SysOperationShim(fs));
        return context;
    }

    private CodingMemoryToolContext codingContext(String name, String content, Object fs) throws IOException {
        Path memoryDir = root.resolve("coding_memory");
        Files.createDirectories(memoryDir);
        Files.writeString(memoryDir.resolve(name), content);
        Workspace workspace = workspace();
        CodingMemoryToolContext context = new CodingMemoryToolContext();
        context.setWorkspace(workspace);
        context.setCodingMemoryDir(workspace.getNodePath("coding_memory").normalize().toString());
        context.setSysOperation(new SysOperationShim(fs));
        return context;
    }

    private Workspace workspace() {
        return new Workspace(root.toString(), "cn");
    }

    /**
     * Exposes an {@code fs()} accessor, mirroring {@code SysOperation}.
     */
    private static final class SysOperationShim {
        private final Object fs;

        private SysOperationShim(Object fs) {
            this.fs = fs;
        }

        public Object fs() {
            return fs;
        }
    }

    /**
     * Honors every read signature the memory tool operations may dispatch to; each one slices
     * the same way {@code LocalFsOperation.selectTextLines} does (1-based, inclusive).
     */
    private static final class FakeFs {
        private final String content;

        private FakeFs(String content) {
            this.content = content;
        }

        public CompletableFuture<ReadFileResult> readFile(
                String path,
                BaseFsOperation.FileMode mode,
                Integer head,
                Integer tail,
                BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(result(lineRange));
        }

        public CompletableFuture<ReadFileResult> readFile(
                String path,
                String mode,
                Integer head,
                Integer tail,
                BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options
        ) {
            return CompletableFuture.completedFuture(result(lineRange));
        }

        public CompletableFuture<ReadFileResult> readFile(String path, int[] lineRange) {
            BaseFsProtocal.LineRange range = lineRange == null
                    ? null
                    : new BaseFsProtocal.LineRange(lineRange[0], lineRange[1]);
            return CompletableFuture.completedFuture(result(range));
        }

        public CompletableFuture<ReadFileResult> readFile(String path) {
            return CompletableFuture.completedFuture(result(null));
        }

        private ReadFileResult result(BaseFsProtocal.LineRange lineRange) {
            ReadFileResult result = new ReadFileResult();
            result.setData(ReadFileData.builder()
                    .path("")
                    .content(select(lineRange))
                    .mode(BaseFsProtocal.MODE_TEXT)
                    .build());
            return result;
        }

        private String select(BaseFsProtocal.LineRange lineRange) {
            if (lineRange == null) {
                return content;
            }
            String[] lines = content.split("\n", -1);
            int from = Math.max(0, lineRange.startLine() - 1);
            int to = lineRange.endLine() <= 0 ? lines.length : Math.min(lines.length, lineRange.endLine());
            if (from >= to) {
                return "";
            }
            return String.join("\n", List.of(lines).subList(from, to));
        }
    }
}
