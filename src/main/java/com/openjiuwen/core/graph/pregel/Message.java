/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.io.Serial;
import java.io.Serializable;

/**
 * Base message passed between Pregel nodes via channels.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.Message}.
 */
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String sender;
    private final String target;
    private final Object payload;

    public Message(String sender, String target) {
        this(sender, target, null);
    }

    public Message(String sender, String target, Object payload) {
        this.sender = sender;
        this.target = target;
        this.payload = payload;
    }

    public String getSender() {
        return sender;
    }

    public String getTarget() {
        return target;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Message{sender='" + sender + "', target='" + target + "'}";
    }
}
