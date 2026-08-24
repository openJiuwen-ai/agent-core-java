/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.OperationDef;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.OperationRegistry;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sandbox shell execution operation.
 *
 * <p>Mirrors Python's {@code ShellOperation} in
 * {@code openjiuwen/core/sys_operation/sandbox/shell_operation.py}.</p>
 */
public class SandboxShellOperation extends BaseShellOperation {

    public static final OperationDef OP_DEF = new OperationDef(
            SandboxShellOperation.class,
            "Sandbox shell execution operation",
            "shell",
            OperationMode.SANDBOX
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SandboxGatewayClientMixin sandboxClient = new SandboxGatewayClientMixin();

    static {
        OperationRegistry.register(SandboxShellOperation.class);
    }

    public SandboxShellOperation(SandboxGatewayConfig config) {
        this("shell", OperationMode.SANDBOX, "Sandbox shell execution operation",
                SandboxRunConfig.builder().config(config).build());
    }

    public SandboxShellOperation(Object runConfig) {
        this("shell", OperationMode.SANDBOX, "Sandbox shell execution operation", runConfig);
    }

    public SandboxShellOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
        SandboxRunConfig sandboxRunConfig = toSandboxRunConfig(runConfig);
        sandboxClient.initClientContext(sandboxRunConfig, "shell");
    }

    @Override
    public CompletableFuture<ExecuteCmdResult> executeCmd(String command, String cwd, Integer timeout,
                                                          Map<String, String> environment,
                                                          Map<String, Object> options, ShellType shellType) {
        return sandboxClient.invoke("executeCmd", executeParams(command, cwd, timeout, environment, options,
                shellType)).thenApply(raw -> convert(raw, ExecuteCmdResult.class));
    }

    @Override
    public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, Integer timeout,
                                                                   Map<String, String> environment,
                                                                   Map<String, Object> options,
                                                                   ShellType shellType) {
        return mappedPublisher(sandboxClient.invokeStream("executeCmdStream", executeParams(command, cwd, timeout,
                environment, options, shellType)), ExecuteCmdStreamResult.class);
    }

    @Override
    public CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(String command, String cwd,
                                                                              Map<String, String> environment,
                                                                              double grace, ShellType shellType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command);
        params.put("cwd", cwd);
        params.put("environment", environment);
        params.put("grace", grace);
        params.put("shellType", shellTypeValue(shellType));
        return sandboxClient.invoke("executeCmdBackground", params)
                .thenApply(raw -> convert(raw, ExecuteCmdBackgroundResult.class));
    }

    private static Map<String, Object> executeParams(String command, String cwd, Integer timeout,
                                                     Map<String, String> environment, Map<String, Object> options,
                                                     ShellType shellType) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", command);
        params.put("cwd", cwd);
        params.put("timeoutSeconds", timeout);
        params.put("environment", environment);
        params.put("options", options);
        params.put("shellType", shellTypeValue(shellType));
        return params;
    }

    private static String shellTypeValue(ShellType shellType) {
        return (shellType == null ? ShellType.AUTO : shellType).value();
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
