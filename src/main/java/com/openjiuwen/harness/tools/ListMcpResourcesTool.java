/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Public class ListMcpResourcesTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class ListMcpResourcesTool {
    private final McpResourceService service;

    /**
     * Auto-generated for codecheck compliance.
     */
    public ListMcpResourcesTool(McpResourceService service) {
        this.service = service;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput invoke(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return ToolOutput.builder().success(false).error("server_id is required").build();
        }
        try {
            List<?> resources = service.listResources(serverId);
            List<McpResourceDescriptor> mapped = new ArrayList<>();
            if (resources != null) {
                for (Object resource : resources) {
                    mapped.add(new McpResourceDescriptor(
                            value(resource, "getUri", "uri"),
                            value(resource, "getName", "name"),
                            nullable(resource, "getMimeType", "mimeType"),
                            nullable(resource, "getDescription", "description")
                    ));
                }
            }
            return ToolOutput.builder().success(true).data(mapped).build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    static String value(Object object, String getter, String fieldName) {
        String value = nullable(object, getter, fieldName);
        return value != null ? value : String.valueOf(object);
    }

    static String nullable(Object object, String getter, String fieldName) {
        if (object instanceof java.util.Map<?, ?> map) {
            Object value = map.get(fieldName);
            return value != null ? String.valueOf(value) : null;
        }
        try {
            Method method = object.getClass().getMethod(getter);
            Object value = method.invoke(object);
            return value != null ? String.valueOf(value) : null;
        } catch (Exception ignored) {
            try {
                var field = object.getClass().getField(fieldName);
                Object value = field.get(object);
                return value != null ? String.valueOf(value) : null;
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }
}
