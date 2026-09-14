/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.workflow.dl_transformer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow node model.
 * <p>
 * Mirrors Python's {@code Node} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/workflow/dl_transformer/models.py}.
 */
public class Node {
    private String id;
    private String type;
    private Map<String, Object> meta;
    private DataConfig data;

    public Node(String id, String type) {
        this(id, type, new LinkedHashMap<>());
    }

    public Node(String id, String type, Map<String, Object> meta) {
        this.id = id;
        this.type = type;
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
        this.data = new DataConfig();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public void setMeta(Map<String, Object> meta) {
        this.meta = meta != null ? new LinkedHashMap<>(meta) : new LinkedHashMap<>();
    }

    public DataConfig getData() {
        return data;
    }
}
