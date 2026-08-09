/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.OperationDef;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.OperationRegistry;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;

/**
 * Sandbox code execution operation.
 *
 * <p>Mirrors Python's {@code CodeOperation} in
 * {@code openjiuwen/core/sys_operation/sandbox/code_operation.py}.</p>
 */
public class SandboxCodeOperation extends BaseCodeOperation {

    public static final OperationDef OP_DEF = new OperationDef(
            SandboxCodeOperation.class,
            "Sandbox code execution operation",
            "code",
            OperationMode.SANDBOX
    );

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SandboxGatewayClientMixin sandboxClient = new SandboxGatewayClientMixin();

    static {
        OperationRegistry.register(SandboxCodeOperation.class);
    }

    public SandboxCodeOperation(SandboxGatewayConfig config) {
        this("code", OperationMode.SANDBOX, "Sandbox code execution operation",
                SandboxRunConfig.builder().config(config).build());
    }

    public SandboxCodeOperation(Object runConfig) {
        this("code", OperationMode.SANDBOX, "Sandbox code execution operation", runConfig);
    }

    public SandboxCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
        SandboxRunConfig sandboxRunConfig = toSandboxRunConfig(runConfig);
        sandboxClient.initClientContext(sandboxRunConfig, "code");
    }

    @Override
    public CompletableFuture<ExecuteCodeResult> executeCode(String code, CodeLanguage language, int timeout,
                                                            Map<String, String> environment, String cwd,
                                                            Map<String, Object> options) {
        return sandboxClient.invoke("executeCode", params(code, language, timeout, environment, cwd, options))
                .thenApply(SandboxCodeOperation::toExecuteCodeResult);
    }

    @Override
    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(String code, CodeLanguage language, int timeout,
                                                                     Map<String, String> environment, String cwd,
                                                                     Map<String, Object> options) {
        CompletableFuture<Flow.Publisher<?>> rawPublisher = sandboxClient.invokeStream(
                "executeCodeStream",
                params(code, language, timeout, environment, cwd, options)
        );
        return subscriber -> {
            Objects.requireNonNull(subscriber, "subscriber");
            rawPublisher.whenComplete((publisher, error) -> {
                if (error != null) {
                    subscriber.onSubscribe(new EmptySubscription());
                    subscriber.onError(rootCause(error));
                    return;
                }
                subscribeMapped(publisher, subscriber);
            });
        };
    }

    private static Map<String, Object> params(String code, CodeLanguage language, int timeout,
                                              Map<String, String> environment, String cwd,
                                              Map<String, Object> options) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("code", code);
        params.put("language", (language == null ? CodeLanguage.PYTHON : language).value());
        params.put("timeoutSeconds", timeout);
        params.put("environment", environment);
        params.put("cwd", cwd);
        params.put("options", options);
        return params;
    }

    private static ExecuteCodeResult toExecuteCodeResult(Object raw) {
        if (raw instanceof ExecuteCodeResult result) {
            return result;
        }
        return OBJECT_MAPPER.convertValue(raw, ExecuteCodeResult.class);
    }

    private static ExecuteCodeStreamResult toExecuteCodeStreamResult(Object raw) {
        if (raw instanceof ExecuteCodeStreamResult result) {
            return result;
        }
        return OBJECT_MAPPER.convertValue(raw, ExecuteCodeStreamResult.class);
    }

    @SuppressWarnings("unchecked")
    private static void subscribeMapped(Flow.Publisher<?> publisher,
                                        Flow.Subscriber<? super ExecuteCodeStreamResult> subscriber) {
        Flow.Publisher<Object> rawPublisher = (Flow.Publisher<Object>) publisher;
        rawPublisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscriber.onSubscribe(subscription);
            }

            @Override
            public void onNext(Object item) {
                subscriber.onNext(toExecuteCodeStreamResult(item));
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
