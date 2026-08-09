/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.providers;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
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
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors Python's {@code BaseFSProvider} in
 * {@code openjiuwen/core/sys_operation/sandbox/providers/base_provider.py}.
 */
public abstract class BaseFsProvider extends BaseFsProtocal {

    private final SandboxEndpoint endpoint;

    private final SandboxGatewayConfig config;

    protected BaseFsProvider(SandboxEndpoint endpoint) {
        this(endpoint, null);
    }

    protected BaseFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        this.endpoint = endpoint;
        this.config = config;
    }

    public SandboxEndpoint getEndpoint() {
        return endpoint;
    }

    public SandboxGatewayConfig getConfig() {
        return config;
    }

    @Override
    public CompletableFuture<ReadFileResult> readFile(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options) {
        return failedFuture("readFile");
    }

    @Override
    public Flow.Publisher<ReadFileStreamResult> readFileStream(
            String path,
            String mode,
            Integer head,
            Integer tail,
            LineRange lineRange,
            String encoding,
            int chunkSize,
            Map<String, Object> options) {
        return failedPublisher("readFileStream");
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(
            String path,
            String content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options) {
        return failedFuture("writeFile");
    }

    @Override
    public CompletableFuture<WriteFileResult> writeFile(
            String path,
            byte[] content,
            String mode,
            boolean prependNewline,
            boolean appendNewline,
            boolean append,
            boolean createIfNotExist,
            String permissions,
            String encoding,
            Map<String, Object> options) {
        return failedFuture("writeFile");
    }

    @Override
    public CompletableFuture<UploadFileResult> uploadFile(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return failedFuture("uploadFile");
    }

    @Override
    public Flow.Publisher<UploadFileStreamResult> uploadFileStream(
            String localPath,
            String targetPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return failedPublisher("uploadFileStream");
    }

    @Override
    public CompletableFuture<DownloadFileResult> downloadFile(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return failedFuture("downloadFile");
    }

    @Override
    public Flow.Publisher<DownloadFileStreamResult> downloadFileStream(
            String sourcePath,
            String localPath,
            boolean overwrite,
            boolean createParentDirs,
            boolean preservePermissions,
            int chunkSize,
            Map<String, Object> options) {
        return failedPublisher("downloadFileStream");
    }

    @Override
    public CompletableFuture<ListFilesResult> listFiles(
            String path,
            boolean recursive,
            Integer maxDepth,
            String sortBy,
            boolean sortDescending,
            List<String> fileTypes,
            Map<String, Object> options) {
        return failedFuture("listFiles");
    }

    @Override
    public CompletableFuture<ListDirsResult> listDirectories(
            String path,
            boolean recursive,
            Integer maxDepth,
            String sortBy,
            boolean sortDescending,
            Map<String, Object> options) {
        return failedFuture("listDirectories");
    }

    @Override
    public CompletableFuture<SearchFilesResult> searchFiles(
            String path,
            String pattern,
            List<String> excludePatterns) {
        return failedFuture("searchFiles");
    }

    private <T> CompletableFuture<T> failedFuture(String methodName) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(notImplementedMessage(methodName)));
    }

    private <T> Flow.Publisher<T> failedPublisher(String methodName) {
        UnsupportedOperationException error = new UnsupportedOperationException(notImplementedMessage(methodName));
        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber");
            subscriber.onSubscribe(new Flow.Subscription() {
                private final AtomicBoolean done = new AtomicBoolean(false);

                @Override
                public void request(long itemCount) {
                    if (done.compareAndSet(false, true)) {
                        subscriber.onError(error);
                    }
                }

                @Override
                public void cancel() {
                    done.set(true);
                }
            });
        };
    }

    private String notImplementedMessage(String methodName) {
        return getClass().getSimpleName() + "." + methodName + " is not implemented";
    }
}
