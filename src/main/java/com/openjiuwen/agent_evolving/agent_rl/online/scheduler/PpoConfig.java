// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.scheduler;

import java.util.*;

/**
 * PPO training configuration.
 * <p>
 * Mirrors Python's {@code ppo_config.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.online.scheduler.ppo_config}.
 */
public class PpoConfig {
    
    private int batchSize = 128;
    private int miniBatchSize = 32;
    private int epochs = 4;
    private double learningRate = 1e-5;
    private double clipRatio = 0.2;
    private double valueLossCoef = 0.5;
    private double entropyCoef = 0.01;
    private double gamma = 0.99;
    private double gaeLambda = 0.95;
    private int maxGradNorm = 1;
    private double warmupSteps = 100;
    private Map<String, Object> extraParams = new LinkedHashMap<>();
    
    public PpoConfig() {
    }
    
    // Getters and setters
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    
    public int getMiniBatchSize() { return miniBatchSize; }
    public void setMiniBatchSize(int miniBatchSize) { this.miniBatchSize = miniBatchSize; }
    
    public int getEpochs() { return epochs; }
    public void setEpochs(int epochs) { this.epochs = epochs; }
    
    public double getLearningRate() { return learningRate; }
    public void setLearningRate(double learningRate) { this.learningRate = learningRate; }
    
    public double getClipRatio() { return clipRatio; }
    public void setClipRatio(double clipRatio) { this.clipRatio = clipRatio; }
    
    public double getValueLossCoef() { return valueLossCoef; }
    public void setValueLossCoef(double valueLossCoef) { this.valueLossCoef = valueLossCoef; }
    
    public double getEntropyCoef() { return entropyCoef; }
    public void setEntropyCoef(double entropyCoef) { this.entropyCoef = entropyCoef; }
    
    public double getGamma() { return gamma; }
    public void setGamma(double gamma) { this.gamma = gamma; }
    
    public double getGaeLambda() { return gaeLambda; }
    public void setGaeLambda(double gaeLambda) { this.gaeLambda = gaeLambda; }
    
    public int getMaxGradNorm() { return maxGradNorm; }
    public void setMaxGradNorm(int maxGradNorm) { this.maxGradNorm = maxGradNorm; }
    
    public double getWarmupSteps() { return warmupSteps; }
    public void setWarmupSteps(double warmupSteps) { this.warmupSteps = warmupSteps; }
    
    public Map<String, Object> getExtraParams() { return extraParams; }
    public void setExtraParams(Map<String, Object> extraParams) { this.extraParams = extraParams; }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private int batchSize = 128;
        private int miniBatchSize = 32;
        private int epochs = 4;
        private double learningRate = 1e-5;
        private double clipRatio = 0.2;
        private double valueLossCoef = 0.5;
        private double entropyCoef = 0.01;
        private double gamma = 0.99;
        private double gaeLambda = 0.95;
        private int maxGradNorm = 1;
        private double warmupSteps = 100;
        private Map<String, Object> extraParams = new LinkedHashMap<>();
        
        public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
        public Builder miniBatchSize(int miniBatchSize) { this.miniBatchSize = miniBatchSize; return this; }
        public Builder epochs(int epochs) { this.epochs = epochs; return this; }
        public Builder learningRate(double learningRate) { this.learningRate = learningRate; return this; }
        public Builder clipRatio(double clipRatio) { this.clipRatio = clipRatio; return this; }
        public Builder valueLossCoef(double valueLossCoef) { this.valueLossCoef = valueLossCoef; return this; }
        public Builder entropyCoef(double entropyCoef) { this.entropyCoef = entropyCoef; return this; }
        public Builder gamma(double gamma) { this.gamma = gamma; return this; }
        public Builder gaeLambda(double gaeLambda) { this.gaeLambda = gaeLambda; return this; }
        public Builder maxGradNorm(int maxGradNorm) { this.maxGradNorm = maxGradNorm; return this; }
        public Builder warmupSteps(double warmupSteps) { this.warmupSteps = warmupSteps; return this; }
        public Builder extraParams(Map<String, Object> extraParams) { this.extraParams = extraParams; return this; }
        
        public PpoConfig build() {
            PpoConfig config = new PpoConfig();
            config.setBatchSize(batchSize);
            config.setMiniBatchSize(miniBatchSize);
            config.setEpochs(epochs);
            config.setLearningRate(learningRate);
            config.setClipRatio(clipRatio);
            config.setValueLossCoef(valueLossCoef);
            config.setEntropyCoef(entropyCoef);
            config.setGamma(gamma);
            config.setGaeLambda(gaeLambda);
            config.setMaxGradNorm(maxGradNorm);
            config.setWarmupSteps(warmupSteps);
            config.setExtraParams(extraParams);
            return config;
        }
    }
}