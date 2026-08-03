/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseCodeOperation;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code CodeOperation} behavior in
 * {@code openjiuwen/core/sys_operation/local/code_operation.py}.
 */
class LocalCodeOperationTest {

    @TempDir
    private Path tempDir;

    @Test
    void executeCodeRejectsBlankCode() throws Exception {
        ExecuteCodeResult result = operation().executeCode(
                "  ",
                BaseCodeOperation.CodeLanguage.PYTHON,
                5,
                null,
                null,
                null).get(5, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("code can not be empty");
    }

    @Test
    void executeCodeCapturesOutputExitCodeAndCwd() throws Exception {
        Files.writeString(tempDir.resolve("marker.txt"), "ok");
        String code = """
                import os, sys, pathlib
                print(os.getcwd())
                print(os.environ.get('OPENJIUWEN_CODE_TEST'))
                print(os.environ.get('PYTHONUTF8'))
                print(pathlib.Path('marker.txt').read_text())
                print('warn', file=sys.stderr)
                sys.exit(7)
                """;

        ExecuteCodeResult result = operation().executeCode(
                code,
                BaseCodeOperation.CodeLanguage.PYTHON,
                10,
                Map.of("OPENJIUWEN_CODE_TEST", "yes"),
                tempDir.toString(),
                null).get(20, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(Path.of(firstLine(result.getData().getStdout())).toRealPath())
                .isEqualTo(tempDir.toRealPath());
        assertThat(result.getData().getStdout()).contains("yes", "1", "ok");
        assertThat(result.getData().getStderr()).contains("warn");
        assertThat(result.getData().getExitCode()).isEqualTo(7);
        assertThat(result.getData().getLanguage()).isEqualTo("python");
        assertThat(result.getData().getCodeContent()).isEqualTo(code);
    }

    @Test
    void executeCodeUsesTempFileWhenForced() throws Exception {
        String code = "print('file-mode')";

        ExecuteCodeResult result = operation().executeCode(
                code,
                BaseCodeOperation.CodeLanguage.PYTHON,
                10,
                null,
                null,
                Map.of("force_file", true)).get(20, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getStdout()).contains("file-mode");
        assertThat(result.getData().getExitCode()).isZero();
    }

    @Test
    void executeCodeTimeoutReturnsErrorWithPartialOutput() throws Exception {
        String code = """
                import time
                print('before', flush=True)
                time.sleep(2)
                print('after')
                """;

        ExecuteCodeResult result = operation().executeCode(
                code,
                BaseCodeOperation.CodeLanguage.PYTHON,
                1,
                null,
                null,
                null).get(10, TimeUnit.SECONDS);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("execution timeout after 1 seconds");
        assertThat(result.getData().getStdout()).contains("before");
        assertThat(result.getData().getStdout()).doesNotContain("after");
    }

    @Test
    void executeCodeStreamEmitsOutputAndExitChunks() throws Exception {
        Files.writeString(tempDir.resolve("stream-marker.txt"), "stream-cwd-ok");
        String code = """
                import pathlib, sys
                print(pathlib.Path('stream-marker.txt').read_text(), flush=True)
                print('hello', flush=True)
                print('warn', file=sys.stderr, flush=True)
                sys.exit(3)
                """;

        List<ExecuteCodeStreamResult> chunks = collect(operation().executeCodeStream(
                code,
                BaseCodeOperation.CodeLanguage.PYTHON,
                10,
                null,
                tempDir.toString(),
                Map.of("chunk_size", 16)));

        assertThat(chunks).isNotEmpty();
        assertThat(combinedText(chunks, "stdout")).contains("stream-cwd-ok", "hello");
        assertThat(combinedText(chunks, "stderr")).contains("warn");
        assertThat(chunks.get(chunks.size() - 1).getData().getExitCode()).isEqualTo(3);
    }

    private LocalCodeOperation operation() {
        return new LocalCodeOperation("code", OperationMode.LOCAL, "local code operation", null);
    }

    private static String firstLine(String text) {
        return text == null ? "" : text.lines().findFirst().orElse("");
    }

    private static String combinedText(List<ExecuteCodeStreamResult> chunks, String type) {
        StringBuilder builder = new StringBuilder();
        for (ExecuteCodeStreamResult chunk : chunks) {
            if (chunk.getData() != null && type.equals(chunk.getData().getType())) {
                builder.append(chunk.getData().getText());
            }
        }
        return builder.toString();
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
                return done.get(20, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
