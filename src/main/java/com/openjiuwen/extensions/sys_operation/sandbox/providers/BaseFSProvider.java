/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base interface for file system operations.
 */
public interface BaseFSProvider {

    /**
     * Read file content.
     *
     * @param filePath File path to read.
     * @return CompletableFuture with file content.
     */
    CompletableFuture<String> readFile(String filePath);

    /**
     * Write file content.
     *
     * @param filePath File path to write.
     * @param content Content to write.
     * @return CompletableFuture for async operation.
     */
    CompletableFuture<Void> writeFile(String filePath, String content);

    /**
     * List files in directory.
     *
     * @param dirPath Directory path.
     * @return CompletableFuture with file list.
     */
    CompletableFuture<List<String>> listFiles(String dirPath);
}