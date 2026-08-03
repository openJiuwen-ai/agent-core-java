/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseCodeOperation;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sys_operation.sandbox.launchers.SandboxLauncher;
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
 * Mirrors Python's {@code CodeOperation} behavior in
 * {@code openjiuwen/core/sys_operation/sandbox/code_operation.py}.
 */
class SandboxCodeOperationTest {

    @AfterEach
    void cleanup() throws Exception {
        SandboxRegistry.unregisterLauncher("code-operation-test-launcher");
        SandboxRegistry.unregisterProvider("code-operation-test-sandbox", "code");
        Field field = com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxGateway.class
                .getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void executeCodeRoutesThroughSandboxGateway() {
        SandboxRegistry.registerLauncher("code-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("code-operation-test-sandbox", "code", TestCodeProvider.class);
        SandboxCodeOperation operation = operation();

        ExecuteCodeResult result = operation.executeCode(
                "print('ok')",
                BaseCodeOperation.CodeLanguage.PYTHON,
                12,
                Map.of("PYTHONUTF8", "1"),
                "/workspace",
                Map.of("force_file", true)).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getCodeContent()).isEqualTo("print('ok')");
        assertThat(result.getData().getLanguage()).isEqualTo("python");
        assertThat(result.getData().getStdout()).isEqualTo("python:12:/workspace:true:1");
        assertThat(TestCodeProvider.lastEndpoint.baseUrl()).isEqualTo("http://sandbox");
    }

    @Test
    void executeCodeStreamMapsGatewayPublisherItems() throws Exception {
        SandboxRegistry.registerLauncher("code-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("code-operation-test-sandbox", "code", TestCodeProvider.class);
        SandboxCodeOperation operation = operation();

        List<ExecuteCodeStreamResult> chunks = collect(operation.executeCodeStream(
                "console.log('ok')",
                BaseCodeOperation.CodeLanguage.JAVASCRIPT,
                30,
                null,
                null,
                null));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(chunks.get(0).getData().getType()).isEqualTo("stdout");
        assertThat(chunks.get(0).getData().getText()).contains("javascript");
        assertThat(chunks.get(1).getData().getExitCode()).isZero();
    }

    private static SandboxCodeOperation operation() {
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("code-operation-test-launcher")
                .sandboxType("code-operation-test-sandbox")
                .build();
        SandboxRunConfig runConfig = SandboxRunConfig.builder()
                .config(SandboxGatewayConfig.builder()
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .isolationKeyTemplate("code-op-test")
                .build();
        return new SandboxCodeOperation(
                "code",
                OperationMode.SANDBOX,
                "Sandbox code execution operation",
                runConfig);
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
            return CompletableFuture.completedFuture(new LaunchedSandbox("http://sandbox", "sb-code"));
        }
    }

    public static final class TestCodeProvider {
        private static SandboxEndpoint lastEndpoint;

        public TestCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            lastEndpoint = endpoint;
        }

        public CompletableFuture<ExecuteCodeResult> executeCode(String code, String language, int timeoutSeconds,
                                                                Map<String, String> environment, String cwd,
                                                                Map<String, Object> options) {
            ExecuteCodeResult result = new ExecuteCodeResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage("Code executed successfully");
            result.setData(ExecuteCodeData.builder()
                    .codeContent(code)
                    .language(language)
                    .stdout(language + ":" + timeoutSeconds + ":" + cwd + ":"
                            + Boolean.TRUE.equals(options == null ? null : options.get("force_file")) + ":"
                            + (environment == null ? 0 : environment.size()))
                    .stderr("")
                    .exitCode(0)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
                String code,
                String language,
                int timeoutSeconds,
                Map<String, String> environment,
                String cwd,
                Map<String, Object> options) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done;

                @Override
                public void request(long itemCount) {
                    if (done) {
                        return;
                    }
                    done = true;
                    ExecuteCodeStreamResult stdout = new ExecuteCodeStreamResult();
                    stdout.setCode(StatusCode.SUCCESS.getCode());
                    stdout.setMessage("Get stdout stream successfully");
                    stdout.setData(ExecuteCodeChunkData.builder()
                            .type("stdout")
                            .text(language + ":" + code)
                            .chunkIndex(0)
                            .build());
                    ExecuteCodeStreamResult exit = new ExecuteCodeStreamResult();
                    exit.setCode(StatusCode.SUCCESS.getCode());
                    exit.setMessage("Code executed successfully");
                    exit.setData(ExecuteCodeChunkData.builder()
                            .chunkIndex(1)
                            .exitCode(0)
                            .build());
                    subscriber.onNext(stdout);
                    subscriber.onNext(exit);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
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
