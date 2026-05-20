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

/** Sandbox file system operation routed through the sandbox gateway/provider chain. */
@Operation(name = "fs", mode = OperationMode.SANDBOX, description = "sandbox fs operation")
public class SandboxFsOperation extends BaseFsOperation {
  private static final String OP_TYPE = "fs";

  private final SandboxGatewayClient gatewayClient;

  /** Auto-generated for codecheck compliance. */
  public SandboxFsOperation(Object runConfig) {
    super("fs", OperationMode.SANDBOX, "sandbox fs operation", runConfig);
    this.gatewayClient =
        new SandboxGatewayClient(
            getSandboxConfig(), SandboxOperationSupport.resolveIsolationKey(getSandboxConfig()));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public ReadFileResult readFile(
      String path,
      String mode,
      Integer head,
      Integer tail,
      int[] lineRange,
      String encoding,
      int chunkSize,
      Map<String, Object> options) {
    return invoke(
        "readFile",
        ReadFileResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "path", path,
              "mode", mode,
              "head", head,
              "tail", tail,
              "lineRange", lineRange,
              "encoding", encoding,
              "chunkSize", chunkSize,
              "options", options
            }));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public Iterator<ReadFileStreamResult> readFileStream(
      String path,
      String mode,
      Integer head,
      Integer tail,
      int[] lineRange,
      String encoding,
      int chunkSize,
      Map<String, Object> options) {
    @SuppressWarnings("unchecked")
    Iterator<ReadFileStreamResult> iterator =
        invoke(
            "readFileStream",
            Iterator.class,
            SandboxOperationSupport.params(
                new Object[] {
                  "path", path,
                  "mode", mode,
                  "head", head,
                  "tail", tail,
                  "lineRange", lineRange,
                  "encoding", encoding,
                  "chunkSize", chunkSize,
                  "options", options
                }));
    return iterator;
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public WriteFileResult writeFile(
      String path,
      Object content,
      String mode,
      boolean prependNewline,
      boolean appendNewline,
      boolean createIfNotExist,
      String permissions,
      String encoding,
      Map<String, Object> options) {
    return invoke(
        "writeFile",
        WriteFileResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "path", path,
              "content", content,
              "mode", mode,
              "prependNewline", prependNewline,
              "appendNewline", appendNewline,
              "createIfNotExist", createIfNotExist,
              "permissions", permissions,
              "encoding", encoding,
              "options", options
            }));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public UploadFileResult uploadFile(
      String localPath,
      String targetPath,
      boolean overwrite,
      boolean createParentDirs,
      boolean preservePermissions,
      int chunkSize,
      Map<String, Object> options) {
    return invoke(
        "uploadFile",
        UploadFileResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "localPath", localPath,
              "targetPath", targetPath,
              "overwrite", overwrite,
              "createParentDirs", createParentDirs,
              "preservePermissions", preservePermissions,
              "chunkSize", chunkSize,
              "options", options
            }));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public Iterator<UploadFileStreamResult> uploadFileStream(
      String localPath,
      String targetPath,
      boolean overwrite,
      boolean createParentDirs,
      boolean preservePermissions,
      int chunkSize,
      Map<String, Object> options) {
    @SuppressWarnings("unchecked")
    Iterator<UploadFileStreamResult> iterator =
        invoke(
            "uploadFileStream",
            Iterator.class,
            SandboxOperationSupport.params(
                new Object[] {
                  "localPath", localPath,
                  "targetPath", targetPath,
                  "overwrite", overwrite,
                  "createParentDirs", createParentDirs,
                  "preservePermissions", preservePermissions,
                  "chunkSize", chunkSize,
                  "options", options
                }));
    return iterator;
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public DownloadFileResult downloadFile(
      String sourcePath,
      String localPath,
      boolean overwrite,
      boolean createParentDirs,
      boolean preservePermissions,
      int chunkSize,
      Map<String, Object> options) {
    return invoke(
        "downloadFile",
        DownloadFileResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "sourcePath", sourcePath,
              "localPath", localPath,
              "overwrite", overwrite,
              "createParentDirs", createParentDirs,
              "preservePermissions", preservePermissions,
              "chunkSize", chunkSize,
              "options", options
            }));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public Iterator<DownloadFileStreamResult> downloadFileStream(
      String sourcePath,
      String localPath,
      boolean overwrite,
      boolean createParentDirs,
      boolean preservePermissions,
      int chunkSize,
      Map<String, Object> options) {
    @SuppressWarnings("unchecked")
    Iterator<DownloadFileStreamResult> iterator =
        invoke(
            "downloadFileStream",
            Iterator.class,
            SandboxOperationSupport.params(
                new Object[] {
                  "sourcePath", sourcePath,
                  "localPath", localPath,
                  "overwrite", overwrite,
                  "createParentDirs", createParentDirs,
                  "preservePermissions", preservePermissions,
                  "chunkSize", chunkSize,
                  "options", options
                }));
    return iterator;
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public ListFilesResult listFiles(
      String path,
      boolean recursive,
      Integer maxDepth,
      String sortBy,
      boolean sortDescending,
      List<String> fileTypes,
      Map<String, Object> options) {
    return invoke(
        "listFiles",
        ListFilesResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "path", path,
              "recursive", recursive,
              "maxDepth", maxDepth,
              "sortBy", sortBy,
              "sortDescending", sortDescending,
              "fileTypes", fileTypes,
              "options", options
            }));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public ListDirsResult listDirectories(
      String path,
      boolean recursive,
      Integer maxDepth,
      String sortBy,
      boolean sortDescending,
      Map<String, Object> options) {
    return invoke(
        "listDirectories",
        ListDirsResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "path", path,
              "recursive", recursive,
              "maxDepth", maxDepth,
              "sortBy", sortBy,
              "sortDescending", sortDescending,
              "options", options
            }));
  }

  @Override
  /** Auto-generated for codecheck compliance. */
  public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
    return invoke(
        "searchFiles",
        SearchFilesResult.class,
        SandboxOperationSupport.params(
            new Object[] {
              "path", path,
              "pattern", pattern,
              "excludePatterns", excludePatterns
            }));
  }

  private <T> T invoke(String method, Class<T> type, Map<String, Object> params) {
    Object result = gatewayClient.invoke(OP_TYPE, method, params);
    if (type.isInstance(result)) {
      return type.cast(result);
    }
    throw new IllegalArgumentException("Unexpected sandbox fs response data type");
  }
}
