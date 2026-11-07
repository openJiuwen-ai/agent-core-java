/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One archived node output version.
 *
 * @param ts archive time in epoch milliseconds
 * @param text archived text, when present
 * @param outputRef archived asset reference, when present
 * @since 2026-11-06
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DesignerNodeHistory(
        @JsonProperty("ts") long ts,
        @JsonProperty("text") String text,
        @JsonProperty("output_ref") DesignerAssetRef outputRef) {
}
