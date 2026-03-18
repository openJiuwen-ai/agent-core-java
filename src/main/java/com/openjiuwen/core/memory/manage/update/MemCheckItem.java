/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.memory.manage.update;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a single memory check result item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemCheckItem {
    private String infoId;
    private String infoText;
    private CheckResult result;
    @Builder.Default
    private Map<String, String> relatedInfos = new LinkedHashMap<>();
}
