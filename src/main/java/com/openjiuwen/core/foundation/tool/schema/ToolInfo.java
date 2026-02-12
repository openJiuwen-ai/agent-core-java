package com.openjiuwen.core.foundation.tool.schema;

import java.util.Map;

/**
 * 工具信息记录
 * 
 * <p>包含工具的元信息：类型、名称、描述和参数schema。
 * 
 * @param type 工具类型，默认为"function"
 * @param name 工具名称
 * @param description 工具描述
 * @param parameters 参数schema（JSON Schema格式）
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public record ToolInfo(
    String type,
    String name,
    String description,
    Object parameters
) {
    /**
     * 默认构造器（type默认为"function"）
     */
    public ToolInfo {
        if (type == null || type.isEmpty()) {
            type = "function";
        }
        if (name == null) {
            name = "";
        }
        if (description == null) {
            description = "";
        }
        if (parameters == null) {
            parameters = Map.of();
        }
    }
    
    /**
     * 简化构造器（type自动设为"function"）
     */
    public ToolInfo(String name, String description, Object parameters) {
        this("function", name, description, parameters);
    }
    
    /**
     * 创建一个新的Builder实例。
     *
     * @return 新的Builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * ToolInfo的构建器。
     */
    public static class Builder {
        private String type = "function";
        private String name = "";
        private String description = "";
        private Object parameters = Map.of();
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder parameters(Object parameters) {
            this.parameters = parameters;
            return this;
        }
        
        public ToolInfo build() {
            return new ToolInfo(type, name, description, parameters);
        }
    }
}



