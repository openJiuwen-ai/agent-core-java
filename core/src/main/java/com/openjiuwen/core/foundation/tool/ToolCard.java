/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool metadata card.
 *
 * <p>Mirrors Python's {@code ToolCard} in
 * {@code openjiuwen/core/foundation/tool/base.py}.</p>
 *
 * <p>Builder compatibility supports Python's {@code OpenApiClient} cards in
 * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
 */
public class ToolCard extends BaseCard {

    private Map<String, Object> inputParams = new LinkedHashMap<>();
    private Map<String, Object> properties = new LinkedHashMap<>();
    private boolean isParallelSafe = true;
    private boolean isStateless;
    private boolean isIdempotent;

    public ToolCard() {
        super();
    }

    public ToolCard(String id, String name, String description) {
        super(id, name, description);
    }

    public ToolCard(String id, String name, String description, Map<String, Object> inputParams) {
        this(id, name, description, inputParams, null);
    }

    public ToolCard(String id, String name, String description,
                    Map<String, Object> inputParams, Map<String, Object> properties) {
        super(id, name, description);
        setInputParams(inputParams);
        setProperties(properties);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, Object> getInputParams() {
        return inputParams;
    }

    public void setInputParams(Map<String, Object> inputParams) {
        // Prefer caller reference when the map is already mutable (Python assigns directly).
        // Copy unmodifiable maps (e.g. Map.of) so getters remain mutable per ToolCard instance.
        if (inputParams == null) {
            this.inputParams = new LinkedHashMap<>();
            return;
        }
        if (inputParams instanceof LinkedHashMap || inputParams instanceof HashMap) {
            this.inputParams = inputParams;
            return;
        }
        this.inputParams = new LinkedHashMap<>(inputParams);
    }

    public Map<String, Object> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Object> properties) {
        if (properties == null) {
            this.properties = new LinkedHashMap<>();
            return;
        }
        if (properties instanceof LinkedHashMap || properties instanceof HashMap) {
            this.properties = properties;
            return;
        }
        this.properties = new LinkedHashMap<>(properties);
    }

    public boolean isParallelSafe() {
        return isParallelSafe;
    }

    public void setParallelSafe(boolean isParallelSafe) {
        this.isParallelSafe = isParallelSafe;
    }

    public boolean isStateless() {
        return isStateless;
    }

    public void setStateless(boolean isStateless) {
        this.isStateless = isStateless;
    }

    public boolean isIdempotent() {
        return isIdempotent;
    }

    public void setIdempotent(boolean isIdempotent) {
        this.isIdempotent = isIdempotent;
    }

    /**
     * Build the tool function-calling descriptor.
     *
     * @return tool information with this card's name, description, and input parameters
     */
    @Override
    public ToolInfo toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(inputParams != null ? inputParams : Map.of())
                .build();
    }

    public ToolInfo tool_info() {
        return toolInfo();
    }

    @Override
    public String toString() {
        return toStr();
    }

    /**
     * Builder kept for compatibility with existing translated Java callers.
     *
     * <p>Mirrors Python's {@code ToolCard} construction in
     * {@code openjiuwen/core/foundation/tool/base.py}.</p>
     *
     * <p>Supports Python's {@code OpenApiClient} card compatibility in
     * {@code openjiuwen/core/foundation/tool/mcp/client/openapi_client.py}.</p>
     */
    public static class Builder {
        private String id;
        private String name = "";
        private String description = "";
        private Map<String, Object> inputParams;
        private Map<String, Object> properties;
        private Boolean isParallelSafe;
        private Boolean isStateless;
        private Boolean isIdempotent;

        protected Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name != null ? name : "";
            return this;
        }

        public Builder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        public Builder inputParams(Map<String, Object> inputParams) {
            this.inputParams = inputParams;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = properties;
            return this;
        }

        public Builder parallelSafe(boolean isParallelSafe) {
            this.isParallelSafe = isParallelSafe;
            return this;
        }

        public Builder stateless(boolean isStateless) {
            this.isStateless = isStateless;
            return this;
        }

        public Builder idempotent(boolean isIdempotent) {
            this.isIdempotent = isIdempotent;
            return this;
        }

        public ToolCard build() {
            ToolCard card = id == null ? new ToolCard() : new ToolCard(id, name, description);
            card.setName(name);
            card.setDescription(description);
            card.setInputParams(inputParams);
            card.setProperties(properties);
            if (isParallelSafe != null) {
                card.setParallelSafe(isParallelSafe);
            }
            if (isStateless != null) {
                card.setStateless(isStateless);
            }
            if (isIdempotent != null) {
                card.setIdempotent(isIdempotent);
            }
            return card;
        }
    }
}
