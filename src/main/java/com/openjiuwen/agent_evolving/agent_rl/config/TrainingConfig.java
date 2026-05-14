/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Training configuration for RL training.
 * <p>
 * Mirrors Python's {@code TrainingConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class TrainingConfig {

    private String projectName = "OpenJiuwenAgentRL";
    private String experimentName = "grpo_experiment";
    private String modelPath;
    private String visibleDevice = "0,1,2,3";
    private List<String> logger = new ArrayList<>(List.of("tensorboard"));

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { this.experimentName = experimentName; }
    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
    public String getVisibleDevice() { return visibleDevice; }
    public void setVisibleDevice(String visibleDevice) { this.visibleDevice = visibleDevice; }
    public List<String> getLogger() { return logger; }
    public void setLogger(List<String> logger) { this.logger = logger != null ? new ArrayList<>(logger) : new ArrayList<>(); }
}
