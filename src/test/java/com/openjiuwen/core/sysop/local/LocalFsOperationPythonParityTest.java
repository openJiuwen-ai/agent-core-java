/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.Cwd;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.result.DownloadFileStreamResult;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.UploadFileStreamResult;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's module tests in
 * {@code tests/unit_tests/core/sys_operation/sandbox/test_fs.py}.
 */
class LocalFsOperationPythonParityTest {

    @TempDir
    private Path tempDir;

    private LocalFsOperation operation;

    @BeforeEach
    void setUp() {
        Cwd.initCwd(tempDir.toString(), tempDir.toString(), tempDir.toString(), null);
        operation = new LocalFsOperation("fs", OperationMode.LOCAL, "local fs", LocalWorkConfig.builder().build());
    }

    @AfterEach
    void tearDown() {
        Cwd.clear();
    }

    @Test
    void fsReadWrite() throws Exception {
        String content = "Hello, world!\nLine 2";

        assertThat(writeText("test_basics.txt", content, false, false).getCode())
                .isEqualTo(StatusCode.SUCCESS.getCode());

        ReadFileResult readResult = readText("test_basics.txt", null, null, null);
        assertThat(readResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(readResult.getData().getContent()).isEqualTo(content);

        writeText("test_append.txt", "Appended", true, false);
        assertThat(readText("test_append.txt", null, null, null).getData().getContent())
                .isEqualTo("\nAppended");

        byte[] binary = new byte[] {0, 1, 2};
        operation.writeFile(sandboxPath("test.bin"), binary, BaseFsOperation.FileMode.BYTES,
                false, false, false, true, "644", "utf-8", null).get(5, TimeUnit.SECONDS);
        ReadFileResult readBinary = operation.readFile(sandboxPath("test.bin"), BaseFsOperation.FileMode.BYTES,
                null, null, null, "utf-8", BaseFsOperation.DEFAULT_READ_CHUNK_SIZE, null)
                .get(5, TimeUnit.SECONDS);
        assertThat((byte[]) readBinary.getData().getContent()).containsExactly(binary);
    }

    @Test
    void fsReadHeadTailLineRange() throws Exception {
        String path = "multi_line.txt";
        writeText(path, "Line 1\nLine 2\nLine 3\nLine 4\nLine 5", false, false);

        assertThat(readText(path, 3, null, null).getData().getContent())
                .isEqualTo("Line 1\nLine 2\nLine 3\n");
        assertThat(readText(path, null, 2, null).getData().getContent())
                .isEqualTo("Line 4\nLine 5");
        assertThat(readText(path, null, null, new BaseFsProtocal.LineRange(2, 4)).getData().getContent())
                .isEqualTo("Line 2\nLine 3\nLine 4\n");

        List<ReadFileStreamResult> chunks = collect(operation.readFileStream(sandboxPath(path),
                BaseFsOperation.FileMode.TEXT,
                null, null, new BaseFsProtocal.LineRange(2, 4), "utf-8",
                BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE, null));
        assertThat(joinChunks(chunks)).isEqualTo("Line 2\nLine 3\nLine 4\n");
        assertThat(chunks.get(chunks.size() - 1).getData().isLastChunk()).isTrue();
    }

    @Test
    void fsReadFileMutuallyExclusiveParams() throws Exception {
        String path = "multi_line.txt";
        writeText(path, "line1\nline2\nline3", false, false);

        ReadFileResult result = readText(path, 2, 1, null);
        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("cannot be specified simultaneously");

        List<ReadFileStreamResult> chunks = collect(operation.readFileStream(sandboxPath(path),
                BaseFsOperation.FileMode.TEXT,
                -1, 2, null, "utf-8", BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE, null));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getCode()).isEqualTo(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode());
    }

    @Test
    void fsReadFileNegativeZeroParams() throws Exception {
        String path = "multi_line.txt";
        String content = "line1\nline2\nline3\nline4\nline5";
        writeText(path, content, false, false);

        assertThat(readText(path, -5, null, null).getData().getContent()).isEqualTo("");
        assertThat(readText(path, null, -5, null).getData().getContent()).isEqualTo("");
        assertThat(readText(path, 0, null, null).getData().getContent()).isEqualTo(content);
        assertThat(readText(path, null, 0, null).getData().getContent()).isEqualTo(content);
        assertThat(readText(path, null, null, new BaseFsProtocal.LineRange(0, 0)).getData().getContent())
                .isEqualTo("");
        assertThat(readText(path, null, null, new BaseFsProtocal.LineRange(1, -1)).getData().getContent())
                .isEqualTo("");

        List<ReadFileStreamResult> chunks = collect(operation.readFileStream(sandboxPath(path),
                BaseFsOperation.FileMode.TEXT,
                -5, null, null, "utf-8", BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE, null));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getData().getChunkContent()).isEqualTo("");
    }

    @Test
    void fsReadFileBinaryModeParameters() throws Exception {
        String path = "binary_test.txt";
        writeText(path, "Hello\nLine 2", false, false);

        ReadFileResult result = operation.readFile(sandboxPath(path), BaseFsOperation.FileMode.BYTES,
                2, null, null, "utf-8", BaseFsOperation.DEFAULT_READ_CHUNK_SIZE, null)
                .get(5, TimeUnit.SECONDS);
        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("only supported in text mode");

        List<ReadFileStreamResult> chunks = collect(operation.readFileStream(sandboxPath(path),
                BaseFsOperation.FileMode.BYTES,
                null, null, new BaseFsProtocal.LineRange(1, 2), "utf-8",
                BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE, null));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getCode()).isEqualTo(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode());
    }

    @Test
    void fsSecurityAndStreams() throws Exception {
        LocalWorkConfig restricted = LocalWorkConfig.builder()
                .restrictToSandbox(true)
                .sandboxRoot(List.of(tempDir.toString()))
                .build();
        LocalFsOperation restrictedOperation = new LocalFsOperation("fs", OperationMode.LOCAL, "local fs", restricted);

        ReadFileResult denied = restrictedOperation.readFile("../outside.txt", BaseFsOperation.FileMode.TEXT,
                null, null, null, "utf-8", BaseFsOperation.DEFAULT_READ_CHUNK_SIZE, null)
                .get(5, TimeUnit.SECONDS);
        assertThat(denied.getCode()).isEqualTo(StatusCode.SYS_OPERATION_FS_EXECUTION_ERROR.getCode());
        assertThat(denied.getMessage()).containsAnyOf("Access denied", "outside sandbox");

        writeText("stream.txt", "line1\nline2", false, false);
        List<ReadFileStreamResult> chunks = collect(operation.readFileStream(sandboxPath("stream.txt"),
                BaseFsOperation.FileMode.TEXT, null, null, null, "utf-8",
                BaseFsOperation.DEFAULT_READ_STREAM_CHUNK_SIZE, null));
        assertThat(joinChunks(chunks)).isEqualTo("line1\nline2");
    }

    @Test
    void fsUploadDownload() throws Exception {
        Path localSource = tempDir.resolve("upload.txt");
        Files.writeString(localSource, "Hello, upload and download!");

        assertThat(operation.uploadFile(localSource.toString(), sandboxPath("uploaded.txt"),
                false, true, true, BaseFsOperation.DEFAULT_UPLOAD_CHUNK_SIZE, null)
                .get(5, TimeUnit.SECONDS).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        Path localTarget = tempDir.resolve("downloaded.txt");
        assertThat(operation.downloadFile(sandboxPath("uploaded.txt"), localTarget.toString(),
                false, true, true, BaseFsOperation.DEFAULT_DOWNLOAD_CHUNK_SIZE, null)
                .get(5, TimeUnit.SECONDS).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(Files.readString(localTarget)).isEqualTo("Hello, upload and download!");

        List<UploadFileStreamResult> uploadStream = collect(operation.uploadFileStream(localSource.toString(),
                sandboxPath("stream_uploaded.txt"), false, true, true,
                BaseFsOperation.DEFAULT_UPLOAD_STREAM_CHUNK_SIZE, null));
        assertThat(uploadStream).hasSize(1);
        assertThat(uploadStream.get(0).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());

        Path streamTarget = tempDir.resolve("stream_downloaded.txt");
        List<DownloadFileStreamResult> downloadStream = collect(operation.downloadFileStream(
                sandboxPath("stream_uploaded.txt"), streamTarget.toString(), false, true, true,
                BaseFsOperation.DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE, null));
        assertThat(downloadStream).hasSize(1);
        assertThat(downloadStream.get(0).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
    }

    @Test
    void fsListOperations() throws Exception {
        writeText("file1.txt", "Content 1", false, false);
        writeText("dir1/file2.txt", "Content 2", false, false);
        writeText("dir1/subdir1/file3.txt", "Content 3", false, false);
        writeText("dir2/file4.txt", "Content 4", false, false);

        ListFilesResult listResult = operation.listFiles(tempDir.toString(), false, null, BaseFsOperation.SortBy.NAME,
                false, null, null).get(5, TimeUnit.SECONDS);
        assertThat(listResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(names(listResult.getData().getListItems())).contains("file1.txt");

        ListFilesResult recursiveResult = operation.listFiles(tempDir.toString(), true, null, BaseFsOperation.SortBy.NAME,
                false, null, null).get(5, TimeUnit.SECONDS);
        assertThat(recursiveResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(names(recursiveResult.getData().getListItems()))
                .contains("file1.txt", "file2.txt", "file3.txt", "file4.txt");

        ListFilesResult txtResult = operation.listFiles(tempDir.toString(), true, null, BaseFsOperation.SortBy.NAME,
                false, List.of(".txt"), null).get(5, TimeUnit.SECONDS);
        assertThat(txtResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(txtResult.getData().getListItems()).hasSizeGreaterThanOrEqualTo(4);

        ListDirsResult dirsResult = operation.listDirectories(tempDir.toString(), true, null, BaseFsOperation.SortBy.NAME,
                false, null).get(5, TimeUnit.SECONDS);
        assertThat(dirsResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(names(dirsResult.getData().getListItems())).contains("dir1", "dir2", "subdir1");
    }

    @Test
    void fsSearchOperations() throws Exception {
        writeText("test1.txt", "Content 1", false, false);
        writeText("test2.txt", "Content 2", false, false);
        writeText("data1.csv", "CSV content", false, false);
        writeText("subdir/test3.txt", "Content 3", false, false);

        SearchFilesResult txtResult = operation.searchFiles(tempDir.toString(), "*.txt", null)
                .get(5, TimeUnit.SECONDS);
        assertThat(txtResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(names(txtResult.getData().getMatchingFiles())).contains("test1.txt", "test2.txt", "test3.txt");

        SearchFilesResult excludeResult = operation.searchFiles(tempDir.toString(), "*", List.of("*.csv"))
                .get(5, TimeUnit.SECONDS);
        assertThat(excludeResult.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(excludeResult.getData().getMatchingFiles())
                .allSatisfy(item -> assertThat(item.getName()).doesNotEndWith(".csv"));

        SearchFilesResult noMatch = operation.searchFiles(tempDir.toString(), "*.xyz", null)
                .get(5, TimeUnit.SECONDS);
        assertThat(noMatch.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(noMatch.getData().getTotalMatches()).isZero();
    }

    private ReadFileResult readText(String path,
                                    Integer head,
                                    Integer tail,
                                    BaseFsProtocal.LineRange lineRange) throws Exception {
        return operation.readFile(sandboxPath(path), BaseFsOperation.FileMode.TEXT, head, tail, lineRange,
                "utf-8", BaseFsOperation.DEFAULT_READ_CHUNK_SIZE, null).get(5, TimeUnit.SECONDS);
    }

    private com.openjiuwen.core.sysop.result.WriteFileResult writeText(String path,
                                                                               String content,
                                                                               boolean prependNewline,
                                                                               boolean appendNewline)
            throws Exception {
        return operation.writeFile(sandboxPath(path), content, BaseFsOperation.FileMode.TEXT,
                prependNewline, appendNewline, false, true, "644", "utf-8", null)
                .get(5, TimeUnit.SECONDS);
    }

    private String sandboxPath(String path) {
        return tempDir.resolve(path).normalize().toString();
    }

    private static String joinChunks(List<ReadFileStreamResult> chunks) {
        return chunks.stream()
                .map(chunk -> String.valueOf(chunk.getData().getChunkContent()))
                .collect(Collectors.joining());
    }

    private static Set<String> names(List<FileSystemItem> items) {
        return items.stream().map(FileSystemItem::getName).collect(Collectors.toSet());
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
    }

    private static final class CapturingSubscriber<T> implements Flow.Subscriber<T> {
        private final java.util.ArrayList<T> items = new java.util.ArrayList<>();
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
