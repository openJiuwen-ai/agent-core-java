/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.OperationDef;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.OperationRegistry;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
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
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sandbox file-system operation.
 *
 * <p>Mirrors Python's {@code FsOperation} in
 * {@code openjiuwen/core/sys_operation/sandbox/fs_operation.py}.</p>
 */
public class SandboxFsOperation extends BaseFsOperation {

    public static final OperationDef OP_DEF = new OperationDef(
            SandboxFsOperation.class,
            "Sandbox file system operation",
            "fs",
            OperationMode.SANDBOX
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SandboxGatewayClientMixin sandboxClient = new SandboxGatewayClientMixin();

    static {
        OperationRegistry.register(SandboxFsOperation.class);
    }

    public SandboxFsOperation(SandboxGatewayConfig config) {
        this("fs", OperationMode.SANDBOX, "Sandbox file system operation",
                SandboxRunConfig.builder().config(config).build());
    }

    public SandboxFsOperation(Object runConfig) {
        this("fs", OperationMode.SANDBOX, "Sandbox file system operation", runConfig);
    }

    public SandboxFsOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
        SandboxRunConfig sandboxRunConfig = toSandboxRunConfig(runConfig);
        sandboxClient.initClientContext(sandboxRunConfig, "fs");
    }

    @Override
    public CompletableFuture<ReadFileResult> readFile(String path, FileMode mode, Integer head, Integer tail,
                                                      BaseFsProtocal.LineRange lineRange, String encoding,
                                                      int chunkSize, Map<String, Object> options) {
        return sandboxClient.invoke("readFile", readParams(path, mode, head, tail, lineRange, encoding, chunkSize,
                options)).thenApply(raw -> convert(raw, ReadFileResult.class));
    }

    @Override
    public Flow.Publisher<ReadFileStreamResult> readFileStream(String path, FileMode mode, Integer head, Integer tail,
                                                               BaseFsProtocal.LineRange lineRange, String encoding,
                                                               int chunkSize, Map<String, Object> options) {
        return mappedPublisher(sandboxClient.invokeStream("readFileStream", readParams(path, mode, head, tail,
                lineRange, encoding, chunkSize, options)), ReadFileStreamResult.class);
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(String path, String content, FileMode mode,
                                                        boolean prependNewline, boolean appendNewline,
                                                        boolean append, boolean createIfNotExist, String permissions,
                                                        String encoding, Map<String, Object> options) {
        return sandboxClient.invoke("writeFile", writeParams(path, content, fileModeValue(mode, FileMode.TEXT),
                prependNewline, appendNewline, append, createIfNotExist, permissions, encoding, options))
                .thenApply(raw -> convert(raw, WriteFileResult.class));
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(String path, byte[] content, FileMode mode,
                                                        boolean prependNewline, boolean appendNewline,
                                                        boolean append, boolean createIfNotExist, String permissions,
                                                        String encoding, Map<String, Object> options) {
        byte[] rawContent = content == null ? new byte[0] : content;
        return sandboxClient.invoke("writeFile", writeParams(path, rawContent, fileModeValue(mode, FileMode.BYTES),
                prependNewline, appendNewline, append, createIfNotExist, permissions, encoding, options))
                .thenApply(raw -> convert(raw, WriteFileResult.class));
    }

    @Override
    public CompletableFuture<UploadFileResult> uploadFile(String localPath, String targetPath, boolean overwrite,
                                                          boolean createParentDirs, boolean preservePermissions,
                                                          int chunkSize, Map<String, Object> options) {
        return sandboxClient.invoke("uploadFile", transferParams(localPath, targetPath, overwrite, createParentDirs,
                preservePermissions, chunkSize, options)).thenApply(raw -> convert(raw, UploadFileResult.class));
    }

    @Override
    public Flow.Publisher<UploadFileStreamResult> uploadFileStream(String localPath, String targetPath,
                                                                   boolean overwrite, boolean createParentDirs,
                                                                   boolean preservePermissions, int chunkSize,
                                                                   Map<String, Object> options) {
        return mappedPublisher(sandboxClient.invokeStream("uploadFileStream", transferParams(localPath, targetPath,
                overwrite, createParentDirs, preservePermissions, chunkSize, options)), UploadFileStreamResult.class);
    }

    @Override
    public CompletableFuture<DownloadFileResult> downloadFile(String sourcePath, String localPath, boolean overwrite,
                                                              boolean createParentDirs, boolean preservePermissions,
                                                              int chunkSize, Map<String, Object> options) {
        return sandboxClient.invoke("downloadFile", downloadParams(sourcePath, localPath, overwrite, createParentDirs,
                preservePermissions, chunkSize, options)).thenApply(raw -> convert(raw, DownloadFileResult.class));
    }

    @Override
    public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(String sourcePath, String localPath,
                                                                       boolean overwrite, boolean createParentDirs,
                                                                       boolean preservePermissions, int chunkSize,
                                                                       Map<String, Object> options) {
        return mappedPublisher(sandboxClient.invokeStream("downloadFileStream", downloadParams(sourcePath, localPath,
                overwrite, createParentDirs, preservePermissions, chunkSize, options)),
                DownloadFileStreamResult.class);
    }

    @Override
    public CompletableFuture<ListFilesResult> listFiles(String path, boolean recursive, Integer maxDepth,
                                                        SortBy sortBy, boolean sortDescending,
                                                        List<String> fileTypes, Map<String, Object> options) {
        return sandboxClient.invoke("listFiles", listParams(path, recursive, maxDepth, sortBy, sortDescending,
                fileTypes, options)).thenApply(raw -> convert(raw, ListFilesResult.class));
    }

    @Override
    public CompletableFuture<ListDirsResult> listDirectories(String path, boolean recursive, Integer maxDepth,
                                                             SortBy sortBy, boolean sortDescending,
                                                             Map<String, Object> options) {
        return sandboxClient.invoke("listDirectories", listDirParams(path, recursive, maxDepth, sortBy,
                sortDescending, options)).thenApply(raw -> convert(raw, ListDirsResult.class));
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern,
                                                            List<String> excludePatterns) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", path);
        params.put("pattern", pattern);
        params.put("excludePatterns", excludePatterns);
        return sandboxClient.invoke("searchFiles", params).thenApply(raw -> convert(raw, SearchFilesResult.class));
    }

    private static Map<String, Object> readParams(String path, FileMode mode, Integer head, Integer tail,
                                                  BaseFsProtocal.LineRange lineRange, String encoding,
                                                  int chunkSize, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", path);
        params.put("mode", fileModeValue(mode, FileMode.TEXT));
        params.put("head", head);
        params.put("tail", tail);
        params.put("lineRange", lineRange);
        params.put("encoding", encoding);
        params.put("chunkSize", chunkSize);
        params.put("options", options);
        return params;
    }

    private static Map<String, Object> writeParams(String path, Object content, String mode,
                                                   boolean prependNewline, boolean appendNewline, boolean append,
                                                   boolean createIfNotExist, String permissions, String encoding,
                                                   Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", path);
        params.put("content", content);
        params.put("mode", mode);
        params.put("prependNewline", prependNewline);
        params.put("appendNewline", appendNewline);
        params.put("append", append);
        params.put("createIfNotExist", createIfNotExist);
        params.put("permissions", permissions);
        params.put("encoding", encoding);
        params.put("options", options);
        return params;
    }

    private static Map<String, Object> transferParams(String localPath, String targetPath, boolean overwrite,
                                                      boolean createParentDirs, boolean preservePermissions,
                                                      int chunkSize, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("localPath", localPath);
        params.put("targetPath", targetPath);
        params.put("overwrite", overwrite);
        params.put("createParentDirs", createParentDirs);
        params.put("preservePermissions", preservePermissions);
        params.put("chunkSize", chunkSize);
        params.put("options", options);
        return params;
    }

    private static Map<String, Object> downloadParams(String sourcePath, String localPath, boolean overwrite,
                                                      boolean createParentDirs, boolean preservePermissions,
                                                      int chunkSize, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("sourcePath", sourcePath);
        params.put("localPath", localPath);
        params.put("overwrite", overwrite);
        params.put("createParentDirs", createParentDirs);
        params.put("preservePermissions", preservePermissions);
        params.put("chunkSize", chunkSize);
        params.put("options", options);
        return params;
    }

    private static Map<String, Object> listParams(String path, boolean recursive, Integer maxDepth, SortBy sortBy,
                                                  boolean sortDescending, List<String> fileTypes,
                                                  Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", path);
        params.put("recursive", recursive);
        params.put("maxDepth", maxDepth);
        params.put("sortBy", sortByValue(sortBy));
        params.put("sortDescending", sortDescending);
        params.put("fileTypes", fileTypes);
        params.put("options", options);
        return params;
    }

    private static Map<String, Object> listDirParams(String path, boolean recursive, Integer maxDepth, SortBy sortBy,
                                                     boolean sortDescending, Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", path);
        params.put("recursive", recursive);
        params.put("maxDepth", maxDepth);
        params.put("sortBy", sortByValue(sortBy));
        params.put("sortDescending", sortDescending);
        params.put("options", options);
        return params;
    }

    private static String fileModeValue(FileMode mode, FileMode defaultMode) {
        return (mode == null ? defaultMode : mode).value();
    }

    private static String sortByValue(SortBy sortBy) {
        return (sortBy == null ? SortBy.NAME : sortBy).value();
    }

    private static <T> T convert(Object raw, Class<T> resultClass) {
        if (resultClass.isInstance(raw)) {
            return resultClass.cast(raw);
        }
        return OBJECT_MAPPER.convertValue(raw, resultClass);
    }

    private static <T> Flow.Publisher<T> mappedPublisher(CompletableFuture<Flow.Publisher<?>> rawPublisher,
                                                         Class<T> resultClass) {
        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber");
            rawPublisher.whenComplete((publisher, error) -> {
                if (error != null) {
                    subscriber.onSubscribe(new EmptySubscription());
                    subscriber.onError(rootCause(error));
                    return;
                }
                subscribeMapped(publisher, subscriber, resultClass);
            });
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> void subscribeMapped(Flow.Publisher<?> publisher, Flow.Subscriber<? super T> subscriber,
                                            Class<T> resultClass) {
        Flow.Publisher<Object> rawPublisher = (Flow.Publisher<Object>) publisher;
        rawPublisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(Object item) {
                subscriber.onNext(convert(item, resultClass));
            }

            @Override
            public void onError(Throwable throwable) {
                subscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                subscriber.onComplete();
            }
        });
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor;
    }

    private static final class EmptySubscription implements Flow.Subscription {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public void request(long itemCount) {
            // No-op: this subscription only satisfies the Flow onSubscribe contract before onError.
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }

    private static SandboxRunConfig toSandboxRunConfig(Object runConfig) {
        if (runConfig instanceof SandboxRunConfig config) {
            return config;
        }
        if (runConfig instanceof SandboxGatewayConfig config) {
            return SandboxRunConfig.builder().config(config).build();
        }
        return SandboxRunConfig.builder().build();
    }
}
