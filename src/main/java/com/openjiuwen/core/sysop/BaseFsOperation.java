  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.result.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Base file system operation — abstract class for FS operations.
 * <p>
 * Mirrors Python's {@code BaseFsOperation} in {@code sys_operation/fs.py}.
 */
public abstract class BaseFsOperation extends BaseOperation {

    protected BaseFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public List<ToolCard> listTools() {
        return generateToolCards(List.of(
                "readFile", "readFileStream", "writeFile",
                "uploadFile", "uploadFileStream",
                "downloadFile", "downloadFileStream",
                "listFiles", "listDirectories", "searchFiles"
        ));
    }

    /**
     * Read a file with specified mode and parameters.
     *
     * @param path      file path to read
     * @param mode      reading mode: "text" or "bytes"
     * @param head      lines from start (text mode)
     * @param tail      lines from end (text mode)
     * @param lineRange 2-element array [start, end] (1-indexed, inclusive), or null
     * @param encoding  character encoding
     * @param chunkSize max bytes to read at once (0 = unlimited)
     * @param options   extended configuration
     * @return structured result
     */
    public abstract ReadFileResult readFile(
            String path, String mode, Integer head, Integer tail,
            int[] lineRange, String encoding,
            int chunkSize, Map<String, Object> options);

    /**
     * Read a file with streaming output.
     */
    public abstract Iterator<ReadFileStreamResult> readFileStream(
            String path, String mode, Integer head, Integer tail,
            int[] lineRange, String encoding,
            int chunkSize, Map<String, Object> options);

    /**
     * Write content to a file.
     *
     * @param path            file path to write
     * @param content         data to write (String for text mode, byte[] for bytes mode).
     *                        Mirrors Python's {@code content: str | bytes}.
     * @param mode            writing mode: "text" or "bytes"
     * @param prependNewline  add newline before content (text mode)
     * @param appendNewline   add newline after content (text mode)
     * @param createIfNotExist auto-create the file
     * @param permissions     octal file permissions
     * @param encoding        character encoding
     * @param options         extended configuration
     * @return structured result
     */
    public abstract WriteFileResult writeFile(
            String path, Object content, String mode,
            boolean prependNewline, boolean appendNewline,
            boolean createIfNotExist, String permissions,
            String encoding, Map<String, Object> options);

    /**
     * Upload a file from local to target path.
     */
    public abstract UploadFileResult uploadFile(
            String localPath, String targetPath,
            boolean overwrite, boolean createParentDirs,
            boolean preservePermissions, int chunkSize,
            Map<String, Object> options);

    /**
     * Upload a file with streaming.
     */
    public abstract Iterator<UploadFileStreamResult> uploadFileStream(
            String localPath, String targetPath,
            boolean overwrite, boolean createParentDirs,
            boolean preservePermissions, int chunkSize,
            Map<String, Object> options);

    /**
     * Download a file from source to local path.
     */
    public abstract DownloadFileResult downloadFile(
            String sourcePath, String localPath,
            boolean overwrite, boolean createParentDirs,
            boolean preservePermissions, int chunkSize,
            Map<String, Object> options);

    /**
     * Download a file with streaming.
     */
    public abstract Iterator<DownloadFileStreamResult> downloadFileStream(
            String sourcePath, String localPath,
            boolean overwrite, boolean createParentDirs,
            boolean preservePermissions, int chunkSize,
            Map<String, Object> options);

    /**
     * List files under the specified path.
     */
    public abstract ListFilesResult listFiles(
            String path, boolean recursive, Integer maxDepth,
            String sortBy, boolean sortDescending,
            List<String> fileTypes, Map<String, Object> options);

    /**
     * List directories under the specified path.
     */
    public abstract ListDirsResult listDirectories(
            String path, boolean recursive, Integer maxDepth,
            String sortBy, boolean sortDescending,
            Map<String, Object> options);

    /**
     * Search files under the specified path.
     */
    public abstract SearchFilesResult searchFiles(
            String path, String pattern, List<String> excludePatterns);
}
