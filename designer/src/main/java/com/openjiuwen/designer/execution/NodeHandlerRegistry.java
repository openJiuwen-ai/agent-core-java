/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.execution;

import com.openjiuwen.designer.schema.DesignerNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves node handlers by delegate, pipeline, role, then modality.
 *
 * @since 2026-11-06
 */
public final class NodeHandlerRegistry {

    private final Map<String, NodeHandler> handlers = new LinkedHashMap<>();

    /**
     * Register a handler.
     *
     * @param key pipeline, role, modality, or {@code user_reference}
     * @param handler handler
     * @return this registry
     */
    public NodeHandlerRegistry register(String key, NodeHandler handler) {
        handlers.put(key, handler);
        return this;
    }

    /**
     * Resolve the handler for one node.
     *
     * @param node node
     * @return handler when present
     */
    public Optional<NodeHandler> resolve(DesignerNode node) {
        for (String key : keys(node)) {
            NodeHandler handler = handlers.get(key);
            if (handler != null) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    private static String[] keys(DesignerNode node) {
        Object userReference = node.config().get("user_reference");
        if (Boolean.TRUE.equals(userReference)) {
            return new String[]{"user_reference", node.type()};
        }
        String pipeline = text(node.config().get("pipeline"));
        String role = text(node.config().get("role"));
        String type = text(node.type());
        return new String[]{pipeline, role, type};
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip();
    }
}
