/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.tune.trainer;

import com.openjiuwen.dev_tools.tune.TuneConstant;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Mirrors Python's openjiuwen.dev_tools.tune.trainer.base.Progress.
 */
public class Progress {

    private int currentEpoch;
    private int maxEpoch;
    private int currentBatchIter;
    private int maxBatchIter;
    private double bestScore;
    private double bestBatchScore;
    private double currentEpochScore;

    public Progress() {
        this.currentEpoch = 0;
        this.maxEpoch = TuneConstant.DEFAULT_ITERATION_NUM;
        this.currentBatchIter = 0;
        this.maxBatchIter = 1;
        this.bestScore = 0.0d;
        this.bestBatchScore = 0.0d;
        this.currentEpochScore = 0.0d;
    }

    public Progress(int currentEpoch,
                    int maxEpoch,
                    int currentBatchIter,
                    int maxBatchIter,
                    double bestScore,
                    double bestBatchScore,
                    double currentEpochScore) {
        this.currentEpoch = Math.max(0, currentEpoch);
        this.maxEpoch = Math.max(0, maxEpoch);
        this.currentBatchIter = Math.max(0, currentBatchIter);
        this.maxBatchIter = Math.max(0, maxBatchIter);
        this.bestScore = clamp(bestScore);
        this.bestBatchScore = clamp(bestBatchScore);
        this.currentEpochScore = clamp(currentEpochScore);
    }

    public static Builder builder() { return new Builder(); }
    public int getCurrentEpoch() { return currentEpoch; }
    public void setCurrentEpoch(int currentEpoch) { this.currentEpoch = Math.max(0, currentEpoch); }
    public int getMaxEpoch() { return maxEpoch; }
    public void setMaxEpoch(int maxEpoch) { this.maxEpoch = Math.max(0, maxEpoch); }
    public int getCurrentBatchIter() { return currentBatchIter; }
    public void setCurrentBatchIter(int currentBatchIter) { this.currentBatchIter = Math.max(0, currentBatchIter); }
    public int getMaxBatchIter() { return maxBatchIter; }
    public void setMaxBatchIter(int maxBatchIter) { this.maxBatchIter = Math.max(0, maxBatchIter); }
    public double getBestScore() { return bestScore; }
    public void setBestScore(double bestScore) { this.bestScore = clamp(bestScore); }
    public double getBestBatchScore() { return bestBatchScore; }
    public void setBestBatchScore(double bestBatchScore) { this.bestBatchScore = clamp(bestBatchScore); }
    public double getCurrentEpochScore() { return currentEpochScore; }
    public void setCurrentEpochScore(double currentEpochScore) { this.currentEpochScore = clamp(currentEpochScore); }

    public Stream<Integer> runEpoch() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<Integer>() {
                            private int epoch = 1;

                            @Override
                            public boolean hasNext() {
                                return epoch <= maxEpoch;
                            }

                            @Override
                            public Integer next() {
                                currentEpoch = epoch;
                                return epoch++;
                            }
                        },
                        Spliterator.ORDERED
                ),
                false
        );
    }

    public Stream<Integer> runBatch() {
        bestBatchScore = 0.0d;
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<Integer>() {
                            private int batchIter = 0;

                            @Override
                            public boolean hasNext() {
                                return batchIter < maxBatchIter;
                            }

                            @Override
                            public Integer next() {
                                currentBatchIter = batchIter;
                                return batchIter++;
                            }
                        },
                        Spliterator.ORDERED
                ),
                false
        );
    }

    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    public static final class Builder {
        private int currentEpoch = 0;
        private int maxEpoch = TuneConstant.DEFAULT_ITERATION_NUM;
        private int currentBatchIter = 0;
        private int maxBatchIter = 1;
        private double bestScore = 0.0d;
        private double bestBatchScore = 0.0d;
        private double currentEpochScore = 0.0d;

        private Builder() {
        }

        public Builder currentEpoch(int currentEpoch) { this.currentEpoch = currentEpoch; return this; }
        public Builder maxEpoch(int maxEpoch) { this.maxEpoch = maxEpoch; return this; }
        public Builder currentBatchIter(int currentBatchIter) { this.currentBatchIter = currentBatchIter; return this; }
        public Builder maxBatchIter(int maxBatchIter) { this.maxBatchIter = maxBatchIter; return this; }
        public Builder bestScore(double bestScore) { this.bestScore = bestScore; return this; }
        public Builder bestBatchScore(double bestBatchScore) { this.bestBatchScore = bestBatchScore; return this; }
        public Builder currentEpochScore(double currentEpochScore) { this.currentEpochScore = currentEpochScore; return this; }

        public Progress build() {
            return new Progress(currentEpoch, maxEpoch, currentBatchIter, maxBatchIter, bestScore, bestBatchScore, currentEpochScore);
        }
    }
}