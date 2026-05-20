/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools.database;

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
 * Public class TaskRecord used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TaskRecord {
    private String taskId;
    private String teamName;
    private String title;
    private String content;
    private String status;
    private String assignee;
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();
    private long updatedAt;
}
