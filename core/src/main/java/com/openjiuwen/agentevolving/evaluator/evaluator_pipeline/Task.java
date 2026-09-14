/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluation task definition.
 * <p>
 * Mirrors Python's {@code Task} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/models.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    private String taskId;
    private String instruction;
    private Map<String, Object> metadata = new LinkedHashMap<>();
    private Map<String, Object> environmentSpec = new LinkedHashMap<>();
    private boolean hasSkills = false;
    private List<String> skills = new ArrayList<>();
}
