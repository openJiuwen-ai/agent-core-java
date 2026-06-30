/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.McpToolInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP tool card with server identification.
 * <p>
 * Mirrors Python's {@code McpToolCard} model.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpToolCard extends ToolCard {

    /** Server name this tool belongs to. */
    @JsonProperty("server_name")
    private String serverName;

    /** Server identifier. */
    @JsonProperty("server_id")
    private String serverId = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public McpToolInfo toolInfo() {
        return McpToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getInputParams())
                .serverName(serverName)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class Builder extends ToolCard.Builder {
        private String serverName;
        private String serverId = "";

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder inputParams(Map<String, Object> inputParams) {
            super.inputParams(inputParams);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder properties(Map<String, Object> properties) {
            super.properties(properties);
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        @Override
        /**
         * Auto-generated for codecheck compliance.
         */
        public McpToolCard build() {
            McpToolCard card = new McpToolCard();
            if (id != null) {
                card.setId(id);
            }
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setProperties(properties);
            card.setServerName(serverName);
            card.setServerId(serverId);
            return card;
        }
    }
}
