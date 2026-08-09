/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code SysOperationToolAdapter} in
 * {@code openjiuwen/core/sys_operation/tool_adapter.py}.
 */
class SysOperationToolAdapterTest {

    @AfterEach
    void cleanRegistry() {
        OperationRegistry.clearForTest();
    }

    @Test
    void extractToolsWrapsCallableOperationMethods() throws Exception {
        OperationRegistry.register(DemoShellOperation.class, "shell", OperationMode.LOCAL, "demo shell");
        SysOperationCard card = new SysOperationCard("sys_op", OperationMode.LOCAL, new LocalWorkConfig());
        SysOperation operation = new SysOperation(card);

        List<SysOperationToolAdapter.ToolBinding> tools = SysOperationToolAdapter.extractTools(card, operation);

        assertThat(tools).hasSize(1);
        SysOperationToolAdapter.ToolBinding binding = tools.get(0);
        assertThat(binding.toolId()).isEqualTo("sys_op.shell.execute_cmd");
        assertThat(binding.localFunction().getCard().getId()).isEqualTo("sys_op.shell.execute_cmd");
        assertThat(binding.localFunction().getCard().getName()).isEqualTo("execute_cmd");

        Object result = binding.localFunction().invoke(Map.of(
                "command", "pwd",
                "timeout", 7,
                "shellType", BaseShellOperation.ShellType.BASH
        ));

        assertThat(result).isInstanceOf(ExecuteCmdResult.class);
    }

    @Test
    void extractToolsSkipsMissingOperationAndEmptyToolLists() {
        OperationRegistry.register(EmptyShellOperation.class, "shell", OperationMode.LOCAL, "empty shell");
        SysOperationCard card = new SysOperationCard("sys_op", OperationMode.LOCAL, new LocalWorkConfig());
        SysOperation operation = new SysOperation(card);

        assertThat(SysOperationToolAdapter.extractTools(card, operation)).isEmpty();
    }

    @Test
    void getToolIdPrefixMatchesPythonReturnShape() {
        assertThat(SysOperationToolAdapter.getToolIdPrefix("sys_op")).isEqualTo("sys_op.");
        assertThat(SysOperationToolAdapter.getToolIdPrefix(List.of("a", "b"))).containsExactly("a.", "b.");
    }

    public static class DemoShellOperation extends BaseShellOperation {
        public static final OperationDef OP_DEF = new OperationDef(
                DemoShellOperation.class,
                "demo shell",
                "shell",
                OperationMode.LOCAL
        );

        public DemoShellOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        @Override
        public List<ToolCard> listTools() {
            return List.of(ToolCard.builder()
                    .name("execute_cmd")
                    .description("Execute a command")
                    .build());
        }

        @Override
        public CompletableFuture<ExecuteCmdResult> executeCmd(String command,
                                                              String cwd,
                                                              Integer timeout,
                                                              Map<String, String> environment,
                                                              Map<String, Object> options,
                                                              ShellType shellType) {
            return CompletableFuture.completedFuture(new ExecuteCmdResult());
        }

        @Override
        public CompletableFuture<ExecuteCmdBackgroundResult> executeCmdBackground(String command,
                                                                                  String cwd,
                                                                                  Map<String, String> environment,
                                                                                  double grace,
                                                                                  ShellType shellType) {
            return CompletableFuture.completedFuture(new ExecuteCmdBackgroundResult());
        }
    }

    public static final class EmptyShellOperation extends DemoShellOperation {
        public static final OperationDef OP_DEF = new OperationDef(
                EmptyShellOperation.class,
                "empty shell",
                "shell",
                OperationMode.LOCAL
        );

        public EmptyShellOperation(String name, OperationMode mode, String description, Object runConfig) {
            super(name, mode, description, runConfig);
        }

        @Override
        public List<ToolCard> listTools() {
            return null;
        }
    }
}
