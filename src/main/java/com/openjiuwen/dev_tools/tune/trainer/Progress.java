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
 * 
 * @since 0.1.7
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
     * Progress.
     * 
     * @since 0.1.7
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
     * Progress.
     * 
     * @param currentEpoch currentEpoch
     * @param maxEpoch maxEpoch
     * @param currentBatchIter currentBatchIter
     * @param maxBatchIter maxBatchIter
     * @param bestScore bestScore
     * @param bestBatchScore bestBatchScore
     * @param currentEpochScore currentEpochScore
     * @since 0.1.7
     */
    public Progress(int currentEpoch, int maxEpoch, int currentBatchIter, int maxBatchIter, double bestScore,
            double bestBatchScore, double currentEpochScore) {
        this.currentEpoch = Math.max(0, currentEpoch);
        this.maxEpoch = Math.max(0, maxEpoch);
        this.currentBatchIter = Math.max(0, currentBatchIter);
        this.maxBatchIter = Math.max(0, maxBatchIter);
        this.bestScore = clamp(bestScore);
        this.bestBatchScore = clamp(bestBatchScore);
        this.currentEpochScore = clamp(currentEpochScore);
    }

    /**
     * builder.
     * 
     * @return the result
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * getCurrentEpoch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCurrentEpoch() {
        return currentEpoch;
    }

    /**
     * setCurrentEpoch.
     * 
     * @param currentEpoch currentEpoch
     * @since 0.1.7
     */
    public void setCurrentEpoch(int currentEpoch) {
        this.currentEpoch = Math.max(0, currentEpoch);
    }

    /**
     * getMaxEpoch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxEpoch() {
        return maxEpoch;
    }

    /**
     * setMaxEpoch.
     * 
     * @param maxEpoch maxEpoch
     * @since 0.1.7
     */
    public void setMaxEpoch(int maxEpoch) {
        this.maxEpoch = Math.max(0, maxEpoch);
    }

    /**
     * getCurrentBatchIter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getCurrentBatchIter() {
        return currentBatchIter;
    }

    /**
     * setCurrentBatchIter.
     * 
     * @param currentBatchIter currentBatchIter
     * @since 0.1.7
     */
    public void setCurrentBatchIter(int currentBatchIter) {
        this.currentBatchIter = Math.max(0, currentBatchIter);
    }

    /**
     * getMaxBatchIter.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMaxBatchIter() {
        return maxBatchIter;
    }

    /**
     * setMaxBatchIter.
     * 
     * @param maxBatchIter maxBatchIter
     * @since 0.1.7
     */
    public void setMaxBatchIter(int maxBatchIter) {
        this.maxBatchIter = Math.max(0, maxBatchIter);
    }

    /**
     * getBestScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getBestScore() {
        return bestScore;
    }

    /**
     * setBestScore.
     * 
     * @param bestScore bestScore
     * @since 0.1.7
     */
    public void setBestScore(double bestScore) {
        this.bestScore = clamp(bestScore);
    }

    /**
     * getBestBatchScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getBestBatchScore() {
        return bestBatchScore;
    }

    /**
     * setBestBatchScore.
     * 
     * @param bestBatchScore bestBatchScore
     * @since 0.1.7
     */
    public void setBestBatchScore(double bestBatchScore) {
        this.bestBatchScore = clamp(bestBatchScore);
    }

    /**
     * getCurrentEpochScore.
     * 
     * @return the result
     * @since 0.1.7
     */
    public double getCurrentEpochScore() {
        return currentEpochScore;
    }

    /**
     * setCurrentEpochScore.
     * 
     * @param currentEpochScore currentEpochScore
     * @since 0.1.7
     */
    public void setCurrentEpochScore(double currentEpochScore) {
        this.currentEpochScore = clamp(currentEpochScore);
    }

    /**
     * runEpoch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Stream<Integer> runEpoch() {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<Integer>() {
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
        }, Spliterator.ORDERED), false);
    }

    /**
     * runBatch.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Stream<Integer> runBatch() {
        bestBatchScore = 0.0d;
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<Integer>() {
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
        }, Spliterator.ORDERED), false);
    }

    /**
     * clamp.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static double clamp(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /**
     * Builder.
     * 
     * @since 0.1.7
     */
    public static final class Builder {
        private int currentEpoch = 0;
        private int maxEpoch = TuneConstant.DEFAULT_ITERATION_NUM;
        private int currentBatchIter = 0;
        private int maxBatchIter = 1;
        private double bestScore = 0.0d;
        private double bestBatchScore = 0.0d;
        private double currentEpochScore = 0.0d;

        /**
         * Builder.
         * 
         * @since 0.1.7
         */
        private Builder() {
        }

        /**
         * currentEpoch.
         * 
         * @param currentEpoch currentEpoch
         * @return the result
         * @since 0.1.7
         */
        public Builder currentEpoch(int currentEpoch) {
            this.currentEpoch = currentEpoch;
            return this;
        }

        /**
         * maxEpoch.
         * 
         * @param maxEpoch maxEpoch
         * @return the result
         * @since 0.1.7
         */
        public Builder maxEpoch(int maxEpoch) {
            this.maxEpoch = maxEpoch;
            return this;
        }

        /**
         * currentBatchIter.
         * 
         * @param currentBatchIter currentBatchIter
         * @return the result
         * @since 0.1.7
         */
        public Builder currentBatchIter(int currentBatchIter) {
            this.currentBatchIter = currentBatchIter;
            return this;
        }

        /**
         * maxBatchIter.
         * 
         * @param maxBatchIter maxBatchIter
         * @return the result
         * @since 0.1.7
         */
        public Builder maxBatchIter(int maxBatchIter) {
            this.maxBatchIter = maxBatchIter;
            return this;
        }

        /**
         * bestScore.
         * 
         * @param bestScore bestScore
         * @return the result
         * @since 0.1.7
         */
        public Builder bestScore(double bestScore) {
            this.bestScore = bestScore;
            return this;
        }

        /**
         * bestBatchScore.
         * 
         * @param bestBatchScore bestBatchScore
         * @return the result
         * @since 0.1.7
         */
        public Builder bestBatchScore(double bestBatchScore) {
            this.bestBatchScore = bestBatchScore;
            return this;
        }

        /**
         * currentEpochScore.
         * 
         * @param currentEpochScore currentEpochScore
         * @return the result
         * @since 0.1.7
         */
        public Builder currentEpochScore(double currentEpochScore) {
            this.currentEpochScore = currentEpochScore;
            return this;
        }

        /**
         * build.
         * 
         * @return the result
         * @since 0.1.7
         */
        public Progress build() {
            return new Progress(currentEpoch, maxEpoch, currentBatchIter, maxBatchIter, bestScore, bestBatchScore,
                    currentEpochScore);
        }
    }
}
