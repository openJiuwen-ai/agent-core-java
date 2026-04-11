/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.McpToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * MCP tool card with server identification.
 * <p>
 * Mirrors Python's {@code McpToolCard} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class McpToolCard extends ToolCard {

    /** Server name this tool belongs to. */
    @JsonProperty("server_name")
    private String serverName;

    /** Server identifier. */
    @Builder.Default
    @JsonProperty("server_id")
    private String serverId = "";

    @Override
    public McpToolInfo toolInfo() {
        return McpToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getInputParams())
                .serverName(serverName)
                .build();
    }
}
