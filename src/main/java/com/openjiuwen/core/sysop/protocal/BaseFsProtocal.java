/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.protocal;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified FS method signatures.
 *
 * <p>Mirrors Python's {@code BaseFsProtocal} in
 * {@code openjiuwen.core.sys_operation.protocal.fs_protocal}.</p>
 */
public interface BaseFsProtocal {

    // Default chunk sizes
    int DEFAULT_READ_CHUNK_SIZE = 0;
    int DEFAULT_UPLOAD_CHUNK_SIZE = 0;
    int DEFAULT_DOWNLOAD_CHUNK_SIZE = 0;
    int DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;
    int DEFAULT_UPLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;
    int DEFAULT_READ_STREAM_CHUNK_SIZE = 8192;

    /**
     * Read file asynchronously.
     *
     * @param path     file path
     * @param mode     read mode (text or bytes)
     * @param head     lines to read from start
     * @param tail     lines to read from end
     * @param encoding file encoding
     * @param chunkSize chunk size
     * @param options  additional options
     * @return read result
     */
    CompletableFuture<Object> readFile(
            String path,
            String mode,
            Integer head,
            Integer tail,
            String encoding,
            int chunkSize,
            Map<String, Object> options
    );

    /**
     * Write file asynchronously.
     *
     * @param path     file path
     * @param content  file content
     * @param mode     write mode (text or bytes)
     * @param encoding file encoding
     * @param options  additional options
     * @return write result
     */
    CompletableFuture<Object> writeFile(
            String path,
            Object content,
            String mode,
            String encoding,
            Map<String, Object> options
    );

    /**
     * List files in directory.
     *
     * @param path    directory path
     * @param pattern file pattern
     * @param options additional options
     * @return list result
     */
    CompletableFuture<Object> listFiles(
            String path,
            String pattern,
            Map<String, Object> options
    );

    /**
     * List directories.
     *
     * @param path    directory path
     * @param options additional options
     * @return list result
     */
    CompletableFuture<Object> listDirectories(
            String path,
            Map<String, Object> options
    );

    /**
     * Search files by pattern.
     *
     * @param path    search path
     * @param pattern search pattern
     * @param options additional options
     * @return search result
     */
    CompletableFuture<Object> searchFiles(
            String path,
            String pattern,
            Map<String, Object> options
    );
}