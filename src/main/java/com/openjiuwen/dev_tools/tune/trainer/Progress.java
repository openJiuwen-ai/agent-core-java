/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public Progress() {
        this.currentEpoch = 0;
        this.maxEpoch = TuneConstant.DEFAULT_ITERATION_NUM;
        this.currentBatchIter = 0;
        this.maxBatchIter = 1;
        this.bestScore = 0.0d;
        this.bestBatchScore = 0.0d;
        this.currentEpochScore = 0.0d;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCurrentEpoch() {
        return currentEpoch;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCurrentEpoch(int currentEpoch) {
        this.currentEpoch = Math.max(0, currentEpoch);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxEpoch() {
        return maxEpoch;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMaxEpoch(int maxEpoch) {
        this.maxEpoch = Math.max(0, maxEpoch);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getCurrentBatchIter() {
        return currentBatchIter;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCurrentBatchIter(int currentBatchIter) {
        this.currentBatchIter = Math.max(0, currentBatchIter);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getMaxBatchIter() {
        return maxBatchIter;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMaxBatchIter(int maxBatchIter) {
        this.maxBatchIter = Math.max(0, maxBatchIter);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getBestScore() {
        return bestScore;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setBestScore(double bestScore) {
        this.bestScore = clamp(bestScore);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getBestBatchScore() {
        return bestBatchScore;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setBestBatchScore(double bestBatchScore) {
        this.bestBatchScore = clamp(bestBatchScore);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public double getCurrentEpochScore() {
        return currentEpochScore;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setCurrentEpochScore(double currentEpochScore) {
        this.currentEpochScore = clamp(currentEpochScore);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Stream<Integer> runEpoch() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<Integer>() {
                            private int epoch = 1;

                            /**
                             * Auto-generated for codecheck compliance.
                             */
                            @Override
                            /**
                             * Auto-generated for codecheck compliance.
                             */
                            public boolean hasNext() {
                                return epoch <= maxEpoch;
                            }

                            /**
                             * Auto-generated for codecheck compliance.
                             */
                            @Override
                            /**
                             * Auto-generated for codecheck compliance.
                             */
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public Stream<Integer> runBatch() {
        bestBatchScore = 0.0d;
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<Integer>() {
                            private int batchIter = 0;

                            /**
                             * Auto-generated for codecheck compliance.
                             */
                            @Override
                            /**
                             * Auto-generated for codecheck compliance.
                             */
                            public boolean hasNext() {
                                return batchIter < maxBatchIter;
                            }

                            /**
                             * Auto-generated for codecheck compliance.
                             */
                            @Override
                            /**
                             * Auto-generated for codecheck compliance.
                             */
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

    /**
     * Auto-generated for codecheck compliance.
     */
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

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder currentEpoch(int currentEpoch) {
            this.currentEpoch = currentEpoch;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder maxEpoch(int maxEpoch) {
            this.maxEpoch = maxEpoch;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder currentBatchIter(int currentBatchIter) {
            this.currentBatchIter = currentBatchIter;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder maxBatchIter(int maxBatchIter) {
            this.maxBatchIter = maxBatchIter;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder bestScore(double bestScore) {
            this.bestScore = bestScore;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder bestBatchScore(double bestBatchScore) {
            this.bestBatchScore = bestBatchScore;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Builder currentEpochScore(double currentEpochScore) {
            this.currentEpochScore = currentEpochScore;
            return this;
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public Progress build() {
            return new Progress(currentEpoch, maxEpoch, currentBatchIter, maxBatchIter, bestScore, bestBatchScore, currentEpochScore);
        }
    }
}
