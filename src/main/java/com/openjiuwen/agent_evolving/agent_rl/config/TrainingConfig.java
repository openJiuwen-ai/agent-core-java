/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Training configuration covering data, model, algorithm, and Verl trainer params.
 * <p>
 * Mirrors Python's {@code TrainingConfig} in
 * {@code openjiuwen.agent_evolving.agent_rl.config.offline_config}.
 */
public class TrainingConfig {

    // --- project / experiment ---
    private String projectName = "OpenJiuwenAgentRL";
    private String experimentName = "grpo_experiment";

    // --- data paths ---
    private String trainDataPath;
    private String valDataPath;
    // aliases for backward compat
    private String trainFiles;
    private String valFiles;

    // --- model ---
    private String modelPath;
    private String savePath;

    // --- algorithm ---
    private String algorithmAdvEstimator = "grpo";
    private boolean algorithmUseKlInReward = false;
    private boolean algorithmFilterGroups = false;
    private boolean algorithmNormAdvByStdInGrpo = true;
    private boolean wholeTrajectory = false;

    // --- training control ---
    private int epochNum = 2;
    private int totalEpochs = 2; // alias
    private int saveFreq = 20;
    private int testFreq = 20;
    private int trainBatchSize = 32;
    private int rolloutConcurrency = 40;

    // --- hardware ---
    private String visibleDevice = "0,1,2,3";
    private int nnodes = 1;
    private int nGpusPerNode = 4;
    private int microBatchSizePerGpu = 4;

    // --- sequence lengths ---
    private int maxPromptLength = 3072;
    private int maxResponseLength = 3072;
    private String truncation = "truncate";

    // --- validation ---
    private boolean valBeforeTrain = true;

    // --- trainer / logging ---
    private int criticWarmup = 0;
    private List<String> logger = new ArrayList<>(List.of("tensorboard"));
    private boolean logRolloutDetails = true;
    private boolean logRewardDistribution = false;
    private boolean developMode = false;

    // --- low-level overrides for Verl config (optional) ---
    private Map<String, Object> verlExtra = new HashMap<>();
    private String verlConfigPath;

    // --- project / experiment ---
    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }
    public String getExperimentName() { return experimentName; }
    public void setExperimentName(String experimentName) { this.experimentName = experimentName; }

    // --- data paths ---
    public String getTrainDataPath() { return trainDataPath; }
    public void setTrainDataPath(String trainDataPath) { this.trainDataPath = trainDataPath; }
    public String getValDataPath() { return valDataPath; }
    public void setValDataPath(String valDataPath) { this.valDataPath = valDataPath; }
    public String getTrainFiles() { return trainFiles; }
    public void setTrainFiles(String trainFiles) { this.trainFiles = trainFiles; }
    public String getValFiles() { return valFiles; }
    public void setValFiles(String valFiles) { this.valFiles = valFiles; }

    // --- model ---
    public String getModelPath() { return modelPath; }
    public void setModelPath(String modelPath) { this.modelPath = modelPath; }
    public String getSavePath() { return savePath; }
    public void setSavePath(String savePath) { this.savePath = savePath; }

    // --- algorithm ---
    public String getAlgorithmAdvEstimator() { return algorithmAdvEstimator; }
    public void setAlgorithmAdvEstimator(String algorithmAdvEstimator) { this.algorithmAdvEstimator = algorithmAdvEstimator; }
    public boolean isAlgorithmUseKlInReward() { return algorithmUseKlInReward; }
    public void setAlgorithmUseKlInReward(boolean algorithmUseKlInReward) { this.algorithmUseKlInReward = algorithmUseKlInReward; }
    public boolean isAlgorithmFilterGroups() { return algorithmFilterGroups; }
    public void setAlgorithmFilterGroups(boolean algorithmFilterGroups) { this.algorithmFilterGroups = algorithmFilterGroups; }
    public boolean isAlgorithmNormAdvByStdInGrpo() { return algorithmNormAdvByStdInGrpo; }
    public void setAlgorithmNormAdvByStdInGrpo(boolean algorithmNormAdvByStdInGrpo) { this.algorithmNormAdvByStdInGrpo = algorithmNormAdvByStdInGrpo; }
    public boolean isWholeTrajectory() { return wholeTrajectory; }
    public void setWholeTrajectory(boolean wholeTrajectory) { this.wholeTrajectory = wholeTrajectory; }

    // --- training control ---
    public int getEpochNum() { return epochNum; }
    public void setEpochNum(int epochNum) { this.epochNum = epochNum; }
    public int getTotalEpochs() { return totalEpochs; }
    public void setTotalEpochs(int totalEpochs) { this.totalEpochs = totalEpochs; }
    public int getSaveFreq() { return saveFreq; }
    public void setSaveFreq(int saveFreq) { this.saveFreq = saveFreq; }
    public int getTestFreq() { return testFreq; }
    public void setTestFreq(int testFreq) { this.testFreq = testFreq; }
    public int getTrainBatchSize() { return trainBatchSize; }
    public void setTrainBatchSize(int trainBatchSize) { this.trainBatchSize = trainBatchSize; }
    public int getRolloutConcurrency() { return rolloutConcurrency; }
    public void setRolloutConcurrency(int rolloutConcurrency) { this.rolloutConcurrency = rolloutConcurrency; }

    // --- hardware ---
    public String getVisibleDevice() { return visibleDevice; }
    public void setVisibleDevice(String visibleDevice) { this.visibleDevice = visibleDevice; }
    public int getNnodes() { return nnodes; }
    public void setNnodes(int nnodes) { this.nnodes = nnodes; }
    public int getNGpusPerNode() { return nGpusPerNode; }
    public void setNGpusPerNode(int nGpusPerNode) { this.nGpusPerNode = nGpusPerNode; }
    public int getMicroBatchSizePerGpu() { return microBatchSizePerGpu; }
    public void setMicroBatchSizePerGpu(int microBatchSizePerGpu) { this.microBatchSizePerGpu = microBatchSizePerGpu; }

    // --- sequence lengths ---
    public int getMaxPromptLength() { return maxPromptLength; }
    public void setMaxPromptLength(int maxPromptLength) { this.maxPromptLength = maxPromptLength; }
    public int getMaxResponseLength() { return maxResponseLength; }
    public void setMaxResponseLength(int maxResponseLength) { this.maxResponseLength = maxResponseLength; }
    public String getTruncation() { return truncation; }
    public void setTruncation(String truncation) { this.truncation = truncation; }

    // --- validation ---
    public boolean isValBeforeTrain() { return valBeforeTrain; }
    public void setValBeforeTrain(boolean valBeforeTrain) { this.valBeforeTrain = valBeforeTrain; }

    // --- trainer / logging ---
    public int getCriticWarmup() { return criticWarmup; }
    public void setCriticWarmup(int criticWarmup) { this.criticWarmup = criticWarmup; }
    public List<String> getLogger() { return logger; }
    public void setLogger(List<String> logger) { this.logger = logger != null ? new ArrayList<>(logger) : new ArrayList<>(); }
    public boolean isLogRolloutDetails() { return logRolloutDetails; }
    public void setLogRolloutDetails(boolean logRolloutDetails) { this.logRolloutDetails = logRolloutDetails; }
    public boolean isLogRewardDistribution() { return logRewardDistribution; }
    public void setLogRewardDistribution(boolean logRewardDistribution) { this.logRewardDistribution = logRewardDistribution; }
    public boolean isDevelopMode() { return developMode; }
    public void setDevelopMode(boolean developMode) { this.developMode = developMode; }

    // --- low-level overrides ---
    public Map<String, Object> getVerlExtra() { return verlExtra; }
    public void setVerlExtra(Map<String, Object> verlExtra) { this.verlExtra = verlExtra != null ? new HashMap<>(verlExtra) : new HashMap<>(); }
    public String getVerlConfigPath() { return verlConfigPath; }
    public void setVerlConfigPath(String verlConfigPath) { this.verlConfigPath = verlConfigPath; }

    // --- computed properties ---
    public String getResolvedTrainFiles() {
        return trainFiles != null ? trainFiles : trainDataPath;
    }

    public String getResolvedValFiles() {
        return valFiles != null ? valFiles : valDataPath;
    }
}