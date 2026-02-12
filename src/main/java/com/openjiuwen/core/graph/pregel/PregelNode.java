// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

import java.util.List;
import java.util.function.Function;

/**
 * PregelNode类表示Pregel图中的节点
 */
public class PregelNode {
    private final String name;
    private final Function<Object, Object> func;
    private final List<IRouter> routers;

    /**
     * 构造一个PregelNode对象
     *
     * @param name 节点名称
     * @param func 节点执行函数
     * @param routers 路由器列表
     */
    public PregelNode(String name, Function<Object, Object> func, List<IRouter> routers) {
        this.name = name;
        this.func = func;
        this.routers = routers;
    }

    /**
     * 获取节点名称
     *
     * @return 节点名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取节点执行函数
     *
     * @return 节点执行函数
     */
    public Function<Object, Object> getFunc() {
        return func;
    }

    /**
     * 获取路由器列表
     *
     * @return 路由器列表
     */
    public List<IRouter> getRouters() {
        return routers;
    }
}

