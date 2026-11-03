/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loop event schemas for DeepAgent outer task-loop.
 *
 * <p>A queued outer-loop event. The first two fields are ordering keys for priority queue:
 * - priority: lower is higher priority
 * - seq: FIFO within same priority
 *
 * <p>Mirrors Python's {@code DeepLoopEvent} in
 * {@code openjiuwen/harness/schema/loop_event.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepLoopEvent implements Comparable<DeepLoopEvent> {

    /** Priority for queue ordering (lower is higher priority). */
    private int priority;

    /** Sequence number for FIFO ordering within same priority. */
    private int seq;

    /** Creation timestamp. */
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** Unique event ID. */
    @Builder.Default
    private String eventId = UUID.randomUUID().toString();

    /** Event type. */
    @Builder.Default
    private DeepLoopEventType eventType = DeepLoopEventType.FOLLOWUP;

    /** Event content/message. */
    @Builder.Default
    private String content = "";

    /** Associated task ID. */
    private String taskId;

    /** Additional metadata. */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Override
    public int compareTo(DeepLoopEvent other) {
        // First compare by priority (lower is higher priority)
        int priorityCompare = Integer.compare(this.priority, other.priority);
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // Then compare by sequence number (FIFO)
        return Integer.compare(this.seq, other.seq);
    }

    /**
     * Create a loop event with default priority.
     */
    public static DeepLoopEvent create(int seq, DeepLoopEventType eventType, String content) {
        return create(seq, eventType, content, null, null, null);
    }

    /**
     * Create a loop event with all parameters.
     */
    public static DeepLoopEvent create(
            int seq,
            DeepLoopEventType eventType,
            String content,
            String taskId,
            Map<String, Object> metadata,
            Integer priority) {
        int eventPriority = priority != null ? priority : eventType.getDefaultPriority();
        return DeepLoopEvent.builder()
                .priority(eventPriority)
                .seq(seq)
                .eventType(eventType)
                .content(content != null ? content : "")
                .taskId(taskId)
                .metadata(metadata != null ? metadata : new HashMap<>())
                .build();
    }
}
