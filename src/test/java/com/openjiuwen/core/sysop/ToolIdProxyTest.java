/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.sysop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToolIdProxy.
 */
class ToolIdProxyTest {

    @Test
    @DisplayName("toolId generates correct format: cardId.opType.methodName")
    void testToolIdFormat() {
        ToolIdProxy proxy = new ToolIdProxy("sys_op", "fs");
        assertEquals("sys_op.fs.readFile", proxy.toolId("readFile"));
    }

    @Test
    @DisplayName("getCardId returns the card ID")
    void testGetCardId() {
        ToolIdProxy proxy = new ToolIdProxy("myCard", "shell");
        assertEquals("myCard", proxy.getCardId());
    }

    @Test
    @DisplayName("getOpType returns the operation type")
    void testGetOpType() {
        ToolIdProxy proxy = new ToolIdProxy("myCard", "code");
        assertEquals("code", proxy.getOpType());
    }
}
