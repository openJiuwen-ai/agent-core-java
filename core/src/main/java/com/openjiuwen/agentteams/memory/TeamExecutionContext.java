/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.memory;

import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.harness.workspace.Workspace;

/**
 * Runtime resources passed to an optional team memory integration.
 *
 * @param workspace team member workspace
 * @param sysOperation workspace system operation
 * @param defaultTeamMemoryDir default shared-memory directory
 * @param backend team data backend
 * @param extractionModel model available for memory extraction
 * @since 0.1.7
 */
public record TeamExecutionContext(Workspace workspace, Object sysOperation, String defaultTeamMemoryDir,
        TeamBackend backend, Model extractionModel) {
}
