/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Construction parameters for {@link TeamMemoryManager}.
 * <p>
 * Mirrors Python's {@code TeamMemoryManagerParams} dataclass from
 * {@code core/memory/team/manager_params.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemoryManagerParams {

    private String memberName;
    private String teamName;
    private String role; // "leader" | "teammate"
    private String lifecycle; // "temporary" | "persistent"
    private String scenario; // "general" | "coding"
    private Object embeddingConfig;
    private Object workspace;
    private Object sysOperation;
    private String teamMemoryDir;
    private String language; // "cn" | "en"
    private String promptMode; // "proactive" | "passive"
    private boolean enableAutoExtract;
    private String readOnlySourceWorkspace;

    @Builder.Default
    private Object db = null;

    @Builder.Default
    private Object taskManager = null;

    @Builder.Default
    private Object extractionModel = null;

    @Builder.Default
    private double timezoneOffsetHours = 8.0;
}
