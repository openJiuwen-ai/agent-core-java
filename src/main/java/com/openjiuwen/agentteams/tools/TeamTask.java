/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.tools;

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
 * Public class TeamTask used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class TeamTask {
    private String taskId;
    private String teamName;
    private String title;
    private String content;
    @Builder.Default
    private String status = "pending";
    private String assignee;
    private long updatedAt;
    @Builder.Default
    private List<String> dependencies = new ArrayList<>();
}
