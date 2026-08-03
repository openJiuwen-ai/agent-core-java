/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.sandbox.providers;

import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sys_operation.result.ReadFileStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseSandboxProviderTest {

    @Test
    void baseFsProviderPreservesConstructorStateAndFailsLikePythonDefault() {
        SandboxEndpoint endpoint = new SandboxEndpoint("http://sandbox", "sb-fs");
        SandboxGatewayConfig config = SandboxGatewayConfig.builder().timeoutSeconds(42).build();
        DemoFsProvider provider = new DemoFsProvider(endpoint, config);

        assertThat(provider.getEndpoint()).isEqualTo(endpoint);
        assertThat(provider.getConfig()).isEqualTo(config);

        CompletionException error = assertThrows(CompletionException.class, () -> provider.readFile("/tmp/data.txt").join());
        assertThat(error.getCause())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("DemoFsProvider.readFile is not implemented");

        CapturingSubscriber<ReadFileStreamResult> subscriber = new CapturingSubscriber<>();
        provider.readFileStream("/tmp/data.txt").subscribe(subscriber);
        assertThat(subscriber.error)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("DemoFsProvider.readFileStream is not implemented");
    }

    @Test
    void baseShellProviderPreservesConstructorStateAndFailsLikePythonDefault() {
        SandboxEndpoint endpoint = new SandboxEndpoint("http://sandbox", "sb-shell");
        DemoShellProvider provider = new DemoShellProvider(endpoint);

        assertThat(provider.getEndpoint()).isEqualTo(endpoint);
        assertThat(provider.getConfig()).isNull();

        CompletionException error = assertThrows(CompletionException.class, () -> provider.executeCmd("pwd").join());
        assertThat(error.getCause())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("DemoShellProvider.executeCmd is not implemented");

        CapturingSubscriber<ExecuteCmdStreamResult> subscriber = new CapturingSubscriber<>();
        provider.executeCmdStream("pwd").subscribe(subscriber);
        assertThat(subscriber.error)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("DemoShellProvider.executeCmdStream is not implemented");
    }

    @Test
    void baseCodeProviderPreservesConstructorStateAndFailsLikePythonDefault() {
        SandboxEndpoint endpoint = new SandboxEndpoint("http://sandbox", "sb-code");
        DemoCodeProvider provider = new DemoCodeProvider(endpoint);

        assertThat(provider.getEndpoint()).isEqualTo(endpoint);
        assertThat(provider.getConfig()).isNull();

        CompletionException error = assertThrows(CompletionException.class, () -> provider.executeCode("print(1)").join());
        assertThat(error.getCause())
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("DemoCodeProvider.executeCode is not implemented");

        CapturingSubscriber<ExecuteCodeStreamResult> subscriber = new CapturingSubscriber<>();
        provider.executeCodeStream("print(1)").subscribe(subscriber);
        assertThat(subscriber.error)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("DemoCodeProvider.executeCodeStream is not implemented");
    }

    private static final class DemoFsProvider extends BaseFsProvider {
        private DemoFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(endpoint, config);
        }
    }

    private static final class DemoShellProvider extends BaseShellProvider {
        private DemoShellProvider(SandboxEndpoint endpoint) {
            super(endpoint);
        }
    }

    private static final class DemoCodeProvider extends BaseCodeProvider {
        private DemoCodeProvider(SandboxEndpoint endpoint) {
            super(endpoint);
        }
    }

    private static final class CapturingSubscriber<T> implements Flow.Subscriber<T> {
        private Throwable error;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            // No-op: these providers fail before emitting values.
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onComplete() {
            // No-op: these providers fail instead of completing.
        }
    }
}
