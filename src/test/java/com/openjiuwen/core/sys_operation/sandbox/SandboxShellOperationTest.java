/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseShellOperation;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.config.SandboxLauncherConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
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
 * Mirrors Python's {@code ShellOperation} behavior in
 * {@code openjiuwen/core/sys_operation/sandbox/shell_operation.py}.
 */
class SandboxShellOperationTest {

    @AfterEach
    void cleanup() throws Exception {
        SandboxRegistry.unregisterLauncher("shell-operation-test-launcher");
        SandboxRegistry.unregisterProvider("shell-operation-test-sandbox", "shell");
        Field field = com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxGateway.class
                .getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void executeCmdRoutesThroughSandboxGateway() {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        SandboxShellOperation operation = operation();

        ExecuteCmdResult result = operation.executeCmd(
                "echo ok",
                "/workspace",
                12,
                Map.of("A", "B"),
                Map.of("pty", true),
                BaseShellOperation.ShellType.BASH).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getCommand()).isEqualTo("echo ok");
        assertThat(result.getData().getCwd()).isEqualTo("/workspace");
        assertThat(result.getData().getStdout()).isEqualTo("bash:12:B:true");
        assertThat(TestShellProvider.lastEndpoint.baseUrl()).isEqualTo("http://sandbox-shell");
    }

    @Test
    void executeCmdStreamMapsGatewayPublisherItems() throws Exception {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        SandboxShellOperation operation = operation();

        List<ExecuteCmdStreamResult> chunks = collect(operation.executeCmdStream(
                "printf ok",
                null,
                30,
                null,
                null,
                BaseShellOperation.ShellType.SH));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(chunks.get(0).getData().getType()).isEqualTo("stdout");
        assertThat(chunks.get(0).getData().getText()).isEqualTo("sh:printf ok");
        assertThat(chunks.get(1).getData().getExitCode()).isZero();
    }

    @Test
    void executeCmdBackgroundDelegatesToGatewayProvider() {
        SandboxRegistry.registerLauncher("shell-operation-test-launcher", TestLauncher.class);
        SandboxRegistry.registerProvider("shell-operation-test-sandbox", "shell", TestShellProvider.class);
        SandboxShellOperation operation = operation();

        ExecuteCmdBackgroundResult result = operation.executeCmdBackground(
                "sleep 10",
                "/workspace",
                Map.of("PATH", "/bin"),
                1.5d,
                BaseShellOperation.ShellType.BASH).join();

        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getData().getCommand()).isEqualTo("sleep 10");
        assertThat(result.getData().getCwd()).isEqualTo("/workspace");
        assertThat(result.getData().getPid()).isEqualTo(1500);
    }

    private static SandboxShellOperation operation() {
        SandboxLauncherConfig launcherConfig = SandboxLauncherConfig.builder()
                .launcherType("shell-operation-test-launcher")
                .sandboxType("shell-operation-test-sandbox")
                .build();
        SandboxRunConfig runConfig = SandboxRunConfig.builder()
                .config(SandboxGatewayConfig.builder()
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .isolationKeyTemplate("shell-op-test")
                .build();
        return new SandboxShellOperation(
                "shell",
                OperationMode.SANDBOX,
                "Sandbox shell execution operation",
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
            return CompletableFuture.completedFuture(new LaunchedSandbox("http://sandbox-shell", "sb-shell"));
        }
    }

    public static final class TestShellProvider {
        private static SandboxEndpoint lastEndpoint;

        public TestShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            lastEndpoint = endpoint;
        }

        public CompletableFuture<ExecuteCmdResult> executeCmd(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options,
                String shellType) {
            ExecuteCmdResult result = new ExecuteCmdResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ExecuteCmdData.builder()
                    .command(command)
                    .cwd(cwd)
                    .stdout(shellType + ":" + timeoutSeconds + ":" + environment.get("A") + ":"
                            + Boolean.TRUE.equals(options.get("pty")))
                    .stderr("")
                    .exitCode(0)
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options,
                String shellType) {
            return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                private boolean done;

                @Override
                public void request(long itemCount) {
                    if (done) {
                        return;
                    }
                    done = true;
                    subscriber.onNext(streamChunk(shellType + ":" + command, "stdout", 0, null));
                    subscriber.onNext(streamChunk("", null, 1, 0));
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    done = true;
                }
            });
        }

        public CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(
                String command,
                String cwd,
                Map<String, String> environment,
                double grace,
                String shellType) {
            ExecuteCmdBackgroundResult result = new ExecuteCmdBackgroundResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ExecuteCmdBackgroundData.builder()
                    .command(command)
                    .cwd(cwd)
                    .pid((int) (grace * 1000))
                    .build());
            return CompletableFuture.completedFuture(result);
        }

        private static ExecuteCmdStreamResult streamChunk(String text, String type, int index, Integer exitCode) {
            ExecuteCmdStreamResult result = new ExecuteCmdStreamResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(StatusCode.SUCCESS.getErrmsg());
            result.setData(ExecuteCmdChunkData.builder()
                    .text(text)
                    .type(type)
                    .chunkIndex(index)
                    .exitCode(exitCode)
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
