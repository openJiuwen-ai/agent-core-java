/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.protocal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.sys_operation.result.DownloadFileResult;
import com.openjiuwen.core.sys_operation.result.DownloadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.ListDirsResult;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.result.UploadFileResult;
import com.openjiuwen.core.sys_operation.result.UploadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.WriteFileResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code BaseFsProtocal} in
 * {@code openjiuwen/core/sys_operation/protocal/fs_protocal.py}.
 */
class BaseFsProtocalTest {

    @Test
    void readFileUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();
        CompletableFuture<ReadFileResult> expected = CompletableFuture.completedFuture(new ReadFileResult());
        protocal.readFileResult = expected;

        CompletableFuture<ReadFileResult> actual = protocal.readFile("/tmp/a.txt");

        assertSame(expected, actual);
        assertEquals("readFile", protocal.lastMethod);
        assertEquals("/tmp/a.txt", protocal.lastPath);
        assertEquals(BaseFsProtocal.MODE_TEXT, protocal.lastMode);
        assertNull(protocal.lastHead);
        assertNull(protocal.lastTail);
        assertNull(protocal.lastLineRange);
        assertEquals(BaseFsProtocal.DEFAULT_ENCODING, protocal.lastEncoding);
        assertEquals(BaseFsProtocal.DEFAULT_READ_CHUNK_SIZE, protocal.lastChunkSize);
        assertNull(protocal.lastOptions);
    }

    @Test
    void readFileStreamUsesStreamingChunkDefault() {
        RecordingProtocal protocal = new RecordingProtocal();
        Flow.Publisher<ReadFileStreamResult> expected = new SubmissionPublisher<>();
        protocal.readFileStreamResult = expected;

        Flow.Publisher<ReadFileStreamResult> actual = protocal.readFileStream("/tmp/stream.txt");

        assertSame(expected, actual);
        assertEquals("readFileStream", protocal.lastMethod);
        assertEquals(BaseFsProtocal.DEFAULT_READ_STREAM_CHUNK_SIZE, protocal.lastChunkSize);
    }

    @Test
    void writeFileTextUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();

        protocal.writeFile("/tmp/out.txt", "hello");

        assertEquals("writeFileText", protocal.lastMethod);
        assertEquals("/tmp/out.txt", protocal.lastPath);
        assertEquals("hello", protocal.lastTextContent);
        assertEquals(BaseFsProtocal.MODE_TEXT, protocal.lastMode);
        assertTrue(protocal.lastPrependNewline);
        assertFalse(protocal.lastAppendNewline);
        assertFalse(protocal.lastAppend);
        assertTrue(protocal.lastCreateIfNotExist);
        assertEquals(BaseFsProtocal.DEFAULT_PERMISSIONS, protocal.lastPermissions);
        assertEquals(BaseFsProtocal.DEFAULT_ENCODING, protocal.lastEncoding);
        assertNull(protocal.lastOptions);
    }

    @Test
    void writeFileBytesUsesBinaryDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();
        byte[] payload = new byte[] {1, 2, 3};

        protocal.writeFile("/tmp/out.bin", payload);

        assertEquals("writeFileBytes", protocal.lastMethod);
        assertEquals(BaseFsProtocal.MODE_BYTES, protocal.lastMode);
        assertArrayEquals(payload, protocal.lastBinaryContent);
    }

    @Test
    void listFilesUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();

        protocal.listFiles("/workspace");

        assertEquals("listFiles", protocal.lastMethod);
        assertEquals("/workspace", protocal.lastPath);
        assertFalse(protocal.lastRecursive);
        assertNull(protocal.lastMaxDepth);
        assertEquals(BaseFsProtocal.SORT_BY_NAME, protocal.lastSortBy);
        assertFalse(protocal.lastSortDescending);
        assertNull(protocal.lastFileTypes);
        assertNull(protocal.lastOptions);
    }

    @Test
    void searchFilesUsesNullExcludePatternsByDefault() {
        RecordingProtocal protocal = new RecordingProtocal();

        protocal.searchFiles("/workspace", "*.py");

        assertEquals("searchFiles", protocal.lastMethod);
        assertEquals("/workspace", protocal.lastPath);
        assertEquals("*.py", protocal.lastPattern);
        assertNull(protocal.lastExcludePatterns);
    }

    private static final class RecordingProtocal extends BaseFsProtocal {
        private String lastMethod;
        private String lastPath;
        private String lastTargetPath;
        private String lastMode;
        private Integer lastHead;
        private Integer lastTail;
        private LineRange lastLineRange;
        private String lastEncoding;
        private int lastChunkSize;
        private Map<String, Object> lastOptions;
        private String lastTextContent;
        private byte[] lastBinaryContent;
        private boolean lastPrependNewline;
        private boolean lastAppendNewline;
        private boolean lastAppend;
        private boolean lastCreateIfNotExist;
        private String lastPermissions;
        private boolean lastOverwrite;
        private boolean lastCreateParentDirs;
        private boolean lastPreservePermissions;
        private boolean lastRecursive;
        private Integer lastMaxDepth;
        private String lastSortBy;
        private boolean lastSortDescending;
        private List<String> lastFileTypes;
        private String lastPattern;
        private List<String> lastExcludePatterns;
        private CompletableFuture<ReadFileResult> readFileResult =
                CompletableFuture.completedFuture(new ReadFileResult());
        private Flow.Publisher<ReadFileStreamResult> readFileStreamResult = new SubmissionPublisher<>();

        @Override
        public CompletableFuture<ReadFileResult> readFile(
                String path,
                String mode,
                Integer head,
                Integer tail,
                LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options) {
            lastMethod = "readFile";
            lastPath = path;
            lastMode = mode;
            lastHead = head;
            lastTail = tail;
            lastLineRange = lineRange;
            lastEncoding = encoding;
            lastChunkSize = chunkSize;
            lastOptions = options;
            return readFileResult;
        }

        @Override
        public Flow.Publisher<ReadFileStreamResult> readFileStream(
                String path,
                String mode,
                Integer head,
                Integer tail,
                LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options) {
            lastMethod = "readFileStream";
            lastPath = path;
            lastMode = mode;
            lastHead = head;
            lastTail = tail;
            lastLineRange = lineRange;
            lastEncoding = encoding;
            lastChunkSize = chunkSize;
            lastOptions = options;
            return readFileStreamResult;
        }

        @Override
        public CompletableFuture<WriteFileResult> writeFile(
                String path,
                String content,
                String mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                Map<String, Object> options) {
            recordWrite(path, mode, prependNewline, appendNewline, append, createIfNotExist, permissions, encoding, options);
            lastMethod = "writeFileText";
            lastTextContent = content;
            lastBinaryContent = null;
            return CompletableFuture.completedFuture(new WriteFileResult());
        }

        @Override
        public CompletableFuture<WriteFileResult> writeFile(
                String path,
                byte[] content,
                String mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                Map<String, Object> options) {
            recordWrite(path, mode, prependNewline, appendNewline, append, createIfNotExist, permissions, encoding, options);
            lastMethod = "writeFileBytes";
            lastTextContent = null;
            lastBinaryContent = content;
            return CompletableFuture.completedFuture(new WriteFileResult());
        }

        @Override
        public CompletableFuture<UploadFileResult> uploadFile(
                String localPath,
                String targetPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                Map<String, Object> options) {
            recordTransfer("uploadFile", localPath, targetPath, overwrite, createParentDirs, preservePermissions, chunkSize, options);
            return CompletableFuture.completedFuture(new UploadFileResult());
        }

        @Override
        public Flow.Publisher<UploadFileStreamResult> uploadFileStream(
                String localPath,
                String targetPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                Map<String, Object> options) {
            recordTransfer("uploadFileStream", localPath, targetPath, overwrite, createParentDirs, preservePermissions, chunkSize, options);
            return new SubmissionPublisher<>();
        }

        @Override
        public CompletableFuture<DownloadFileResult> downloadFile(
                String sourcePath,
                String localPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                Map<String, Object> options) {
            recordTransfer("downloadFile", sourcePath, localPath, overwrite, createParentDirs, preservePermissions, chunkSize, options);
            return CompletableFuture.completedFuture(new DownloadFileResult());
        }

        @Override
        public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(
                String sourcePath,
                String localPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                Map<String, Object> options) {
            recordTransfer("downloadFileStream", sourcePath, localPath, overwrite, createParentDirs, preservePermissions, chunkSize, options);
            return new SubmissionPublisher<>();
        }

        @Override
        public CompletableFuture<ListFilesResult> listFiles(
                String path,
                boolean recursive,
                Integer maxDepth,
                String sortBy,
                boolean sortDescending,
                List<String> fileTypes,
                Map<String, Object> options) {
            lastMethod = "listFiles";
            lastPath = path;
            lastRecursive = recursive;
            lastMaxDepth = maxDepth;
            lastSortBy = sortBy;
            lastSortDescending = sortDescending;
            lastFileTypes = fileTypes;
            lastOptions = options;
            return CompletableFuture.completedFuture(new ListFilesResult());
        }

        @Override
        public CompletableFuture<ListDirsResult> listDirectories(
                String path,
                boolean recursive,
                Integer maxDepth,
                String sortBy,
                boolean sortDescending,
                Map<String, Object> options) {
            lastMethod = "listDirectories";
            lastPath = path;
            lastRecursive = recursive;
            lastMaxDepth = maxDepth;
            lastSortBy = sortBy;
            lastSortDescending = sortDescending;
            lastOptions = options;
            return CompletableFuture.completedFuture(new ListDirsResult());
        }

        @Override
        public CompletableFuture<SearchFilesResult> searchFiles(
                String path,
                String pattern,
                List<String> excludePatterns) {
            lastMethod = "searchFiles";
            lastPath = path;
            lastPattern = pattern;
            lastExcludePatterns = excludePatterns;
            return CompletableFuture.completedFuture(new SearchFilesResult());
        }

        private void recordWrite(
                String path,
                String mode,
                boolean prependNewline,
                boolean appendNewline,
                boolean append,
                boolean createIfNotExist,
                String permissions,
                String encoding,
                Map<String, Object> options) {
            lastPath = path;
            lastMode = mode;
            lastPrependNewline = prependNewline;
            lastAppendNewline = appendNewline;
            lastAppend = append;
            lastCreateIfNotExist = createIfNotExist;
            lastPermissions = permissions;
            lastEncoding = encoding;
            lastOptions = options;
        }

        private void recordTransfer(
                String method,
                String path,
                String targetPath,
                boolean overwrite,
                boolean createParentDirs,
                boolean preservePermissions,
                int chunkSize,
                Map<String, Object> options) {
            lastMethod = method;
            lastPath = path;
            lastTargetPath = targetPath;
            lastOverwrite = overwrite;
            lastCreateParentDirs = createParentDirs;
            lastPreservePermissions = preservePermissions;
            lastChunkSize = chunkSize;
            lastOptions = options;
        }
    }
}
