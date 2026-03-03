// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.constants.enums;

/**
 * Controller 类型枚举
 */
public enum ControllerType {

    /**
     * ReAct 控制器
     */
    ReActController("react"),

    /**
     * Workflow 控制器
     */
    WorkflowController("workflow"),

    /**
     * 未定义的控制器类型
     */
    Undefined("undefined");

    private final String value;

    /**
     * 构造函数
     *
     * @param value 控制器类型的字符串值
     */
    ControllerType(String value) {
        this.value = value;
    }

    /**
     * 获取控制器类型的字符串值
     *
     * @return 控制器类型的字符串值
     */
    public String getValue() {
        return value;
    }
}