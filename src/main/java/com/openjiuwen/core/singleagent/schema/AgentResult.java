/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

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
    private List<Artifact> artifacts = new ArrayList<>();
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
