/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.schema.DesignerNode;

/**
 * Executes one designer node.
 *
 * @since 2026-11-06
 */
@FunctionalInterface
public interface NodeHandler {

    /**
     * Execute the node.
     *
     * @param node node to execute
     * @param context run context
     * @return node outcome
     */
    NodeOutcome execute(DesignerNode node, DesignerRunContext context);
}
