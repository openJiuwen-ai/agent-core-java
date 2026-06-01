/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.registry.Operation;
import com.openjiuwen.core.sysop.result.*;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sandbox file system operation.
 * <p>
 * Mirrors Python's {@code FsOperation} in {@code sandbox/fs_operation.py}.
 */
@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation")
public class SandboxFsOperation extends BaseFsOperation {

    private final BaseSandboxMixin sandboxMixin;
    private final String sessionId;
    private final boolean sandboxContextInitialized;

    public SandboxFsOperation(Object runConfig) {
        this(runConfig, "default_session");
    }

    public SandboxFsOperation(Object runConfig, String sessionId) {
        super("fs", OperationMode.SANDBOX, "sandbox fs operation", runConfig);
        this.sessionId = sessionId;
        this.sandboxMixin = new BaseSandboxMixin();
        if (runConfig instanceof SandboxRunConfig sandboxRunConfig) {
            this.sandboxMixin.initSandboxContext(sandboxRunConfig, "fs");
            this.sandboxContextInitialized = true;
        } else {
            this.sandboxContextInitialized = false;
        }
    }

    @Override
    public ReadFileResult readFile(String path, String mode, Integer head, Integer tail,
                                   int[] lineRange, String encoding, int chunkSize,
                                   Map<String, Object> options) {
        Map<String, Object> params = newParams("path", path);
        params.put("mode", mode != null ? mode : "text");
        params.put("head", head);
        params.put("tail", tail);
        params.put("line_range", lineRange);
        params.put("encoding", encoding != null ? encoding : "utf-8");
        params.put("chunk_size", chunkSize);
        params.put("options", options);
        return invoke("read_file", params, ReadFileResult.class);
    }

    @Override
    public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail,
                                                          int[] lineRange, String encoding, int chunkSize,
                                                          Map<String, Object> options) {
        Map<String, Object> params = newParams("path", path);
        params.put("mode", mode != null ? mode : "text");
        params.put("head", head);
        params.put("tail", tail);
        params.put("line_range", lineRange);
        params.put("encoding", encoding != null ? encoding : "utf-8");
        params.put("chunk_size", chunkSize);
        params.put("options", options);
        return invokeStream("read_file_stream", params, ReadFileStreamResult.class);
    }

    @Override
    public WriteFileResult writeFile(String path, Object content, String mode,
                                     boolean prependNewline, boolean appendNewline,
                                     boolean createIfNotExist, String permissions,
                                     String encoding, Map<String, Object> options) {
        Map<String, Object> params = newParams("path", path);
        params.put("content", content);
        params.put("mode", mode != null ? mode : "text");
        params.put("prepend_newline", prependNewline);
        params.put("append_newline", appendNewline);
        params.put("create_if_not_exist", createIfNotExist);
        params.put("permissions", permissions != null ? permissions : "644");
        params.put("encoding", encoding != null ? encoding : "utf-8");
        params.put("options", options);
        return invoke("write_file", params, WriteFileResult.class);
    }

    @Override
    public UploadFileResult uploadFile(String localPath, String targetPath,
                                       boolean overwrite, boolean createParentDirs,
                                       boolean preservePermissions, int chunkSize,
                                       Map<String, Object> options) {
        Map<String, Object> params = newParams("local_path", localPath);
        params.put("target_path", targetPath);
        params.put("overwrite", overwrite);
        params.put("create_parent_dirs", createParentDirs);
        params.put("preserve_permissions", preservePermissions);
        params.put("chunk_size", chunkSize);
        params.put("options", options);
        return invoke("upload_file", params, UploadFileResult.class);
    }

    @Override
    public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath,
                                                              boolean overwrite, boolean createParentDirs,
                                                              boolean preservePermissions, int chunkSize,
                                                              Map<String, Object> options) {
        Map<String, Object> params = newParams("local_path", localPath);
        params.put("target_path", targetPath);
        params.put("overwrite", overwrite);
        params.put("create_parent_dirs", createParentDirs);
        params.put("preserve_permissions", preservePermissions);
        params.put("chunk_size", chunkSize);
        params.put("options", options);
        return invokeStream("upload_file_stream", params, UploadFileStreamResult.class);
    }

    @Override
    public DownloadFileResult downloadFile(String sourcePath, String localPath,
                                           boolean overwrite, boolean createParentDirs,
                                           boolean preservePermissions, int chunkSize,
                                           Map<String, Object> options) {
        Map<String, Object> params = newParams("source_path", sourcePath);
        params.put("local_path", localPath);
        params.put("overwrite", overwrite);
        params.put("create_parent_dirs", createParentDirs);
        params.put("preserve_permissions", preservePermissions);
        params.put("chunk_size", chunkSize);
        params.put("options", options);
        return invoke("download_file", params, DownloadFileResult.class);
    }

    @Override
    public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
                                                                  boolean overwrite, boolean createParentDirs,
                                                                  boolean preservePermissions, int chunkSize,
                                                                  Map<String, Object> options) {
        Map<String, Object> params = newParams("source_path", sourcePath);
        params.put("local_path", localPath);
        params.put("overwrite", overwrite);
        params.put("create_parent_dirs", createParentDirs);
        params.put("preserve_permissions", preservePermissions);
        params.put("chunk_size", chunkSize);
        params.put("options", options);
        return invokeStream("download_file_stream", params, DownloadFileStreamResult.class);
    }

    @Override
    public ListFilesResult listFiles(String path, boolean recursive, Integer maxDepth,
                                     String sortBy, boolean sortDescending,
                                     List<String> fileTypes, Map<String, Object> options) {
        Map<String, Object> params = newParams("path", path);
        params.put("recursive", recursive);
        params.put("max_depth", maxDepth);
        params.put("sort_by", sortBy != null ? sortBy : "name");
        params.put("sort_descending", sortDescending);
        params.put("file_types", fileTypes);
        params.put("options", options);
        return invoke("list_files", params, ListFilesResult.class);
    }

    @Override
    public ListDirsResult listDirectories(String path, boolean recursive, Integer maxDepth,
                                          String sortBy, boolean sortDescending,
                                          Map<String, Object> options) {
        Map<String, Object> params = newParams("path", path);
        params.put("recursive", recursive);
        params.put("max_depth", maxDepth);
        params.put("sort_by", sortBy != null ? sortBy : "name");
        params.put("sort_descending", sortDescending);
        params.put("options", options);
        return invoke("list_directories", params, ListDirsResult.class);
    }

    @Override
    public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
        Map<String, Object> params = newParams("path", path);
        params.put("pattern", pattern);
        params.put("exclude_patterns", excludePatterns);
        return invoke("search_files", params, SearchFilesResult.class);
    }

    private Map<String, Object> newParams(String key, Object value) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(key, value);
        return params;
    }

    private void requireSandboxContext() {
        if (!sandboxContextInitialized) {
            throw new UnsupportedOperationException("Sandbox fs operation requires SandboxRunConfig.");
        }
    }

    private <T> T invoke(String method, Map<String, Object> params, Class<T> resultClass) {
        requireSandboxContext();
        Object raw = sandboxMixin.invoke(sessionId, method, params);
        if (resultClass.isInstance(raw)) {
            return resultClass.cast(raw);
        }
        throw new RuntimeException("Invalid " + method + " result type: "
                + (raw == null ? "null" : raw.getClass().getName()));
    }

    @SuppressWarnings("unchecked")
    private <T> Iterator<T> invokeStream(String method, Map<String, Object> params, Class<T> resultClass) {
        requireSandboxContext();
        try {
            Iterator<?> iterator = sandboxMixin.invokeStream(sessionId, method, params);
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public T next() {
                    Object value = iterator.next();
                    if (resultClass.isInstance(value)) {
                        return resultClass.cast(value);
                    }
                    throw new RuntimeException("Invalid " + method + " stream item type: "
                            + (value == null ? "null" : value.getClass().getName()));
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(method + " failed", e);
        }
    }
}
