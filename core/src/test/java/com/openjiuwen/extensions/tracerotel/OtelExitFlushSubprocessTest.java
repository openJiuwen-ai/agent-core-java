/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exit-flush regression test for {@link OtelTracerSetup}.
 *
 * <p>Runs {@link OtelExitFlushProbe} in a child JVM against a local OTLP/HTTP collector stub and
 * asserts that the span buffered in the {@code BatchSpanProcessor} reaches the collector before the
 * child exits. Without an exit-time flush the daemon export worker never runs, so the assertion
 * fails — this is the reproduction of "the last span is lost at process exit".</p>
 *
 * @since 0.1.17
 */
@DisplayName("tracerotel exit flush (subprocess)")
class OtelExitFlushSubprocessTest {
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(60);

    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    private static final long DELIVERY_POLL_MILLIS = 100L;

    private static final int COLLECTOR_STUB_BACKLOG = 8;

    @Test
    @DisplayName("buffered span is exported before the JVM exits")
    void testBufferedSpanExportedOnExit()
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        AtomicInteger exports = new AtomicInteger();
        HttpServer collector = HttpServer.create(new InetSocketAddress("127.0.0.1", 0),
                COLLECTOR_STUB_BACKLOG);
        collector.createContext("/v1/traces", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exports.incrementAndGet();
            // 返回合法 OTLP/HTTP 成功响应：空 protobuf 体 + 正确 Content-Type，避免导出器重试退避
            exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        collector.start();
        try {
            String endpoint = "http://127.0.0.1:" + collector.getAddress().getPort();
            Process probe = new ProcessBuilder(buildProbeCommand(endpoint)).redirectErrorStream(true)
                    .start();
            // 在独立线程上排空子进程输出，避免其写满管道缓冲后阻塞；读取仍受 PROBE_TIMEOUT 约束
            CompletableFuture<String> probeOutputFuture =
                    CompletableFuture.supplyAsync(() -> readProbeOutput(probe));
            boolean hasExited = probe.waitFor(PROBE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!hasExited) {
                probe.destroyForcibly();
            }
            String probeOutput = probeOutputFuture.get(PROBE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

            assertThat(hasExited).as("probe JVM must exit within %s; output=%s", PROBE_TIMEOUT, probeOutput)
                    .isTrue();
            assertThat(probe.exitValue()).as("probe exit code; output=%s", probeOutput).isZero();
            assertThat(probeOutput).as("probe must end a span before exiting").contains("PROBE_SPAN_ENDED");
            assertThat(awaitExports(exports))
                    .as("collector must receive the buffered span before exit; output=%s", probeOutput)
                    .isTrue();
        } finally {
            collector.stop(0);
        }
    }

    /** Read the merged stdout/stderr of the probe so the child never blocks on a full pipe. */
    private static String readProbeOutput(Process probe) {
        try (InputStream input = probe.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "probe output unavailable - " + e.getMessage();
        }
    }

    private static List<String> buildProbeCommand(String endpoint) {
        String javaBinary = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaBinary);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(OtelExitFlushProbe.class.getName());
        command.add(endpoint);
        return command;
    }

    private static boolean awaitExports(AtomicInteger exports) throws InterruptedException {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (exports.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(DELIVERY_POLL_MILLIS);
        }
        return exports.get() > 0;
    }
}
