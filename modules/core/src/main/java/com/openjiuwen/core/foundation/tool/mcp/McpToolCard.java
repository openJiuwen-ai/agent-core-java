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
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpToolCard extends ToolCard {
    @JsonProperty("server_name")
    private String serverName;

    /** Server identifier. */
    @JsonProperty("server_id")
    private String serverId = "";

    /**
     * toolInfo.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public McpToolInfo toolInfo() {
        return McpToolInfo.builder().name(getName()).description(getDescription()).parameters(getInputParams())
                .serverName(serverName).build();
    }

    /**
     * getServerName.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * setServerName.
     * 
     * @param serverName serverName
     * @since 0.1.7
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * getServerId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * setServerId.
     * 
     * @param serverId serverId
     * @since 0.1.7
     */
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static class Builder extends ToolCard.Builder {
        private String serverName;
        private String serverId = "";

        /**
         * serverName.
         * 
         * @param serverName serverName
         * @return the result
         * @since 0.1.7
         */
        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        /**
         * serverId.
         * 
         * @param serverId serverId
         * @return the result
         * @since 0.1.7
         */
        public Builder serverId(String serverId) {
            this.serverId = serverId;
            return this;
        }

        /**
         * id.
         * 
         * @param id id
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder id(String id) {
            super.id(id);
            return this;
        }

        /**
         * name.
         * 
         * @param name name
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder name(String name) {
            super.name(name);
            return this;
        }

        /**
         * description.
         * 
         * @param description description
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder description(String description) {
            super.description(description);
            return this;
        }

        /**
         * inputParams.
         * 
         * @param inputParams inputParams
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder inputParams(Map<String, Object> inputParams) {
            super.inputParams(inputParams);
            return this;
        }

        /**
         * properties.
         * 
         * @param properties properties
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Builder properties(Map<String, Object> properties) {
            super.properties(properties);
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
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
