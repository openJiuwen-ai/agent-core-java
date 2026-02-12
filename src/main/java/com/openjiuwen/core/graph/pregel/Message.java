// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

/**
 * Message类表示图节点之间传递的消息
 */
public class Message {
    private final String sender;
    private final String target;
    private final Object payload;

    /**
     * 构造一个Message对象
     *
     * @param sender 发送者节点名称
     * @param target 目标节点名称
     * @param payload 消息负载数据
     */
    public Message(String sender, String target, Object payload) {
        this.sender = sender;
        this.target = target;
        this.payload = payload;
    }

    /**
     * 构造一个没有负载的Message对象
     *
     * @param sender 发送者节点名称
     * @param target 目标节点名称
     */
    public Message(String sender, String target) {
        this(sender, target, null);
    }

    /**
     * 获取发送者节点名称
     *
     * @return 发送者节点名称
     */
    public String getSender() {
        return sender;
    }

    /**
     * 获取目标节点名称
     *
     * @return 目标节点名称
     */
    public String getTarget() {
        return target;
    }

    /**
     * 获取消息负载数据
     *
     * @return 消息负载数据
     */
    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Message{" +
                "sender='" + sender + '\'' +
                ", target='" + target + '\'' +
                ", payload=" + payload +
                '}';
    }
}

