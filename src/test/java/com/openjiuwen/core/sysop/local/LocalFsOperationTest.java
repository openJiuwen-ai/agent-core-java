/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for LocalFsOperation.
 * Mirrors Python's test_fs_operation.py test cases.
 */
class LocalFsOperationTest {

    @TempDir
    Path workDir;

    private SysOperation sysOp;

    @BeforeEach
    void setUp() {
        LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(workDir.toString())
                .build();
        SysOperationCard card = new SysOperationCard();
        card.setId("test_fs_op");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(config);
        sysOp = new SysOperation(card);
    }

    private BaseFsOperation fs() {
        return sysOp.fs();
    }

    private List<ReadFileStreamResult> collectStreamResults(Iterator<ReadFileStreamResult> it) {
        List<ReadFileStreamResult> results = new ArrayList<>();
        while (it.hasNext()) {
            results.add(it.next());
        }
        return results;
    }

    // ==================== Read & Write ====================

    @Test
    @DisplayName("Basic write and read file")
    void testBasicWriteAndRead() {
        String content = "Hello, world!\nLine 2";
        WriteFileResult wr = fs().writeFile("test_basics.txt", content, "text",
                false, false, true, null, "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), wr.getCode());

        ReadFileResult rr = fs().readFile("test_basics.txt", "text",
                null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), rr.getCode());
        assertEquals(content, rr.getData().getContent());
    }

    @Test
    @DisplayName("Write with prependNewline")
    void testWritePrependNewline() {
        // Write initial content
        fs().writeFile("append.txt", "Initial", "text",
                false, false, true, null, "utf-8", null);
        ReadFileResult r1 = fs().readFile("append.txt", "text",
                null, null, null, "utf-8", 0, null);
        assertEquals("Initial", r1.getData().getContent());

        // Overwrite with prepend newline
        fs().writeFile("append.txt", "Appended", "text",
                true, false, true, null, "utf-8", null);
        ReadFileResult r2 = fs().readFile("append.txt", "text",
                null, null, null, "utf-8", 0, null);
        assertEquals("\nAppended", r2.getData().getContent());
    }

    @Test
    @DisplayName("Write with appendNewline")
    void testWriteAppendNewline() {
        fs().writeFile("newline.txt", "content", "text",
                false, true, true, null, "utf-8", null);
        ReadFileResult rr = fs().readFile("newline.txt", "text",
                null, null, null, "utf-8", 0, null);
        assertEquals("content\n", rr.getData().getContent());
    }

    @Test
    @DisplayName("Write file with createIfNotExist=false fails for new file")
    void testWriteFileNoCreate() {
        WriteFileResult wr = fs().writeFile("nonexist.txt", "content", "text",
                false, false, false, null, "utf-8", null);
        assertNotEquals(StatusCode.SUCCESS.getCode(), wr.getCode());
    }

    @Test
    @DisplayName("Read non-existent file returns error")
    void testReadNonExistentFile() {
        ReadFileResult rr = fs().readFile("nonexistent.txt", "text",
                null, null, null, "utf-8", 0, null);
        assertNotEquals(StatusCode.SUCCESS.getCode(), rr.getCode());
    }

    // ==================== Read Head ====================

    @Test
    @DisplayName("Head with more lines than exist returns full content")
    void testReadHeadMoreThanExist() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                10, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(content, res.getData().getContent());
    }

    @Test
    @DisplayName("Head with exactly the number of lines returns full content")
    void testReadHeadExactLines() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                5, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(content, res.getData().getContent());
    }

    @Test
    @DisplayName("Head with fewer lines returns only first N lines")
    void testReadHeadFewerLines() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                3, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        String resultContent = res.getData().getContent();
        assertTrue(resultContent.contains("Line 1"));
        assertTrue(resultContent.contains("Line 2"));
        assertTrue(resultContent.contains("Line 3"));
        assertFalse(resultContent.contains("Line 4"));
        assertFalse(resultContent.contains("Line 5"));
    }

    @Test
    @DisplayName("Stream head with lastChunk validation")
    void testStreamHead() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("multi.txt", "text", 3, null, null, "utf-8", 0, null));
        assertEquals(3, chunks.size());

        // All but last should have lastChunk=false
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertFalse(chunks.get(i).getData().isLastChunk(),
                    "Chunk " + i + " should not be last");
        }
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk(),
                "Last chunk should be marked as last");

        // Verify content
        String joined = chunks.stream()
                .map(c -> c.getData().getChunkContent())
                .reduce("", String::concat);
        assertTrue(joined.contains("Line 1"));
        assertTrue(joined.contains("Line 2"));
        assertTrue(joined.contains("Line 3"));
        assertFalse(joined.contains("Line 4"));
    }

    // ==================== Read Tail ====================

    @Test
    @DisplayName("Tail with more lines than exist returns full content")
    void testReadTailMoreThanExist() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, 10, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(content, res.getData().getContent());
    }

    @Test
    @DisplayName("Tail with exactly the number of lines returns full content")
    void testReadTailExactLines() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, 5, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(content, res.getData().getContent());
    }

    @Test
    @DisplayName("Tail with fewer lines returns only last N lines")
    void testReadTailFewerLines() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, 2, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("Line 4\nLine 5", res.getData().getContent());
    }

    @Test
    @DisplayName("Tail on empty file returns empty content")
    void testReadTailEmptyFile() throws IOException {
        Files.writeString(workDir.resolve("empty.txt"), "");

        ReadFileResult res = fs().readFile("empty.txt", "text",
                null, 5, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("", res.getData().getContent());
    }

    @Test
    @DisplayName("Tail on single-line file")
    void testReadTailSingleLine() throws IOException {
        Files.writeString(workDir.resolve("single.txt"), "Only one line");

        ReadFileResult res = fs().readFile("single.txt", "text",
                null, 5, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("Only one line", res.getData().getContent());
    }

    @Test
    @DisplayName("Stream tail with lastChunk validation")
    void testStreamTail() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        // Stream tail with more lines
        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("multi.txt", "text", null, 10, null, "utf-8", 0, null));
        assertEquals(5, chunks.size());
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertFalse(chunks.get(i).getData().isLastChunk());
        }
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());

        // Stream tail with fewer lines
        List<ReadFileStreamResult> tailChunks = collectStreamResults(
                fs().readFileStream("multi.txt", "text", null, 2, null, "utf-8", 0, null));
        assertEquals(2, tailChunks.size());
        assertFalse(tailChunks.get(0).getData().isLastChunk());
        assertTrue(tailChunks.get(1).getData().isLastChunk());
        String joinedTail = tailChunks.stream()
                .map(c -> c.getData().getChunkContent())
                .reduce("", String::concat);
        assertTrue(joinedTail.contains("Line 4"));
        assertTrue(joinedTail.contains("Line 5"));
    }

    // ==================== Read Line Range ====================

    @Test
    @DisplayName("Line range within bounds")
    void testReadLineRangeWithinBounds() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{2, 4}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        String rc = res.getData().getContent();
        assertTrue(rc.contains("Line 2"));
        assertTrue(rc.contains("Line 3"));
        assertTrue(rc.contains("Line 4"));
        assertFalse(rc.contains("Line 1"));
        assertFalse(rc.contains("Line 5"));
    }

    @Test
    @DisplayName("Line range starting at 1")
    void testReadLineRangeFromStart() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{1, 3}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        String rc = res.getData().getContent();
        assertTrue(rc.contains("Line 1"));
        assertTrue(rc.contains("Line 2"));
        assertTrue(rc.contains("Line 3"));
        assertFalse(rc.contains("Line 4"));
    }

    @Test
    @DisplayName("Line range ending at last line")
    void testReadLineRangeToEnd() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{4, 5}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("Line 4\nLine 5", res.getData().getContent());
    }

    @Test
    @DisplayName("Line range with start > end returns empty")
    void testReadLineRangeStartGreaterThanEnd() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{4, 2}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("", res.getData().getContent());
    }

    @Test
    @DisplayName("Line range exceeding file length")
    void testReadLineRangeExceedLength() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{2, 10}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        String rc = res.getData().getContent();
        assertTrue(rc.contains("Line 2"));
        assertTrue(rc.contains("Line 5"));
        assertFalse(rc.contains("Line 1"));
    }

    @Test
    @DisplayName("Line range starting beyond file end returns empty")
    void testReadLineRangeBeyondEnd() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{10, 20}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("", res.getData().getContent());
    }

    @Test
    @DisplayName("Single line range")
    void testReadSingleLineRange() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{3, 3}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getContent().contains("Line 3"));
        assertFalse(res.getData().getContent().contains("Line 2"));
        assertFalse(res.getData().getContent().contains("Line 4"));
    }

    @Test
    @DisplayName("Line range at exact file end")
    void testReadLineRangeAtEnd() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{5, 5}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("Line 5", res.getData().getContent());
    }

    @Test
    @DisplayName("Line range on empty file")
    void testReadLineRangeEmptyFile() throws IOException {
        Files.writeString(workDir.resolve("empty.txt"), "");

        ReadFileResult res = fs().readFile("empty.txt", "text",
                null, null, new int[]{1, 5}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("", res.getData().getContent());
    }

    @Test
    @DisplayName("Stream line range with lastChunk validation")
    void testStreamLineRange() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("multi.txt", "text", null, null,
                        new int[]{2, 4}, "utf-8", 0, null));
        assertEquals(3, chunks.size());
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertFalse(chunks.get(i).getData().isLastChunk());
        }
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
    }

    @Test
    @DisplayName("Stream line range exceeding file length")
    void testStreamLineRangeExceedLength() throws IOException {
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("multi.txt", "text", null, null,
                        new int[]{2, 10}, "utf-8", 0, null));
        assertEquals(4, chunks.size()); // Lines 2-5
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
    }

    // ==================== Security (Path Traversal) ====================

    @Test
    @DisplayName("Path traversal is denied")
    void testPathTraversalDenied() {
        ReadFileResult res = fs().readFile("../outside.txt", "text",
                null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("Access denied") || res.getMessage().contains("traverses outside"),
                "Error message should mention access denial: " + res.getMessage());
    }

    // ==================== Basic Stream ====================

    @Test
    @DisplayName("Read file stream returns correct content")
    void testReadFileStream() throws IOException {
        Files.writeString(workDir.resolve("stream.txt"), "line1\nline2");

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("stream.txt", "text", null, null, null, "utf-8", 0, null));
        assertTrue(chunks.size() > 0);
        for (ReadFileStreamResult chunk : chunks) {
            assertEquals(StatusCode.SUCCESS.getCode(), chunk.getCode());
        }
        String joined = chunks.stream()
                .map(c -> c.getData().getChunkContent())
                .reduce("", String::concat);
        assertTrue(joined.contains("line1"));
        assertTrue(joined.contains("line2"));
    }

    // ==================== Mutually Exclusive Params ====================

    @Test
    @DisplayName("head and tail cannot be specified simultaneously")
    void testHeadAndTailMutuallyExclusive() throws IOException {
        Files.writeString(workDir.resolve("multi.txt"), "line1\nline2\nline3\nline4\nline5");

        ReadFileResult res = fs().readFile("multi.txt", "text",
                2, 2, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("cannot be specified simultaneously")
                || res.getMessage().contains("tail and head"),
                "Should mention mutually exclusive: " + res.getMessage());
    }

    @Test
    @DisplayName("head and line_range cannot be specified simultaneously")
    void testHeadAndLineRangeMutuallyExclusive() throws IOException {
        Files.writeString(workDir.resolve("multi.txt"), "line1\nline2\nline3\nline4\nline5");

        ReadFileResult res = fs().readFile("multi.txt", "text",
                2, null, new int[]{2, 4}, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("cannot be specified simultaneously")
                || res.getMessage().contains("head and line_range"),
                "Should mention mutually exclusive: " + res.getMessage());
    }

    @Test
    @DisplayName("tail and line_range cannot be specified simultaneously")
    void testTailAndLineRangeMutuallyExclusive() throws IOException {
        Files.writeString(workDir.resolve("multi.txt"), "line1\nline2\nline3\nline4\nline5");

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, 2, new int[]{2, 4}, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("cannot be specified simultaneously")
                || res.getMessage().contains("tail and line_range"),
                "Should mention mutually exclusive: " + res.getMessage());
    }

    @Test
    @DisplayName("Stream: mutually exclusive params return error")
    void testStreamMutuallyExclusiveParams() throws IOException {
        Files.writeString(workDir.resolve("multi.txt"), "line1\nline2\nline3\nline4\nline5");

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("multi.txt", "text", 2, 2, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.get(0).getCode());
    }

    @Test
    @DisplayName("head=0 with tail=2 should work (0 treated as not passed)")
    void testHeadZeroWithTail() throws IOException {
        Files.writeString(workDir.resolve("multi.txt"), "line1\nline2\nline3\nline4\nline5");

        ReadFileResult res = fs().readFile("multi.txt", "text",
                0, 2, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("line4\nline5", res.getData().getContent());
    }

    // ==================== Negative/Zero Params ====================

    @Test
    @DisplayName("Zero head returns full content (treated as not passed)")
    void testZeroHead() throws IOException {
        String content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                0, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(content, res.getData().getContent());
    }

    @Test
    @DisplayName("Zero tail returns full content (treated as not passed)")
    void testZeroTail() throws IOException {
        String content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, 0, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(content, res.getData().getContent());
    }

    @Test
    @DisplayName("Zero line_range returns empty")
    void testZeroLineRange() throws IOException {
        String content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{0, 0}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("", res.getData().getContent());
    }

    @Test
    @DisplayName("Negative line_range (1, -1) returns empty")
    void testNegativeEndLineRange() throws IOException {
        String content = "line1\nline2\nline3\nline4\nline5";
        Files.writeString(workDir.resolve("multi.txt"), content);

        ReadFileResult res = fs().readFile("multi.txt", "text",
                null, null, new int[]{1, -1}, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("", res.getData().getContent());
    }

    // ==================== Binary Mode Parameters ====================

    @Test
    @DisplayName("Binary mode with head fails")
    void testBinaryModeWithHead() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Hello, world!\nLine 2");

        ReadFileResult res = fs().readFile("test.txt", "bytes",
                2, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("only supported in text mode"));
    }

    @Test
    @DisplayName("Binary mode with tail fails")
    void testBinaryModeWithTail() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Hello, world!\nLine 2");

        ReadFileResult res = fs().readFile("test.txt", "bytes",
                null, 2, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("only supported in text mode"));
    }

    @Test
    @DisplayName("Binary mode with line_range fails")
    void testBinaryModeWithLineRange() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Hello, world!\nLine 2");

        ReadFileResult res = fs().readFile("test.txt", "bytes",
                null, null, new int[]{1, 2}, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("only supported in text mode"));
    }

    @Test
    @DisplayName("Stream: binary mode with head fails")
    void testStreamBinaryModeWithHead() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Hello, world!");

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("test.txt", "bytes", 2, null, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.get(0).getCode());
        assertTrue(chunks.get(0).getMessage().contains("only supported in text mode"));
    }

    @Test
    @DisplayName("Stream: binary mode with tail fails")
    void testStreamBinaryModeWithTail() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Hello, world!");

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("test.txt", "bytes", null, 2, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.get(0).getCode());
        assertTrue(chunks.get(0).getMessage().contains("only supported in text mode"));
    }

    @Test
    @DisplayName("Stream: binary mode with line_range fails")
    void testStreamBinaryModeWithLineRange() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Hello, world!");

        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("test.txt", "bytes", null, null,
                        new int[]{1, 2}, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.get(0).getCode());
        assertTrue(chunks.get(0).getMessage().contains("only supported in text mode"));
    }

    // ==================== Binary Read/Write ====================

    @Test
    @DisplayName("Binary file read returns base64 encoded content")
    void testBinaryReadWrite() throws IOException {
        byte[] binData = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF};
        Files.write(workDir.resolve("test.bin"), binData);

        ReadFileResult res = fs().readFile("test.bin", "bytes",
                null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        // In Java, binary read returns Base64 encoded string
        String b64Content = res.getData().getContent();
        byte[] decoded = Base64.getDecoder().decode(b64Content);
        assertArrayEquals(binData, decoded);
    }

    // ==================== Upload & Download ====================

    @Test
    @DisplayName("Normal upload and download")
    void testUploadAndDownload() throws IOException {
        String content = "Hello, upload and download!";
        Files.writeString(workDir.resolve("upload_test.txt"), content);

        String absPath = workDir.resolve("upload_test.txt").toString();
        UploadFileResult uploadRes = fs().uploadFile(absPath, "uploaded.txt",
                true, true, false, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), uploadRes.getCode());

        String downloadTarget = workDir.resolve("downloaded.txt").toString();
        DownloadFileResult downloadRes = fs().downloadFile("uploaded.txt", downloadTarget,
                true, true, false, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), downloadRes.getCode());

        String downloadedContent = Files.readString(Path.of(downloadTarget));
        assertEquals(content, downloadedContent);
    }

    @Test
    @DisplayName("Stream upload and download")
    void testStreamUploadAndDownload() throws IOException {
        String content = "Stream upload download test content";
        Files.writeString(workDir.resolve("stream_upload.txt"), content);

        String absPath = workDir.resolve("stream_upload.txt").toString();
        List<UploadFileStreamResult> uploadChunks = new ArrayList<>();
        Iterator<UploadFileStreamResult> uit = fs().uploadFileStream(absPath, "stream_uploaded.txt",
                true, true, false, 0, null);
        while (uit.hasNext()) {
            UploadFileStreamResult chunk = uit.next();
            assertEquals(StatusCode.SUCCESS.getCode(), chunk.getCode());
            uploadChunks.add(chunk);
        }
        assertTrue(uploadChunks.size() > 0);

        String downloadTarget = workDir.resolve("stream_downloaded.txt").toString();
        List<DownloadFileStreamResult> downloadChunks = new ArrayList<>();
        Iterator<DownloadFileStreamResult> dit = fs().downloadFileStream("stream_uploaded.txt",
                downloadTarget, true, true, false, 0, null);
        while (dit.hasNext()) {
            DownloadFileStreamResult chunk = dit.next();
            assertEquals(StatusCode.SUCCESS.getCode(), chunk.getCode());
            downloadChunks.add(chunk);
        }
        assertTrue(downloadChunks.size() > 0);

        String downloadedContent = Files.readString(Path.of(downloadTarget));
        assertEquals(content, downloadedContent);
    }

    @Test
    @DisplayName("Upload/download empty file")
    void testUploadDownloadEmptyFile() throws IOException {
        Files.writeString(workDir.resolve("empty.txt"), "");

        String absPath = workDir.resolve("empty.txt").toString();
        UploadFileResult uploadRes = fs().uploadFile(absPath, "empty_uploaded.txt",
                true, true, false, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), uploadRes.getCode());

        String downloadTarget = workDir.resolve("empty_downloaded.txt").toString();
        DownloadFileResult downloadRes = fs().downloadFile("empty_uploaded.txt",
                downloadTarget, true, true, false, 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), downloadRes.getCode());

        String downloadedContent = Files.readString(Path.of(downloadTarget));
        assertEquals("", downloadedContent);
    }

    // ==================== List Operations ====================

    @Test
    @DisplayName("List files - basic")
    void testListFilesBasic() throws IOException {
        Files.writeString(workDir.resolve("file1.txt"), "Content 1");
        Files.createDirectories(workDir.resolve("dir1"));
        Files.writeString(workDir.resolve("dir1").resolve("file2.txt"), "Content 2");

        ListFilesResult res = fs().listFiles(".", false, null,
                "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getTotalCount() >= 1);
    }

    @Test
    @DisplayName("List files - recursive")
    void testListFilesRecursive() throws IOException {
        Files.createDirectories(workDir.resolve("dir1").resolve("subdir1"));
        Files.createDirectories(workDir.resolve("dir2"));
        Files.writeString(workDir.resolve("file1.txt"), "Content 1");
        Files.writeString(workDir.resolve("dir1").resolve("file2.txt"), "Content 2");
        Files.writeString(workDir.resolve("dir1").resolve("subdir1").resolve("file3.txt"), "Content 3");
        Files.writeString(workDir.resolve("dir2").resolve("file4.txt"), "Content 4");

        ListFilesResult res = fs().listFiles(".", true, null,
                "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getTotalCount() >= 4);
    }

    @Test
    @DisplayName("List files - with file types filter")
    void testListFilesWithTypes() throws IOException {
        Files.writeString(workDir.resolve("file1.txt"), "Content 1");
        Files.writeString(workDir.resolve("file2.csv"), "Content 2");
        Files.writeString(workDir.resolve("file3.txt"), "Content 3");

        ListFilesResult res = fs().listFiles(".", false, null,
                "name", false, List.of(".txt"), null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        // Only .txt files should be returned
        for (FileSystemItem item : res.getData().getListItems()) {
            assertTrue(item.getName().endsWith(".txt"),
                    "Non-.txt file found: " + item.getName());
        }
    }

    @Test
    @DisplayName("List directories - basic")
    void testListDirectoriesBasic() throws IOException {
        Files.createDirectories(workDir.resolve("dir1"));
        Files.createDirectories(workDir.resolve("dir2"));
        Files.writeString(workDir.resolve("file1.txt"), "Content");

        ListDirsResult res = fs().listDirectories(".", false, null,
                "name", false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getTotalCount() >= 2);
        for (FileSystemItem item : res.getData().getListItems()) {
            assertTrue(item.isDirectory(), "Non-directory found: " + item.getName());
        }
    }

    @Test
    @DisplayName("List directories - recursive")
    void testListDirectoriesRecursive() throws IOException {
        Files.createDirectories(workDir.resolve("dir1").resolve("subdir1"));
        Files.createDirectories(workDir.resolve("dir2"));

        ListDirsResult res = fs().listDirectories(".", true, null,
                "name", false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getTotalCount() >= 3);
    }

    @Test
    @DisplayName("List files on empty directory")
    void testListFilesEmptyDir() throws IOException {
        Files.createDirectories(workDir.resolve("empty_dir"));

        ListFilesResult res = fs().listFiles("empty_dir", false, null,
                "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(0, res.getData().getTotalCount());
    }

    @Test
    @DisplayName("List directories on empty directory")
    void testListDirsEmptyDir() throws IOException {
        Files.createDirectories(workDir.resolve("empty_dir"));

        ListDirsResult res = fs().listDirectories("empty_dir", false, null,
                "name", false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(0, res.getData().getTotalCount());
    }

    // ==================== Search Operations ====================

    @Test
    @DisplayName("Search for txt files")
    void testSearchTxtFiles() throws IOException {
        Files.writeString(workDir.resolve("test1.txt"), "Content 1");
        Files.writeString(workDir.resolve("test2.txt"), "Content 2");
        Files.writeString(workDir.resolve("data1.csv"), "CSV content");
        Files.createDirectories(workDir.resolve("subdir"));
        Files.writeString(workDir.resolve("subdir").resolve("test3.txt"), "Content 3");

        SearchFilesResult res = fs().searchFiles(".", "*.txt", null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getTotalMatches() >= 3);
    }

    @Test
    @DisplayName("Search for csv files")
    void testSearchCsvFiles() throws IOException {
        Files.writeString(workDir.resolve("data1.csv"), "CSV 1");
        Files.writeString(workDir.resolve("data2.csv"), "CSV 2");
        Files.writeString(workDir.resolve("test.txt"), "TXT");

        SearchFilesResult res = fs().searchFiles(".", "*.csv", null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertTrue(res.getData().getTotalMatches() >= 2);
    }

    @Test
    @DisplayName("Search with exclude patterns")
    void testSearchWithExclude() throws IOException {
        Files.writeString(workDir.resolve("test1.txt"), "Content 1");
        Files.writeString(workDir.resolve("data1.csv"), "CSV");

        SearchFilesResult res = fs().searchFiles(".", "*", List.of("*.csv"));
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        boolean hasCsv = res.getData().getMatchingFiles().stream()
                .anyMatch(f -> f.getName().endsWith(".csv"));
        assertFalse(hasCsv, "CSV files should be excluded");
    }

    @Test
    @DisplayName("Search with no matching pattern")
    void testSearchNoMatch() throws IOException {
        Files.writeString(workDir.resolve("test.txt"), "Content");

        SearchFilesResult res = fs().searchFiles(".", "*.xyz", null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals(0, res.getData().getTotalMatches());
    }

    // ==================== Stream on non-existent file ====================

    @Test
    @DisplayName("Stream read on non-existent file returns error")
    void testStreamNonExistentFile() {
        List<ReadFileStreamResult> chunks = collectStreamResults(
                fs().readFileStream("nonexist.txt", "text",
                        null, null, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertNotEquals(StatusCode.SUCCESS.getCode(), chunks.get(0).getCode());
    }

    // ==================== Write to directory should fail ====================

    @Test
    @DisplayName("Write to a directory path returns error")
    void testWriteToDirectory() throws IOException {
        Files.createDirectories(workDir.resolve("mydir"));

        WriteFileResult res = fs().writeFile("mydir", "content", "text",
                false, false, true, null, "utf-8", null);
        assertNotEquals(StatusCode.SUCCESS.getCode(), res.getCode());
    }
}
