/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.foundation.store.Embedding;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration of Graph Store.
 * <p>
 * Mirrors Python's {@code GraphConfig} in
 * {@code openjiuwen/core/foundation/store/graph/config.py}.
 */
public class GraphConfig {

    private static final LoggerProtocol STORE_LOGGER = Loggers.STORE;

    private final String uri;
    private final String name;
    private final String token;
    private final String backend;
    private final double timeout;
    private final Map<String, Object> extras;
    private final int maxConcurrent;
    private final int embedDim;
    private final int embedBatchSize;
    private final Embedding embeddingModel;
    private final GraphStoreStorageConfig dbStorageConfig;
    private final GraphStoreIndexConfig dbEmbedConfig;
    private final int requestMaxRetries;

    private GraphConfig(Builder builder) {
        if (builder.uri == null || builder.uri.isBlank()) {
            throw new IllegalArgumentException("uri must not be null or blank");
        }
        if (builder.timeout <= 0) {
            throw new IllegalArgumentException("timeout must be > 0, got " + builder.timeout);
        }
        if (builder.maxConcurrent < 0) {
            throw new IllegalArgumentException("maxConcurrent must be >= 0, got " + builder.maxConcurrent);
        }
        if (builder.embedDim < 32) {
            throw new IllegalArgumentException("embedDim must be >= 32, got " + builder.embedDim);
        }
        if (builder.embedBatchSize < 1) {
            throw new IllegalArgumentException("embedBatchSize must be >= 1, got " + builder.embedBatchSize);
        }
        validateExtras(builder.extras);

        this.uri = builder.uri;
        this.name = builder.name;
        this.token = builder.token;
        this.backend = builder.backend;
        this.timeout = builder.timeout;
        this.extras = builder.extras instanceof Map<?, ?> extrasMap
                ? copyStringObjectMap(extrasMap)
                : Map.of();
        this.maxConcurrent = builder.maxConcurrent;
        this.embedDim = builder.embedDim;
        this.embedBatchSize = builder.embedBatchSize;
        this.embeddingModel = builder.embeddingModel;
        this.dbStorageConfig = builder.dbStorageConfig != null ? builder.dbStorageConfig : new GraphStoreStorageConfig();
        this.dbEmbedConfig = builder.dbEmbedConfig != null ? builder.dbEmbedConfig : new GraphStoreIndexConfig();
        this.requestMaxRetries = builder.requestMaxRetries;
        checkValidity();
    }

    private static void validateExtras(Object extras) {
        if (extras == null || extras instanceof Map<?, ?> extrasMap
                && extrasMap.keySet().stream().allMatch(String.class::isInstance)) {
            return;
        }
        throw new IllegalArgumentException("Extras must be a dictionary with string keys.");
    }

    private void checkValidity() {
        boolean uriIsFilePath = !uri.contains("://");
        if (uriIsFilePath) {
            Path parentDir = Path.of(uri).getParent();
            if (parentDir != null && !parentDir.toString().strip().equals(".")) {
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException exception) {
                    STORE_LOGGER.warning("Failed to create parent directory for graph db uri: " + parentDir);
                }
            }
            return;
        }

        try {
            String cleanedUri = uri.split("//")[uri.split("//").length - 1];
            String[] hostPort = cleanedUri.split(":");
            if (hostPort.length < 2) {
                throw new IllegalArgumentException("Graph DB uri must include host and port.");
            }
            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(hostPort[0], Integer.parseInt(hostPort[1])),
                        (int) (timeout * 1000));
            }
        } catch (Exception exception) {
            STORE_LOGGER.error("Graph DB config uri did not respond within "
                    + timeout
                    + " seconds: "
                    + exception);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUri() {
        return uri;
    }

    public String getName() {
        return name;
    }

    public String getToken() {
        return token;
    }

    public String getBackend() {
        return backend;
    }

    public double getTimeout() {
        return timeout;
    }

    public Map<String, Object> getExtras() {
        return extras;
    }

    public boolean isWipeAtStartup() {
        return Boolean.TRUE.equals(extras.get("wipeAtStartup")) || Boolean.TRUE.equals(extras.get("wipe_at_startup"));
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public int getEmbedDim() {
        return embedDim;
    }

    public int getEmbedBatchSize() {
        return embedBatchSize;
    }

    public Embedding getEmbeddingModel() {
        return embeddingModel;
    }

    public GraphStoreStorageConfig getDbStorageConfig() {
        return dbStorageConfig;
    }

    public GraphStoreIndexConfig getDbEmbedConfig() {
        return dbEmbedConfig;
    }

    public int getRequestMaxRetries() {
        return requestMaxRetries;
    }

    public static final class Builder {

        private String uri;
        private String name = "";
        private String token = "";
        private String backend = "milvus";
        private double timeout = 15.0d;
        private Object extras;
        private int maxConcurrent = 10;
        private int embedDim = 512;
        private int embedBatchSize = 10;
        private Embedding embeddingModel;
        private GraphStoreStorageConfig dbStorageConfig;
        private GraphStoreIndexConfig dbEmbedConfig;
        private int requestMaxRetries = 5;

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder token(String token) {
            this.token = token;
            return this;
        }

        public Builder backend(String backend) {
            this.backend = backend;
            return this;
        }

        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder extras(Map<String, Object> extras) {
            this.extras = extras;
            return this;
        }

        public Builder extras(Object extras) {
            this.extras = extras;
            return this;
        }

        public Builder maxConcurrent(int maxConcurrent) {
            this.maxConcurrent = maxConcurrent;
            return this;
        }

        public Builder embedDim(int embedDim) {
            this.embedDim = embedDim;
            return this;
        }

        public Builder embedBatchSize(int embedBatchSize) {
            this.embedBatchSize = embedBatchSize;
            return this;
        }

        public Builder embeddingModel(Embedding embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder dbStorageConfig(GraphStoreStorageConfig dbStorageConfig) {
            this.dbStorageConfig = dbStorageConfig;
            return this;
        }

        public Builder dbEmbedConfig(GraphStoreIndexConfig dbEmbedConfig) {
            this.dbEmbedConfig = dbEmbedConfig;
            return this;
        }

        public Builder requestMaxRetries(int requestMaxRetries) {
            this.requestMaxRetries = requestMaxRetries;
            return this;
        }

        public Builder wipeAtStartup(boolean wipeAtStartup) {
            LinkedHashMap<String, Object> nextExtras = new LinkedHashMap<>();
            if (this.extras instanceof Map<?, ?> extrasMap) {
                for (Map.Entry<?, ?> entry : extrasMap.entrySet()) {
                    nextExtras.put((String) entry.getKey(), entry.getValue());
                }
            }
            nextExtras.put("wipeAtStartup", wipeAtStartup);
            nextExtras.put("wipe_at_startup", wipeAtStartup);
            this.extras = nextExtras;
            return this;
        }

        public GraphConfig build() {
            return new GraphConfig(this);
        }
    }

    private static Map<String, Object> copyStringObjectMap(Map<?, ?> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            copy.put((String) entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}
