/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer.step;

import java.util.List;

/**
 * Append-only audit log for step boundaries.
 *
 * <p>The journal never participates in resume: losing trailing records on a
 * crash loses audit tail only, never correctness. Implementations should
 * sanitize payloads before persisting (issue #195 section 3.6.1) and honor a
 * retention policy aligned with the checkpointer's.</p>
 */
public interface StepJournal {

    /**
     * Append one step record. Implementations must not throw on storage
     * failure; audit must not break the execution path.
     *
     * @param record record to append
     */
    void append(StepRecord record);

    /**
     * Replay the recorded steps of one session in append order.
     *
     * @param sessionId session whose trail to replay
     * @return records in append order, empty when the session has no trail
     */
    List<StepRecord> replay(String sessionId);

    /**
     * Remove records older than the retention deadline.
     *
     * @param beforeEpochMillis retention deadline in epoch millis
     * @return number of removed records
     */
    int purgeBefore(long beforeEpochMillis);

    /**
     * Release journal resources.
     */
    default void close() {
    }
}
