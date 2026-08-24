/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verifies OpenAI-compatible client can issue more than OkHttp's default 5 concurrent
 * requests to the same host (needed for DeepAgent multi-session load).
 */
class OpenAiCompatibleModelClientConcurrencyTest {

    @BeforeEach
    void clearOpenAiInvoker() {
        Model.unregisterInvoker("OpenAI");
    }

    @AfterEach
    void restoreOpenAiInvoker() {
        Model.unregisterInvoker("OpenAI");
    }

    @Test
    void concurrentInvokesCanExceedDefaultOkHttpPerHostLimitOfFive() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        CountDownLatch enteredEight = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            int now = inFlight.incrementAndGet();
            peak.updateAndGet(prev -> Math.max(prev, now));
            enteredEight.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) {
                    writeJson(exchange, "{\"error\":\"release_timeout\"}");
                    return;
                }
                writeJson(exchange, "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                writeJson(exchange, "{\"error\":\"interrupted\"}");
            } finally {
                inFlight.decrementAndGet();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("sk-test")
                    .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").timeout(30).build();
            ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName("test-model").build();
            Model model = new Model(clientConfig, requestConfig);

            List<Future<AssistantMessage>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null,
                        null, null, null, null, null)));
            }

            boolean reachedEight = enteredEight.await(5, TimeUnit.SECONDS);
            release.countDown();

            for (Future<AssistantMessage> future : futures) {
                assertThat(future.get(20, TimeUnit.SECONDS).getContentAsString()).isEqualTo("ok");
            }

            assertThat(reachedEight).as("expected >=8 concurrent in-flight calls to same host; peak=" + peak.get())
                    .isTrue();
            assertThat(peak.get()).isGreaterThanOrEqualTo(8);
        } finally {
            release.countDown();
            pool.shutdownNow();
            server.stop(0);
        }
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
