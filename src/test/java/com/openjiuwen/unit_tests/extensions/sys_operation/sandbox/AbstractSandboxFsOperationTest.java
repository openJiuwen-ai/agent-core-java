/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.extensions.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.UploadFileStreamResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

abstract class AbstractSandboxFsOperationTest extends SandboxExtensionTestSupport {

    @TempDir
    Path tempDir;

    protected abstract SysOperation createSysOp();

    @Test
    void testFsReadWrite() {
        SysOperation sysOp = createSysOp();
        String fileName = "/tmp/test_basics_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        String content = "Hello, world!\nLine 2";

        var writeRes = sysOp.fs().writeFile(fileName, content, "text", false, false, true, "644", "utf-8", null);
        assertEquals(StatusCode.SUCCESS.getCode(), writeRes.getCode());

        var readRes = sysOp.fs().readFile(fileName, "text", null, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SUCCESS.getCode(), readRes.getCode());
        assertEquals(content, readRes.getData().getContentAsString());

        String appendFile = "/tmp/test_append_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        sysOp.fs().writeFile(appendFile, "Initial", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(appendFile, "Appended", "text", true, false, true, "644", "utf-8", null);
        assertEquals("\nAppended",
                sysOp.fs().readFile(appendFile, "text", null, null, null, "utf-8", 0, null)
                        .getData().getContentAsString());

        String binFile = "/tmp/test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".bin";
        byte[] binData = new byte[]{0x00, 0x01, 0x02};
        sysOp.fs().writeFile(binFile, binData, "bytes", false, false, true, "644", "utf-8", null);
        assertArrayEquals(
                binData,
                sysOp.fs().readFile(binFile, "bytes", null, null, null, "utf-8", 0, null).getData().getContentAsBytes()
        );
    }

    @Test
    void testFsReadHead() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_head_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        sysOp.fs().writeFile(file, content, "text", false, false, true, "644", "utf-8", null);

        assertEquals(content, sysOp.fs().readFile(file, "text", 10, null, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals(content, sysOp.fs().readFile(file, "text", 5, null, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals("Line 1\nLine 2\nLine 3\n",
                sysOp.fs().readFile(file, "text", 3, null, null, "utf-8", 0, null).getData().getContentAsString());

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(file, "text", 3, null, null, "utf-8", 0, null));
        assertEquals(3, chunks.size());
        assertTrue(chunks.getLast().getData().isLastChunk());
        assertEquals("Line 1\nLine 2\nLine 3\n", joinChunks(chunks));
    }

    @Test
    void testFsReadTail() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_tail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        sysOp.fs().writeFile(file, content, "text", false, false, true, "644", "utf-8", null);

        assertEquals(content, sysOp.fs().readFile(file, "text", null, 10, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals(content, sysOp.fs().readFile(file, "text", null, 5, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals("Line 4\nLine 5",
                sysOp.fs().readFile(file, "text", null, 2, null, "utf-8", 0, null).getData().getContentAsString());

        String emptyFile = "/tmp/empty_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        sysOp.fs().writeFile(emptyFile, "", "text", false, false, true, "644", "utf-8", null);
        assertEquals("", sysOp.fs().readFile(emptyFile, "text", null, 5, null, "utf-8", 0, null).getData().getContentAsString());

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(file, "text", null, 2, null, "utf-8", 0, null));
        assertEquals(2, chunks.size());
        assertTrue(chunks.getLast().getData().isLastChunk());
        assertEquals("Line 4\nLine 5", joinChunks(chunks));
    }

    @Test
    void testFsReadLineRange() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_range_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        String content = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";
        sysOp.fs().writeFile(file, content, "text", false, false, true, "644", "utf-8", null);

        assertEquals("Line 2\nLine 3\nLine 4\n",
                sysOp.fs().readFile(file, "text", null, null, new int[]{2, 4}, "utf-8", 0, null)
                        .getData().getContentAsString());
        assertEquals("Line 4\nLine 5",
                sysOp.fs().readFile(file, "text", null, null, new int[]{4, 5}, "utf-8", 0, null)
                        .getData().getContentAsString());
        assertEquals("",
                sysOp.fs().readFile(file, "text", null, null, new int[]{4, 2}, "utf-8", 0, null)
                        .getData().getContentAsString());
        assertEquals("Line 5",
                sysOp.fs().readFile(file, "text", null, null, new int[]{5, 5}, "utf-8", 0, null)
                        .getData().getContentAsString());

        List<ReadFileStreamResult> chunks = collect(
                sysOp.fs().readFileStream(file, "text", null, null, new int[]{2, 4}, "utf-8", 0, null)
        );
        assertEquals(3, chunks.size());
        assertEquals("Line 2\nLine 3\nLine 4\n", joinChunks(chunks));
    }

    @Test
    void testFsReadFileMutuallyExclusiveParams() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_exclusive_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        sysOp.fs().writeFile(file, "line1\nline2\nline3\nline4\nline5", "text", false, false, true, "644", "utf-8", null);

        var res = sysOp.fs().readFile(file, "text", 2, 2, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("cannot be specified simultaneously"));

        List<ReadFileStreamResult> chunks = collect(
                sysOp.fs().readFileStream(file, "text", -1, 2, null, "utf-8", 0, null)
        );
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.getFirst().getCode());
    }

    @Test
    void testFsReadFileNegativeZeroParams() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_neg_zero_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        String content = "line1\nline2\nline3\nline4\nline5";
        sysOp.fs().writeFile(file, content, "text", false, false, true, "644", "utf-8", null);

        assertEquals("", sysOp.fs().readFile(file, "text", -5, null, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals("", sysOp.fs().readFile(file, "text", null, -5, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals(content, sysOp.fs().readFile(file, "text", 0, null, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals(content, sysOp.fs().readFile(file, "text", null, 0, null, "utf-8", 0, null).getData().getContentAsString());
        assertEquals("", sysOp.fs().readFile(file, "text", null, null, new int[]{0, 0}, "utf-8", 0, null).getData().getContentAsString());

        List<ReadFileStreamResult> chunks = collect(sysOp.fs().readFileStream(file, "text", -5, null, null, "utf-8", 0, null));
        assertEquals(1, chunks.size());
        assertEquals("", chunks.getFirst().getData().getChunkContentAsString());
    }

    @Test
    void testFsReadFileBinaryModeParameters() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_binary_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        sysOp.fs().writeFile(file, "Hello, world!\nLine 2", "text", false, false, true, "644", "utf-8", null);

        var res = sysOp.fs().readFile(file, "bytes", 2, null, null, "utf-8", 0, null);
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), res.getCode());
        assertTrue(res.getMessage().contains("only supported in text mode"));

        List<ReadFileStreamResult> chunks = collect(
                sysOp.fs().readFileStream(file, "bytes", null, 2, null, "utf-8", 0, null)
        );
        assertEquals(1, chunks.size());
        assertEquals(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode(), chunks.getFirst().getCode());
    }

    @Test
    void testFsLargeBinaryFile() {
        SysOperation sysOp = createSysOp();
        String file = "/tmp/test_large_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".bin";
        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }

        assertEquals(StatusCode.SUCCESS.getCode(),
                sysOp.fs().writeFile(file, data, "bytes", false, false, true, "644", "utf-8", null).getCode());
        assertArrayEquals(
                data,
                sysOp.fs().readFile(file, "bytes", null, null, null, "utf-8", 0, null).getData().getContentAsBytes()
        );

        List<ReadFileStreamResult> chunks = collect(
                sysOp.fs().readFileStream(file, "bytes", null, null, null, "utf-8", 1024 * 64, null)
        );
        assertFalse(chunks.isEmpty());
        int total = chunks.stream().mapToInt(chunk -> chunk.getData().getChunkContentAsBytes().length).sum();
        assertEquals(data.length, total);
    }

    @Test
    void testFsUploadDownload() throws Exception {
        SysOperation sysOp = createSysOp();
        Path localSource = tempDir.resolve("upload_src.txt");
        Files.writeString(localSource, "Hello, upload and download!");

        String uploadTarget = "/tmp/uploaded_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        assertEquals(StatusCode.SUCCESS.getCode(),
                sysOp.fs().uploadFile(localSource.toString(), uploadTarget, false, true, true, 0, null).getCode());

        Path downloadTarget = tempDir.resolve("downloaded_file.txt");
        assertEquals(StatusCode.SUCCESS.getCode(),
                sysOp.fs().downloadFile(uploadTarget, downloadTarget.toString(), false, true, true, 0, null).getCode());
        assertEquals("Hello, upload and download!", Files.readString(downloadTarget));

        String streamUploadTarget = "/tmp/stream_uploaded_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        List<UploadFileStreamResult> uploadChunks = collect(
                sysOp.fs().uploadFileStream(localSource.toString(), streamUploadTarget, false, true, true, 16, null)
        );
        assertFalse(uploadChunks.isEmpty());

        Path streamDownloadTarget = tempDir.resolve("stream_downloaded.txt");
        List<DownloadFileStreamResult> downloadChunks = collect(
                sysOp.fs().downloadFileStream(streamUploadTarget, streamDownloadTarget.toString(), false, true, true, 16, null)
        );
        assertFalse(downloadChunks.isEmpty());
        assertEquals("Hello, upload and download!", Files.readString(streamDownloadTarget));

        Path emptySource = tempDir.resolve("empty.txt");
        Files.writeString(emptySource, "");
        String emptyUploadTarget = "/tmp/empty_uploaded_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";
        assertEquals(StatusCode.SUCCESS.getCode(),
                sysOp.fs().uploadFile(emptySource.toString(), emptyUploadTarget, false, true, true, 0, null).getCode());

        Path emptyDownloadTarget = tempDir.resolve("empty_downloaded.txt");
        assertEquals(StatusCode.SUCCESS.getCode(),
                sysOp.fs().downloadFile(emptyUploadTarget, emptyDownloadTarget.toString(), false, true, true, 0, null).getCode());
        assertEquals("", Files.readString(emptyDownloadTarget));
    }

    @Test
    void testFsListOperations() {
        SysOperation sysOp = createSysOp();
        String dir = "/tmp/test_list_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        sysOp.fs().writeFile(dir + "/file1.txt", "Content 1", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/dir1/file2.txt", "Content 2", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/dir1/subdir1/file3.txt", "Content 3", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/dir2/file4.txt", "Content 4", "text", false, false, true, "644", "utf-8", null);

        var listRes = sysOp.fs().listFiles(dir, false, null, "name", false, null, null);
        assertEquals(StatusCode.SUCCESS.getCode(), listRes.getCode());
        assertTrue(names(listRes.getData().getListItems()).contains("file1.txt"));

        var recursiveRes = sysOp.fs().listFiles(dir, true, null, "name", false, List.of(".txt"), null);
        assertEquals(StatusCode.SUCCESS.getCode(), recursiveRes.getCode());
        assertTrue(names(recursiveRes.getData().getListItems()).containsAll(Set.of("file1.txt", "file2.txt", "file3.txt", "file4.txt")));

        var dirsRes = sysOp.fs().listDirectories(dir, true, null, "name", false, null);
        assertEquals(StatusCode.SUCCESS.getCode(), dirsRes.getCode());
        assertTrue(names(dirsRes.getData().getListItems()).containsAll(Set.of("dir1", "dir2", "subdir1")));
    }

    @Test
    void testFsSearchOperations() {
        SysOperation sysOp = createSysOp();
        String dir = "/tmp/test_search_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        sysOp.fs().writeFile(dir + "/test1.txt", "Content 1", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/test2.txt", "Content 2", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/data1.csv", "CSV content", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/data2.csv", "More CSV", "text", false, false, true, "644", "utf-8", null);
        sysOp.fs().writeFile(dir + "/subdir/test3.txt", "Content 3", "text", false, false, true, "644", "utf-8", null);

        var txtSearchRes = sysOp.fs().searchFiles(dir, "*.txt", null);
        assertEquals(StatusCode.SUCCESS.getCode(), txtSearchRes.getCode());
        assertTrue(names(txtSearchRes.getData().getMatchingFiles()).containsAll(Set.of("test1.txt", "test2.txt", "test3.txt")));

        var csvSearchRes = sysOp.fs().searchFiles(dir, "*.csv", null);
        assertEquals(StatusCode.SUCCESS.getCode(), csvSearchRes.getCode());
        assertEquals(2, csvSearchRes.getData().getTotalMatches());

        var excludeSearchRes = sysOp.fs().searchFiles(dir, "*", List.of("*.csv"));
        assertEquals(StatusCode.SUCCESS.getCode(), excludeSearchRes.getCode());
        assertTrue(excludeSearchRes.getData().getMatchingFiles().stream().noneMatch(item -> item.getName().endsWith(".csv")));

        var noMatchRes = sysOp.fs().searchFiles(dir, "*.xyz", null);
        assertEquals(StatusCode.SUCCESS.getCode(), noMatchRes.getCode());
        assertEquals(0, noMatchRes.getData().getTotalMatches());
    }

    @Test
    void testFsWriteFileAppendText() {
        SysOperation sysOp = createSysOp();
        String path = "/tmp/test_append_text_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";

        sysOp.fs().writeFile(path, "Line 1", "text", false, false, true, "644", "utf-8", Map.of("append", false));
        sysOp.fs().writeFile(path, "Line 2", "text", false, false, true, "644", "utf-8", Map.of("append", true));
        sysOp.fs().writeFile(path, "\nLine 3", "text", false, false, true, "644", "utf-8", Map.of("append", true));

        assertEquals("Line 1Line 2\nLine 3",
                sysOp.fs().readFile(path, "text", null, null, null, "utf-8", 0, null).getData().getContentAsString());
    }

    @Test
    void testFsWriteFileAppendBinary() {
        SysOperation sysOp = createSysOp();
        String path = "/tmp/test_append_binary_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".bin";

        sysOp.fs().writeFile(path, new byte[]{0x00, 0x01}, "bytes", false, false, true, "644", "utf-8", Map.of("append", false));
        sysOp.fs().writeFile(path, new byte[]{0x02, 0x03}, "bytes", false, false, true, "644", "utf-8", Map.of("append", true));
        sysOp.fs().writeFile(path, new byte[]{0x04, 0x05}, "bytes", false, false, true, "644", "utf-8", Map.of("append", true));

        assertArrayEquals(
                new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05},
                sysOp.fs().readFile(path, "bytes", null, null, null, "utf-8", 0, null).getData().getContentAsBytes()
        );
    }

    @Test
    void testFsWriteFileAppendNewFile() {
        SysOperation sysOp = createSysOp();
        String path = "/tmp/test_append_new_file_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".txt";

        var res = sysOp.fs().writeFile(path, "First content", "text", false, false, true, "644", "utf-8", Map.of("append", true));
        assertEquals(StatusCode.SUCCESS.getCode(), res.getCode());
        assertEquals("First content",
                sysOp.fs().readFile(path, "text", null, null, null, "utf-8", 0, null).getData().getContentAsString());
    }

    private static String joinChunks(List<ReadFileStreamResult> chunks) {
        return chunks.stream().map(chunk -> chunk.getData().getChunkContentAsString()).collect(Collectors.joining());
    }

    private static Set<String> names(List<FileSystemItem> items) {
        return items.stream().map(FileSystemItem::getName).collect(Collectors.toSet());
    }
}
