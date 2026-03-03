// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.schema;

/**
 * 参数类型枚举
 */
public enum ParamType {
    /**
     * 字符串类型
     */
    STRING("string"),
    /**
     * 布尔类型
     */
    BOOLEAN("boolean"),
    /**
     * 整数类型
     */
    INTEGER("integer"),
    /**
     * 数字类型（浮点数）
     */
    NUMBER("number"),
    /**
     * 数组类型
     */
    ARRAY("array"),
    /**
     * 对象类型
     */
    OBJECT("object");

    private final String value;

    ParamType(String value) {
        this.value = value;
    }

    /**
     * 获取类型值
     *
     * @return 类型值字符串
     */
    public String getValue() {
        return value;
    }

    /**
     * 从字符串值创建ParamType
     *
     * @param value 字符串值
     * @return ParamType枚举值
     */
    public static ParamType fromValue(String value) {
        for (ParamType type : ParamType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ParamType: " + value);
    }
}