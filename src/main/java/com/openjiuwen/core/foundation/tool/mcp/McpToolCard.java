/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.McpToolInfo;

import java.util.Map;

/**
 * MCP tool card with server identification.
 *
 * <p>Mirrors Python's {@code McpToolCard} in
 * {@code openjiuwen/core/foundation/tool/mcp/base.py}.</p>
 *
 * <p>Supports Python's {@code OpenApiClient.list_tools()} and
 * {@code OpenApiClient.get_tool_info()} in
 * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
 */
public class McpToolCard extends ToolCard {

    private String serverName;
    private String serverId = "";

    public McpToolCard() {
        super();
    }

    public McpToolCard(String name, String serverName, String description, Map<String, Object> inputParams) {
        this(null, name, description, inputParams, serverName, "");
    }

    public McpToolCard(String id, String name, String description, Map<String, Object> inputParams,
                       String serverName, String serverId) {
        super(id, name, description, inputParams);
        this.serverName = serverName;
        this.serverId = serverId != null ? serverId : "";
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonProperty("server_name")
    public String getServerName() {
        return serverName;
    }

    @JsonProperty("server_name")
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    @JsonProperty("server_id")
    public String getServerId() {
        return serverId;
    }

    @JsonProperty("server_id")
    public void setServerId(String serverId) {
        this.serverId = serverId != null ? serverId : "";
    }

    @Override
    public McpToolInfo toolInfo() {
        return McpToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getInputParams())
                .serverName(serverName)
                .build();
    }

    public McpToolInfo tool_info() {
        return toolInfo();
    }

    /**
     * Builder for MCP tool metadata.
     *
     * <p>Mirrors Python's {@code McpToolCard} construction in
     * {@code openjiuwen/core/foundation/tool/mcp/base.py}.</p>
     *
     * <p>Supports Python's {@code OpenApiClient} cards in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     */
    public static class Builder extends ToolCard.Builder {
        private String id;
        private String name = "";
        private String description = "";
        private Map<String, Object> inputParams;
        private Map<String, Object> properties;
        private String serverName;
        private String serverId = "";

        protected Builder() {
            super();
        }

        @Override
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        @Override
        public Builder name(String name) {
            this.name = name != null ? name : "";
            return this;
        }

        @Override
        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        @Override
        public Builder inputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        @Override
        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder serverId(String serverId) {
            this.serverId = serverId != null ? serverId : "";
            return this;
        }

        @Override
        public McpToolCard build() {
            McpToolCard card = new McpToolCard(id, name, description, inputParams, serverName, serverId);
            card.setProperties(properties);
            return card;
        }
    }
}
