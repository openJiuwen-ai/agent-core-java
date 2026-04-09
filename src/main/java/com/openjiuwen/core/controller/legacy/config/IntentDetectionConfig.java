/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.controller.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Config of Intent Detection Component.
 * Mirrors Python's {@code IntentDetectionConfig}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentDetectionConfig {

    @Builder.Default
    private String categoryInfo = "";

    @Builder.Default
    private List<String> categoryList = new ArrayList<>();

    @Builder.Default
    private List<Map<String, Object>> intentDetectionTemplate = new ArrayList<>();

    @Builder.Default
    private String userPrompt = "";

    @Builder.Default
    private int chatHistoryMaxTurn = 100;

    @Builder.Default
    private String defaultClass = "分类0";

    @Builder.Default
    private boolean enableHistory = false;

    @Builder.Default
    private boolean enableInput = true;

    @Builder.Default
    private List<String> exampleContent = new ArrayList<>();
}
