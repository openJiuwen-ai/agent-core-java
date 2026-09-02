/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
 * Sandbox file system operation routed through the sandbox gateway/provider chain.
 * 
 * @since 0.1.7
 */
@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation")
public class SandboxFsOperation extends BaseFsOperation {
    private static final String OP_TYPE = "fs";

    private final SandboxGatewayClient gatewayClient;

    /**
     * Constructs a new SandboxFsOperation instance.
     * 
     * @param runConfig 运行配置
     * @since 0.1.7
     */
    public SandboxFsOperation(Object runConfig) {
        super("fs", OperationMode.SANDBOX, "sandbox fs operation", runConfig);
        this.gatewayClient = new SandboxGatewayClient(getSandboxConfig(),
                SandboxOperationSupport.resolveIsolationKey(getSandboxConfig()));
    }

    /**
     * readFile.
     * 
     * @param path path
     * @param mode mode
     * @param head head
     * @param tail tail
     * @param lineRange lineRange
     * @param encoding encoding
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ReadFileResult readFile(String path, String mode, Integer head, Integer tail, int[] lineRange,
            String encoding, int chunkSize, Map<String, Object> options) {
        return invoke("readFile", ReadFileResult.class,
                SandboxOperationSupport.paramsOf("path", path, "mode", mode, "head", head, "tail", tail, "lineRange",
                        lineRange, "encoding", encoding, "chunkSize", chunkSize, "options", options));
    }

    /**
     * readFileStream.
     * 
     * @param path path
     * @param mode mode
     * @param head head
     * @param tail tail
     * @param lineRange lineRange
     * @param encoding encoding
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ReadFileStreamResult> readFileStream(String path, String mode, Integer head, Integer tail,
            int[] lineRange, String encoding, int chunkSize, Map<String, Object> options) {
        @SuppressWarnings("unchecked")
        Iterator<ReadFileStreamResult> iterator = invoke("readFileStream", Iterator.class,
                SandboxOperationSupport.paramsOf("path", path, "mode", mode, "head", head, "tail", tail, "lineRange",
                        lineRange, "encoding", encoding, "chunkSize", chunkSize, "options", options));
        return iterator;
    }

    /**
     * writeFile.
     * 
     * @param path path
     * @param content content
     * @param mode mode
     * @param isPrependNewline isPrependNewline
     * @param isAppendNewline isAppendNewline
     * @param isCreateIfMissing isCreateIfMissing
     * @param permissions permissions
     * @param encoding encoding
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public WriteFileResult writeFile(String path, Object content, String mode, boolean isPrependNewline,
            boolean isAppendNewline, boolean isCreateIfMissing, String permissions, String encoding,
            Map<String, Object> options) {
        return invoke("writeFile", WriteFileResult.class,
                SandboxOperationSupport.paramsOf("path", path, "content", content, "mode", mode, "prependNewline",
                        isPrependNewline, "appendNewline", isAppendNewline, "createIfNotExist", isCreateIfMissing,
                        "permissions", permissions, "encoding", encoding, "options", options));
    }

    /**
     * uploadFile.
     * 
     * @param localPath localPath
     * @param targetPath targetPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public UploadFileResult uploadFile(String localPath, String targetPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        return invoke("uploadFile", UploadFileResult.class,
                SandboxOperationSupport.paramsOf("localPath", localPath, "targetPath", targetPath, "overwrite",
                        isOverwrite, "createParentDirs", isCreateParentDirs, "preservePermissions",
                        isPreservePermissions, "chunkSize", chunkSize, "options", options));
    }

    /**
     * uploadFileStream.
     * 
     * @param localPath localPath
     * @param targetPath targetPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        @SuppressWarnings("unchecked")
        Iterator<UploadFileStreamResult> iterator = invoke("uploadFileStream", Iterator.class,
                SandboxOperationSupport.paramsOf("localPath", localPath, "targetPath", targetPath, "overwrite",
                        isOverwrite, "createParentDirs", isCreateParentDirs, "preservePermissions",
                        isPreservePermissions, "chunkSize", chunkSize, "options", options));
        return iterator;
    }

    /**
     * downloadFile.
     * 
     * @param sourcePath sourcePath
     * @param localPath localPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public DownloadFileResult downloadFile(String sourcePath, String localPath, boolean isOverwrite,
            boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize, Map<String, Object> options) {
        return invoke("downloadFile", DownloadFileResult.class,
                SandboxOperationSupport.paramsOf("sourcePath", sourcePath, "localPath", localPath, "overwrite",
                        isOverwrite, "createParentDirs", isCreateParentDirs, "preservePermissions",
                        isPreservePermissions, "chunkSize", chunkSize, "options", options));
    }

    /**
     * downloadFileStream.
     * 
     * @param sourcePath sourcePath
     * @param localPath localPath
     * @param isOverwrite isOverwrite
     * @param isCreateParentDirs isCreateParentDirs
     * @param isPreservePermissions isPreservePermissions
     * @param chunkSize chunkSize
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
            boolean isOverwrite, boolean isCreateParentDirs, boolean isPreservePermissions, int chunkSize,
            Map<String, Object> options) {
        @SuppressWarnings("unchecked")
        Iterator<DownloadFileStreamResult> iterator = invoke("downloadFileStream", Iterator.class,
                SandboxOperationSupport.paramsOf("sourcePath", sourcePath, "localPath", localPath, "overwrite",
                        isOverwrite, "createParentDirs", isCreateParentDirs, "preservePermissions",
                        isPreservePermissions, "chunkSize", chunkSize, "options", options));
        return iterator;
    }

    /**
     * listFiles.
     * 
     * @param path path
     * @param isRecursive isRecursive
     * @param maxDepth maxDepth
     * @param sortBy sortBy
     * @param isSortDescending isSortDescending
     * @param fileTypes fileTypes
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ListFilesResult listFiles(String path, boolean isRecursive, Integer maxDepth, String sortBy,
            boolean isSortDescending, List<String> fileTypes, Map<String, Object> options) {
        return invoke("listFiles", ListFilesResult.class,
                SandboxOperationSupport.paramsOf("path", path, "recursive", isRecursive, "maxDepth", maxDepth, "sortBy",
                        sortBy, "sortDescending", isSortDescending, "fileTypes", fileTypes, "options", options));
    }

    /**
     * listDirectories.
     * 
     * @param path path
     * @param isRecursive isRecursive
     * @param maxDepth maxDepth
     * @param sortBy sortBy
     * @param isSortDescending isSortDescending
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ListDirsResult listDirectories(String path, boolean isRecursive, Integer maxDepth, String sortBy,
            boolean isSortDescending, Map<String, Object> options) {
        return invoke("listDirectories", ListDirsResult.class,
                SandboxOperationSupport.paramsOf("path", path, "recursive", isRecursive, "maxDepth", maxDepth, "sortBy",
                        sortBy, "sortDescending", isSortDescending, "options", options));
    }

    /**
     * searchFiles.
     * 
     * @param path path
     * @param pattern pattern
     * @param excludePatterns excludePatterns
     * @return the result
     * @since 0.1.7
     */
    @Override
    public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
        return invoke("searchFiles", SearchFilesResult.class,
                SandboxOperationSupport.paramsOf("path", path, "pattern", pattern, "excludePatterns", excludePatterns));
    }

    /**
     * invoke.
     * 
     * @param method method
     * @param type type
     * @param params params
     * @return the result
     * @since 0.1.7
     */
    private <T> T invoke(String method, Class<T> type, Map<String, Object> params) {
        Object result = gatewayClient.invoke(OP_TYPE, method, params);
        if (type.isInstance(result)) {
            return type.cast(result);
        }
        throw new IllegalArgumentException("Unexpected sandbox fs response data type");
    }
}
