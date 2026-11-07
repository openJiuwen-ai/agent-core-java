/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One data edge between designer nodes.
 *
 * @param id edge id
 * @param source source node id
 * @param target target node id
 * @param kind edge kind; only {@code data} is supported
 * @param label optional alignment label
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerEdge(
        @JsonProperty("id") String id,
        @JsonProperty("source") String source,
        @JsonProperty("target") String target,
        @JsonProperty("kind") String kind,
        @JsonProperty("label") String label) {
}
