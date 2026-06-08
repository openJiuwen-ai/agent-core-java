/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.pregel;

import java.util.List;

/**
 * Mirrors Python's {@code IRouter} in
 * {@code openjiuwen/core/graph/pregel/base.py}.
 */
public interface IRouter {

    List<Message> dispatch(String sourceNode) throws Exception;
}
