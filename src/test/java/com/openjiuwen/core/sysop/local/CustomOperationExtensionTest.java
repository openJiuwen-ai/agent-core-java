/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.sysop.local;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.sysop.BaseOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.registry.OperationDef;
import com.openjiuwen.core.sysop.registry.OperationRegistry;
import org.junit.jupiter.api.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for custom operation extension and registry.
 * Mirrors Python's test_custom_operation_extension.py.
 */
class CustomOperationExtensionTest {

    private static final String CALCULATOR_OPERATION = "calculator";

    private static void registerCalculatorOperation() {
        OperationRegistry.register(LocalCalculatorOperation.class, CALCULATOR_OPERATION,
                OperationMode.LOCAL, "Calculator operations");
    }

    private static SysOperationCard calculatorCard() {
        SysOperationCard card = new SysOperationCard();
        card.setId("test_calculator_op");
        card.setMode(OperationMode.LOCAL);
        return card;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inputProperties(ToolCard card) {
        return (Map<String, Object>) card.getInputParams().get("properties");
    }

    private abstract static class BaseCalculatorOperation extends BaseOperation {

        BaseCalculatorOperation(Object runConfig) {
            super(CALCULATOR_OPERATION, OperationMode.LOCAL, "Calculator operations", runConfig);
        }

        @Override
        public List<ToolCard> listTools() {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("a", Map.of("type", "integer", "description", "First number"));
            properties.put("b", Map.of("type", "integer", "description", "Second number"));

            Map<String, Object> inputParams = new LinkedHashMap<>();
            inputParams.put("type", "object");
            inputParams.put("properties", properties);
            inputParams.put("required", List.of("a", "b"));

            return List.of(ToolCard.builder()
                    .name("add")
                    .description("Add two numbers.")
                    .inputParams(inputParams)
                    .build());
        }

        public abstract int add(int a, int b);
    }

    public static class LocalCalculatorOperation extends BaseCalculatorOperation {

        public LocalCalculatorOperation(Object runConfig) {
            super(runConfig);
        }

        @Override
        public int add(int a, int b) {
            return a + b;
        }
    }

    // ==================== Custom calculator extension ====================

    @Test
    @DisplayName("Custom calculator operation exposes add tool schema")
    void testCustomCalculatorListTools() {
        registerCalculatorOperation();
        SysOperation sysOp = new SysOperation(calculatorCard());
        BaseOperation operation = sysOp.getOperation(CALCULATOR_OPERATION);

        assertInstanceOf(LocalCalculatorOperation.class, operation);
        List<ToolCard> tools = operation.listTools();
        Map<String, ToolCard> toolsByName = tools.stream()
                .collect(Collectors.toMap(ToolCard::getName, tool -> tool));

        assertEquals(1, tools.size());
        assertTrue(toolsByName.containsKey("add"));

        ToolCard addTool = toolsByName.get("add");
        Map<String, Object> properties = inputProperties(addTool);
        assertTrue(properties.containsKey("a"));
        assertTrue(properties.containsKey("b"));
        assertEquals(List.of("a", "b"), addTool.getInputParams().get("required"));
    }

    @Test
    @DisplayName("Custom calculator operation supports direct invocation")
    void testCustomCalculatorDirectInvocation() {
        registerCalculatorOperation();
        SysOperation sysOp = new SysOperation(calculatorCard());
        LocalCalculatorOperation calculator = (LocalCalculatorOperation) sysOp.getOperation(CALCULATOR_OPERATION);

        assertEquals(15, calculator.add(10, 5));
        assertEquals(28, calculator.add(20, 8));
    }

    @Test
    @DisplayName("Custom calculator operation is invokable through ResourceMgr tool interface")
    void testCustomCalculatorToolInvocation() throws Exception {
        registerCalculatorOperation();
        SysOperationCard card = calculatorCard();
        Runner.start();
        try {
            Result<SysOperationCard> addResult = Runner.resourceMgr().addSysOperation(card, null);
            assertTrue(addResult.isOk());

            Object sysOperation = Runner.resourceMgr().getSysOperation(card.getId(), null, TagMatchStrategy.ALL);
            assertInstanceOf(SysOperation.class, sysOperation);

            String addToolId = SysOperationCard.generateToolId(card.getId(), CALCULATOR_OPERATION, "add");
            assertEquals("test_calculator_op.calculator.add", addToolId);

            Object addTool = Runner.resourceMgr().getTool(addToolId);
            assertInstanceOf(Tool.class, addTool);
            assertEquals("add", ((Tool) addTool).getCard().getName());

            Object result = ((Tool) addTool).invoke(Map.of("a", 100, "b", 50));
            assertEquals(150, result);
        } finally {
            Runner.resourceMgr().removeSysOperation(card.getId(), null, TagMatchStrategy.ALL, true);
            Runner.stop();
        }
    }

    // ==================== Multi-mode coexistence ====================

    @Test
    @DisplayName("Built-in FS for LOCAL and SANDBOX modes coexist in registry")
    void testMultiModeFsCoexistence() {
        Optional<OperationDef> localFs = OperationRegistry.getOperationInfo("fs", OperationMode.LOCAL);
        Optional<OperationDef> sandboxFs = OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX);

        assertTrue(localFs.isPresent(), "LOCAL fs should be registered");
        assertTrue(sandboxFs.isPresent(), "SANDBOX fs should be registered");

        assertEquals(OperationMode.LOCAL, localFs.get().getMode());
        assertEquals(OperationMode.SANDBOX, sandboxFs.get().getMode());
    }

    @Test
    @DisplayName("getSupportedOperations returns built-in operations for both modes")
    void testSupportedOperationsBothModes() {
        List<String> localOps = OperationRegistry.getSupportedOperations(OperationMode.LOCAL);
        List<String> sandboxOps = OperationRegistry.getSupportedOperations(OperationMode.SANDBOX);

        assertTrue(localOps.contains("fs"), "LOCAL should have fs");
        assertTrue(localOps.contains("shell"), "LOCAL should have shell");
        assertTrue(localOps.contains("code"), "LOCAL should have code");

        assertTrue(sandboxOps.contains("fs"), "SANDBOX should have fs");
        assertTrue(sandboxOps.contains("shell"), "SANDBOX should have shell");
        assertTrue(sandboxOps.contains("code"), "SANDBOX should have code");
    }

    @Test
    @DisplayName("Built-in operations for LOCAL have correct mode")
    void testLocalOperationsModes() {
        String[] names = {"fs", "shell", "code"};
        for (String name : names) {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo(name, OperationMode.LOCAL);
            assertTrue(def.isPresent(), name + " should be registered for LOCAL");
            assertEquals(OperationMode.LOCAL, def.get().getMode(),
                    name + " should be LOCAL mode");
        }
    }

    @Test
    @DisplayName("Built-in operations for SANDBOX have correct mode")
    void testSandboxOperationsModes() {
        String[] names = {"fs", "shell", "code"};
        for (String name : names) {
            Optional<OperationDef> def = OperationRegistry.getOperationInfo(name, OperationMode.SANDBOX);
            assertTrue(def.isPresent(), name + " should be registered for SANDBOX");
            assertEquals(OperationMode.SANDBOX, def.get().getMode(),
                    name + " should be SANDBOX mode");
        }
    }

    @Test
    @DisplayName("Non-existent operation returns empty")
    void testNonExistentOperation() {
        Optional<OperationDef> def = OperationRegistry.getOperationInfo("nonexistent", OperationMode.LOCAL);
        assertFalse(def.isPresent());
    }

    @Test
    @DisplayName("Register and retrieve custom operation")
    void testRegisterCustomOperation() {
        registerCalculatorOperation();

        Optional<OperationDef> retrieved = OperationRegistry.getOperationInfo(CALCULATOR_OPERATION, OperationMode.LOCAL);
        assertTrue(retrieved.isPresent());
        assertEquals(CALCULATOR_OPERATION, retrieved.get().getName());
        assertEquals(OperationMode.LOCAL, retrieved.get().getMode());
        assertEquals("Calculator operations", retrieved.get().getDescription());
        assertEquals(LocalCalculatorOperation.class, retrieved.get().getCls());
    }
}
