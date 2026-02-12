// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

/**
 * BarrierMessage类表示N→1扇入（fan-in）消息
 */
public class BarrierMessage extends Message {

    /**
     * 构造一个BarrierMessage对象
     *
     * @param sender 发送者节点名称
     * @param target 目标节点名称
     * @param payload 消息负载数据
     */
    public BarrierMessage(String sender, String target, Object payload) {
        super(sender, target, payload);
    }

    /**
     * 构造一个没有负载的BarrierMessage对象
     *
     * @param sender 发送者节点名称
     * @param target 目标节点名称
     */
    public BarrierMessage(String sender, String target) {
        super(sender, target, null);
    }
}

