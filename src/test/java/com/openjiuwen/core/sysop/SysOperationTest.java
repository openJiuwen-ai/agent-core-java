/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.sysop;

import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SysOperation facade.
 */
class SysOperationTest {

    private SysOperationCard createCard(String id, OperationMode mode) {
        SysOperationCard card = new SysOperationCard();
        card.setId(id);
        if (mode != null) {
            card.setMode(mode);
        }
        return card;
    }

    @Test
    @DisplayName("SysOperation defaults to LOCAL mode when card mode is null")
    void testDefaultMode() {
        SysOperationCard card = createCard("test", null);
        SysOperation sysOp = new SysOperation(card);
        assertEquals(OperationMode.LOCAL, sysOp.getMode());
    }

    @Test
    @DisplayName("SysOperation uses card mode when specified")
    void testExplicitMode() {
        SysOperationCard card = createCard("test", OperationMode.SANDBOX);
        SysOperation sysOp = new SysOperation(card);
        assertEquals(OperationMode.SANDBOX, sysOp.getMode());
    }

    @Test
    @DisplayName("getOperation returns null for unregistered operation")
    void testGetOperationUnregistered() {
        SysOperationCard card = createCard("test", OperationMode.LOCAL);
        SysOperation sysOp = new SysOperation(card);
        // "nonexistent" is not registered
        assertNull(sysOp.getOperation("nonexistent"));
    }
}
