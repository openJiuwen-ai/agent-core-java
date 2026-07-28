/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseFsOperation;
import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.config.LocalWorkConfig;
import com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal;
import com.openjiuwen.core.sys_operation.result.DownloadFileResult;
import com.openjiuwen.core.sys_operation.result.FileSystemItem;
import com.openjiuwen.core.sys_operation.result.ListFilesResult;
import com.openjiuwen.core.sys_operation.result.ReadFileResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.result.SearchFilesResult;
import com.openjiuwen.core.sys_operation.result.UploadFileResult;
import com.openjiuwen.core.sys_operation.result.WriteFileResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code FsOperation} behavior in
 * {@code openjiuwen/core/sys_operation/local/fs_operation.py}.
 */
class LocalFsOperationTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Test
    void writeAndReadTextPreservePythonLineSelectionSemantics() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalFsOperation operation = operation();

        WriteFileResult writeResult = operation.writeFile(
                "notes/example.txt",
                "alpha\nbeta\ngamma",
                BaseFsOperation.FileMode.TEXT,
                false,
                true,
                false,
                true,
                "644",
                "utf-8",
                null).get(5, TimeUnit.SECONDS);
        assertThat(writeResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        ReadFileResult readResult = operation.readFile(
                "notes/example.txt",
                BaseFsOperation.FileMode.TEXT,
                null,
                null,
                new BaseFsProtocal.LineRange(2, 3),
                "utf-8",
                BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                null).get(5, TimeUnit.SECONDS);
        assertThat(readResult.getData().getContent()).isEqualTo("beta\ngamma\n");

        List<ReadFileStreamResult> streamResults = collect(operation.readFileStream(
                "notes/example.txt",
                BaseFsOperation.FileMode.TEXT,
                null,
                2,
                null,
                "utf-8",
                BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE,
                null));
        assertThat(streamResults).hasSize(2);
        assertThat(streamResults).extracting(result -> result.getData().getChunkContent())
                .containsExactly("beta\n", "gamma\n");
        assertThat(streamResults.get(1).getData().isLastChunk()).isTrue();
    }

    @Test
    void explicitWorkDirResolvesRelativePathsWithoutChangingGlobalCwd() throws Exception {
        Path globalCwd = tempDir.resolve("global");
        Path workDir = tempDir.resolve("work");
        Files.createDirectories(globalCwd);
        Files.createDirectories(workDir);
        Files.writeString(workDir.resolve("target.txt"), "from-work-dir");
        Cwd.initCwd(globalCwd.toString(), globalCwd.toString(), globalCwd.toString(), null);
        LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir(workDir.toString())
                .build();
        LocalFsOperation operation = new LocalFsOperation("fs", OperationMode.LOCAL, "local fs", config);

        ReadFileResult result = operation.readFile(
                "target.txt",
                BaseFsOperation.FileMode.TEXT,
                null,
                null,
                null,
                "utf-8",
                BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                null).get(5, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getContent()).isEqualTo("from-work-dir");
        assertThat(Path.of(result.getData().getPath()).toRealPath())
                .isEqualTo(workDir.resolve("target.txt").toRealPath());
        assertThat(Path.of(Cwd.getCwd()).toRealPath()).isEqualTo(globalCwd.toRealPath());
    }

    @Test
    void uploadDownloadListAndSearchUseResolvedLocalPaths() throws Exception {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        LocalFsOperation operation = operation();
        Path externalSource = tempDir.resolve("source.bin");
        byte[] bytes = new byte[] {1, 2, 3, 4, 5};
        Files.write(externalSource, bytes);

        UploadFileResult uploadResult = operation.uploadFile(
                externalSource.toString(),
                "store/copy.bin",
                false,
                true,
                true,
                BaseFsOperation.DEFAULT_UPLOAD_CHUNK_SIZE,
                null).get(5, TimeUnit.SECONDS);
        assertThat(uploadResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(uploadResult.getData().getSize()).isEqualTo(bytes.length);

        Path downloadTarget = tempDir.resolve("download.bin");
        DownloadFileResult downloadResult = operation.downloadFile(
                "store/copy.bin",
                downloadTarget.toString(),
                false,
                true,
                true,
                BaseFsOperation.DEFAULT_DOWNLOAD_CHUNK_SIZE,
                null).get(5, TimeUnit.SECONDS);
        assertThat(downloadResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(Files.readAllBytes(downloadTarget)).containsExactly(bytes);

        ListFilesResult filesResult = operation.listFiles(
                "store",
                false,
                null,
                BaseFsOperation.SortBy.NAME,
                false,
                List.of(".bin"),
                null).get(5, TimeUnit.SECONDS);
        assertThat(filesResult.getData().getListItems()).extracting(FileSystemItem::getName)
                .containsExactly("copy.bin");

        SearchFilesResult searchResult = operation.searchFiles(".", "**/*.bin", List.of("source.bin"))
                .get(5, TimeUnit.SECONDS);
        assertThat(searchResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(searchResult.getData().getMatchingFiles()).extracting(FileSystemItem::getName)
                .contains("copy.bin");
    }

    @Test
    void sandboxRestrictionReturnsStructuredErrorResult() throws Exception {
        Path allowed = tempDir.resolve("allowed");
        Path denied = tempDir.resolve("denied");
        Files.createDirectories(allowed);
        Files.createDirectories(denied);
        Path deniedFile = denied.resolve("secret.txt");
        Files.writeString(deniedFile, "secret");
        Cwd.initCwd(allowed.toString(), allowed.toString(), allowed.toString(), null);
        LocalWorkConfig config = LocalWorkConfig.builder()
                .restrictToSandbox(true)
                .sandboxRoot(List.of(allowed.toString()))
                .build();
        LocalFsOperation operation = new LocalFsOperation("fs", OperationMode.LOCAL, "local fs", config);

        ReadFileResult result = operation.readFile(
                deniedFile.toString(),
                BaseFsOperation.FileMode.TEXT,
                null,
                null,
                null,
                "utf-8",
                BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                null).get(5, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("outside sandbox");
    }

    private LocalFsOperation operation() {
        return new LocalFsOperation("fs", OperationMode.LOCAL, "local fs", LocalWorkConfig.builder().build());
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
    }

    private static final class CapturingSubscriber<T> implements Flow.Subscriber<T> {

        private final List<T> items = new ArrayList<>();
        private final CompletableFuture<List<T>> done = new CompletableFuture<>();
        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            done.complete(List.copyOf(items));
        }

        private List<T> await() throws Exception {
            try {
                return done.get(5, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
