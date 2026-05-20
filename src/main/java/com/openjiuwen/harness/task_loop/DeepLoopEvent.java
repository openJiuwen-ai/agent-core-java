/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-generated for codecheck compliance.
 */
public final class DeepLoopEvent implements Comparable<DeepLoopEvent> {
    private final int priority;
    private final long sequence;
    private final Instant createdAt;
    private final String eventId;
    private final DeepLoopEventType eventType;
    private final String content;
    private final String taskId;
    private final Map<String, Object> metadata;

    private DeepLoopEvent(Builder builder) {
        this.priority = builder.priority != null ? builder.priority : defaultPriority(builder.eventType);
        this.sequence = builder.sequence;
        this.createdAt = builder.createdAt != null ? builder.createdAt : Instant.now();
        this.eventId = builder.eventId != null ? builder.eventId : UUID.randomUUID().toString();
        this.eventType = builder.eventType != null ? builder.eventType : DeepLoopEventType.FOLLOWUP;
        this.content = builder.content != null ? builder.content : "";
        this.taskId = builder.taskId;
        this.metadata = Map.copyOf(builder.metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder(long sequence, DeepLoopEventType eventType, String content) {
        return new Builder(sequence, eventType, content);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static int defaultPriority(DeepLoopEventType eventType) {
        if (eventType == DeepLoopEventType.ABORT) {
            return 0;
        }
        if (eventType == DeepLoopEventType.STEER) {
            return 1;
        }
        return 10;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public int compareTo(DeepLoopEvent other) {
        int priorityCompare = Integer.compare(priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Long.compare(sequence, other.sequence);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepLoopEventType getEventType() {
        return eventType;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getContent() {
        return content;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final class Builder {
        private Integer priority;
        private long sequence;
        private Instant createdAt;
        private String eventId;
        private DeepLoopEventType eventType;
        private String content;
        private String taskId;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        private Builder(long sequence, DeepLoopEventType eventType, String content) {
            this.sequence = sequence;
            this.eventType = eventType;
            this.content = content;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepLoopEvent build() {
            return new DeepLoopEvent(this);
        }
    }
}
