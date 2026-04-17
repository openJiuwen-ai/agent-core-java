/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for sandbox stub operations.
 */
class SandboxOperationTest {

    @Test
    @DisplayName("SandboxCodeOperation.executeCode throws UnsupportedOperationException")
    void testSandboxCodeNotImplemented() {
        SandboxCodeOperation op = new SandboxCodeOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.executeCode("print(1)", "python", 300, null, null));
    }

    @Test
    @DisplayName("SandboxCodeOperation.executeCodeStream throws UnsupportedOperationException")
    void testSandboxCodeStreamNotImplemented() {
        SandboxCodeOperation op = new SandboxCodeOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.executeCodeStream("print(1)", "python", 300, null, null));
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmd throws UnsupportedOperationException")
    void testSandboxShellNotImplemented() {
        SandboxShellOperation op = new SandboxShellOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.executeCmd("ls", null, 300, null, null));
    }

    @Test
    @DisplayName("SandboxShellOperation.executeCmdStream throws UnsupportedOperationException")
    void testSandboxShellStreamNotImplemented() {
        SandboxShellOperation op = new SandboxShellOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.executeCmdStream("ls", null, 300, null, null));
    }

    @Test
    @DisplayName("SandboxFsOperation.readFile throws UnsupportedOperationException")
    void testSandboxFsReadNotImplemented() {
        SandboxFsOperation op = new SandboxFsOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.readFile("/tmp/test.txt", "text", null, null, null, "utf-8", 0, null));
    }

    @Test
    @DisplayName("SandboxFsOperation.writeFile throws UnsupportedOperationException")
    void testSandboxFsWriteNotImplemented() {
        SandboxFsOperation op = new SandboxFsOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.writeFile("/tmp/test.txt", "content", "text",
                        true, false, true, "644", "utf-8", null));
    }

    @Test
    @DisplayName("SandboxFsOperation.searchFiles throws UnsupportedOperationException")
    void testSandboxFsSearchNotImplemented() {
        SandboxFsOperation op = new SandboxFsOperation(new SandboxGatewayConfig());
        assertThrows(UnsupportedOperationException.class, () ->
                op.searchFiles("/tmp", "*.txt", null));
    }
}
