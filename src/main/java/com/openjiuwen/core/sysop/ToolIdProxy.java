/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * A helper for generating tool IDs via method calls.
 * <p>
 * Tool ID format: "{cardId}.{opType}.{method}"
 * <p>
 * Mirrors Python's {@code ToolIdProxy} in {@code sys_operation/sys_operation.py}.
 *
 * <p>Usage:
 * <pre>
 *   ToolIdProxy proxy = new ToolIdProxy("sys_op", "fs");
 *   String toolId = proxy.toolId("readFile"); // returns "sys_op.fs.readFile"
 * </pre>
 */
public class ToolIdProxy {

    private final String cardId;
    private final String opType;

    public ToolIdProxy(String cardId, String opType) {
        this.cardId = cardId;
        this.opType = opType;
    }

    /**
     * Generate a tool ID for a method name.
     *
     * @param methodName the method name
     * @return formatted tool ID: "{cardId}.{opType}.{methodName}"
     */
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
