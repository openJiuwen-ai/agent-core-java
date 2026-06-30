/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class VerifyReportArtifact used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class VerifyReportArtifact {
    @Builder.Default
    private Map<String, Object> ciResult = new LinkedHashMap<>();
    @Builder.Default
    private String fixErrors = "";
    @Builder.Default
    private boolean isReverted = false;
    @Builder.Default
    private String error = "";

}
