package com.openjiuwen.agent_teams.external.cli_agent;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's injector coverage in
 * {@code tests/unit_tests/agent_teams/external/test_cli_agent.py}.
 */
class InjectorTest {

    @Test
    void stdinPipeInjectorWritesNewlineFramedUtf8() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StdinPipeInjector injector = new StdinPipeInjector(outputStream);

        injector.write("hello").toCompletableFuture().join();
        injector.aclose().toCompletableFuture().join();

        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("hello\n");
    }

    @Test
    void injectorProtocolShapeCheckAcceptsImplementation() {
        assertThat(Injector.isInjector(new StdinPipeInjector(new ByteArrayOutputStream()))).isTrue();
        assertThat(Injector.isInjector(new FakeInjector())).isTrue();
        assertThat(Injector.isInjector(new Object())).isFalse();
    }

    @Test
    void writeAfterCloseIsIgnoredAndCloseIsIdempotent() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        StdinPipeInjector injector = new StdinPipeInjector(outputStream);

        injector.write("first").toCompletableFuture().join();
        injector.aclose().toCompletableFuture().join();
        injector.write("second").toCompletableFuture().join();
        injector.aclose().toCompletableFuture().join();

        assertThat(outputStream.toString(StandardCharsets.UTF_8)).isEqualTo("first\n");
    }

    @Test
    void closeSuppressesStreamFailures() {
        StdinPipeInjector injector = new StdinPipeInjector(new CloseFailingOutputStream());

        injector.aclose().toCompletableFuture().join();
        injector.aclose().toCompletableFuture().join();
    }

    private static final class FakeInjector {
        public CompletionStage<Void> write(String text) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }

        public CompletionStage<Void> aclose() {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
    }

    private static final class CloseFailingOutputStream extends OutputStream {
        @Override
        public void write(int b) {
        }

        @Override
        public void close() throws IOException {
            throw new IOException("boom");
        }
    }
}
