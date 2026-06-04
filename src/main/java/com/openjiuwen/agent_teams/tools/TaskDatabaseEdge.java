/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

/**
 * Dependency edge used by {@code TaskDao.mutateDependencyGraph}.
 *
 * <p>Mirrors the Python tuple {@code (task_id, depends_on_task_id)} used by
 * {@code openjiuwen.agent_teams.tools.database.task_dao}.</p>
 */
public record TaskDatabaseEdge(String taskId, String dependsOnTaskId) {
}
