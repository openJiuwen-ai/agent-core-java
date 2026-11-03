package com.openjiuwen.core.sysop.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.common.logging.LoggingUtils;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;
import com.openjiuwen.extensions.tracerotel.SemConv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;

class SandboxGatewayClientMixinTest {
    private static final AttributeKey<String> SANDBOX_ERROR_MESSAGE =
            AttributeKey.stringKey(SemConv.OJ_SANDBOX_ERROR_MESSAGE);

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

    @Test
    void invokeRecordsFailureOnSpanThatStartedBeforeSandboxCall() {
        TestMixin mixin = configuredMixin();
        CompletableFuture<Object> gatewayFuture = new CompletableFuture<>();
        mixin.fakeClient.invokeFuture = gatewayFuture;
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Span span = tracerProvider.get("sandbox-test").spanBuilder("invoke").startSpan();

        try {
            CompletableFuture<Object> result;
            try (Scope scope = span.makeCurrent()) {
                result = mixin.invoke("run", Map.of());
            }
            gatewayFuture.completeExceptionally(
                    new CompletionException("sandbox invoke failed", new IllegalStateException("cause")));
            assertThrows(CompletionException.class, result::join);
            span.end();

            assertEquals("sandbox invoke failed", exporter.getFinishedSpanItems().get(0)
                    .getAttributes().get(SANDBOX_ERROR_MESSAGE));
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void invokeStreamRecordsFailureOnSpanThatStartedBeforeSandboxCall() {
        TestMixin mixin = configuredMixin();
        CompletableFuture<Flow.Publisher<?>> gatewayFuture = new CompletableFuture<>();
        mixin.fakeClient.invokeStreamFuture = gatewayFuture;
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        Span span = tracerProvider.get("sandbox-test").spanBuilder("invokeStream").startSpan();

        try {
            CompletableFuture<Flow.Publisher<?>> result;
            try (Scope scope = span.makeCurrent()) {
                result = mixin.invokeStream("run", Map.of());
            }
            gatewayFuture.completeExceptionally(
                    new CompletionException("sandbox stream failed", new IllegalStateException("cause")));
            assertThrows(CompletionException.class, result::join);
            span.end();

            assertEquals("sandbox stream failed", exporter.getFinishedSpanItems().get(0)
                    .getAttributes().get(SANDBOX_ERROR_MESSAGE));
        } finally {
            tracerProvider.close();
        }
    }

    private static TestMixin configuredMixin() {
        TestMixin mixin = new TestMixin();
        mixin.initSandboxContext(
            SandboxRunConfig.builder()
                .config(new SandboxGatewayConfig())
                .isolationKeyTemplate("fixed-key")
                .build(),
            "shell"
        );
        return mixin;
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
        private CompletableFuture<Object> invokeFuture;
        private CompletableFuture<Flow.Publisher<?>> invokeStreamFuture;

        private FakeSandboxGatewayClient() {
            super(new SandboxGatewayConfig(), "unused");
        }

        @Override
        public CompletableFuture<Object> invoke(String opType, String method, Map<String, Object> params) {
            this.lastOpType = opType;
            this.lastMethod = method;
            this.lastParams = params;
            return invokeFuture == null ? CompletableFuture.completedFuture(result) : invokeFuture;
        }

        @Override
        public CompletableFuture<Flow.Publisher<?>> invokeStream(String opType, String method, Map<String, Object> params) {
            this.lastOpType = opType;
            this.lastMethod = method;
            this.lastParams = params;
            return invokeStreamFuture == null
                    ? CompletableFuture.completedFuture(publisher)
                    : invokeStreamFuture;
        }
    }
}
