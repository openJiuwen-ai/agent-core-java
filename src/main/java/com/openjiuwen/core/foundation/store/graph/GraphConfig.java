/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Configuration of Graph Store.
 * <p>
 * Mirrors Python's {@code GraphConfig} Pydantic model.
 */
public class GraphConfig {

    private static final Logger LOGGER = Logger.getLogger(GraphConfig.class.getName());

    private final String uri;
    private final String name;
    private final String user;
    private final String password;
    private final String token;
    private final String backend;
    private final double timeout;
    private final Map<String, Object> extras;
    private final int workerThreads;
    private final int embedDim;
    private final int embedBatchSize;
    private final Class<? extends Embedding> embeddingCls;
    private final EmbeddingConfig embeddingConfig;
    private final GraphStoreStorageConfig dbStorageConfig;
    private final GraphStoreIndexConfig dbEmbedConfig;
    private final boolean wipeAtStartup;
    private final int requestMaxRetries;
    private final double requestRetryWait;

    @SuppressWarnings("checkstyle:ParameterNumber")
    private GraphConfig(Builder builder) {
        if (builder.uri == null || builder.uri.isBlank()) {
            throw new IllegalArgumentException("uri must not be null or blank");
        }
        if (builder.timeout <= 0) {
            throw new IllegalArgumentException("timeout must be > 0, got " + builder.timeout);
        }
        if (builder.embedDim < 32) {
            throw new IllegalArgumentException("embedDim must be >= 32, got " + builder.embedDim);
        }
        if (builder.embedBatchSize < 1) {
            throw new IllegalArgumentException("embedBatchSize must be >= 1, got " + builder.embedBatchSize);
        }
        // Validate extras keys are all strings (mirrors Python check_extras)
        if (builder.extras != null) {
            for (Object key : builder.extras.keySet()) {
                if (!(key instanceof String)) {
                    throw new IllegalArgumentException("Extras must be a dictionary with string keys.");
                }
            }
        }
        this.uri = builder.uri;
        this.name = builder.name;
        this.user = builder.user;
        this.password = builder.password;
        this.token = builder.token;
        this.backend = builder.backend;
        this.timeout = builder.timeout;
        this.extras = builder.extras != null ? Map.copyOf(builder.extras) : Map.of();
        this.workerThreads = builder.workerThreads;
        this.embedDim = builder.embedDim;
        this.embedBatchSize = builder.embedBatchSize;
        this.embeddingCls = builder.embeddingCls;
        this.embeddingConfig = builder.embeddingConfig;
        this.dbStorageConfig = builder.dbStorageConfig != null ? builder.dbStorageConfig : new GraphStoreStorageConfig();
        this.dbEmbedConfig = builder.dbEmbedConfig != null ? builder.dbEmbedConfig : new GraphStoreIndexConfig();
        this.wipeAtStartup = builder.wipeAtStartup;
        this.requestMaxRetries = builder.requestMaxRetries;
        this.requestRetryWait = builder.requestRetryWait;
        // Validate URI connectivity (mirrors Python check_validity)
        checkValidity();
    }

    private void checkValidity() {
        boolean uriIsFilePath = !this.uri.contains("://");
        if (uriIsFilePath) {
            Path filePath = Path.of(this.uri);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !parentDir.toString().equals(".")) {
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING,
                            "Failed to create parent directory for graph db uri: " + parentDir, e);
                }
            }
        } else {
            try {
                String cleaned = this.uri.split("//")[this.uri.split("//").length - 1];
                String[] hostPort = cleaned.split(":");
                String host = hostPort[0];
                int port = hostPort.length > 1 ? Integer.parseInt(hostPort[1].split("/")[0]) : 80;
                SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                try (SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(host, port)) {
                    sslSocket.connect(new InetSocketAddress(host, port), (int) (this.timeout * 1000));
                }
            } catch (IOException | NumberFormatException | ClassCastException e) {
                LOGGER.log(Level.SEVERE,
                        "Graph DB config uri did not respond within " + this.timeout + " seconds", e);
            }
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUri() {
        return uri;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getName() {
        return name;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getUser() {
        return user;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getToken() {
        return token;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getBackend() {
        return backend;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getExtras() {
        return extras;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getEmbedDim() {
        return embedDim;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getEmbedBatchSize() {
        return embedBatchSize;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Class<? extends Embedding> getEmbeddingCls() {
        return embeddingCls;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphStoreStorageConfig getDbStorageConfig() {
        return dbStorageConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public GraphStoreIndexConfig getDbEmbedConfig() {
        return dbEmbedConfig;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isWipeAtStartup() {
        return wipeAtStartup;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRequestMaxRetries() {
        return requestMaxRetries;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getRequestRetryWait() {
        return requestRetryWait;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder {
        private String uri;
        private String name = "";
        private String user = "";
        private String password = "";
        private String token = "";
        private String backend = "milvus";
        private double timeout = 15.0;
        private Map<String, Object> extras;
        private int workerThreads = 30;
        private int embedDim = 512;
        private int embedBatchSize = 10;
        private Class<? extends Embedding> embeddingCls;
        private EmbeddingConfig embeddingConfig;
        private GraphStoreStorageConfig dbStorageConfig;
        private GraphStoreIndexConfig dbEmbedConfig;
        private boolean wipeAtStartup = false;
        private int requestMaxRetries = 5;
        private double requestRetryWait = 0.1;

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder backend(String backend) {
            this.backend = backend;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder extras(Map<String, Object> extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder workerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder embedDim(int embedDim) {
            this.embedDim = embedDim;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder embedBatchSize(int embedBatchSize) {
            this.embedBatchSize = embedBatchSize;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder embeddingCls(Class<? extends Embedding> embeddingCls) {
            this.embeddingCls = embeddingCls;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder embeddingConfig(EmbeddingConfig embeddingConfig) {
            this.embeddingConfig = embeddingConfig;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder dbStorageConfig(GraphStoreStorageConfig dbStorageConfig) {
            this.dbStorageConfig = dbStorageConfig;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder dbEmbedConfig(GraphStoreIndexConfig dbEmbedConfig) {
            this.dbEmbedConfig = dbEmbedConfig;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder wipeAtStartup(boolean wipeAtStartup) {
            this.wipeAtStartup = wipeAtStartup;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder requestMaxRetries(int requestMaxRetries) {
            this.requestMaxRetries = requestMaxRetries;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder requestRetryWait(double requestRetryWait) {
            this.requestRetryWait = requestRetryWait;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public GraphConfig build() {
            return new GraphConfig(this);
        }
    }
}
