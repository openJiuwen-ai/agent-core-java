/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Distributed request message.
 *
 * <p>Mirrors Python's {@code DmqRequestMessage} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message.py}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DmqRequestMessage extends DmqMessage {

    private DMessageType type = DMessageType.INPUT;
    private String replyTopic = "";
    private String requestId = "";
    private String senderId = "";
    private String receiverId = "";
    private boolean enableStream = false;
    private Double expireAt = null;
}
