/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One designer graph node.
 *
 * <p>{@code type} is only the modality. Narrative roles such as
 * {@code character} or {@code clip} live in {@code config.pipeline}.</p>
 *
 * @param id node id
 * @param type modality: text, table, image, video, or audio
 * @param label display label
 * @param config node configuration; unknown keys are preserved
 * @param layout canvas layout
 * @param outputRef current output, when the node has produced one
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerNode(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("label") String label,
        @JsonProperty("config") Map<String, Object> config,
        @JsonProperty("layout") DesignerLayout layout,
        @JsonProperty("output_ref") DesignerAssetRef outputRef) {

    public DesignerNode {
        config = config == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(config));
        layout = layout == null ? DesignerLayout.DEFAULT : layout;
    }
}
