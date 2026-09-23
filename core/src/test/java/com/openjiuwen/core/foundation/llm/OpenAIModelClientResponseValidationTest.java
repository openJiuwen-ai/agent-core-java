/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ValidationError;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Response-shape validation for OpenAI-compatible chat completions.
 */
class OpenAIModelClientResponseValidationTest {

    @Test
    void invokeWhenContentIsArrayThrowsTypeError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> writeJson(exchange,
                "{\"choices\":[{\"index\":0,\"message\":{\"content\":[\"a\",1,null]},\"finish_reason\":\"stop\"}]}"));
        server.start();

        try {
            Model model = newModel(server);
            BaseError error = assertThrows(BaseError.class,
                    () -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null,
                            null, null));
            ValidationError validation = findCause(error, ValidationError.class);
            assertNotNull(validation);
            assertEquals(StatusCode.MODEL_RESPONSE_TYPE_ERROR, validation.getStatus());
            assertTrue(validation.getMessage().contains("must be a string"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invokeWhenContentIsNestedObjectThrowsTypeError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> writeJson(exchange,
                "{\"choices\":[{\"message\":{\"content\":{\"text\":\"hi\"}},\"finish_reason\":\"stop\"}]}"));
        server.start();

        try {
            Model model = newModel(server);
            BaseError error = assertThrows(BaseError.class,
                    () -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null,
                            null, null));
            ValidationError validation = findCause(error, ValidationError.class);
            assertNotNull(validation);
            assertEquals(StatusCode.MODEL_RESPONSE_TYPE_ERROR, validation.getStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invokeWhenMessageMissingThrowsResponseInvalid() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions",
                exchange -> writeJson(exchange, "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\"}]}"));
        server.start();

        try {
            Model model = newModel(server);
            BaseError error = assertThrows(BaseError.class,
                    () -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null,
                            null, null));
            ValidationError validation = findCause(error, ValidationError.class);
            assertNotNull(validation);
            assertEquals(StatusCode.MODEL_RESPONSE_INVALID, validation.getStatus());
            assertTrue(validation.getMessage().contains("No message"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invokeWhenChoicesEmptyThrowsResponseInvalid() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> writeJson(exchange, "{}"));
        server.start();

        try {
            Model model = newModel(server);
            BaseError error = assertThrows(BaseError.class,
                    () -> model.invoke(List.of(new UserMessage("hello")), null, null, null, null, null, null, null,
                            null, null));
            ValidationError validation = findCause(error, ValidationError.class);
            assertNotNull(validation);
            assertEquals(StatusCode.MODEL_RESPONSE_INVALID, validation.getStatus());
            assertTrue(validation.getMessage().contains("No choices"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void streamWhenDeltaContentIsArrayThrowsTypeError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> writeSse(exchange,
                "data: {\"choices\":[{\"delta\":{\"content\":[\"a\",1]},\"finish_reason\":null}]}\n\n"
                        + "data: [DONE]\n\n"));
        server.start();

        try {
            Model model = newModel(server);
            Iterator<AssistantMessageChunk> stream = model.stream(List.<BaseMessage>of(new UserMessage("hello")));
            BaseError error = assertThrows(BaseError.class, stream::hasNext);
            ValidationError validation = findCause(error, ValidationError.class);
            assertNotNull(validation);
            assertEquals(StatusCode.MODEL_RESPONSE_TYPE_ERROR, validation.getStatus());
        } finally {
            server.stop(0);
        }
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static Model newModel(HttpServer server) {
        ModelClientConfig clientConfig = ModelClientConfig.builder().clientProvider("OpenAI").apiKey("sk-test")
                .apiBase("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").timeout(5).build();
        return new Model(clientConfig, ModelRequestConfig.builder().modelName("test-model").build());
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void writeSse(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
