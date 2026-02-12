// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

/**
 * Channel抽象类表示Pregel图中的通道
 */
public abstract class Channel {
    private final String name;

    /**
     * 构造一个Channel对象
     *
     * @param name 通道名称
     */
    public Channel(String name) {
        this.name = name;
    }

    /**
     * 获取通道的键
     *
     * @return 通道键
     */
    public String getKey() {
        return name;
    }

    /**
     * 获取节点名称
     *
     * @return 节点名称
     */
    public String getNodeName() {
        return name;
    }

    /**
     * 检查通道是否就绪
     *
     * @return 如果通道就绪则返回true
     */
    public abstract boolean isReady();

    /**
     * 接受消息
     *
     * @param msg 要接受的消息
     */
    public abstract void accept(Message msg);

    /**
     * 消费通道数据
     * 返回节点函数的可消费输入并重置内部快照
     *
     * @return 可消费的数据
     */
    public abstract Object consume();

    /**
     * 获取当前快照
     *
     * @return 快照数据
     */
    public abstract Object snapshot();

    /**
     * 从快照恢复状态
     *
     * @param snapshot 快照数据
     */
    public abstract void restore(Object snapshot);
}

