/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.schema;

import com.openjiuwen.autoharness.stages.BaseStage;
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
 * Public class StageSpec used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class StageSpec {
    private String name;
    private Class<? extends BaseStage> stageCls;
    @Builder.Default
    private String scope = "session";
    @Builder.Default
    private List<String> consumes = new ArrayList<>();
    @Builder.Default
    private List<String> produces = new ArrayList<>();
    @Builder.Default
    private String description = "";
}
