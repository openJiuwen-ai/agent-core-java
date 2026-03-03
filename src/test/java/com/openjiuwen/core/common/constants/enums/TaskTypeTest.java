// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.constants.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单元测试类：测试 TaskType 枚举
 */
class TaskTypeTest {

    @Test
    void testPluginValue() {
        assertEquals("plugin", TaskType.PLUGIN.getValue());
    }

    @Test
    void testWorkflowValue() {
        assertEquals("workflow", TaskType.WORKFLOW.getValue());
    }

    @Test
    void testMcpValue() {
        assertEquals("mcp", TaskType.MCP.getValue());
    }

    @Test
    void testUndefinedValue() {
        assertEquals("undefined", TaskType.UNDEFINED.getValue());
    }
}