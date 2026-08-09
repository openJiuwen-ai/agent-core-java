/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.sys_operation.local;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.Result;
import com.openjiuwen.core.sysop.BaseOperation;
import com.openjiuwen.core.sysop.OperationDef;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.OperationRegistry;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code tests.unit_tests.core.sys_operation.local.test_custom_operation_extension} in
 * {@code tests/unit_tests/core/sys_operation/local/test_custom_operation_extension.py}.
 */
class CustomOperationExtensionMissingTest {

    private static final String CARD_ID = "test_calculator_op";

    @BeforeEach
    void registerOperation() {
        OperationRegistry.register(LocalCalculatorOperation.class);
    }

    @AfterEach
    void cleanup() throws Exception {
        Runner.resourceMgr.removeSysOperation(CARD_ID);
        Runner.stop().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    @Test
    void customCalculatorListTools() {
        SysOperation sysOperation = new SysOperation(calculatorCard());
        BaseCalculatorOperation calculator = (BaseCalculatorOperation) sysOperation.getOperation("calculator");

        List<ToolCard> tools = calculator.listTools();
        Map<String, ToolCard> toolsByName = tools.stream()
                .collect(Collectors.toMap(ToolCard::getName, toolCard -> toolCard));

        assertThat(tools).hasSize(1);
        assertThat(toolsByName).containsKey("add");
        ToolCard addTool = toolsByName.get("add");
        assertThat(properties(addTool)).containsKeys("a", "b");
        assertThat(required(addTool)).containsExactly("a", "b");
    }

    @Test
    void customCalculatorDirectInvocation() {
        SysOperation sysOperation = new SysOperation(calculatorCard());
        BaseCalculatorOperation calculator = (BaseCalculatorOperation) sysOperation.getOperation("calculator");

        assertThat(calculator.add(10, 5)).isEqualTo(15);
        assertThat(calculator.add(20, 8)).isEqualTo(28);
    }

    @Test
    void customCalculatorToolInvocation() throws Exception {
        Runner.start().toCompletableFuture().get(10, TimeUnit.SECONDS);
        Result<?, ?> addResult = Runner.resourceMgr.addSysOperation(calculatorCard());
        assertThat(addResult.isOk()).isTrue();

        String addToolId = SysOperationCard.generateToolId(CARD_ID, "calculator", "add");
        assertThat(addToolId).isEqualTo("test_calculator_op.calculator.add");
        Tool addTool = Runner.resourceMgr.getTool(addToolId);

        assertThat(addTool).isNotNull();
        assertThat(addTool.getCard().getName()).isEqualTo("add");
        assertThat(addTool.invoke(Map.of("a", 100, "b", 50))).isEqualTo(150);
    }

    @Test
    void multiModeFsCoexistence() {
        assertThat(OperationRegistry.getOperationInfo("fs", OperationMode.LOCAL)).isNotNull();
        assertThat(OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX)).isNotNull();

        assertThat(OperationRegistry.getOperationInfo("fs", OperationMode.LOCAL).mode())
                .isEqualTo(OperationMode.LOCAL);
        assertThat(OperationRegistry.getOperationInfo("fs", OperationMode.SANDBOX).mode())
                .isEqualTo(OperationMode.SANDBOX);
        assertThat(OperationRegistry.getSupportedOperations(OperationMode.LOCAL)).contains("fs");
        assertThat(OperationRegistry.getSupportedOperations(OperationMode.SANDBOX)).contains("fs");
    }

    private static SysOperationCard calculatorCard() {
        return new SysOperationCard(CARD_ID, OperationMode.LOCAL, new LocalWorkConfig());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(ToolCard toolCard) {
        return (Map<String, Object>) toolCard.getInputParams().get("properties");
    }

    @SuppressWarnings("unchecked")
    private static List<String> required(ToolCard toolCard) {
        return (List<String>) toolCard.getInputParams().get("required");
    }

    /**
     * Mirrors Python's {@code BaseCalculatorOperation} in
     * {@code tests/unit_tests/core/sys_operation/local/custom_operation.py}.
     */
    abstract static class BaseCalculatorOperation extends BaseOperation {

        BaseCalculatorOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        @Override
        public List<ToolCard> listTools() {
            return generateToolCards(List.of("add"));
        }

        public abstract int add(int a, int b);
    }

    /**
     * Mirrors Python's {@code LocalCalculatorOperation} in
     * {@code tests/unit_tests/core/sys_operation/local/custom_operation.py}.
     */
    public static final class LocalCalculatorOperation extends BaseCalculatorOperation {
        public static final OperationDef OP_DEF = OperationRegistry.operationDef(
                LocalCalculatorOperation.class,
                "calculator",
                OperationMode.LOCAL,
                "Calculator operations"
        );

        public LocalCalculatorOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        @Override
        public int add(int a, int b) {
            return a + b;
        }
    }
}
