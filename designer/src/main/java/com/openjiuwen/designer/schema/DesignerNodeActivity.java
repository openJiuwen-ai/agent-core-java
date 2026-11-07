/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Latest activity reported for one designer node.
 *
 * @param kind activity kind: thinking, tool_call, or stage
 * @param text human-readable activity text
 * @param tool optional tool name
 * @param at epoch milliseconds when the activity was reported
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerNodeActivity(
        @JsonProperty("kind") String kind,
        @JsonProperty("text") String text,
        @JsonProperty("tool") String tool,
        @JsonProperty("at") Long at) {
}
