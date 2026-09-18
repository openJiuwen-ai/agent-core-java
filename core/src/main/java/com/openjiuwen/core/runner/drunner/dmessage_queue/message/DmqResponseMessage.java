/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Distributed response message.
 *
 * <p>Mirrors Python's {@code DmqResponseMessage} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message.py}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DmqResponseMessage extends DmqMessage {

    private DMessageType type = DMessageType.OUTPUT;
    private ResultType resultType = ResultType.MESSAGE;
    private String requestId = "";
    private String senderId = "";
    private String receiverId = "";
    private int seq = 0;
    private boolean lastChunk = false;
    private Double expireAt = null;
}
