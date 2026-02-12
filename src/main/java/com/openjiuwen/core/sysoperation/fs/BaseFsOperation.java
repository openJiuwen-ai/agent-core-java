// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.fs;

import com.openjiuwen.core.sysoperation.base.BaseOperation;
import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.result.FileMode;
import com.openjiuwen.core.sysoperation.result.SortBy;
import com.openjiuwen.core.sysoperation.result.fs.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Base file system operation.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.fs.BaseFsOperation
 * 
 * <p>Defines the abstract interface for file system operations:
 * <ul>
 *   <li>readFile / readFileStream - Read file content</li>
 *   <li>writeFile - Write content to file</li>
 *   <li>uploadFile / uploadFileStream - Upload local file to target</li>
 *   <li>downloadFile / downloadFileStream - Download file to local</li>
 *   <li>listFiles / listDirectories - List files/directories</li>
 *   <li>searchFiles - Search files by pattern</li>
 * </ul>
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
public abstract class BaseFsOperation extends BaseOperation {

    /**
     * Constructs a BaseFsOperation.
     */
    public BaseFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    /**
     * Asynchronously read file with specified mode and parameters.
     * 
     * @param path Full or relative path to the file to read (required)
     * @param mode Reading mode - TEXT or BYTES (default: TEXT)
     * @param head Number of lines to read from the start (text mode only)
     * @param tail Number of lines to read from the end (text mode only)
     * @param lineRangeStart Start of line range (1-indexed, inclusive, text mode only)
     * @param lineRangeEnd End of line range (1-indexed, inclusive, text mode only)
     * @param encoding Character encoding for text mode (default: utf-8)
     * @param chunkSize Buffer size for bytes mode reading (default: 8192 bytes)
     * @param options Extended configuration options
     * @return CompletableFuture containing the read result
     */
    public abstract CompletableFuture<ReadFileResult> readFile(
        String path,
        FileMode mode,
        Integer head,
        Integer tail,
        Integer lineRangeStart,
        Integer lineRangeEnd,
        String encoding,
        int chunkSize,
        Map<String, Object> options
    );

    /**
     * Default readFile with common defaults.
     */
    public CompletableFuture<ReadFileResult> readFile(String path) {
        return readFile(path, FileMode.TEXT, null, null, null, null, "utf-8", 8192, null);
    }

    /**
     * Asynchronously read file streaming with specified mode and parameters.
     * 
     * @return Stream of read chunk results
     */
    public abstract Stream<ReadFileStreamResult> readFileStream(
        String path,
        FileMode mode,
        Integer head,
        Integer tail,
        Integer lineRangeStart,
        Integer lineRangeEnd,
        String encoding,
        int chunkSize,
        Map<String, Object> options
    );

    /**
     * Asynchronously writes content to a file.
     * 
     * @param path Full or relative path to the file to write (required)
     * @param content Data to write to the file
     * @param mode Writing mode: TEXT or BYTES (default: TEXT)
     * @param prependNewline Add newline before content (text mode only, default: true)
     * @param appendNewline Add newline after content (text mode only, default: false)
     * @param createIfNotExist Auto-create file if not exists (default: true)
     * @param permissions Octal file permissions (Unix/Linux only, default: "644")
     * @param encoding Character encoding for text mode (default: utf-8)
     * @param options Extended configuration options
     * @return CompletableFuture containing the write result
     */
    public abstract CompletableFuture<WriteFileResult> writeFile(
        String path,
        Object content,
        FileMode mode,
        boolean prependNewline,
        boolean appendNewline,
        boolean createIfNotExist,
        String permissions,
        String encoding,
        Map<String, Object> options
    );

    /**
     * Default writeFile with common defaults.
     */
    public CompletableFuture<WriteFileResult> writeFile(String path, String content) {
        return writeFile(path, content, FileMode.TEXT, true, false, true, "644", "utf-8", null);
    }

    /**
     * Asynchronous file upload.
     * 
     * @param localPath Local source file path (required)
     * @param targetPath Upload destination path (required)
     * @param overwrite Whether to overwrite existing target file (default: false)
     * @param createParentDirs Whether to auto-create target parent directories (default: true)
     * @param preservePermissions Whether to preserve file permissions (default: true)
     * @param chunkSize Chunk size for transfers (default: 1MB)
     * @param options Extended configuration options
     * @return CompletableFuture containing the upload result
     */
    public abstract CompletableFuture<UploadFileResult> uploadFile(
        String localPath,
        String targetPath,
        boolean overwrite,
        boolean createParentDirs,
        boolean preservePermissions,
        int chunkSize,
        Map<String, Object> options
    );

    /**
     * Asynchronous file upload streaming.
     */
    public abstract Stream<UploadFileStreamResult> uploadFileStream(
        String localPath,
        String targetPath,
        boolean overwrite,
        boolean createParentDirs,
        boolean preservePermissions,
        int chunkSize,
        Map<String, Object> options
    );

    /**
     * Asynchronous file download.
     * 
     * @param sourcePath Source file path (required)
     * @param localPath Local destination file path (required)
     * @param overwrite Whether to overwrite existing target file (default: false)
     * @param createParentDirs Whether to auto-create target parent directories (default: true)
     * @param preservePermissions Whether to preserve file permissions (default: true)
     * @param chunkSize Chunk size for transfers (default: 1MB)
     * @param options Extended configuration options
     * @return CompletableFuture containing the download result
     */
    public abstract CompletableFuture<DownloadFileResult> downloadFile(
        String sourcePath,
        String localPath,
        boolean overwrite,
        boolean createParentDirs,
        boolean preservePermissions,
        int chunkSize,
        Map<String, Object> options
    );

    /**
     * Asynchronous file download streaming.
     */
    public abstract Stream<DownloadFileStreamResult> downloadFileStream(
        String sourcePath,
        String localPath,
        boolean overwrite,
        boolean createParentDirs,
        boolean preservePermissions,
        int chunkSize,
        Map<String, Object> options
    );

    /**
     * Asynchronously list files under the specified path.
     * 
     * @param path Target parent directory path (required)
     * @param recursive Whether to list files recursively (default: false)
     * @param maxDepth Maximum recursion depth limit
     * @param sortBy Sorting field (default: NAME)
     * @param sortDescending Whether to sort descending (default: false)
     * @param fileTypes Filter by extensions (e.g., [".txt", ".pdf"])
     * @param options Extended configuration options
     * @return CompletableFuture containing the list result
     */
    public abstract CompletableFuture<ListFilesResult> listFiles(
        String path,
        boolean recursive,
        Integer maxDepth,
        SortBy sortBy,
        boolean sortDescending,
        List<String> fileTypes,
        Map<String, Object> options
    );

    /**
     * Default listFiles with common defaults.
     */
    public CompletableFuture<ListFilesResult> listFiles(String path) {
        return listFiles(path, false, null, SortBy.NAME, false, null, null);
    }

    /**
     * Asynchronously list directories under the specified path.
     * 
     * @param path Target parent directory path (required)
     * @param recursive Whether to list directories recursively (default: false)
     * @param maxDepth Maximum recursion depth limit
     * @param sortBy Sorting field (default: NAME)
     * @param sortDescending Whether to sort descending (default: false)
     * @param options Extended configuration options
     * @return CompletableFuture containing the list result
     */
    public abstract CompletableFuture<ListDirsResult> listDirectories(
        String path,
        boolean recursive,
        Integer maxDepth,
        SortBy sortBy,
        boolean sortDescending,
        Map<String, Object> options
    );

    /**
     * Default listDirectories with common defaults.
     */
    public CompletableFuture<ListDirsResult> listDirectories(String path) {
        return listDirectories(path, false, null, SortBy.NAME, false, null);
    }

    /**
     * Asynchronously search files under the specified path.
     * 
     * @param path Base directory path to start search (required)
     * @param pattern Search pattern to match file names
     * @param excludePatterns Optional patterns to exclude
     * @return CompletableFuture containing the search result
     */
    public abstract CompletableFuture<SearchFilesResult> searchFiles(
        String path,
        String pattern,
        List<String> excludePatterns
    );

    /**
     * Default searchFiles without exclusions.
     */
    public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern) {
        return searchFiles(path, pattern, null);
    }
}

