/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import com.openjiuwen.core.common.utils.SerializationUtils;

import java.io.Serial;
import java.io.Serializable;

/**
 * Base message isPassed between Pregel nodes via channels.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.graph.pregel.base.Message}.
 * 
 * @since 0.1.7
 */
public class Message implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String sender;
    private final String target;
    private final Serializable payload;

    /**
     * Message.
     * 
     * @param sender sender
     * @param target target
     * @since 0.1.7
     */
    public Message(String sender, String target) {
        this(sender, target, null);
    }

    /**
     * Message.
     * 
     * @param sender sender
     * @param target target
     * @param payload payload
     * @since 0.1.7
     */
    public Message(String sender, String target, Object payload) {
        this.sender = sender;
        this.target = target;
        this.payload = SerializationUtils.requireSerializable(payload, "payload");
    }

    /**
     * getSender.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSender() {
        return sender;
    }

    /**
     * getTarget.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTarget() {
        return target;
    }

    /**
     * getPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        return "Message{sender='" + sender + "', target='" + target + "'}";
    }
}
