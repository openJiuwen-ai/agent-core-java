/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.graph;

import com.openjiuwen.core.foundation.store.base_embedding.Embedding;
import com.openjiuwen.core.foundation.store.base_embedding.EmbeddingConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Configuration of Graph Store.
 * <p>
 * Mirrors Python's {@code GraphConfig} Pydantic model.
 * 
 * @since 0.1.7
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
    /**
     * GraphConfig.
     * 
     * @param builder builder
     * @since 0.1.7
     */
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
        this.dbStorageConfig =
            builder.dbStorageConfig != null ? builder.dbStorageConfig : new GraphStoreStorageConfig();
        this.dbEmbedConfig = builder.dbEmbedConfig != null ? builder.dbEmbedConfig : new GraphStoreIndexConfig();
        this.wipeAtStartup = builder.wipeAtStartup;
        this.requestMaxRetries = builder.requestMaxRetries;
        this.requestRetryWait = builder.requestRetryWait;
        // Validate URI connectivity (mirrors Python check_validity)
        checkValidity();
    }

    /**
     * checkValidity.
     * 
     * @since 0.1.7
     */
    private void checkValidity() {
        boolean uriIsFilePath = !this.uri.contains("://");
        if (uriIsFilePath) {
            Path filePath = Path.of(this.uri);
            Path parentDir = filePath.getParent();
            if (parentDir != null && !parentDir.toString().equals(".")) {
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to create parent directory for graph db uri: " + parentDir, e);
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
                LOGGER.log(Level.SEVERE, "Graph DB config uri did not respond within " + this.timeout + " seconds", e);
            }
        }
    }

    /**
     * getUri.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUri() {
        return uri;
    }

    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getName() {
        return name;
    }

    /**
     * getUser.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getUser() {
        return user;
    }

    /**
     * getPassword.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getPassword() {
        return password;
    }

    /**
     * getToken.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getToken() {
        return token;
    }

    /**
     * getBackend.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getBackend() {
        return backend;
    }

    /**
     * getTimeout.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * getExtras.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getExtras() {
        return extras;
    }

    /**
     * getWorkerThreads.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * getEmbedDim.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEmbedDim() {
        return embedDim;
    }

    /**
     * getEmbedBatchSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getEmbedBatchSize() {
        return embedBatchSize;
    }

    /**
     * getEmbeddingCls.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Class<? extends Embedding> getEmbeddingCls() {
        return embeddingCls;
    }

    /**
     * getEmbeddingConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public EmbeddingConfig getEmbeddingConfig() {
        return embeddingConfig;
    }

    /**
     * getDbStorageConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public GraphStoreStorageConfig getDbStorageConfig() {
        return dbStorageConfig;
    }

    /**
     * getDbEmbedConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public GraphStoreIndexConfig getDbEmbedConfig() {
        return dbEmbedConfig;
    }

    /**
     * isWipeAtStartup.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isWipeAtStartup() {
        return wipeAtStartup;
    }

    /**
     * getRequestMaxRetries.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRequestMaxRetries() {
        return requestMaxRetries;
    }

    /**
     * getRequestRetryWait.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getRequestRetryWait() {
        return requestRetryWait;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
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
         * uri.
         * 
         * @param uri uri
         * @return the result
         * @since 0.1.7
         */
        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * user.
         * 
         * @param user user
         * @return the result
         * @since 0.1.7
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * password.
         * 
         * @param password password
         * @return the result
         * @since 0.1.7
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * token.
         * 
         * @param token token
         * @return the result
         * @since 0.1.7
         */
        public Builder token(String token) {
            this.token = token;
            return this;
        }

        /**
         * backend.
         * 
         * @param backend backend
         * @return the result
         * @since 0.1.7
         */
        public Builder backend(String backend) {
            this.backend = backend;
            return this;
        }

        /**
         * timeout.
         * 
         * @param timeout timeout
         * @return the result
         * @since 0.1.7
         */
        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * extras.
         * 
         * @param extras extras
         * @return the result
         * @since 0.1.7
         */
        public Builder extras(Map<String, Object> extras) {
            this.extras = extras;
            return this;
        }

        /**
         * workerThreads.
         * 
         * @param workerThreads workerThreads
         * @return the result
         * @since 0.1.7
         */
        public Builder workerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
            return this;
        }

        /**
         * embedDim.
         * 
         * @param embedDim embedDim
         * @return the result
         * @since 0.1.7
         */
        public Builder embedDim(int embedDim) {
            this.embedDim = embedDim;
            return this;
        }

        /**
         * embedBatchSize.
         * 
         * @param embedBatchSize embedBatchSize
         * @return the result
         * @since 0.1.7
         */
        public Builder embedBatchSize(int embedBatchSize) {
            this.embedBatchSize = embedBatchSize;
            return this;
        }

        /**
         * embeddingCls.
         * 
         * @param embeddingCls embeddingCls
         * @return the result
         * @since 0.1.7
         */
        public Builder embeddingCls(Class<? extends Embedding> embeddingCls) {
            this.embeddingCls = embeddingCls;
            return this;
        }

        /**
         * embeddingConfig.
         * 
         * @param embeddingConfig embeddingConfig
         * @return the result
         * @since 0.1.7
         */
        public Builder embeddingConfig(EmbeddingConfig embeddingConfig) {
            this.embeddingConfig = embeddingConfig;
            return this;
        }

        /**
         * dbStorageConfig.
         * 
         * @param dbStorageConfig dbStorageConfig
         * @return the result
         * @since 0.1.7
         */
        public Builder dbStorageConfig(GraphStoreStorageConfig dbStorageConfig) {
            this.dbStorageConfig = dbStorageConfig;
            return this;
        }

        /**
         * dbEmbedConfig.
         * 
         * @param dbEmbedConfig dbEmbedConfig
         * @return the result
         * @since 0.1.7
         */
        public Builder dbEmbedConfig(GraphStoreIndexConfig dbEmbedConfig) {
            this.dbEmbedConfig = dbEmbedConfig;
            return this;
        }

        /**
         * wipeAtStartup.
         * 
         * @param wipeAtStartup wipeAtStartup
         * @return the result
         * @since 0.1.7
         */
        public Builder wipeAtStartup(boolean wipeAtStartup) {
            this.wipeAtStartup = wipeAtStartup;
            return this;
        }

        /**
         * requestMaxRetries.
         * 
         * @param requestMaxRetries requestMaxRetries
         * @return the result
         * @since 0.1.7
         */
        public Builder requestMaxRetries(int requestMaxRetries) {
            this.requestMaxRetries = requestMaxRetries;
            return this;
        }

        /**
         * requestRetryWait.
         * 
         * @param requestRetryWait requestRetryWait
         * @return the result
         * @since 0.1.7
         */
        public Builder requestRetryWait(double requestRetryWait) {
            this.requestRetryWait = requestRetryWait;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public GraphConfig build() {
            return new GraphConfig(this);
        }
    }
}
