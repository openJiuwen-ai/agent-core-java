/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.model;

import com.openjiuwen.core.foundation.llm.schema.BaseMessage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Offload save request (架构文档 §3.1, handwritten builder following
 * {@code ContextEngineConfig} style — no Lombok in this package).
 *
 * @since 2026-01-01
 */
public final class OffloadRequest {
    private final OffloadNamespace namespace;
    private final String algorithmId;
    private final String source;
    private final List<BaseMessage> messages;
    private final long preTokenCount;
    private final long postTokenCount;
    private final Duration ttl;

    private OffloadRequest(Builder builder) {
        this.namespace = builder.namespace;
        this.algorithmId = builder.algorithmId;
        this.source = builder.source;
        this.messages = Collections.unmodifiableList(new ArrayList<>(builder.messages));
        this.preTokenCount = builder.preTokenCount;
        this.postTokenCount = builder.postTokenCount;
        this.ttl = builder.ttl;
    }

    /**
     * Returns a new builder for constructing an {@link OffloadRequest}.
     *
     * @return a new {@link Builder} instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the namespace.
     *
     * @return the namespace
     */
    public OffloadNamespace getNamespace() {
        return namespace;
    }

    /**
     * Processor type of the triggering policy, e.g. {@code MessageOffloader}.
     *
     * @return the algorithm id identifying the triggering processor
     */
    public String getAlgorithmId() {
        return algorithmId;
    }

    /**
     * Trigger description, typically the tool call id.
     *
     * @return the trigger source description
     */
    public String getSource() {
        return source;
    }

    /**
     * Original messages to offload; defensively copied, never mutated.
     *
     * @return an unmodifiable copy of the messages to offload
     */
    public List<BaseMessage> getMessages() {
        return messages;
    }

    /**
     * Returns the pre-offload token count.
     *
     * @return the pre-offload token count
     */
    public long getPreTokenCount() {
        return preTokenCount;
    }

    /**
     * Token count of the context-visible replacement (preview + marker) that
     * the triggering processor computed for this entry; {@code 0} when the
     * caller does not supply one (AC-6-04 postTokenCount).
     *
     * @return the post-offload token count, or {@code 0} if not supplied
     */
    public long getPostTokenCount() {
        return postTokenCount;
    }

    /**
     * Overrides {@code OffloadConfig.ttlSeconds} for this entry; may be {@code null}.
     *
     * @return the TTL override, or {@code null} to use the config default
     */
    public Duration getTtl() {
        return ttl;
    }

    /** Fluent builder with defensive copies. */
    public static final class Builder {
        private OffloadNamespace namespace;
        private String algorithmId = "";
        private String source = "";
        private List<BaseMessage> messages = List.of();
        private long preTokenCount;
        private long postTokenCount;
        private Duration ttl;

        private Builder() {
        }

        /**
         * Sets the namespace.
         *
         * @param namespace the namespace to set
         * @return this builder
         */
        public Builder namespace(OffloadNamespace namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Sets the algorithm id; {@code null} is treated as empty string.
         *
         * @param algorithmId the algorithm id to set
         * @return this builder
         */
        public Builder algorithmId(String algorithmId) {
            this.algorithmId = algorithmId == null ? "" : algorithmId;
            return this;
        }

        /**
         * Sets the trigger source; {@code null} is treated as empty string.
         *
         * @param source the source to set
         * @return this builder
         */
        public Builder source(String source) {
            this.source = source == null ? "" : source;
            return this;
        }

        /**
         * Sets the messages to offload; {@code null} is treated as empty list.
         *
         * @param messages the messages to set
         * @return this builder
         */
        public Builder messages(List<BaseMessage> messages) {
            this.messages = messages == null ? List.of() : new ArrayList<>(messages);
            return this;
        }

        /**
         * Sets the pre-offload token count.
         *
         * @param preTokenCount the pre-offload token count
         * @return this builder
         */
        public Builder preTokenCount(long preTokenCount) {
            this.preTokenCount = preTokenCount;
            return this;
        }

        /**
         * Sets the post-offload token count.
         *
         * @param postTokenCount the post-offload token count
         * @return this builder
         */
        public Builder postTokenCount(long postTokenCount) {
            this.postTokenCount = postTokenCount;
            return this;
        }

        /**
         * Sets the TTL override; {@code null} means use config default.
         *
         * @param ttl the TTL to set
         * @return this builder
         */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        /**
         * Builds the {@link OffloadRequest} with defensive copies.
         *
         * @return a new {@link OffloadRequest} instance
         */
        public OffloadRequest build() {
            return new OffloadRequest(this);
        }
    }
}
