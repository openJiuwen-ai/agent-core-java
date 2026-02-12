package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.Map;

/**
 * 工具配置卡片
 * 
 * <p>继承BaseCard，添加工具特有的输入参数schema。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class ToolCard extends BaseCard {
    private Object inputParams;
    
    /**
     * 默认构造器
     */
    public ToolCard() {
        super();
        this.inputParams = Map.of();
    }
    
    /**
     * 构造器（指定name和description）
     * 
     * @param name 工具名称
     * @param description 工具描述
     */
    public ToolCard(String name, String description) {
        super(name, description);
        this.inputParams = Map.of();
    }
    
    /**
     * 构造器（指定name、description和inputParams）
     * 
     * @param name 工具名称
     * @param description 工具描述
     * @param inputParams 输入参数schema
     */
    public ToolCard(String name, String description, Object inputParams) {
        super(name, description);
        this.inputParams = inputParams != null ? inputParams : Map.of();
    }
    
    /**
     * 完整构造器
     * 
     * @param id 唯一标识符
     * @param name 工具名称
     * @param description 工具描述
     * @param inputParams 输入参数schema
     */
    public ToolCard(String id, String name, String description, Object inputParams) {
        super(id, name, description, inputParams);
        this.inputParams = inputParams != null ? inputParams : Map.of();
    }
    
    /**
     * 获取输入参数schema
     * 
     * @return 输入参数schema（Map或Pydantic类型）
     */
    public Object getInputParams() {
        return inputParams;
    }
    
    /**
     * 设置输入参数schema
     * 
     * @param inputParams 输入参数schema
     */
    public void setInputParams(Object inputParams) {
        this.inputParams = inputParams;
    }
    
    /**
     * 生成ToolInfo
     * 
     * @return 工具信息对象
     */
    @Override
    public ToolInfo toolInfo() {
        return new ToolInfo(getName(), getDescription(), inputParams);
    }
    
    /**
     * 创建Builder
     * 
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Tool Builder类
     */
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Object inputParams;
        
        public Builder id(String id) {
            this.id = id;
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
        
        public Builder inputParams(Object inputParams) {
            this.inputParams = inputParams;
            return this;
        }
        
        public ToolCard build() {
            return new ToolCard(id, name, description, inputParams);
        }
    }
}

