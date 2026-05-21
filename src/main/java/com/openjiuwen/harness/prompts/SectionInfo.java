/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight snapshot of a single prompt section.
 * <p>
 * Mirrors Python's {@code SectionInfo} dataclass from
 * {@code harness/prompts/report.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionInfo {

    private String name;
    private int priority;
    private int charCount;
}
