/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Document data model for reranking.
 * <p>
 * Mirrors Python's {@code Document} model from
 * <code>foundation/store/base_reranker.py</code>.
 */
public class Document {

    private final String id;
    private final String text;
    private final Map<String, Object> metadata;

    public Document(String text) {
        this(UUID.randomUUID().toString(), text, new HashMap<>());
    }

    public Document(String id, String text, Map<String, Object> metadata) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.text = text;
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public String toString() {
        return text;
    }
}
