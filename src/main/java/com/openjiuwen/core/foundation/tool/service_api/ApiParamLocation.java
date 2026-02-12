package com.openjiuwen.core.foundation.tool.service_api;

/**
 * API参数位置枚举（基于OpenAPI规范）
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public enum ApiParamLocation {
    /**
     * Query参数（URL中，如 ?key=value）
     */
    QUERY("query"),
    
    /**
     * Path参数（URL中，如 /users/{id}）
     */
    PATH("path"),
    
    /**
     * Body参数（请求体）
     */
    BODY("body"),
    
    /**
     * Header参数（HTTP头）
     */
    HEADER("header");
    
    private final String value;
    
    ApiParamLocation(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * 从字符串值获取枚举
     * 
     * @param value 字符串值
     * @return 对应的枚举值
     */
    public static ApiParamLocation fromValue(String value) {
        for (ApiParamLocation location : values()) {
            if (location.value.equalsIgnoreCase(value)) {
                return location;
            }
        }
        throw new IllegalArgumentException("Unknown API param location: " + value);
    }
}

