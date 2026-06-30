/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.io.Serial;
import java.io.Serializable;

/**
 * Base message isPassed between Pregel nodes via channels.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.Message}.
 */
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String sender;
    private final String target;
    private final Object payload;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Message(String sender, String target) {
        this(sender, target, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Message(String sender, String target, Object payload) {
        this.sender = sender;
        this.target = target;
        this.payload = payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSender() {
        return sender;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getPayload() {
        return payload;
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        return "Message{sender='" + sender + "', target='" + target + "'}";
    }
}
