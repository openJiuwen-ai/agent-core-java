/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool;

import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool card — configuration / metadata for a tool.
 * <p>
 * Extends {@link BaseCard} with input parameters and custom properties.
 * Mirrors Python's {@code ToolCard} model.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ToolCard extends BaseCard {

    /** Input parameter schema (JSON Schema format). */
    @Builder.Default
    private Map<String, Object> inputParams = new HashMap<>();

    /** Custom properties map. */
    @Builder.Default
    private Map<String, Object> properties = new HashMap<>();

    /**
     * Build a {@link ToolInfo} descriptor for this tool card.
     *
     * @return a ToolInfo instance with name, description, and parameters
     */
    @Override
    public ToolInfo toolInfo() {
        return ToolInfo.builder()
                .name(getName())
                .description(getDescription())
                .parameters(inputParams)
                .build();
    }
}
