/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Backward-compatible helper for generating system operation tool IDs.
 *
 * <p>Mirrors Python's {@code ToolIdProxy} in
 * {@code openjiuwen/core/sys_operation/sys_operation.py}.</p>
 *
 * @deprecated Use {@link com.openjiuwen.core.sys_operation.ToolIdProxy}.
 */
@Deprecated(since = "0.1.14", forRemoval = false)
public class ToolIdProxy {

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
