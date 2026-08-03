/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.providers;

import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.protocal.BaseCodeProtocal;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors Python's {@code BaseCodeProvider} in
 * {@code openjiuwen/core/sys_operation/sandbox/providers/base_provider.py}.
 */
public abstract class BaseCodeProvider extends BaseCodeProtocal {

    private final SandboxEndpoint endpoint;

    private final SandboxGatewayConfig config;

    protected BaseCodeProvider(SandboxEndpoint endpoint) {
        this(endpoint, null);
    }

    protected BaseCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
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
    public CompletableFuture<ExecuteCodeResult> executeCode(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options) {
        return failedFuture("executeCode");
    }

    @Override
    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options) {
        return failedPublisher("executeCodeStream");
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
