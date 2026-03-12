/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller.legacy.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Legacy reasoner configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReasonerConfig {

    @Builder.Default
    private List<Map<String, Object>> intentDetectionTemplate = new ArrayList<>();

    @Builder.Default
    private String defaultClass = "default";

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
