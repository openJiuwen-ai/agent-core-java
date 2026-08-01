/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DeepLoopEvent.
 * 
 * @since 0.1.7
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

    /**
     * DeepLoopEvent.
     * 
     * @param builder builder
     * @since 0.1.7
     */
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
     * builder.
     * 
     * @param sequence sequence
     * @param eventType eventType
     * @param content content
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder(long sequence, DeepLoopEventType eventType, String content) {
        return new Builder(sequence, eventType, content);
    }

    /**
     * defaultPriority.
     * 
     * @param eventType eventType
     * @return the result
     * @since 0.1.7
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
     * compareTo.
     * 
     * @param other other
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int compareTo(DeepLoopEvent other) {
        int priorityCompare = Integer.compare(priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Long.compare(sequence, other.sequence);
    }

    /**
     * getPriority.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getPriority() {
        return priority;
    }

    /**
     * getSequence.
     * 
     * @return the result
     * @since 0.1.7
     */
    public long getSequence() {
        return sequence;
    }

    /**
     * getCreatedAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * getEventId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * getEventType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public DeepLoopEventType getEventType() {
        return eventType;
    }

    /**
     * getContent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getContent() {
        return content;
    }

    /**
     * getTaskId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * getMetadata.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private Integer priority;
        private long sequence;
        private Instant createdAt;
        private String eventId;
        private DeepLoopEventType eventType;
        private String content;
        private String taskId;

        /**
         * LinkedHashMap<>.
         * 
         * @since 0.1.7
         */
        private Map<String, Object> metadata = new LinkedHashMap<>();

        /**
         * Builder.
         * 
         * @param sequence sequence
         * @param eventType eventType
         * @param content content
         * @since 0.1.7
         */
        private Builder(long sequence, DeepLoopEventType eventType, String content) {
            this.sequence = sequence;
            this.eventType = eventType;
            this.content = content;
        }

        /**
         * priority.
         * 
         * @param priority priority
         * @return the result
         * @since 0.1.7
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * taskId.
         * 
         * @param taskId taskId
         * @return the result
         * @since 0.1.7
         */
        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        /**
         * metadata.
         * 
         * @param metadata metadata
         * @return the result
         * @since 0.1.7
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
            return this;
        }

        /**
         * eventId.
         * 
         * @param eventId eventId
         * @return the result
         * @since 0.1.7
         */
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        /**
         * createdAt.
         * 
         * @param createdAt createdAt
         * @return the result
         * @since 0.1.7
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public DeepLoopEvent build() {
            return new DeepLoopEvent(this);
        }
    }
}
