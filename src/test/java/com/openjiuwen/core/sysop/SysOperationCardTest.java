/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.sysop;

import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SysOperationCard.
 */
class SysOperationCardTest {

    private SysOperationCard createCard(String id, OperationMode mode) {
        SysOperationCard card = new SysOperationCard();
        card.setId(id);
        card.setMode(mode);
        return card;
    }

    @Test
    @DisplayName("generateToolId produces cardId.opType.methodName")
    void testGenerateToolId() {
        assertEquals("sys_op.fs.readFile",
                SysOperationCard.generateToolId("sys_op", "fs", "readFile"));
    }

    @Test
    @DisplayName("validateMode returns LOCAL for 'local'")
    void testValidateModeLocal() {
        assertEquals(OperationMode.LOCAL, SysOperationCard.validateMode("local"));
    }

    @Test
    @DisplayName("validateMode throws on invalid value")
    void testValidateModeInvalid() {
        assertThrows(Exception.class, () -> SysOperationCard.validateMode("invalid"));
    }

    @Test
    @DisplayName("fs() returns ToolIdProxy with opType 'fs'")
    void testFsProxy() {
        SysOperationCard card = createCard("test_card", OperationMode.LOCAL);
        ToolIdProxy proxy = card.fs();
        assertEquals("test_card", proxy.getCardId());
        assertEquals("fs", proxy.getOpType());
    }

    @Test
    @DisplayName("shell() returns ToolIdProxy with opType 'shell'")
    void testShellProxy() {
        SysOperationCard card = createCard("test_card", OperationMode.LOCAL);
        ToolIdProxy proxy = card.shell();
        assertEquals("shell", proxy.getOpType());
    }

    @Test
    @DisplayName("code() returns ToolIdProxy with opType 'code'")
    void testCodeProxy() {
        SysOperationCard card = createCard("test_card", OperationMode.LOCAL);
        ToolIdProxy proxy = card.code();
        assertEquals("code", proxy.getOpType());
    }

    @Test
    @DisplayName("builder sets all fields correctly")
    void testBuilderFields() {
        LocalWorkConfig workConfig = LocalWorkConfig.builder().workDir("/tmp").build();
        SysOperationCard card = new SysOperationCard();
        card.setId("my_card");
        card.setName("Test Card");
        card.setDescription("A test card");
        card.setMode(OperationMode.LOCAL);
        card.setWorkConfig(workConfig);

        assertEquals("my_card", card.getId());
        assertEquals("Test Card", card.getName());
        assertEquals("A test card", card.getDescription());
        assertEquals(OperationMode.LOCAL, card.getMode());
        assertEquals("/tmp", card.getWorkConfig().getWorkDir());
    }
}
