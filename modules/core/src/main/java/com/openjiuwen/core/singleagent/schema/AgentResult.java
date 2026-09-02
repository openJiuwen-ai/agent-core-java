/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.controller.schema.TaskStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent result data model.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    private String taskId;
    private String sessionId;
    private TaskStatus status;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Artifact> artifacts = new ArrayList<>();
    @Builder.Default
    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new HashMap<>();
}
