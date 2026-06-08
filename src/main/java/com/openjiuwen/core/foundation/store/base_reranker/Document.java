/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.base_reranker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Document data model.
 * <p>
 * Mirrors Python's {@code Document} in
 * {@code openjiuwen/core/foundation/store/base_reranker.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Document {

    /** Document ID. */
    @JsonProperty("id_")
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** Document text content. */
    private String text;

    /** Document metadata. */
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
}
