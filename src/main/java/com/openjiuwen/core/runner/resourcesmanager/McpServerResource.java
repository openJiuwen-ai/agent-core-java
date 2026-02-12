// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.resourcesmanager;

import com.openjiuwen.core.foundation.tool.mcp.McpClient;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import java.time.Instant;
import java.util.List;

/**
 * MCP服务器资源
 * 
 * 对应Python: tool_manager.py - McpServerResource
 * 
 * @dataclass
 * class McpServerResource:
 *     config: McpServerConfig
 *     client: McpClient
 *     tool_ids: list[str]
 *     last_update_time: Any
 *     expiry_time: Optional[float] = None
 */
public class McpServerResource {
    
    private final McpServerConfig config;
    private final McpClient client;
    private final List<String> toolIds;
    private Instant lastUpdateTime;
    private final Double expiryTime;  // 秒
    
    /**
     * 完整构造函数
     */
    public McpServerResource(McpServerConfig config, McpClient client, 
                             List<String> toolIds, Instant lastUpdateTime, Double expiryTime) {
        this.config = config;
        this.client = client;
        this.toolIds = toolIds;
        this.lastUpdateTime = lastUpdateTime;
        this.expiryTime = expiryTime;
    }
    
    /**
     * 获取服务器ID（从config获取）
     */
    public String getServerId() {
        return config != null ? config.getServerId() : null;
    }
    
    /**
     * 获取服务器名称（从config获取）
     */
    public String getServerName() {
        return config != null ? config.getServerName() : null;
    }
    
    public McpClient getClient() {
        return client;
    }
    
    public McpServerConfig getConfig() {
        return config;
    }
    
    public List<String> getToolIds() {
        return toolIds;
    }
    
    public Instant getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public void setLastUpdateTime(Instant lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }
    
    public Double getExpiryTime() {
        return expiryTime;
    }
    
    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        if (expiryTime == null) {
            return false;
        }
        long elapsedSeconds = Instant.now().getEpochSecond() - lastUpdateTime.getEpochSecond();
        return elapsedSeconds >= expiryTime;
    }
}
