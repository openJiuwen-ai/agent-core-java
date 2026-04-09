  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.common.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Part data model - represents a content part within an artifact.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Part {
    private String type;
    private String content;
    private Map<String, Object> metadata;
}
