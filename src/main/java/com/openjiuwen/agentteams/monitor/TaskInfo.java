/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Task status projection for team monitor output.
 *
 * @since 1.0
 */
public class TaskInfo {
    private String taskId;
    private String teamId;
    private String title;
    private String content;
    private String status;
    private String assignee;
    private Long updatedAt;
}
