/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.UploadFileStreamResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test FS operations through sandbox routing.
 * <p>
 * Mirrors Python's {@code test_fs.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_fs.py}.
 */
class TestFs extends BaseSandboxTest {

    @TempDir
    Path tempDir;

    @Test
    void testFsReadWrite() {
        String textPath = "test_basics.txt";
        String content = "Hello, world!\nLine 2";

        var writeRes = sysOp.fs().writeFile(textPath, content, "text", false, false, true, null, "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), writeRes.getCode());

        var readRes = sysOp.fs().readFile(textPath, "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), readRes.getCode());
        assertEquals(content, readRes.getData().getContentAsString());

        String appendFile = "test_append.txt";
        sysOp.fs().writeFile(appendFile, "Appended", "text", true, false, true, null, "utf-8", null);
        var appended = sysOp.fs().readFile(appendFile, "text", null, null, null, "utf-8", 0, null);
        assertEquals("\nAppended", appended.getData().getContentAsString());

        String binFile = "test.bin";
        byte[] binData = new byte[]{0x00, 0x01, 0x02};
        sysOp.fs().writeFile(binFile, binData, "bytes", false, false, true, null, "utf-8", null);
        var readBin = sysOp.fs().readFile(binFile, "bytes", null, null, null, "utf-8", 0, null);
        assertArrayEquals(binData, readBin.getData().getContentAsBytes());
    }

    @Test
    void testFsReadHeadTailLineRange() {
        String path = "multi_line.txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        sysOp.fs().writeFile(path, content, "text", false, false, true, null, "utf-8", null);

        var head = sysOp.fs().readFile(path, "text", 3, null, null, "utf-8", 0, null);
        assertEquals("Line 1\nLine 2\nLine 3\n", head.getData().getContentAsString());

        var tail = sysOp.fs().readFile(path, "text", null, 2, null, "utf-8", 0, null);
        assertEquals("Line 4\nLine 5", tail.getData().getContentAsString());

        int[] lineRange = new int[]{2, 4};
        var range = sysOp.fs().readFile(path, "text", null, null, lineRange, "utf-8", 0, null);
        assertEquals("Line 2\nLine 3\nLine 4\n", range.getData().getContentAsString());

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(
                path, "text", null, null, lineRange, "utf-8", 0, null));
        assertEquals("Line 2\nLine 3\nLine 4\n", chunks.stream()
                .map(chunk -> chunk.getData().getChunkContentAsString())
                .collect(Collectors.joining()));
        assertTrue(chunks.get(chunks.size() - 1).getData().isLastChunk());
    }

    @Test
    void testFsReadFileMutuallyExclusiveParams() {
        String path = "multi_line.txt";
        sysOp.fs().writeFile(path, "line1\nline2\nline3", "text", false, false, true, null, "utf-8", null);

        var res = sysOp.fs().readFile(path, "text", 2, 1, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("cannot"));

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(
                path, "text", -1, 2, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.get(0).getCode());
    }

    @Test
    void testFsReadFileNegativeZeroParams() {
        String path = "multi_line.txt";
        String content = "line1\nline2\nline3\nline4\nline5";
        sysOp.fs().writeFile(path, content, "text", false, false, true, null, "utf-8", null);

        assertEquals("", sysOp.fs().readFile(path, "text", -5, null, null, "utf-8", 0, null)
                .getData().getContentAsString());
        assertEquals("", sysOp.fs().readFile(path, "text", null, -5, null, "utf-8", 0, null)
                .getData().getContentAsString());
        assertEquals(content, sysOp.fs().readFile(path, "text", 0, null, null, "utf-8", 0, null)
                .getData().getContentAsString());
        assertEquals(content, sysOp.fs().readFile(path, "text", null, 0, null, "utf-8", 0, null)
                .getData().getContentAsString());
        assertEquals("", sysOp.fs().readFile(path, "text", null, null, new int[]{0, 0}, "utf-8", 0, null)
                .getData().getContentAsString());
        assertEquals("", sysOp.fs().readFile(path, "text", null, null, new int[]{1, -1}, "utf-8", 0, null)
                .getData().getContentAsString());

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(
                path, "text", -5, null, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals("", chunks.get(0).getData().getChunkContentAsString());
    }

    @Test
    void testFsReadFileBinaryModeParameters() {
        String path = "binary_test.txt";
        sysOp.fs().writeFile(path, "Hello\nLine 2", "text", false, false, true, null, "utf-8", null);

        var res = sysOp.fs().readFile(path, "bytes", 2, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("only supported in text mode"));

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(
                path, "bytes", null, null, new int[]{1, 2}, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.get(0).getCode());
    }

    @Test
    void testFsSecurityAndStreams() {
        var denied = sysOp.fs().readFile("../outside.txt", "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), denied.getCode());
        assertTrue(denied.getMessage().contains("Access denied") || denied.getMessage().contains("outside"));

        sysOp.fs().writeFile("stream.txt", "line1\nline2", "text", false, false, true, null, "utf-8", null);
        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(
                "stream.txt", "text", null, null, null, "utf-8", 0, null));
        assertEquals("line1\nline2", chunks.stream()
                .map(chunk -> chunk.getData().getChunkContentAsString())
                .collect(Collectors.joining()));
    }

    @Test
    void testFsUploadDownload() throws Exception {
        Path localSource = tempDir.resolve("upload.txt");
        Files.writeString(localSource, "Hello, upload and download!");

        var uploadRes = sysOp.fs().uploadFile(localSource.toString(), "uploaded.txt", false, true, true, 1048576, null);
        assertEquals(StatusCode.SUCCESS.getCode(), uploadRes.getCode());

        Path localTarget = tempDir.resolve("downloaded.txt");
        var downloadRes = sysOp.fs().downloadFile("uploaded.txt", localTarget.toString(), false, true, true, 1048576, null);
        assertEquals(StatusCode.SUCCESS.getCode(), downloadRes.getCode());
        assertEquals("Hello, upload and download!", Files.readString(localTarget));

        List<UploadFileStreamResult> streamUpload = collect(sysOp.fs().uploadFileStream(
                localSource.toString(), "stream_uploaded.txt", false, true, true, 1048576, null));
        assertEquals(1, streamUpload.size());

        Path streamTarget = tempDir.resolve("stream_downloaded.txt");
        List<DownloadFileStreamResult> streamDownload = collect(sysOp.fs().downloadFileStream(
                "stream_uploaded.txt", streamTarget.toString(), false, true, true, 16, null));
        assertEquals(1, streamDownload.size());
    }

    @Test
    void testFsListOperations() {
        sysOp.fs().writeFile("file1.txt", "Content 1", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("dir1/file2.txt", "Content 2", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("dir1/subdir1/file3.txt", "Content 3", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("dir2/file4.txt", "Content 4", "text", false, false, true, null, "utf-8", null);

        var listRes = sysOp.fs().listFiles(".", false, null, "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), listRes.getCode());
        assertTrue(names(listRes.getData().getListItems()).contains("file1.txt"));

        var recursiveRes = sysOp.fs().listFiles(".", true, null, "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), recursiveRes.getCode());
        assertTrue(names(recursiveRes.getData().getListItems()).containsAll(
                Set.of("file1.txt", "file2.txt", "file3.txt", "file4.txt")));

        var txtRes = sysOp.fs().listFiles(".", true, null, "name", false, List.of(".txt"), null);
        assertEquals(StatusCode.SUCCESS.getCode(), txtRes.getCode());
        assertTrue(txtRes.getData().getListItems().size() >= 4);

        var dirsRes = sysOp.fs().listDirectories(".", true, null, "name", false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), dirsRes.getCode());
        assertTrue(names(dirsRes.getData().getListItems()).containsAll(Set.of("dir1", "dir2", "subdir1")));
    }

    @Test
    void testFsSearchOperations() {
        sysOp.fs().writeFile("test1.txt", "Content 1", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("test2.txt", "Content 2", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("data1.csv", "CSV content", "text", false, false, true, null, "utf-8", null);
        sysOp.fs().writeFile("subdir/test3.txt", "Content 3", "text", false, false, true, null, "utf-8", null);

        var txtRes = sysOp.fs().searchFiles(".", "*.txt", null);
        assertEquals(StatusCode.SUCCESS.getCode(), txtRes.getCode());
        assertTrue(names(txtRes.getData().getMatchingFiles()).containsAll(Set.of("test1.txt", "test2.txt", "test3.txt")));

        var excludeRes = sysOp.fs().searchFiles(".", "*", List.of("*.csv"));
        assertEquals(StatusCode.SUCCESS.getCode(), excludeRes.getCode());
        assertTrue(excludeRes.getData().getMatchingFiles().stream().noneMatch(item -> item.getName().endsWith(".csv")));

        var noMatch = sysOp.fs().searchFiles(".", "*.xyz", null);
        assertEquals(StatusCode.SUCCESS.getCode(), noMatch.getCode());
        assertEquals(0, noMatch.getData().getTotalMatches());
    }

    private static Set<String> names(List<FileSystemItem> items) {
        return items.stream().map(FileSystemItem::getName).collect(Collectors.toSet());
    }

    private static <T> List<T> collect(Iterator<T> iterator) {
        List<T> results = new ArrayList<>();
        while (iterator.hasNext()) {
            results.add(iterator.next());
        }
        return results;
    }
}
