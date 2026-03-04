// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.constants.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单元测试类：测试 ControllerType 枚举
 */
class ControllerTypeTest {

    @Test
    void testReActControllerValue() {
        assertEquals("react", ControllerType.ReActController.getValue());
    }

    @Test
    void testWorkflowControllerValue() {
        assertEquals("workflow", ControllerType.WorkflowController.getValue());
    }

    @Test
    void testUndefinedValue() {
        assertEquals("undefined", ControllerType.Undefined.getValue());
    }
}