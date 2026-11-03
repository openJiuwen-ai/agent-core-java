/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.rail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Mirrors Python's {@code TrajectoryUploader} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/uploader.py}.
 */
public class TrajectoryUploader {

    private static final Logger LOGGER = Logger.getLogger(TrajectoryUploader.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final String UPLOAD_PATH = "/v1/gateway/upload/batch";

    private final String gatewayEndpoint;
    private final int capacity;
    private final int maxRetries;
    private final double backoffBaseSec;
    private final Path walDir;
    private final String apiKey;
    private final boolean ownedPoster;
    private final BatchPoster poster;
    private final ArrayDeque<Map<String, Object>> queue = new ArrayDeque<>();
    private final Object lock = new Object();
    private final AtomicInteger queueDropTotal = new AtomicInteger();
    private final AtomicInteger http4xxTotal = new AtomicInteger();

    private volatile Thread worker;
    private volatile boolean closed;

    public TrajectoryUploader(String gatewayEndpoint) {
        this(gatewayEndpoint, 256, 5, 0.2d, Path.of("records", "rail_v1_wal"), "", null, Duration.ofSeconds(30));
    }

    public TrajectoryUploader(
            String gatewayEndpoint,
            int capacity,
            int maxRetries,
            double backoffBaseSec,
            Path walDir,
            String apiKey
    ) {
        this(gatewayEndpoint, capacity, maxRetries, backoffBaseSec, walDir, apiKey, null, Duration.ofSeconds(30));
    }

    public TrajectoryUploader(
            String gatewayEndpoint,
            int capacity,
            int maxRetries,
            double backoffBaseSec,
            Path walDir,
            String apiKey,
            HttpClient httpClient,
            Duration timeout
    ) {
        this(
                gatewayEndpoint,
                capacity,
                maxRetries,
                backoffBaseSec,
                walDir,
                apiKey,
                new HttpBatchPoster(httpClient != null ? httpClient : HttpClient.newHttpClient(),
                        timeout != null ? timeout : Duration.ofSeconds(30)),
                true
        );
    }

    TrajectoryUploader(
            String gatewayEndpoint,
            int capacity,
            int maxRetries,
            double backoffBaseSec,
            Path walDir,
            String apiKey,
            BatchPoster poster,
            boolean ownedPoster
    ) {
        this.gatewayEndpoint = stripTrailingSlashes(gatewayEndpoint != null ? gatewayEndpoint : "");
        this.capacity = Math.max(1, capacity);
        this.maxRetries = Math.max(0, maxRetries);
        this.backoffBaseSec = Math.max(0.0d, backoffBaseSec);
        this.walDir = walDir != null ? walDir : Path.of("records", "rail_v1_wal");
        this.apiKey = apiKey != null ? apiKey : "";
        this.poster = poster != null ? poster : new HttpBatchPoster(HttpClient.newHttpClient(), Duration.ofSeconds(30));
        this.ownedPoster = ownedPoster;
        try {
            Files.createDirectories(this.walDir);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create WAL directory: " + this.walDir, exception);
        }
    }

    public CompletableFuture<Void> enqueue(Object batch) {
        Map<String, Object> payload = toPayload(batch);
        synchronized (lock) {
            if (queue.size() >= capacity) {
                queue.removeFirst();
                queueDropTotal.incrementAndGet();
                LOGGER.warning("[TrajectoryUploader] queue full; dropped oldest batch");
            }
            queue.addLast(payload);
            ensureWorkerLocked();
            lock.notifyAll();
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> replayWal() {
        try (Stream<Path> paths = Files.list(walDir)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(this::replayWalFile);
        } catch (IOException exception) {
            LOGGER.log(Level.WARNING, "[TrajectoryUploader] WAL replay scan failed", exception);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<Void> shutdown() {
        Thread workerToJoin;
        synchronized (lock) {
            closed = true;
            lock.notifyAll();
            workerToJoin = worker;
        }
        if (workerToJoin != null) {
            try {
                workerToJoin.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
        ArrayDeque<Map<String, Object>> leftovers = new ArrayDeque<>();
        synchronized (lock) {
            while (!queue.isEmpty()) {
                leftovers.addLast(queue.removeFirst());
            }
        }
        while (!leftovers.isEmpty()) {
            writeWal(leftovers.removeFirst());
        }
        if (ownedPoster) {
            try {
                poster.close();
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "[TrajectoryUploader] failed to close owned client", exception);
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    public String getGatewayEndpoint() {
        return gatewayEndpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Path getWalDir() {
        return walDir;
    }

    public int getQueueDropTotal() {
        return queueDropTotal.get();
    }

    public int getHttp4xxTotal() {
        return http4xxTotal.get();
    }

    private void ensureWorkerLocked() {
        if (worker == null || !worker.isAlive()) {
            worker = new Thread(this::runWorker, "trajectory-uploader-worker");
            worker.setDaemon(true);
            worker.start();
        }
    }

    private void runWorker() {
        replayWal();
        while (true) {
            Map<String, Object> payload;
            synchronized (lock) {
                while (queue.isEmpty() && !closed) {
                    try {
                        lock.wait();
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                if (queue.isEmpty() && closed) {
                    return;
                }
                payload = queue.removeFirst();
            }
            sendOrWal(payload);
        }
    }

    private void replayWalFile(Path path) {
        try {
            Map<String, Object> payload = JSON.readValue(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            boolean ok = postWithRetries(payload);
            if (ok) {
                Files.deleteIfExists(path);
            }
        } catch (Exception exception) {
            LOGGER.log(Level.WARNING,
                    () -> "[TrajectoryUploader] WAL replay failed file=" + path + " err=" + exception.getMessage());
        }
    }

    private void sendOrWal(Map<String, Object> payload) {
        boolean ok = postWithRetries(payload);
        if (!ok) {
            writeWal(payload);
        }
    }

    private boolean postWithRetries(Map<String, Object> payload) {
        Exception lastException = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ResponseSnapshot response = poster.post(gatewayEndpoint + UPLOAD_PATH, payload, headers());
                int statusCode = response.statusCode();
                if (statusCode >= 400 && statusCode < 500) {
                    http4xxTotal.incrementAndGet();
                    LOGGER.warning("[TrajectoryUploader] drop 4xx batch status="
                            + statusCode + " body=" + abbreviate(response.body()));
                    return true;
                }
                if (statusCode >= 500) {
                    throw new IOException("HTTP " + statusCode + " " + abbreviate(response.body()));
                }
                return true;
            } catch (Exception exception) {
                lastException = exception;
                if (attempt >= maxRetries) {
                    break;
                }
                long sleepMillis = Math.max(0L, Math.round(backoffBaseSec * Math.pow(2.0d, attempt) * 1000.0d));
                if (sleepMillis > 0L) {
                    try {
                        Thread.sleep(sleepMillis);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        lastException = interruptedException;
                        break;
                    }
                }
            }
        }
        LOGGER.log(Level.WARNING, "[TrajectoryUploader] upload failed; writing WAL", lastException);
        return false;
    }

    private Map<String, String> headers() {
        if (apiKey.isEmpty()) {
            return Map.of();
        }
        return Map.of("Authorization", "Bearer " + apiKey);
    }

    private void writeWal(Map<String, Object> payload) {
        String name = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replace("-", "") + ".json";
        Path path = walDir.resolve(name);
        try {
            Files.writeString(path, JSON.writeValueAsString(payload), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist WAL payload to " + path, exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toPayload(Object batch) {
        if (batch instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            map.forEach((key, value) -> payload.put(String.valueOf(key), value));
            return payload;
        }
        if (batch == null) {
            return new LinkedHashMap<>();
        }
        try {
            Method method = batch.getClass().getDeclaredMethod("toDict");
            method.setAccessible(true);
            Object value = method.invoke(batch);
            if (value instanceof Map<?, ?> map) {
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                map.forEach((key, item) -> payload.put(String.valueOf(key), item));
                return payload;
            }
        } catch (NoSuchMethodException ignored) {
            // Fall through to Map conversion below.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("Failed to materialize batch payload", exception);
        }
        return JSON.convertValue(batch, MAP_TYPE);
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= 200) {
            return value;
        }
        return value.substring(0, 200);
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    interface BatchPoster extends AutoCloseable {
        ResponseSnapshot post(String url, Map<String, Object> payload, Map<String, String> headers) throws Exception;

        @Override
        default void close() throws Exception {
        }
    }

    record ResponseSnapshot(int statusCode, String body) {
    }

    private static final class HttpBatchPoster implements BatchPoster {

        private final HttpClient client;
        private final Duration timeout;

        private HttpBatchPoster(HttpClient client, Duration timeout) {
            this.client = client;
            this.timeout = timeout;
        }

        @Override
        public ResponseSnapshot post(String url, Map<String, Object> payload, Map<String, String> headers)
                throws IOException, InterruptedException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Content-Type", "application/json");
            headers.forEach(builder::header);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new ResponseSnapshot(response.statusCode(), response.body());
        }
    }
}
