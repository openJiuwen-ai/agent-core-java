// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

/**
 * TriggerMessage类表示激活目标节点进入下一个超步的消息
 */
public class TriggerMessage extends Message {

    /**
     * 构造一个TriggerMessage对象
     *
     * @param sender 发送者节点名称
     * @param target 目标节点名称
     * @param payload 消息负载数据
     */
    public TriggerMessage(String sender, String target, Object payload) {
        super(sender, target, payload);
    }

    /**
     * 构造一个没有负载的TriggerMessage对象
     *
     * @param sender 发送者节点名称
     * @param target 目标节点名称
     */
    public TriggerMessage(String sender, String target) {
        super(sender, target, null);
    }
}

