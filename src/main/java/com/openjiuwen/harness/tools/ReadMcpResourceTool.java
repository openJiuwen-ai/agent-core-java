/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class ReadMcpResourceTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ReadMcpResourceTool {
    private final McpResourceService service;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ReadMcpResourceTool(McpResourceService service) {
        this.service = service;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput invoke(String serverId, String uri) {
        if (serverId == null || serverId.isBlank()) {
            return ToolOutput.builder().success(false).error("server_id is required").build();
        }
        if (uri == null || uri.isBlank()) {
            return ToolOutput.builder().success(false).error("uri is required").build();
        }
        try {
            List<?> contents = service.readResource(serverId, uri);
            List<McpResourceContent> mapped = new ArrayList<>();
            if (contents != null) {
                for (Object content : contents) {
                    mapped.add(new McpResourceContent(
                            ListMcpResourcesTool.value(content, "getUri", "uri"),
                            ListMcpResourcesTool.nullable(content, "getMimeType", "mimeType"),
                            ListMcpResourcesTool.nullable(content, "getText", "text")
                    ));
                }
            }
            return ToolOutput.builder().success(true).data(mapped).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
