/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Distributed response message.
 * 
 * @since 0.1.7
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DmqResponseMessage extends DmqMessage {
    private DMessageType type = DMessageType.OUTPUT;

    private ResultType resultType = ResultType.MESSAGE;

    private String requestId = "";

    private String senderId = "";

    private String receiverId = "";

    private int seq;

    private boolean lastChunk;

    private Double expireAt;
}
