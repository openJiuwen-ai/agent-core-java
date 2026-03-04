// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 单元测试类：测试 Constants 类中的常量定义
 */
class ConstantsTest {

    // ==================== IR 相关常量 ====================

    @Test
    void testIrUserFields() {
        assertEquals("userFields", Constants.USER_FIELDS);
    }

    @Test
    void testIrQuery() {
        assertEquals("query", Constants.QUERY);
    }

    @Test
    void testIrSystemFields() {
        assertEquals("systemFields", Constants.SYSTEM_FIELDS);
    }

    // ==================== Workflow 相关常量 ====================

    @Test
    void testInteraction() {
        assertEquals("__interaction__", Constants.INTERACTION);
    }

    @Test
    void testInteractiveInput() {
        assertEquals("__interactive_input__", Constants.INTERACTIVE_INPUT);
    }

    @Test
    void testInputsKey() {
        assertEquals("inputs", Constants.INPUTS_KEY);
    }

    @Test
    void testConfigKey() {
        assertEquals("config", Constants.CONFIG_KEY);
    }

    @Test
    void testEndFrame() {
        assertEquals("all streaming outputs finish", Constants.END_FRAME);
    }

    @Test
    void testEndNodeStream() {
        assertEquals("end node stream", Constants.END_NODE_STREAM);
    }

    @Test
    void testLoopId() {
        assertEquals("__sys_loop_id", Constants.LOOP_ID);
    }

    @Test
    void testIndex() {
        assertEquals("index", Constants.INDEX);
    }

    @Test
    void testFinishIndex() {
        assertEquals("finish_index", Constants.FINISH_INDEX);
    }

    // ==================== 安全限制常量 ====================

    @Test
    void testMaxCollectionSize() {
        assertEquals(100000, Constants.MAX_COLLECTION_SIZE);
    }

    @Test
    void testMaxExpressionLength() {
        assertEquals(5000, Constants.MAX_EXPRESSION_LENGTH);
    }

    @Test
    void testMaxAstDepth() {
        assertEquals(50, Constants.MAX_AST_DEPTH);
    }

    @Test
    void testNestedLoopDepth() {
        assertEquals(1, Constants.NESTED_LOOP_DEPTH);
    }
}