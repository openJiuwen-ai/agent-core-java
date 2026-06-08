/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Embedding model configuration.
 * <p>
 * Mirrors Python's {@code EmbeddingConfig} in
 * {@code openjiuwen/core/foundation/store/base_embedding.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmbeddingConfig {

    /** Model name. */
    private String modelName;

    /** API base URL. */
    private String baseUrl;

    /** Optional API key. */
    private String apiKey;
}
