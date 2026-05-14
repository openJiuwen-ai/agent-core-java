/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.task;

import java.util.ArrayList;
import java.util.List;

/**
 * Full task detail.
 *
 * <p>Mirrors Python's {@code TaskDetail} in
 * {@code openjiuwen.agent_teams.schema.task}.
 */
public class TaskDetail extends TaskSummary {

    private final String content;
    private final List<String> blocks;
    private final long updatedAt;

    public TaskDetail(
            String taskId,
            String title,
            String content,
            TaskStatus status,
            String assignee,
            List<String> blockedBy,
            List<String> blocks,
            long updatedAt
    ) {
        super(taskId, title, status, assignee, blockedBy);
        this.content = content;
        this.blocks = blocks != null ? new ArrayList<>(blocks) : new ArrayList<>();
        this.updatedAt = updatedAt;
    }

    public String getContent() {
        return content;
    }

    public List<String> getBlocks() {
        return new ArrayList<>(blocks);
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
