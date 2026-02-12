// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.sysoperation.sandbox;

import com.openjiuwen.core.sysoperation.base.OperationMode;
import com.openjiuwen.core.sysoperation.fs.BaseFsOperation;
import com.openjiuwen.core.sysoperation.registry.Operation;
import com.openjiuwen.core.sysoperation.registry.OperationRegistry;
import com.openjiuwen.core.sysoperation.result.FileMode;
import com.openjiuwen.core.sysoperation.result.SortBy;
import com.openjiuwen.core.sysoperation.result.fs.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Sandbox file system operation placeholder.
 * 
 * <p>对应 Python: openjiuwen.core.sys_operation.sandbox.fs_operation.FsOperation
 * 
 * <p>Note: This is a placeholder implementation. Sandbox mode operations
 * are not yet implemented in the Java version.
 * 
 * @author OpenJiuwen
 * @since 2026-02-05
 */
@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation")
public class SandboxFsOperation extends BaseFsOperation {

    static {
        OperationRegistry.register(SandboxFsOperation.class, "fs", OperationMode.SANDBOX, "sandbox fs operation");
    }

    public SandboxFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ReadFileResult> readFile(
            String path, FileMode mode, Integer head, Integer tail,
            Integer lineRangeStart, Integer lineRangeEnd, String encoding,
            int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public Stream<ReadFileStreamResult> readFileStream(
            String path, FileMode mode, Integer head, Integer tail,
            Integer lineRangeStart, Integer lineRangeEnd, String encoding,
            int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(
            String path, Object content, FileMode mode, boolean prependNewline,
            boolean appendNewline, boolean createIfNotExist, String permissions,
            String encoding, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public CompletableFuture<UploadFileResult> uploadFile(
            String localPath, String targetPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public Stream<UploadFileStreamResult> uploadFileStream(
            String localPath, String targetPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public CompletableFuture<DownloadFileResult> downloadFile(
            String sourcePath, String localPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public Stream<DownloadFileStreamResult> downloadFileStream(
            String sourcePath, String localPath, boolean overwrite,
            boolean createParentDirs, boolean preservePermissions,
            int chunkSize, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public CompletableFuture<ListFilesResult> listFiles(
            String path, boolean recursive, Integer maxDepth, SortBy sortBy,
            boolean sortDescending, List<String> fileTypes, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public CompletableFuture<ListDirsResult> listDirectories(
            String path, boolean recursive, Integer maxDepth, SortBy sortBy,
            boolean sortDescending, Map<String, Object> options) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(
            String path, String pattern, List<String> excludePatterns) {
        throw new UnsupportedOperationException("Sandbox mode not implemented");
    }
}

