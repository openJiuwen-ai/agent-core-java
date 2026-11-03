/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.providers;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
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
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Legacy synchronous FS provider SPI (renamed from BaseFSProvider to avoid Windows case-collision with BaseFsProvider).
 *
 * Abstract base class for file system providers in the sandbox environment.
 * Defines the SPI contract for file read, write, upload, download, list, and search operations.
 * 
 * @version 1.0
 * @since 0.1.7
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public abstract class SyncBaseFSProvider {
    /**
     * endpoint.
     * 
     * @since 0.1.7
     */
    protected final SandboxEndpoint endpoint;

    /**
     * config.
     * 
     * @since 0.1.7
     */
    protected final SandboxGatewayConfig config;

    /**
     * Constructs a SyncBaseFSProvider with the given endpoint and config.
     * 
     * @param endpoint the sandbox endpoint
     * @param config the sandbox gateway configuration
     * @since 0.1.7
     */
    protected SyncBaseFSProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    /**
     * Reads a file from the sandbox and returns the result.
     * 
     * @param path the file path to read
     * @param mode the read mode
     * @param head the number of lines to read from the head
     * @param tail the number of lines to read from the tail
     * @param lineRange the line range to read
     * @param encoding the file encoding
     * @param chunkSize the chunk size for streaming
     * @param options additional options map
     * @return the read file result
     * @since 0.1.7
     */
    public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange,
            String encoding, int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".readFile is not implemented");
    }

    /**
     * Reads a file from the sandbox as a stream of chunks.
     * 
     * @param path the file path to read
     * @param mode the read mode
     * @param head the number of lines to read from the head
     * @param tail the number of lines to read from the tail
     * @param lineRange the line range to read
     * @param encoding the file encoding
     * @param chunkSize the chunk size for streaming
     * @param options additional options map
     * @return an iterator of read file stream results
     * @since 0.1.7
     */
    public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail,
            int[] lineRange, String encoding, int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".readFileStream is not implemented");
    }

    /**
     * Writes content to a file in the sandbox.
     * 
     * @param path the file path to write
     * @param content the content to write
     * @param mode the write mode
     * @param isPrependNewline whether to prepend a newline before content
     * @param isAppendNewline whether to append a newline after content
     * @param isCreateIfMissing whether to create the file if it does not exist
     * @param permissions the file permissions
     * @param encoding the file encoding
     * @param options additional options map
     * @return the write file result
     * @since 0.1.7
     */
    public WriteFileResult writeFile(String path, Object content, String mode, boolean isPrependNewline,
            boolean isAppendNewline, boolean isCreateIfMissing, String permissions, String encoding,
            Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".writeFile is not implemented");
    }

    /**
     * Uploads a local file to the sandbox.
     * 
     * @param localPath the local file path
     * @param targetPath the target path in the sandbox
     * @param isOverwrite whether to overwrite existing files
     * @param isCreateParentDirs whether to create parent directories
     * @param isPreservePermissions whether to preserve file permissions
     * @param chunkSize the chunk size for upload
     * @param options additional options map
     * @return the upload file result
     * @since 0.1.7
     */
    public UploadFileResult uploadFile(String localPath, String targetPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".uploadFile is not implemented");
    }

    /**
     * Uploads a local file to the sandbox as a stream of chunks.
     * 
     * @param localPath the local file path
     * @param targetPath the target path in the sandbox
     * @param isOverwrite whether to overwrite existing files
     * @param isCreateParentDirs whether to create parent directories
     * @param isPreservePermissions whether to preserve file permissions
     * @param chunkSize the chunk size for upload
     * @param options additional options map
     * @return an iterator of upload file stream results
     * @since 0.1.7
     */
    public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".uploadFileStream is not implemented");
    }

    /**
     * Downloads a file from the sandbox to a local path.
     * 
     * @param sourcePath the source path in the sandbox
     * @param localPath the local file path to download to
     * @param isOverwrite whether to overwrite existing local files
     * @param isCreateParentDirs whether to create parent directories locally
     * @param isPreservePermissions whether to preserve file permissions
     * @param chunkSize the chunk size for download
     * @param options additional options map
     * @return the download file result
     * @since 0.1.7
     */
    public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".downloadFile is not implemented");
    }

    /**
     * Downloads a file from the sandbox as a stream of chunks.
     * 
     * @param sourcePath the source path in the sandbox
     * @param localPath the local file path to download to
     * @param isOverwrite whether to overwrite existing local files
     * @param isCreateParentDirs whether to create parent directories locally
     * @param isPreservePermissions whether to preserve file permissions
     * @param chunkSize the chunk size for download
     * @param options additional options map
     * @return an iterator of download file stream results
     * @since 0.1.7
     */
    public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
            boolean isOverwrite, boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize,
            Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".downloadFileStream is not implemented");
    }

    /**
     * Lists files in a sandbox directory with optional filtering and sorting.
     * 
     * @param path the directory path to list
     * @param isRecursive whether to list recursively
     * @param maxDepth the maximum recursion depth
     * @param sortBy the sort field
     * @param isSortDescending whether to sort in descending order
     * @param fileTypes the file type filters
     * @param options additional options map
     * @return the list files result
     * @since 0.1.7
     */
    public ListFilesResult listFiles(String path, boolean isRecursive, Integer maxDepth, String sortBy,
            boolean isSortDescending, List<String> fileTypes, Map<String, Object> options) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".listFiles is not implemented");
    }

    /**
     * Lists directories in a sandbox directory with optional filtering and sorting.
     * 
     * @param path the directory path to list
     * @param isRecursive whether to list recursively
     * @param maxDepth the maximum recursion depth
     * @param sortBy the sort field
     * @param isSortDescending whether to sort in descending order
     * @param options additional options map
     * @return the list directories result
     * @since 0.1.7
     */
    public ListDirsResult listDirectories(String path, boolean isRecursive, Integer maxDepth, String sortBy,
            boolean isSortDescending, Map<String, Object> options) {
        throw new UnsupportedOperationException(
                this.getClass().getSimpleName() + ".listDirectories is not implemented");
    }

    /**
     * Searches files in a sandbox directory by pattern with optional exclusion patterns.
     * 
     * @param path the directory path to search
     * @param pattern the search pattern
     * @param excludePatterns the patterns to exclude from results
     * @return the search files result
     * @since 0.1.7
     */
    public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + ".searchFiles is not implemented");
    }
}
