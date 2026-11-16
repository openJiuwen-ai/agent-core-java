/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.trajectory;

/**
 * Consumer of trajectory span records produced by {@link SpanRecordProcessor}.
 *
 * <p>The swarm registers exactly one consumer (its session sink router) in the
 * server process. Implementations must be thread safe: records are dispatched
 * from whichever thread ends the span.</p>
 *
 * @since 0.1.18
 */
public interface TrajectoryRecordConsumer {
    /**
     * Consume one final (ended) span record.
     *
     * @param record the ended span record
     */
    void consume(OtlpSpanRecord record);

    /**
     * Consume one running-span snapshot record.
     *
     * <p>Consumers may coalesce snapshots per {@code recordId()} with
     * latest-wins semantics.</p>
     *
     * @param record the running span snapshot record
     */
    default void consumeSnapshot(OtlpSpanSnapshotRecord record) {
    }

    /**
     * Consume one model streaming frame.
     *
     * <p>Frames are never coalesced; consumers append them in sequence
     * order.</p>
     *
     * @param frame the streaming frame record
     */
    default void consumeStreamFrame(StreamFrameRecord frame) {
    }
}
