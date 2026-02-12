package com.openjiuwen.core.common.schema;

/**
 * 参数类型枚举
 * 
 * <p>定义参数支持的数据类型。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
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
     * 获取类型的字符串值
     * 
     * @return 类型字符串
     */
    public String getValue() {
        return value;
    }
    
    /**
     * 从字符串值解析为枚举
     * 
     * @param value 字符串值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果值不合法
     */
    public static ParamType fromValue(String value) {
        for (ParamType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ParamType: " + value);
    }
}

