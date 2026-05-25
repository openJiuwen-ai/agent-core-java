/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test FS operations through sandbox routing.
 * <p>
 * Mirrors Python's {@code test_fs.py} in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_fs.py}.
 *
 * <p>Note: Sandbox mode is stubbed in Java - tests are disabled until implemented.
 */
@Disabled("Sandbox mode is not fully implemented in Java")
class TestFs extends BaseSandboxTest {

    @Test
    void testFsReadWrite() {
        /** Test text and binary read/write behavior in sandbox mode. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        String textPath = "test_basics.txt";
        String content = "Hello, world!\nLine 2";

        WriteFileResult writeRes = fs.writeFile(textPath, content, "text", false, false, true, null, "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), writeRes.getCode());

        ReadFileResult readRes = fs.readFile(textPath, "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), readRes.getCode());
        assertEquals(content, readRes.getData().getContentAsString());

        // Test append
        String appendFile = "test_append.txt";
        fs.writeFile(appendFile, "Appended", "text", true, false, true, null, "utf-8", null);
        ReadFileResult appended = fs.readFile(appendFile, "text", null, null, null, "utf-8", 0, null);
        assertEquals("\nAppended", appended.getData().getContentAsString());

        // Test binary
        String binFile = "test.bin";
        byte[] binData = new byte[]{0x00, 0x01, 0x02};
        fs.writeFile(binFile, binData, "bytes", false, false, true, null, null, null);
        ReadFileResult readBin = fs.readFile(binFile, "bytes", null, null, null, null, 0, null);
        assertArrayEquals(binData, readBin.getData().getContentAsBytes());
    }

    @Test
    void testFsReadHeadTailLineRange() {
        /** Test line slicing for read_file and read_file_stream. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        String path = "multi_line.txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        fs.writeFile(path, content, "text", false, false, true, null, "utf-8", null);

        // Test head
        ReadFileResult head = fs.readFile(path, "text", 3, null, null, "utf-8", 0, null);
        assertEquals("Line 1\nLine 2\nLine 3\n", head.getData().getContentAsString());

        // Test tail
        ReadFileResult tail = fs.readFile(path, "text", null, 2, null, "utf-8", 0, null);
        assertEquals("Line 4\nLine 5", tail.getData().getContentAsString());

        // Test line range
        int[] lineRange = new int[]{2, 4};
        ReadFileResult rangeRes = fs.readFile(path, "text", null, null, lineRange, "utf-8", 0, null);
        assertEquals("Line 2\nLine 3\nLine 4\n", rangeRes.getData().getContentAsString());

        // Test streaming with line range
        List<ReadFileStreamResult> chunks = new ArrayList<>();
        Iterator<ReadFileStreamResult> iter = fs.readFileStream(path, "text", null, null, lineRange, "utf-8", 0, null);
        while (iter.hasNext()) {
            chunks.add(iter.next());
        }
        StringBuilder combined = new StringBuilder();
        for (ReadFileStreamResult c : chunks) {
            combined.append(c.getData().getChunkContentAsString());
        }
        assertEquals("Line 2\nLine 3\nLine 4\n", combined.toString());
    }

    @Test
    void testFsReadFileStreamChunked() {
        /** Test streaming file read with small chunks. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        String path = "stream_test.txt";
        String content = "This is a test file for streaming";
        fs.writeFile(path, content, "text", false, false, true, null, "utf-8", null);

        List<ReadFileStreamResult> chunks = new ArrayList<>();
        Iterator<ReadFileStreamResult> iter = fs.readFileStream(path, "text", null, null, null, "utf-8", 10, null);
        while (iter.hasNext()) {
            chunks.add(iter.next());
        }

        assertTrue(chunks.size() > 1);
        StringBuilder combined = new StringBuilder();
        for (ReadFileStreamResult c : chunks) {
            combined.append(c.getData().getChunkContentAsString());
        }
        assertEquals(content, combined.toString());
    }

    @Test
    void testFsListFiles() {
        /** Test list_files operation. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("file1.txt", "1", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("dir1/file2.txt", "2", "text", false, false, true, null, "utf-8", null);

        ListFilesResult res = fs.listFiles(".", true, null, null, false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        Set<String> names = new HashSet<>();
        for (FileSystemItem item : res.getData().getListItems()) {
            names.add(item.getName());
        }
        assertTrue(names.contains("file1.txt"));
        assertTrue(names.contains("file2.txt"));
    }

    @Test
    void testFsSearchFiles() {
        /** Test search_files operation. */
        assumeSandboxImplemented();

        BaseFsOperation fs = sysOp.fs();
        fs.writeFile("matched.txt", "match", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("nested/other.txt", "nested", "text", false, false, true, null, "utf-8", null);
        fs.writeFile("ignored.csv", "csv", "text", false, false, true, null, "utf-8", null);

        SearchFilesResult res = fs.searchFiles(".", "*.txt", null);
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());

        Set<String> names = new HashSet<>();
        for (FileSystemItem item : res.getData().getMatchingFiles()) {
            names.add(item.getName());
        }
        assertTrue(names.contains("matched.txt"));
        assertTrue(names.contains("other.txt"));
        assertFalse(names.contains("ignored.csv"));
    }

    private void assumeSandboxImplemented() {
        Assumptions.assumeTrue(sysOp != null, "Sandbox mode is not fully implemented");
    }
}