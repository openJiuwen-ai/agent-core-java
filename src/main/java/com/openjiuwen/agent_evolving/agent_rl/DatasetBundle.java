/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl;

import java.util.Map;
import java.util.function.Function;

/**
 * Dataset bundle containing training and validation datasets.
 * <p>
 * Mirrors Python's {@code DatasetBundle} in
 * {@code openjiuwen.agent_evolving.agent_rl.dataset}.
 */
public class DatasetBundle {

    private Object trainDataset;
    private Object valDataset;
    private Function<Object[], Object> collateFn;
    private Object trainSampler;
    private Runnable cleanupFn;

    public DatasetBundle() {
    }

    public DatasetBundle(Object trainDataset, Object valDataset, Function<Object[], Object> collateFn) {
        this.trainDataset = trainDataset;
        this.valDataset = valDataset;
        this.collateFn = collateFn;
    }

    public Object getTrainDataset() { return trainDataset; }
    public void setTrainDataset(Object trainDataset) { this.trainDataset = trainDataset; }
    public Object getValDataset() { return valDataset; }
    public void setValDataset(Object valDataset) { this.valDataset = valDataset; }
    public Function<Object[], Object> getCollateFn() { return collateFn; }
    public void setCollateFn(Function<Object[], Object> collateFn) { this.collateFn = collateFn; }
    public Object getTrainSampler() { return trainSampler; }
    public void setTrainSampler(Object trainSampler) { this.trainSampler = trainSampler; }
    public Runnable getCleanupFn() { return cleanupFn; }
    public void setCleanupFn(Runnable cleanupFn) { this.cleanupFn = cleanupFn; }

    /**
     * Execute cleanup if available.
     */
    public void cleanup() {
        if (cleanupFn != null) {
            cleanupFn.run();
        }
    }
}