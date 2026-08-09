package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SandboxGatewayClientMixinTest {

    @Test
    void resolveIsolationKeyTemplateUsesCurrentSessionId() {
        LoggingUtils.setSessionId("session-42");

        assertEquals(
            "tenant-session-42",
            SandboxGatewayClientMixin.resolveIsolationKeyTemplate("tenant-{session_id}")
        );
        assertEquals("fixed-key", SandboxGatewayClientMixin.resolveIsolationKeyTemplate("fixed-key"));
    }

    @Test
    void resolveIsolationKeyTemplateFallsBackWhenSessionIdBlank() {
        LoggingUtils.setSessionId("");

        assertEquals(
            "tenant-default_session",
            SandboxGatewayClientMixin.resolveIsolationKeyTemplate("tenant-{session_id}")
        );
    }

    @Test
    void invokeDelegatesToGatewayClientWithResolvedIsolationKey() {
        LoggingUtils.setSessionId("sandbox-session");
        TestMixin mixin = new TestMixin();
        mixin.initSandboxContext(
            SandboxRunConfig.builder()
                .config(new SandboxGatewayConfig())
                .isolationKeyTemplate("tenant-{session_id}")
                .build(),
            "shell"
        );

        Object result = mixin.invoke("ping", Map.of("cwd", "/tmp")).join();

        assertEquals("tenant-sandbox-session", mixin.lastIsolationKey);
        assertEquals("shell", mixin.fakeClient.lastOpType);
        assertEquals("ping", mixin.fakeClient.lastMethod);
        assertEquals(Map.of("cwd", "/tmp"), mixin.fakeClient.lastParams);
        assertSame(mixin.fakeClient.result, result);
    }

    @Test
    void invokeStreamDelegatesToGatewayClientWithResolvedIsolationKey() {
        LoggingUtils.setSessionId("stream-session");
        TestMixin mixin = new TestMixin();
        mixin.initSandboxContext(
            SandboxRunConfig.builder()
                .config(new SandboxGatewayConfig())
                .isolationKeyTemplate("tenant-{session_id}")
                .build(),
            "shell"
        );

        Flow.Publisher<?> publisher = mixin.invokeStream("tail", Map.of("path", "/tmp/app.log")).join();

        assertEquals("tenant-stream-session", mixin.lastIsolationKey);
        assertEquals("shell", mixin.fakeClient.lastOpType);
        assertEquals("tail", mixin.fakeClient.lastMethod);
        assertEquals(Map.of("path", "/tmp/app.log"), mixin.fakeClient.lastParams);
        assertSame(mixin.fakeClient.publisher, publisher);
    }

    private static final class TestMixin extends BaseSandboxMixin {
        private final FakeSandboxGatewayClient fakeClient = new FakeSandboxGatewayClient();
        private String lastIsolationKey;

        @Override
        protected SandboxGatewayClient createGatewayClient(String isolationKey) {
            this.lastIsolationKey = isolationKey;
            return fakeClient;
        }
    }

    private static final class FakeSandboxGatewayClient extends SandboxGatewayClient {
        private final Object result = Map.of("ok", true);
        private final Flow.Publisher<?> publisher = subscriber -> { };
        private String lastOpType;
        private String lastMethod;
        private Map<String, Object> lastParams = Map.of();

        private FakeSandboxGatewayClient() {
            super(new SandboxGatewayConfig(), "unused");
        }

        @Override
        public CompletableFuture<Object> invoke(String opType, String method, Map<String, Object> params) {
            this.lastOpType = opType;
            this.lastMethod = method;
            this.lastParams = params;
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public CompletableFuture<Flow.Publisher<?>> invokeStream(String opType, String method, Map<String, Object> params) {
            this.lastOpType = opType;
            this.lastMethod = method;
            this.lastParams = params;
            return CompletableFuture.completedFuture(publisher);
        }
    }
}
