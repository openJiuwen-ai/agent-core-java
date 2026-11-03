/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for {@link OpenAIModelClient} image and video generation endpoints against a local
 * HTTP fixture server.
 *
 * @since 2026-10-10
 */
@Tag("system-test")
class OpenAIModelClientGenerationTest {
    @Test
    void generateImageParsesUrlResponseAndBuildsOpenAiBody() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange -> {
            requestBody.set(readBody(exchange));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            writeJson(exchange, "{\"created\":1728000000,"
                    + "\"data\":[{\"url\":\"http://example.com/a.png\"}]}");
        });
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort());

            ImageGenerationResponse response = model.generateImage(
                    List.of(new UserMessage("a corgi on snow mountain")),
                    "seedream", "1024x1024", null, 1, true, false, 0, null)
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);

            assertEquals("seedream", response.getModel());
            assertEquals(List.of("http://example.com/a.png"), response.getImages());
            assertTrue(response.getImagesBase64() == null || response.getImagesBase64().isEmpty());
            assertEquals("Bearer sk-test", authorization.get());
            String body = requestBody.get();
            assertTrue(body.contains("\"model\":\"seedream\""), body);
            assertTrue(body.contains("\"prompt\":\"a corgi on snow mountain\""), body);
            assertTrue(body.contains("\"size\":\"1024x1024\""), body);
            assertTrue(body.contains("\"n\":1"), body);
            assertTrue(!body.contains("watermark") && !body.contains("prompt_extend"), body);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateImageParsesBase64Response() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange ->
                writeJson(exchange, "{\"data\":[{\"b64_json\":\"QUJD\"}]}"));
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort());

            ImageGenerationResponse response = model.generateImage(
                    List.of(new UserMessage("a corgi")), null, null, null, 1, true, false, 0, null)
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);

            assertEquals(List.of("QUJD"), response.getImagesBase64());
            assertTrue(response.getImages() == null || response.getImages().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateImageThrowsWhenResponseHasNoImages() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/images/generations", exchange ->
                writeJson(exchange, "{\"created\":1728000000}"));
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort());

            ExecutionException exception = assertThrows(ExecutionException.class, () -> model.generateImage(
                    List.of(new UserMessage("a corgi")), null, null, null, 1, true, false, 0, null)
                    .toCompletableFuture().get(30, TimeUnit.SECONDS));
            assertTrue(exception.getCause() instanceof BaseError, String.valueOf(exception.getCause()));
            assertTrue(exception.getCause().getMessage().contains("No usable image url"),
                    exception.getCause().getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateImageWithCustomPathAndUrlList() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/image_generation", exchange -> {
            requestBody.set(readBody(exchange));
            writeJson(exchange, "{\"id\":\"img-1\",\"data\":{\"image_urls\":"
                    + "[\"http://example.com/a.png\",\"http://example.com/b.png\"]}}");
        });
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort(), "");

            ImageGenerationResponse response = model.generateImage(
                    List.of(new UserMessage("a corgi")), "test-image-model", null, null, 2, true, false, 0,
                    Map.of("image_create_path", "/v1/image_generation",
                            "image_urls_path", "data.image_urls",
                            "aspect_ratio", "16:9",
                            "response_format", "url"))
                    .toCompletableFuture().get(30, TimeUnit.SECONDS);

            assertEquals(List.of("http://example.com/a.png", "http://example.com/b.png"),
                    response.getImages());
            String body = requestBody.get();
            assertTrue(body.contains("\"model\":\"test-image-model\""), body);
            assertTrue(body.contains("\"n\":2"), body);
            assertTrue(body.contains("\"aspect_ratio\":\"16:9\""), body);
            assertTrue(body.contains("\"response_format\":\"url\""), body);
            assertTrue(!body.contains("\"size\""), body);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateVideoTaskFlowWithUrlResult() throws Exception {
        AtomicInteger pollCount = new AtomicInteger();
        AtomicReference<String> createBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/contents/generation/tasks", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                createBody.set(readBody(exchange));
                writeJson(exchange, "{\"id\":\"cgt-1\",\"status\":\"queued\"}");
                return;
            }
            int call = pollCount.incrementAndGet();
            if (call == 1) {
                writeJson(exchange, "{\"id\":\"cgt-1\",\"status\":\"running\"}");
            } else {
                writeJson(exchange, "{\"id\":\"cgt-1\",\"status\":\"succeeded\","
                        + "\"content\":{\"video_url\":\"http://example.com/v.mp4\"}}");
            }
        });
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort());

            VideoGenerationResponse response = model.generateVideo(
                    List.of(new UserMessage("ocean waves")), null, null, "seedance", null, null,
                    5, true, false, null, null,
                    Map.of("video_body", "reference",
                            "video_create_path", "/contents/generation/tasks",
                            "video_result_url_path", "content.video_url",
                            "poll_interval_seconds", 1))
                    .toCompletableFuture().get(60, TimeUnit.SECONDS);

            assertEquals("http://example.com/v.mp4", response.getVideoUrl());
            assertEquals("seedance", response.getModel());
            String body = createBody.get();
            assertTrue(body.contains("\"model\":\"seedance\""), body);
            assertTrue(body.contains("\"type\":\"text\""), body);
            assertTrue(body.contains("\"duration\":5"), body);
            assertTrue(pollCount.get() >= 2, "poll should run at least twice");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateVideoStandardProtocolDownloadsBinaryContent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/videos", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/content")) {
                writeBytes(exchange, "VIDEO".getBytes(StandardCharsets.UTF_8));
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, "{\"id\":\"vid_1\",\"status\":\"queued\"}");
            } else {
                writeJson(exchange, "{\"id\":\"vid_1\",\"status\":\"completed\"}");
            }
        });
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort());

            VideoGenerationResponse response = model.generateVideo(
                    List.of(new UserMessage("ocean waves")), null, null, "sora", "1280x720", null,
                    4, true, false, null, null, Map.of())
                    .toCompletableFuture().get(60, TimeUnit.SECONDS);

            assertArrayEquals("VIDEO".getBytes(StandardCharsets.UTF_8), response.getVideoData());
            assertTrue(response.getVideoUrl() == null);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateVideoTaskFailsOnFailureStatus() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/contents/generation/tasks", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(exchange, "{\"id\":\"cgt-2\",\"status\":\"queued\"}");
                return;
            }
            writeJson(exchange, "{\"id\":\"cgt-2\",\"status\":\"failed\","
                    + "\"error\":{\"message\":\"content policy violation\"}}");
        });
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort());

            ExecutionException exception = assertThrows(ExecutionException.class, () -> model.generateVideo(
                    List.of(new UserMessage("ocean waves")), null, null, "seedance", null, null,
                    0, true, false, null, null,
                    Map.of("video_body", "reference",
                            "video_create_path", "/contents/generation/tasks",
                            "poll_interval_seconds", 1))
                    .toCompletableFuture().get(60, TimeUnit.SECONDS));
            assertTrue(exception.getCause() instanceof BaseError, String.valueOf(exception.getCause()));
            assertTrue(exception.getCause().getMessage().contains("failed"),
                    exception.getCause().getMessage());
            assertTrue(exception.getCause().getMessage().contains("content policy violation"),
                    exception.getCause().getMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateVideoFileIndirectTaskFlow() throws Exception {
        AtomicReference<String> createBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/video_generation", exchange -> {
            createBody.set(readBody(exchange));
            writeJson(exchange, "{\"task_id\":\"task-1\"}");
        });
        AtomicInteger pollCount = new AtomicInteger();
        server.createContext("/v1/query/video_generation", exchange -> {
            int call = pollCount.incrementAndGet();
            if (call == 1) {
                writeJson(exchange, "{\"task_id\":\"task-1\",\"status\":\"Processing\"}");
            } else {
                writeJson(exchange, "{\"task_id\":\"task-1\",\"status\":\"Success\","
                        + "\"file_id\":\"file-9\"}");
            }
        });
        server.createContext("/v1/files/retrieve", exchange ->
                writeJson(exchange, "{\"file\":{\"file_id\":\"file-9\","
                        + "\"download_url\":\"http://127.0.0.1:" + server.getAddress().getPort()
                        + "/download/task-video.mp4\"}}"));
        server.createContext("/download/task-video.mp4", exchange ->
                writeBytes(exchange, "VIDEODATA".getBytes(StandardCharsets.UTF_8)));
        server.start();
        try {
            Model model = createModel(server.getAddress().getPort(), "");

            VideoGenerationResponse response = model.generateVideo(
                    List.of(new UserMessage("space opera trailer")), null, null, "video-h3-model", null,
                    "768P", 4, true, false, null, null,
                    Map.of("video_body", "reference",
                            "video_create_path", "/v2/video_generation",
                            "video_poll_path", "/v1/query/video_generation?task_id={taskId}",
                            "video_file_retrieve_path", "/v1/files/retrieve?file_id={fileId}&purpose=video_generation",
                            "video_file_url_path", "file.download_url",
                            "poll_interval_seconds", 1))
                    .toCompletableFuture().get(60, TimeUnit.SECONDS);

            assertArrayEquals("VIDEODATA".getBytes(StandardCharsets.UTF_8), response.getVideoData());
            String body = createBody.get();
            assertTrue(body.contains("\"model\":\"video-h3-model\""), body);
            assertTrue(body.contains("\"resolution\":\"768P\""), body);
            assertTrue(body.contains("\"duration\":4"), body);
            assertTrue(body.contains("\"type\":\"text\""), body);
        } finally {
            server.stop(0);
        }
    }

    private static Model createModel(int port) {
        return createModel(port, "/v1");
    }

    private static Model createModel(int port, String apiBaseSuffix) {
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("sk-test")
                .apiBase("http://127.0.0.1:" + port + apiBaseSuffix)
                .timeout(30.0f)
                .maxRetries(1)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName("test-model")
                .build();
        return new Model(clientConfig, requestConfig);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void writeJson(HttpExchange exchange, String body) throws IOException {
        writeBytes(exchange, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(HttpExchange exchange, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
