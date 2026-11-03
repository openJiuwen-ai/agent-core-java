/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */

package com.openjiuwen.agentevolving.evaluator.evaluator_pipeline;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * CLI-style overrides used to build evaluator pipeline config.
 * <p>
 * Mirrors Python's {@code BuildConfigArgs} in
 * {@code openjiuwen/agent_evolving/evaluator/evaluator_pipeline/config.py}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildConfigArgs {

    private String tasksDir = "tasks";
    private String apiKey;
    private String apiBase;
    private String modelName = "glm-5";
    private boolean evolutionMode = false;
    private int maxIterations = 5;
    private List<String> taskIds;
    private String resultsDir;
    private String workspaceDir = "/workspace";
    private int evolutionWaitTime = 60;
    private int agentTimeout = 880;
    private String skillPersistenceDir = "~/.jiuwenswarm/agent/workspace/skills";
}
