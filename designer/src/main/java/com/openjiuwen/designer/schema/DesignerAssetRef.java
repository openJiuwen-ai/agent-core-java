/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reference to a produced or uploaded asset.
 *
 * @param kind asset family: text, table, image, video, or audio
 * @param uri asset URI, normally a {@code file:} URI
 * @param mimeType optional MIME type
 * @param label optional display label
 * @since 2026-11-06
 */
public record DesignerAssetRef(
        @JsonProperty("kind") String kind,
        @JsonProperty("uri") String uri,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("label") String label) {
}
