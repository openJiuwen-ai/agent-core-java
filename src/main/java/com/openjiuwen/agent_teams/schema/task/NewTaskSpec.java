/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.task;

/**
 * Task creation input used by dependency graph mutations.
 *
 * <p>Mirrors Python's {@code NewTaskSpec} in
 * {@code openjiuwen.agent_teams.schema.task}.</p>
 */
public record NewTaskSpec(String taskId, String title, String content, String initialStatus) {
}
