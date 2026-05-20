/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class ResearchContext used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class ResearchContext {
    @Builder.Default
    private List<Experience> experiences = new ArrayList<>();
    @Builder.Default
    private Map<String, String> sourceFiles = new LinkedHashMap<>();
    @Builder.Default
    private String gapReport = null;
}
