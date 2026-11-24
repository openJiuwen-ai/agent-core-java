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
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;
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

    @Test
    void writeAndEditMemoryUseRealLocalFsOperation() throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", "alpha\nbeta", localFs());
        String resolved = root.resolve("memory").resolve("MEMORY.md").normalize().toString();

        Map<String, Object> appended = MemoryToolOps.writeMemoryWithContext(context, "MEMORY.md", "gamma", true)
                .toCompletableFuture()
                .join();
        Map<String, Object> edited = MemoryToolOps.editMemoryWithContext(context, "MEMORY.md", "beta", "delta")
                .toCompletableFuture()
                .join();

        assertThat(appended).containsEntry("success", true);
        assertThat(edited).containsEntry("success", true);
        assertThat(Files.readString(Path.of(resolved))).isEqualTo("alpha\ndelta\ngamma");
    }

    @Test
    void codingMemoryWriteAndEditUseRealLocalFsOperation() throws IOException {
        LocalFsOperation fs = localFs();
        CodingMemoryToolContext context = codingContext("seed.md", THREE_LINES, fs);
        String content = """
                ---
                name: Architecture Note
                description: Useful project note
                type: project
                ---

                Remember this design decision.
                """;

        Map<String, Object> written = CodingMemoryToolOps
                .codingMemoryWriteWithContext(context, "arch.md", content)
                .join();
        Map<String, Object> edited = CodingMemoryToolOps
                .codingMemoryEditWithContext(context, "arch.md", "design", "architecture")
                .join();

        assertThat(written).containsEntry("success", true).containsEntry("mode", "create");
        assertThat(edited).containsEntry("success", true);
        assertThat(Files.readString(root.resolve("coding_memory").resolve("arch.md")))
                .contains("Remember this architecture decision.");
        assertThat(Files.readString(root.resolve("coding_memory").resolve("MEMORY.md")))
                .contains("[Architecture Note](arch.md)");
    }

    @Test
    void writeFailuresAreNotReportedAsSuccess() throws IOException {
        MemoryToolContext memoryContext = memoryContext("MEMORY.md", THREE_LINES, failingWriteFs());
        CodingMemoryToolContext codingContext = codingContext("seed.md", THREE_LINES, failingWriteFs());
        String content = """
                ---
                name: Rejected Note
                description: Must not be written
                type: project
                ---

                Body.
                """;

        Map<String, Object> memoryResult = MemoryToolOps
                .writeMemoryWithContext(memoryContext, "MEMORY.md", "rejected", false)
                .toCompletableFuture()
                .join();
        Map<String, Object> codingResult = CodingMemoryToolOps
                .codingMemoryWriteWithContext(codingContext, "rejected.md", content)
                .join();

        assertThat(memoryResult).containsEntry("success", false);
        assertThat(memoryResult.get("error").toString()).contains("write rejected");
        assertThat(codingResult).containsEntry("success", false);
        assertThat(codingResult.get("error").toString()).contains("write rejected");
        assertThat(Files.exists(root.resolve("coding_memory").resolve("rejected.md"))).isFalse();
    }

    @Test
    void codingMemoryListFailureDoesNotLookLikeAnEmptyDirectory() throws IOException {
        CodingMemoryToolContext context = codingContext("seed.md", THREE_LINES, failingListFs());
        String content = """
                ---
                name: New Note
                description: Must not overwrite after a failed listing
                type: project
                ---

                Body.
                """;

        Map<String, Object> result = CodingMemoryToolOps
                .codingMemoryWriteWithContext(context, "new.md", content)
                .join();

        assertThat(result).containsEntry("success", false);
        assertThat(result.get("error").toString()).contains("list rejected");
        assertThat(Files.exists(root.resolve("coding_memory").resolve("new.md"))).isFalse();
    }

    @Test
    void viewLinesHandlesExtremeOffsetAndLimitWithoutOverflow() {
        String[] lines = {"one", "two", "three"};

        assertThat(MemoryToolOps.viewLines(lines, 1, Integer.MAX_VALUE))
                .extracting(MemoryToolOps.ViewResult::text, MemoryToolOps.ViewResult::endLine)
                .containsExactly("one\ntwo\nthree", 3);
        assertThat(MemoryToolOps.viewLines(lines, Integer.MAX_VALUE, Integer.MAX_VALUE))
                .extracting(MemoryToolOps.ViewResult::text, MemoryToolOps.ViewResult::startLine)
                .containsExactly("", 0);
    }

    @Test
    void emptyFilesHaveZeroLines() throws IOException {
        assertThat(readMemory("")).containsEntry("success", true)
                .containsEntry("totalLines", 0)
                .containsEntry("start_line", 0)
                .containsEntry("end_line", 0);
        assertThat(readCodingMemory("")).containsEntry("success", true)
                .containsEntry("totalLines", 0)
                .containsEntry("start_line", 0)
                .containsEntry("end_line", 0);
    }

    @Test
    void zeroLimitReturnsEmptyRange() throws IOException {
        assertThat(readMemory(THREE_LINES, 2, 0)).containsEntry("success", true)
                .containsEntry("content", "")
                .containsEntry("totalLines", 3)
                .containsEntry("start_line", 0)
                .containsEntry("end_line", 0)
                .containsEntry("truncated", true);
        assertThat(readCodingMemory(THREE_LINES, 2, 0)).containsEntry("success", true)
                .containsEntry("start_line", 0)
                .containsEntry("end_line", 0);
    }

    @Test
    void trailingNewlineDoesNotCreateAnExtraLine() throws IOException {
        assertThat(readMemory("one\ntwo\n")).containsEntry("totalLines", 2)
                .containsEntry("content", "one\ntwo")
                .containsEntry("end_line", 2);
        assertThat(readCodingMemory("one\ntwo\n")).containsEntry("totalLines", 2)
                .containsEntry("end_line", 2);
    }

    @Test
    void readFailuresAreNotReportedAsSuccess() throws IOException {
        MemoryToolContext memoryContext = memoryContext("MEMORY.md", THREE_LINES, failingReadFs());
        CodingMemoryToolContext codingContext = codingContext("seed.md", THREE_LINES, failingReadFs());

        Map<String, Object> memoryResult = MemoryToolOps
                .readMemoryWithContext(memoryContext, "MEMORY.md", 1, 1)
                .toCompletableFuture()
                .join();
        Map<String, Object> codingResult = CodingMemoryToolOps
                .codingMemoryReadWithContext(codingContext, "seed.md", 1, 1)
                .join();

        assertThat(memoryResult).containsEntry("success", false);
        assertThat(memoryResult.get("error").toString()).contains("read rejected");
        assertThat(codingResult).containsEntry("success", false);
        assertThat(codingResult.get("error").toString()).contains("read rejected");
    }

    private Map<String, Object> readMemory(String content) throws IOException {
        return readMemory(content, null, null);
    }

    private Map<String, Object> readMemory(String content, Integer offset, Integer limit) throws IOException {
        MemoryToolContext context = memoryContext("MEMORY.md", content, new FakeFs(content));
        return MemoryToolOps.readMemoryWithContext(context, "MEMORY.md", offset, limit)
                .toCompletableFuture()
                .join();
    }

    private Map<String, Object> readCodingMemory(String content) throws IOException {
        return readCodingMemory(content, null, null);
    }

    private Map<String, Object> readCodingMemory(String content, Integer offset, Integer limit) throws IOException {
        CodingMemoryToolContext context = codingContext("readme.md", content, new FakeFs(content));
        return CodingMemoryToolOps.codingMemoryReadWithContext(context, "readme.md", offset, limit).join();
    }

    private LocalFsOperation failingReadFs() {
        return new LocalFsOperation("fs", OperationMode.LOCAL, "failing read fs",
                LocalWorkConfig.builder().workDir(root.toString()).build()) {
            @Override
            public CompletableFuture<ReadFileResult> readFile(
                    String path, FileMode mode, Integer head, Integer tail,
                    BaseFsProtocal.LineRange lineRange, String encoding,
                    int chunkSize, Map<String, Object> options) {
                ReadFileResult result = new ReadFileResult();
                result.setCode(-1);
                result.setMessage("read rejected");
                return CompletableFuture.completedFuture(result);
            }
        };
    }

    private LocalFsOperation failingWriteFs() {
        return new LocalFsOperation("fs", OperationMode.LOCAL, "failing write fs",
                LocalWorkConfig.builder().workDir(root.toString()).build()) {
            @Override
            public CompletableFuture<WriteFileResult> writeFile(
                    String path, String content, FileMode mode, boolean prependNewline,
                    boolean appendNewline, boolean append, boolean createIfNotExist,
                    String permissions, String encoding, Map<String, Object> options) {
                WriteFileResult result = new WriteFileResult();
                result.setCode(-1);
                result.setMessage("write rejected");
                return CompletableFuture.completedFuture(result);
            }
        };
    }

    private LocalFsOperation failingListFs() {
        return new LocalFsOperation("fs", OperationMode.LOCAL, "failing list fs",
                LocalWorkConfig.builder().workDir(root.toString()).build()) {
            @Override
            public CompletableFuture<ListFilesResult> listFiles(
                    String path, boolean recursive, Integer maxDepth, SortBy sortBy,
                    boolean sortDescending, List<String> fileTypes, Map<String, Object> options) {
                ListFilesResult result = new ListFilesResult();
                result.setCode(-1);
                result.setMessage("list rejected");
                return CompletableFuture.completedFuture(result);
            }
        };
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
