/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

/**
 * Helper for generating tool IDs by operation type and method name.
 *
 * <p>Mirrors Python's {@code ToolIdProxy} in
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.</p>
 */
public final class ToolIdProxy {

    private final String cardId;
    private final String opType;

    public ToolIdProxy(String cardId, String opType) {
        this.cardId = cardId;
        this.opType = opType;
    }

    public String toolId(String methodName) {
        return SysOperationCard.generateToolId(cardId, opType, methodName);
    }

    public String getCardId() {
        return cardId;
    }

    public String getOpType() {
        return opType;
    }
}
