// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.constants.enums;

/**
 * 任务类型枚举
 */
public enum TaskType {

    /**
     * 插件任务
     */
    PLUGIN("plugin"),

    /**
     * 工作流任务
     */
    WORKFLOW("workflow"),

    /**
     * MCP 任务
     */
    MCP("mcp"),

    /**
     * 未定义的任务类型
     */
    UNDEFINED("undefined");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 任务类型的字符串值
     */
    TaskType(String value) {
        this.value = value;
    }

    /**
     * 获取任务类型的字符串值
     *
     * @return 任务类型的字符串值
     */
    public String getValue() {
        return value;
    }
}