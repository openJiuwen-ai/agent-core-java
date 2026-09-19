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

/**
 * Public class TeamWorkspaceConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamWorkspaceConfig {
    @Builder.Default
    private boolean isEnabled = false;
    private String rootPath;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @param "trajectories" "trajectories"
     * @since 0.1.7
     */
    private List<String> artifactDirs =
        new ArrayList<>(List.of("artifacts/code", "artifacts/docs", "artifacts/reports", "trajectories"));
    @Builder.Default
    private boolean isVersionControl = true;
    @Builder.Default
    private ConflictStrategy conflictStrategy = ConflictStrategy.LOCK;
    private String remoteUrl;

    /**
     * TeamWorkspaceConfigBuilder.
     * 
     * @since 0.1.7
     */
    public static class TeamWorkspaceConfigBuilder {
        /**
         * versionControl.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public TeamWorkspaceConfigBuilder versionControl(boolean value) {
            return this.isVersionControl(value);
        }
    }
}
