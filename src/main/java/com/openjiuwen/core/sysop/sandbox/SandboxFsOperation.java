/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sandbox file system operation stub — not yet implemented.
 * <p>
 * Mirrors Python's {@code sandbox/fs_operation.py}.
 */
@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation")
public class SandboxFsOperation extends BaseFsOperation {

    public SandboxFsOperation(Object runConfig) {
        super("fs", OperationMode.SANDBOX, "sandbox fs operation", runConfig);
    }

    @Override
    public ReadFileResult readFile(String path, String mode, Integer head, Integer tail,
                                   int[] lineRange, String encoding, int chunkSize,
                                   Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail,
                                                          int[] lineRange, String encoding, int chunkSize,
                                                          Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public WriteFileResult writeFile(String path, String content, String mode,
                                     boolean prependNewline, boolean appendNewline,
                                     boolean createIfNotExist, String permissions,
                                     String encoding, Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public UploadFileResult uploadFile(String localPath, String targetPath,
                                       boolean overwrite, boolean createParentDirs,
                                       boolean preservePermissions, int chunkSize,
                                       Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath,
                                                              boolean overwrite, boolean createParentDirs,
                                                              boolean preservePermissions, int chunkSize,
                                                              Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public DownloadFileResult downloadFile(String sourcePath, String localPath,
                                           boolean overwrite, boolean createParentDirs,
                                           boolean preservePermissions, int chunkSize,
                                           Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
                                                                  boolean overwrite, boolean createParentDirs,
                                                                  boolean preservePermissions, int chunkSize,
                                                                  Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth,
                                     String sortBy, boolean sortDescending,
                                     List<String> fileTypes, Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth,
                                          String sortBy, boolean sortDescending,
                                          Map<String, Object> options) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }

    @Override
    public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
        throw new UnsupportedOperationException("Fs operation sandbox mode is not implemented yet.");
    }
}
