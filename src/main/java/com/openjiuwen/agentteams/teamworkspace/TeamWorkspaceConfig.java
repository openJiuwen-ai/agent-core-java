/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.teamworkspace;

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
 * Public class TeamWorkspaceConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TeamWorkspaceConfig {
    @Builder.Default
    private boolean isEnabled = false;
    private String rootPath;
    @Builder.Default
    private List<String> artifactDirs = new ArrayList<>(List.of(
            "artifacts/code",
            "artifacts/docs",
            "artifacts/reports",
            "trajectories"
    ));
    @Builder.Default
    private boolean isVersionControl = true;
    @Builder.Default
    private ConflictStrategy conflictStrategy = ConflictStrategy.LOCK;
    private String remoteUrl;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class TeamWorkspaceConfigBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public TeamWorkspaceConfigBuilder versionControl(boolean value) {
            return this.isVersionControl(value);
        }
    }
}
