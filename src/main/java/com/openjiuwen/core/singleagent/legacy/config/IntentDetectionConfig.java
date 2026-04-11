/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy intent detection configuration.
 *
 * <p>Mirrors Python's {@code IntentDetectionConfig} in {@code single_agent/legacy/config.py}.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntentDetectionConfig {

    @Builder.Default
    private List<Map<String, String>> intentDetectionTemplate = new ArrayList<>();

    @Builder.Default
    private String defaultClass = "分类1";

    @Builder.Default
    private boolean enableInput = true;

    @Builder.Default
    private boolean enableHistory = false;

    @Builder.Default
    private int chatHistoryMaxTurn = 5;

    @Builder.Default
    private List<String> categoryList = new ArrayList<>();

    @Builder.Default
    private String userPrompt = "";

    @Builder.Default
    private List<String> exampleContent = new ArrayList<>();
}
