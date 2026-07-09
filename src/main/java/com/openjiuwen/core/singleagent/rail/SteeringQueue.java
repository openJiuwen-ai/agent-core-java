/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import java.util.List;

/**
 * Queue contract used by agent callback contexts for runtime steering.
 * 
 * @since 0.1.7
 */
public interface SteeringQueue {
    /**
     * pushSteering.
     * 
     * @param message message
     * @since 0.1.7
     */
    void pushSteering(String message);

    /**
     * Drain pending steering instructions in FIFO order.
     * 
     * @return drained steering instructions
     * @since 0.1.7
     */
    List<String> drainSteering();
}
