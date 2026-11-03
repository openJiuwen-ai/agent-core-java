/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration of the Context Offload v2 pipeline (架构文档 §3.4).
 *
 * <p>Snake-case {@code @JsonProperty} names for JSON configs; unknown keys
 * are ignored so older configs keep parsing. Defaults mirror the design
 * document: FILESYSTEM → REDIS → MEMORY chain, 24h TTL, 30min Janitor
 * interval, 512MB / 2000 entries per-session quota.</p>
 *
 * @since 2026-01-01
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OffloadConfig {
    @JsonProperty("primary_backend")
    private OffloadBackend primaryBackend = OffloadBackend.FILESYSTEM;

    @JsonProperty("fallback_backend")
    private OffloadBackend fallbackBackend = OffloadBackend.REDIS;

    @JsonProperty("final_fallback")
    private OffloadBackend finalFallback = OffloadBackend.MEMORY;

    @JsonProperty("ttl_seconds")
    private long ttlSeconds = 86400L;

    @JsonProperty("janitor_interval_seconds")
    private long janitorIntervalSeconds = 1800L;

    @JsonProperty("max_bytes_per_session")
    private long maxBytesPerSession = 536870912L;

    @JsonProperty("max_entries_per_session")
    private int maxEntriesPerSession = 2000;

    @JsonProperty("recent_message_protection_count")
    private int recentMessageProtectionCount = 5;

    @JsonProperty("protected_tool_names")
    private List<String> protectedToolNames = List.of("reload_original_context_messages");

    @JsonProperty("redis")
    private OffloadRedisConfig redis;

    @JsonProperty("workspace_root")
    private String workspaceRoot;

    /** T19 (P2): emit OffloadMetric through OpenTelemetry in addition to Slf4j. Default off. */
    @JsonProperty("enable_otel")
    private boolean isOtelEnabled = false;

    /** T21 (P2/C6): TTL applied to orphan-suspect entries (seconds). 0 disables orphan shortening. */
    @JsonProperty("orphan_ttl_seconds")
    private long orphanTtlSeconds = 3600L;

    /** T20 (P2): acquire a sidecar file lock during FileOffloadStore publish for
     *  shared-FS multi-instance deployments. Default off. */
    @JsonProperty("enable_file_lock")
    private boolean isFileLockEnabled = false;

    public OffloadConfig() {
    }

    /**
     * Returns the primary backend.
     *
     * @return the primary backend
     */
    public OffloadBackend getPrimaryBackend() {
        return primaryBackend;
    }

    public void setPrimaryBackend(OffloadBackend primaryBackend) {
        this.primaryBackend = primaryBackend == null ? OffloadBackend.FILESYSTEM : primaryBackend;
    }

    /**
     * Returns the fallback backend.
     *
     * @return the fallback backend
     */
    public OffloadBackend getFallbackBackend() {
        return fallbackBackend;
    }

    public void setFallbackBackend(OffloadBackend fallbackBackend) {
        this.fallbackBackend = fallbackBackend == null ? OffloadBackend.REDIS : fallbackBackend;
    }

    /**
     * Returns the final fallback backend.
     *
     * @return the final fallback backend
     */
    public OffloadBackend getFinalFallback() {
        return finalFallback;
    }

    public void setFinalFallback(OffloadBackend finalFallback) {
        this.finalFallback = finalFallback == null ? OffloadBackend.MEMORY : finalFallback;
    }

    /**
     * Returns the TTL in seconds.
     *
     * @return the TTL in seconds
     */
    public long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Returns the janitor interval in seconds.
     *
     * @return the janitor interval in seconds
     */
    public long getJanitorIntervalSeconds() {
        return janitorIntervalSeconds;
    }

    public void setJanitorIntervalSeconds(long janitorIntervalSeconds) {
        this.janitorIntervalSeconds = janitorIntervalSeconds;
    }

    /**
     * Returns the max bytes per session.
     *
     * @return the max bytes per session
     */
    public long getMaxBytesPerSession() {
        return maxBytesPerSession;
    }

    public void setMaxBytesPerSession(long maxBytesPerSession) {
        this.maxBytesPerSession = maxBytesPerSession;
    }

    /**
     * Returns the max entries per session.
     *
     * @return the max entries per session
     */
    public int getMaxEntriesPerSession() {
        return maxEntriesPerSession;
    }

    public void setMaxEntriesPerSession(int maxEntriesPerSession) {
        this.maxEntriesPerSession = maxEntriesPerSession;
    }

    /**
     * Returns the recent message protection count.
     *
     * @return the recent message protection count
     */
    public int getRecentMessageProtectionCount() {
        return recentMessageProtectionCount;
    }

    public void setRecentMessageProtectionCount(int recentMessageProtectionCount) {
        this.recentMessageProtectionCount = recentMessageProtectionCount;
    }

    /**
     * Returns the protected tool names.
     *
     * @return the protected tool names
     */
    public List<String> getProtectedToolNames() {
        return new ArrayList<>(protectedToolNames);
    }

    public void setProtectedToolNames(List<String> protectedToolNames) {
        this.protectedToolNames = protectedToolNames == null
                ? List.of("reload_original_context_messages")
                : new ArrayList<>(protectedToolNames);
    }

    /**
     * Returns the Redis offload config.
     *
     * @return the Redis offload config
     */
    public OffloadRedisConfig getRedis() {
        return redis;
    }

    public void setRedis(OffloadRedisConfig redis) {
        this.redis = redis;
    }

    /**
     * Returns the workspace root.
     *
     * @return the workspace root
     */
    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    /**
     * Returns whether OpenTelemetry metric emission is enabled.
     *
     * @return whether OpenTelemetry metric emission is enabled
     */
    public boolean isEnableOtel() {
        return isOtelEnabled;
    }

    /**
     * Sets whether OpenTelemetry metric emission is enabled.
     *
     * @param isOtelEnabled whether to enable OpenTelemetry metric emission
     */
    public void setEnableOtel(boolean isOtelEnabled) {
        this.isOtelEnabled = isOtelEnabled;
    }

    /**
     * Returns the orphan TTL in seconds.
     *
     * @return the orphan TTL in seconds
     */
    public long getOrphanTtlSeconds() {
        return orphanTtlSeconds;
    }

    public void setOrphanTtlSeconds(long orphanTtlSeconds) {
        this.orphanTtlSeconds = Math.max(0L, orphanTtlSeconds);
    }

    /**
     * Returns whether sidecar file lock is enabled.
     *
     * @return whether sidecar file lock is enabled
     */
    public boolean isEnableFileLock() {
        return isFileLockEnabled;
    }

    /**
     * Sets whether sidecar file lock is enabled.
     *
     * @param isFileLockEnabled whether to enable the sidecar file lock
     */
    public void setEnableFileLock(boolean isFileLockEnabled) {
        this.isFileLockEnabled = isFileLockEnabled;
    }

    /**
     * Creates a fluent builder mirroring the handwritten style of
     * {@code ContextEngineConfig}.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Handwritten builder for programmatic construction. */
    public static final class Builder {
        private final OffloadConfig config = new OffloadConfig();

        private Builder() {
        }

        /**
         * Sets the primary backend.
         *
         * @param primaryBackend the the primary backend
         * @return this builder for chaining
         */
        public Builder primaryBackend(OffloadBackend primaryBackend) {
            config.setPrimaryBackend(primaryBackend);
            return this;
        }

        /**
         * Sets the fallback backend.
         *
         * @param fallbackBackend the the fallback backend
         * @return this builder for chaining
         */
        public Builder fallbackBackend(OffloadBackend fallbackBackend) {
            config.setFallbackBackend(fallbackBackend);
            return this;
        }

        /**
         * Sets the final fallback backend.
         *
         * @param finalFallback the the final fallback backend
         * @return this builder for chaining
         */
        public Builder finalFallback(OffloadBackend finalFallback) {
            config.setFinalFallback(finalFallback);
            return this;
        }

        /**
         * Sets the TTL in seconds.
         *
         * @param ttlSeconds the the ttl in seconds
         * @return this builder for chaining
         */
        public Builder ttlSeconds(long ttlSeconds) {
            config.setTtlSeconds(ttlSeconds);
            return this;
        }

        /**
         * Sets the janitor interval in seconds.
         *
         * @param janitorIntervalSeconds the the janitor interval in seconds
         * @return this builder for chaining
         */
        public Builder janitorIntervalSeconds(long janitorIntervalSeconds) {
            config.setJanitorIntervalSeconds(janitorIntervalSeconds);
            return this;
        }

        /**
         * Sets the max bytes per session.
         *
         * @param maxBytesPerSession the the max bytes per session
         * @return this builder for chaining
         */
        public Builder maxBytesPerSession(long maxBytesPerSession) {
            config.setMaxBytesPerSession(maxBytesPerSession);
            return this;
        }

        /**
         * Sets the max entries per session.
         *
         * @param maxEntriesPerSession the the max entries per session
         * @return this builder for chaining
         */
        public Builder maxEntriesPerSession(int maxEntriesPerSession) {
            config.setMaxEntriesPerSession(maxEntriesPerSession);
            return this;
        }

        /**
         * Sets the recent message protection count.
         *
         * @param recentMessageProtectionCount the the recent message protection count
         * @return this builder for chaining
         */
        public Builder recentMessageProtectionCount(int recentMessageProtectionCount) {
            config.setRecentMessageProtectionCount(recentMessageProtectionCount);
            return this;
        }

        /**
         * Sets the protected tool names.
         *
         * @param protectedToolNames the the protected tool names
         * @return this builder for chaining
         */
        public Builder protectedToolNames(List<String> protectedToolNames) {
            config.setProtectedToolNames(protectedToolNames);
            return this;
        }

        /**
         * Sets the Redis offload config.
         *
         * @param redis the the redis offload config
         * @return this builder for chaining
         */
        public Builder redis(OffloadRedisConfig redis) {
            config.setRedis(redis);
            return this;
        }

        /**
         * Sets the workspace root.
         *
         * @param workspaceRoot the the workspace root
         * @return this builder for chaining
         */
        public Builder workspaceRoot(String workspaceRoot) {
            config.setWorkspaceRoot(workspaceRoot);
            return this;
        }

        /**
         * Sets whether OpenTelemetry metric emission is enabled.
         *
         * @param isOtelEnabled whether to enable OpenTelemetry metric emission
         * @return this builder for chaining
         */
        public Builder enableOtel(boolean isOtelEnabled) {
            config.setEnableOtel(isOtelEnabled);
            return this;
        }

        /**
         * Sets the orphan TTL in seconds.
         *
         * @param orphanTtlSeconds the the orphan ttl in seconds
         * @return this builder for chaining
         */
        public Builder orphanTtlSeconds(long orphanTtlSeconds) {
            config.setOrphanTtlSeconds(orphanTtlSeconds);
            return this;
        }

        /**
         * Sets whether sidecar file lock is enabled.
         *
         * @param isFileLockEnabled whether to enable the sidecar file lock
         * @return this builder for chaining
         */
        public Builder enableFileLock(boolean isFileLockEnabled) {
            config.setEnableFileLock(isFileLockEnabled);
            return this;
        }

        /**
         * Builds and returns the configured OffloadConfig.
         *
         * @return the fully configured OffloadConfig instance
         */
        public OffloadConfig build() {
            return config;
        }
    }
}
