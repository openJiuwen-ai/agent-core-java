// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

import java.util.Arrays;
import java.util.List;

/**
 * GraphInterrupt异常表示图执行中的中断异常
 */
public class GraphInterrupt extends Exception {
    private final List<Interrupt> values;

    /**
     * 构造一个GraphInterrupt异常
     *
     * @param values 中断值列表
     */
    public GraphInterrupt(Interrupt... values) {
        super(values != null ? Arrays.toString(values) : "null");
        this.values = values != null ? Arrays.asList(values) : null;
    }

    /**
     * 构造一个GraphInterrupt异常
     *
     * @param value 单个中断值
     */
    public GraphInterrupt(Interrupt value) {
        super(String.valueOf(value));
        this.values = value != null ? List.of(value) : null;
    }

    /**
     * 构造一个没有中断值的GraphInterrupt异常
     */
    public GraphInterrupt() {
        super("null");
        this.values = null;
    }

    /**
     * 获取中断值列表
     *
     * @return 中断值列表
     */
    public List<Interrupt> getValues() {
        return values;
    }
}

