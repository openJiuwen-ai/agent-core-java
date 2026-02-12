// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.UUID;

/**
 * MCP工具卡片
 * 
 * <p>扩展ToolCard，添加MCP服务器相关的配置信息。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class McpToolCard extends ToolCard {
    
    private String serverName;
    private String serverId;
    
    /**
     * 构造函数
     */
    public McpToolCard(
            String name,
            String description,
            Object inputParams) {
        super(name, description, inputParams);
        this.serverName = "";
        this.serverId = "";
    }
    
    /**
     * Builder构造函数（用于Builder模式）
     */
    private McpToolCard(
            String id,
            String name,
            String description,
            Object inputParams,
            String serverName,
            String serverId) {
        super(id, name, description, inputParams);
        this.serverName = serverName;
        this.serverId = serverId;
    }
    
    // Getters and Setters
    
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    /**
     * 生成ToolInfo
     */
    public ToolInfo toolInfo() {
        return new ToolInfo(
            "function",
            this.getName(),
            this.getDescription(),
            this.getInputParams()
        );
    }
    
    /**
     * 静态builder工厂方法
     * 
     * @return Builder实例
     */
    public static Builder mcpBuilder() {
        return new Builder();
    }
    
    /**
     * Builder类
     */
    public static class Builder {
        private String id;
        private String name;
        private String description;
        private Object inputParams;
        private String serverName;
        private String serverId;
        
        public Builder id(String id) {
            this.id = id != null ? id : UUID.randomUUID().toString();
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
        
        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }
        
        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }
        
        public McpToolCard build() {
            return new McpToolCard(
                id,
                name,
                description,
                inputParams,
                serverName,
                serverId
            );
        }
    }
}

