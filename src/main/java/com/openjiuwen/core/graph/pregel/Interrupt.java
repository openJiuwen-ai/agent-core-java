// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

/**
 * Interrupt类表示图执行中的中断值
 */
public class Interrupt {
    private final Object value;

    /**
     * 构造一个Interrupt对象
     *
     * @param value 中断值
     */
    public Interrupt(Object value) {
        this.value = value;
    }

    /**
     * 获取中断值
     *
     * @return 中断值
     */
    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "Interrupt{value=" + value + "}";
    }
}

