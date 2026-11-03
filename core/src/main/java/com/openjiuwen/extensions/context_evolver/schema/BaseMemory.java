/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;

/**
 * Mirrors Python's {@code BaseMemory} in
 * {@code openjiuwen/extensions/context_evolver/schema/io_schema.py}.
 */
public interface BaseMemory {

    String getWorkspaceId();

    void setWorkspaceId(String workspaceId);

    VectorNode toVectorNode();
}
