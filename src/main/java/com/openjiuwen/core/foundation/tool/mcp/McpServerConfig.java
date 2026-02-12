// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.foundation.tool.mcp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP服务器配置
 * 
 * <p>定义MCP服务器的连接参数和认证信息。
 *
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class McpServerConfig {
    
    private String serverId;
    private String serverName;
    private String serverPath;
    private String clientType;
    private Map<String, Object> params;
    private Map<String, String> authHeaders;
    private Map<String, String> authQueryParams;
    
    /**
     * 默认构造函数
     */
    public McpServerConfig() {
        this.serverId = UUID.randomUUID().toString();
        this.serverName = "";
        this.serverPath = "";
        this.clientType = "sse";
        this.params = new HashMap<>();
        this.authHeaders = new HashMap<>();
        this.authQueryParams = new HashMap<>();
    }
    
    /**
     * 全参数构造函数
     */
    private McpServerConfig(
            String serverId,
            String serverName,
            String serverPath,
            String clientType,
            Map<String, Object> params,
            Map<String, String> authHeaders,
            Map<String, String> authQueryParams) {
        this.serverId = serverId;
        this.serverName = serverName;
        this.serverPath = serverPath;
        this.clientType = clientType;
        this.params = params != null ? params : new HashMap<>();
        this.authHeaders = authHeaders != null ? authHeaders : new HashMap<>();
        this.authQueryParams = authQueryParams != null ? authQueryParams : new HashMap<>();
    }
    
    // Getters and Setters
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public String getServerName() {
        return serverName;
    }
    
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    
    public String getServerPath() {
        return serverPath;
    }
    
    public void setServerPath(String serverPath) {
        this.serverPath = serverPath;
    }
    
    public String getClientType() {
        return clientType;
    }
    
    public void setClientType(String clientType) {
        this.clientType = clientType;
    }
    
    public Map<String, Object> getParams() {
        return new HashMap<>(params);
    }
    
    public void setParams(Map<String, Object> params) {
        this.params = params != null ? new HashMap<>(params) : new HashMap<>();
    }
    
    public Map<String, String> getAuthHeaders() {
        return new HashMap<>(authHeaders);
    }
    
    public void setAuthHeaders(Map<String, String> authHeaders) {
        this.authHeaders = authHeaders != null ? new HashMap<>(authHeaders) : new HashMap<>();
    }
    
    public Map<String, String> getAuthQueryParams() {
        return new HashMap<>(authQueryParams);
    }
    
    public void setAuthQueryParams(Map<String, String> authQueryParams) {
        this.authQueryParams = authQueryParams != null ? new HashMap<>(authQueryParams) : new HashMap<>();
    }
    
    /**
     * 静态builder工厂方法
     * 
     * @return Builder实例
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder类
     */
    public static class Builder {
        private String serverId;
        private String serverName;
        private String serverPath;
        private String clientType = "sse";
        private Map<String, Object> params = new HashMap<>();
        private Map<String, String> authHeaders = new HashMap<>();
        private Map<String, String> authQueryParams = new HashMap<>();
        
        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }
        
        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }
        
        public Builder serverPath(String serverPath) {
            this.serverPath = serverPath;
            return this;
        }
        
        public Builder clientType(String clientType) {
            this.clientType = clientType;
            return this;
        }
        
        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }
        
        public Builder authHeaders(Map<String, String> authHeaders) {
            this.authHeaders = authHeaders;
            return this;
        }
        
        public Builder authQueryParams(Map<String, String> authQueryParams) {
            this.authQueryParams = authQueryParams;
            return this;
        }
        
        public McpServerConfig build() {
            return new McpServerConfig(
                serverId != null ? serverId : UUID.randomUUID().toString(),
                serverName,
                serverPath,
                clientType,
                params,
                authHeaders,
                authQueryParams
            );
        }
    }
}

