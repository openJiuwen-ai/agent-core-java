/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Serializable workspace specification.
 * Mirrors Python WorkspaceSpec.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceSpec {
    @Builder.Default
    private String rootPath = "./";
    @Builder.Default
    private String language = "cn";
    @Builder.Default
    private boolean stableBase = false;
}
