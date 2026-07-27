/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.cwd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mutable working-directory state for the current execution context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CwdState {
    private String cwd;

    private String originalCwd;

    private String projectRoot;

    private String workspace;

    private String teamWorkspace;
}
