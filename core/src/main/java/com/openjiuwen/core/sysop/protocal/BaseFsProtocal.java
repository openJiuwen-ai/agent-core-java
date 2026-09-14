/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.protocal;

import com.openjiuwen.core.sysop.result.DownloadFileResult;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.UploadFileResult;
import com.openjiuwen.core.sysop.result.UploadFileStreamResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Unified FS method signatures shared by operation and provider layers.
 *
 * <p>Mirrors Python's {@code BaseFsProtocal} in
 * {@code openjiuwen/core/sys_operation/protocal/fs_protocal.py}.</p>
 */
public abstract class BaseFsProtocal {

    public static final String MODE_TEXT = "text";

    public static final String MODE_BYTES = "bytes";

    public static final String SORT_BY_NAME = "name";

    public static final String SORT_BY_MODIFIED_TIME = "modified_time";

    public static final String SORT_BY_SIZE = "size";

    public static final String DEFAULT_ENCODING = "utf-8";

    public static final String DEFAULT_PERMISSIONS = "644";

    public static final int DEFAULT_READ_CHUNK_SIZE = 0;

    public static final int DEFAULT_UPLOAD_CHUNK_SIZE = 0;

    public static final int DEFAULT_DOWNLOAD_CHUNK_SIZE = 0;

    public static final int DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;

    public static final int DEFAULT_UPLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;

    public static final int DEFAULT_READ_STREAM_CHUNK_SIZE = 8192;

    public abstract CompletableFuture<ReadFileResult> readFile(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options);

    public abstract Flow.Publisher<ReadFileStreamResult> readFileStream(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options);

    public abstract CompletableFuture<WriteFileResult> writeFile(
            String path,
            String content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options);

    public abstract CompletableFuture<WriteFileResult> writeFile(
            String path,
            byte[] content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options);

    public abstract CompletableFuture<UploadFileResult> uploadFile(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract Flow.Publisher<UploadFileStreamResult> uploadFileStream(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract CompletableFuture<DownloadFileResult> downloadFile(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract Flow.Publisher<DownloadFileStreamResult> downloadFileStream(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options);

    public abstract CompletableFuture<ListFilesResult> listFiles(
            String path,
            boolean recursive,
            Integer maxDepth,
            String sortBy,
            boolean sortDescending,
            List<String> fileTypes,
            Map<String, Object> options);

    public abstract CompletableFuture<ListDirsResult> listDirectories(
            String path,
            boolean recursive,
            Integer maxDepth,
            String sortBy,
            boolean sortDescending,
            Map<String, Object> options);

    public abstract CompletableFuture<SearchFilesResult> searchFiles(
            String path,
            String pattern,
            List<String> excludePatterns);

    public CompletableFuture<ReadFileResult> readFile(String path) {
        return readFile(path, MODE_TEXT, null, null, null, DEFAULT_ENCODING, DEFAULT_READ_CHUNK_SIZE, null);
    }

    public Flow.Publisher<ReadFileStreamResult> readFileStream(String path) {
        return readFileStream(
                path,
                MODE_TEXT,
                null,
                null,
                null,
                DEFAULT_ENCODING,
                DEFAULT_READ_STREAM_CHUNK_SIZE,
                null);
    }

    public CompletableFuture<WriteFileResult> writeFile(String path, String content) {
        return writeFile(
                path,
                content,
                MODE_TEXT,
                true,
                false,
                false,
                true,
                DEFAULT_PERMISSIONS,
                DEFAULT_ENCODING,
                null);
    }

    public CompletableFuture<WriteFileResult> writeFile(String path, byte[] content) {
        return writeFile(
                path,
                content,
                MODE_BYTES,
                true,
                false,
                false,
                true,
                DEFAULT_PERMISSIONS,
                DEFAULT_ENCODING,
                null);
    }

    public CompletableFuture<UploadFileResult> uploadFile(String localPath, String targetPath) {
        return uploadFile(localPath, targetPath, false, true, true, DEFAULT_UPLOAD_CHUNK_SIZE, null);
    }

    public Flow.Publisher<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath) {
        return uploadFileStream(
                localPath,
                targetPath,
                false,
                true,
                true,
                DEFAULT_UPLOAD_STREAM_CHUNK_SIZE,
                null);
    }

    public CompletableFuture<DownloadFileResult> downloadFile(String sourcePath, String localPath) {
        return downloadFile(sourcePath, localPath, false, true, true, DEFAULT_DOWNLOAD_CHUNK_SIZE, null);
    }

    public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath) {
        return downloadFileStream(
                sourcePath,
                localPath,
                false,
                true,
                true,
                DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE,
                null);
    }

    public CompletableFuture<ListFilesResult> listFiles(String path) {
        return listFiles(path, false, null, SORT_BY_NAME, false, null, null);
    }

    public CompletableFuture<ListDirsResult> listDirectories(String path) {
        return listDirectories(path, false, null, SORT_BY_NAME, false, null);
    }

    public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern) {
        return searchFiles(path, pattern, null);
    }

    /**
     * Mirrors Python's line-range tuple in
     * {@code openjiuwen/core/sys_operation/protocal/fs_protocal.py}.
     */
    public record LineRange(int startLine, int endLine) {
    }
}
