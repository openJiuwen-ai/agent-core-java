/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.contexts;

import com.openjiuwen.autoharness.schema.Experience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class TaskRuntime used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TaskRuntime {
    @Builder.Default
    private List<Experience> related = new ArrayList<>();
    @Builder.Default
    private String wtPath = "";
    private Object editSafetyRail;
    @Builder.Default
    private List<String> preexistingDirtyFiles = new ArrayList<>();
    private Object taskAgent;
    private Object commitAgent;
    private Object taskSession;
    private Object fixAgent;
}
