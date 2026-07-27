/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.List;

/**
 * Queue contract used by agent callback contexts for runtime steering.
 */
public interface SteeringQueue {
    /**
     * Push one steering instruction.
     *
     * @param message steering text
     */
    void pushSteering(String message);

    /**
     * Drain pending steering instructions in FIFO order.
     *
     * @return drained steering instructions
     */
    List<String> drainSteering();
}
