// -*- coding: UTF-8 -*-
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.graph.pregel;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * IRouter接口定义了路由器的行为
 */
public interface IRouter {

    /**
     * 根据源节点分发消息
     *
     * @param sourceNode 源节点名称
     * @return 包含消息列表的Mono
     */
    Mono<List<Message>> dispatch(String sourceNode);
}

