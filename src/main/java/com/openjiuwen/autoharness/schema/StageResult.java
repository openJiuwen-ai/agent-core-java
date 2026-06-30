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
 * Public class StageResult used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class StageResult {
    @Builder.Default
    private String status = "success";
    @Builder.Default
    private Map<String, Object> artifacts = new LinkedHashMap<>();
    @Builder.Default
    private List<String> messages = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metrics = new LinkedHashMap<>();
    @Builder.Default
    private String error = "";

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isSuccess() {
        return "success".equals(status);
    }
}
