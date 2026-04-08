/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.common.schema.Part;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Artifact data model - represents a result artifact within an AgentResult.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Artifact {
    private String artifactId;
    private String name;
    private String description;
    @Builder.Default
    private List<Part> parts = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
