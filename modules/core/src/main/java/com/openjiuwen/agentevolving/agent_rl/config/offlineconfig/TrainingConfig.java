/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.offlineconfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.config.offlineconfig.TrainingConfig.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TrainingConfig {
    @JsonProperty("project_name")
    private String projectName = "OpenJiuwenAgentRL";
    @JsonProperty("experiment_name")
    private String experimentName = "grpo_experiment";

    @JsonProperty("train_data_path")
    private String trainDataPath;
    @JsonProperty("val_data_path")
    private String valDataPath;
    @JsonProperty("train_files")
    private String trainFiles;
    @JsonProperty("val_files")
    private String valFiles;

    @JsonProperty("model_path")
    private String modelPath;
    @JsonProperty("save_path")
    private String savePath;

    private String algorithmAdvEstimator = "grpo";
    private boolean isAlgorithmUseKlInRewardEnabled = false;
    private boolean isAlgorithmFilterGroupsEnabled = false;
    private boolean isAlgorithmNormAdvByStdInGrpoEnabled = true;
    private boolean isWholeTrajectoryEnabled = false;

    private int epochNum = 2;
    @JsonProperty("total_epochs")
    private int totalEpochs = 2;
    private int saveFreq = 20;
    private int testFreq = 20;
    private int trainBatchSize = 32;
    private int rolloutConcurrency = 40;

    private String visibleDevice = "0,1,2,3";
    private int nnodes = 1;
    private int nGpusPerNode = 4;
    private int microBatchSizePerGpu = 4;

    private int maxPromptLength = 3072;
    private int maxResponseLength = 3072;
    private String truncation = "truncate";

    private boolean isValBeforeTrainEnabled = true;

    private int criticWarmup = 0;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> logger = new ArrayList<>(List.of("tensorboard"));
    private boolean isLogRolloutDetailsEnabled = true;
    private boolean isLogRewardDistributionEnabled = false;
    private boolean isDevelopModeEnabled = false;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> verlExtra = new LinkedHashMap<>();
    private String verlConfigPath;

    /**
     * resolvedTrainFiles.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String resolvedTrainFiles() {
        return trainFiles != null ? trainFiles : trainDataPath;
    }

    /**
     * resolvedValFiles.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String resolvedValFiles() {
        return valFiles != null ? valFiles : valDataPath;
    }

    /**
     * getProject_name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public String getProject_name() {
        return getProjectName();
    }

    /**
     * getExperiment_name.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public String getExperiment_name() {
        return getExperimentName();
    }

    /**
     * getAlgorithm_adv_estimator.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public String getAlgorithm_adv_estimator() {
        return getAlgorithmAdvEstimator();
    }

    /**
     * isAlgorithm_norm_adv_by_std_in_grpo.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isAlgorithm_norm_adv_by_std_in_grpo() {
        return isAlgorithmNormAdvByStdInGrpoEnabled();
    }

    /**
     * getEpoch_num.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public int getEpoch_num() {
        return getEpochNum();
    }

    /**
     * getTrain_batch_size.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public int getTrain_batch_size() {
        return getTrainBatchSize();
    }

    /**
     * getRollout_concurrency.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public int getRollout_concurrency() {
        return getRolloutConcurrency();
    }

    /**
     * getVisible_device.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public String getVisible_device() {
        return getVisibleDevice();
    }

    /**
     * getMax_prompt_length.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public int getMax_prompt_length() {
        return getMaxPromptLength();
    }

    /**
     * getMax_response_length.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public int getMax_response_length() {
        return getMaxResponseLength();
    }

    /**
     * isVal_before_train.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isVal_before_train() {
        return isValBeforeTrainEnabled();
    }

    /**
     * isLog_rollout_details.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isLog_rollout_details() {
        return isLogRolloutDetailsEnabled();
    }

    /**
     * isLog_reward_distribution.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public boolean isLog_reward_distribution() {
        return isLogRewardDistributionEnabled();
    }

    /**
     * getVerl_extra.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public Map<String, Object> getVerl_extra() {
        return getVerlExtra();
    }

    /**
     * getVerl_config_path.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public String getVerl_config_path() {
        return getVerlConfigPath();
    }

    /**
     * getTotal_epochs.
     * 
     * @return the result
     * @since 0.1.7
     */
    @JsonIgnore
    public int getTotal_epochs() {
        return getTotalEpochs();
    }

    /**
     * setTrain_data_path.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setTrain_data_path(String value) {
        setTrainDataPath(value);
    }

    /**
     * setVal_data_path.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setVal_data_path(String value) {
        setValDataPath(value);
    }

    /**
     * setTrain_files.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setTrain_files(String value) {
        setTrainFiles(value);
    }

    /**
     * setVal_files.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setVal_files(String value) {
        setValFiles(value);
    }

    /**
     * setTotal_epochs.
     * 
     * @param value value
     * @since 0.1.7
     */
    @JsonIgnore
    public void setTotal_epochs(int value) {
        setTotalEpochs(value);
    }
}
