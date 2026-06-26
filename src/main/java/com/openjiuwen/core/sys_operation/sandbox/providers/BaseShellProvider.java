/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.providers;

import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.protocal.BaseShellProtocal;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mirrors Python's {@code BaseShellProvider} in
 * {@code openjiuwen/core/sys_operation/sandbox/providers/base_provider.py}.
 */
public abstract class BaseShellProvider extends BaseShellProtocal {

    private final SandboxEndpoint endpoint;

    private final SandboxGatewayConfig config;

    protected BaseShellProvider(SandboxEndpoint endpoint) {
        this(endpoint, null);
    }

    protected BaseShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
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
    public CompletableFuture<ExecuteCmdResult> executeCmd(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment,
            Map<String, Object> options) {
        return failedFuture("executeCmd");
    }

    @Override
    public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment,
            Map<String, Object> options) {
        return failedPublisher("executeCmdStream");
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
