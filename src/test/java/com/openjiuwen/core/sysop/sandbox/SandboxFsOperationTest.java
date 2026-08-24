/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.result.FileSystemData;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileChunkData;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.SearchFilesData;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.WriteFileData;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code FsOperation} behavior in
 * {@code openjiuwen/core/sys_operation/sandbox/fs_operation.py}.
 */
class SandboxFsOperationTest {

    @AfterEach
    void cleanup() throws Exception {
        SandboxRegistry.unregisterLauncher("fs-operation-test-launcher");
        SandboxRegistry.unregisterProvider("fs-operation-test-sandbox", "fs");
        Field field = com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway.class
                .getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void readFileRoutesThroughSandboxGateway() {
        SandboxRegistry.registerLauncher("fs-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("fs-operation-test-sandbox", "fs", TestFsProvider.class);
        SandboxFsOperation operation = operation();

        ReadFileResult result = operation.readFile(
                "/workspace/a.txt",
                BaseFsOperation.FileMode.TEXT,
                3,
                null,
                null,
                "utf-8",
                128,
                Map.of("flag", true)).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getPath()).isEqualTo("/workspace/a.txt");
        assertThat(result.getData().getContent()).isEqualTo("text:3:128:true");
        assertThat(TestFsProvider.lastEndpoint.baseUrl()).isEqualTo("http://sandbox-fs");
    }

    @Test
    void readFileStreamMapsGatewayPublisherItems() throws Exception {
        SandboxRegistry.registerLauncher("fs-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("fs-operation-test-sandbox", "fs", TestFsProvider.class);
        SandboxFsOperation operation = operation();

        List<ReadFileStreamResult> chunks = collect(operation.readFileStream(
                "/workspace/a.txt",
                BaseFsOperation.FileMode.TEXT,
                null,
                null,
                new BaseFsProtocal.LineRange(1, 2),
                "utf-8",
                64,
                null));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(chunks.get(0).getData().getChunkContent()).isEqualTo("line-1\n");
        assertThat(chunks.get(1).getData().isLastChunk()).isTrue();
    }

    @Test
    void writeFileRoutesStringAndByteContentToProviderOverloads() {
        SandboxRegistry.registerLauncher("fs-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("fs-operation-test-sandbox", "fs", TestFsProvider.class);
        SandboxFsOperation operation = operation();

        WriteFileResult text = operation.writeFile(
                "/workspace/a.txt",
                "hello",
                BaseFsOperation.FileMode.TEXT,
                false,
                true,
                false,
                true,
                "644",
                "utf-8",
                null).join();
        WriteFileResult bytes = operation.writeFile(
                "/workspace/a.bin",
                new byte[]{1, 2, 3},
                BaseFsOperation.FileMode.BYTES,
                false,
                false,
                false,
                true,
                "644",
                "utf-8",
                null).join();

        assertThat(text.getData().getPath()).isEqualTo("string:/workspace/a.txt");
        assertThat(text.getData().getSize()).isEqualTo(6);
        assertThat(bytes.getData().getPath()).isEqualTo("bytes:/workspace/a.bin");
        assertThat(bytes.getData().getSize()).isEqualTo(3);
    }

    @Test
    void listAndSearchFilesPreserveCollectionArguments() {
        SandboxRegistry.registerLauncher("fs-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("fs-operation-test-sandbox", "fs", TestFsProvider.class);
        SandboxFsOperation operation = operation();

        ListFilesResult listed = operation.listFiles(
                "/workspace",
                true,
                2,
                BaseFsOperation.SortBy.MODIFIED_TIME,
                true,
                List.of(".java"),
                Map.of("includeHidden", true)).join();
        SearchFilesResult searched = operation.searchFiles("/workspace", "*.java", List.of("target/*")).join();

        assertThat(listed.getData().getRootPath()).isEqualTo("/workspace:modified_time:true:.java");
        assertThat(listed.getData().getMaxDepth()).isEqualTo(2);
        assertThat(searched.getData().getSearchPath()).isEqualTo("/workspace");
        assertThat(searched.getData().getExcludePatterns()).containsExactly("target/*");
    }

    private static SandboxFsOperation operation() {
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("fs-operation-test-launcher")
                .sandboxType("fs-operation-test-sandbox")
                .build();
        SandboxRunConfig runConfig = SandboxRunConfig.builder()
                .config(SandboxGatewayConfig.builder()
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .isolationKeyTemplate("fs-op-test")
                .build();
        return new SandboxFsOperation("fs", OperationMode.SANDBOX, "Sandbox file system operation", runConfig);
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
    }

    public static final class TestLauncher extends SandboxLauncher {

        @Override
        public CompletableFuture<LaunchedSandbox> launch(
                SandboxLauncherConfig config,
                int timeoutSeconds,
                String isolationKey) {
            return CompletableFuture.completedFuture(new LaunchedSandbox("http://sandbox-fs", "sb-fs"));
        }
    }

    public static final class TestFsProvider {
        private static SandboxEndpoint lastEndpoint;

        public TestFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            lastEndpoint = endpoint;
        }

        public CompletableFuture<ReadFileResult> readFile(String path, String mode, Integer head, Integer tail,
                                                          BaseFsProtocal.LineRange lineRange, String encoding,
                                                          int chunkSize, Map<String, Object> options) {
            ReadFileResult result = new ReadFileResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ReadFileData.builder()
                    .path(path)
                    .content(mode + ":" + head + ":" + chunkSize + ":"
                            + Boolean.TRUE.equals(options == null ? null : options.get("flag")))
                    .mode(mode)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        public Flow.Publisher<ReadFileStreamResult> readFileStream(
                String path,
                String mode,
                Integer head,
                Integer tail,
                BaseFsProtocal.LineRange lineRange,
                String encoding,
                int chunkSize,
                Map<String, Object> options) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done;

                @Override
                public void request(long itemCount) {
                    if (done) {
                        return;
                    }
                    done = true;
                    subscriber.onNext(streamChunk(path, "line-1\n", 0, false));
                    subscriber.onNext(streamChunk(path, "line-2\n", 1, true));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }

        public CompletableFuture<WriteFileResult> writeFile(String path, String content, String mode,
                                                            boolean prependNewline, boolean appendNewline,
                                                            boolean append, boolean createIfNotExist,
                                                            String permissions, String encoding,
                                                            Map<String, Object> options) {
            String value = content == null ? "" : content;
            if (prependNewline) {
                value = "\n" + value;
            }
            if (appendNewline) {
                value = value + "\n";
            }
            return CompletableFuture.completedFuture(writeResult("string:" + path, value.length(), mode));
        }

        public CompletableFuture<WriteFileResult> writeFile(String path, byte[] content, String mode,
                                                            boolean prependNewline, boolean appendNewline,
                                                            boolean append, boolean createIfNotExist,
                                                            String permissions, String encoding,
                                                            Map<String, Object> options) {
            return CompletableFuture.completedFuture(writeResult("bytes:" + path, content == null ? 0 : content.length,
                    mode));
        }

        public CompletableFuture<ListFilesResult> listFiles(
                String path,
                boolean recursive,
                Integer maxDepth,
                String sortBy,
                boolean sortDescending,
                List<String> fileTypes,
                Map<String, Object> options) {
            ListFilesResult result = new ListFilesResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(FileSystemData.builder()
                    .totalCount(1)
                    .listItems(List.of(FileSystemItem.builder().name("A.java").path(path + "/A.java").build()))
                    .rootPath(path + ":" + sortBy + ":" + sortDescending + ":" + String.join(",", fileTypes))
                    .recursive(recursive)
                    .maxDepth(maxDepth)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        public CompletableFuture<SearchFilesResult> searchFiles(String path, String pattern,
                                                                List<String> excludePatterns) {
            SearchFilesResult result = new SearchFilesResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(SearchFilesData.builder()
                    .totalMatches(1)
                    .matchingFiles(List.of(FileSystemItem.builder().name("A.java").path(path + "/A.java").build()))
                    .searchPath(path)
                    .searchPattern(pattern)
                    .excludePatterns(excludePatterns)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        private static ReadFileStreamResult streamChunk(String path, String content, int index, boolean last) {
            ReadFileStreamResult result = new ReadFileStreamResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ReadFileChunkData.builder()
                    .path(path)
                    .chunkContent(content)
                    .mode("text")
                    .chunkSize(content.length())
                    .chunkIndex(index)
                    .isLastChunk(last)
                    .build());
            return result;
        }

        private static WriteFileResult writeResult(String path, int size, String mode) {
            WriteFileResult result = new WriteFileResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(WriteFileData.builder()
                    .path(path)
                    .size(size)
                    .mode(mode)
                    .build());
            return result;
        }
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
                return done.get(10, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
