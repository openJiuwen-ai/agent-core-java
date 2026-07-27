/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trainer;

import com.openjiuwen.agentevolving.TuneConstant;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Self-evolving training progress counters and scores.
 *
 * <p>Mirrors Python's {@code Progress} in
 * {@code openjiuwen/agent_evolving/trainer/progress.py}.</p>
 */
public class Progress {

    private int startEpoch;
    private int currentEpoch;
    private int maxEpoch = TuneConstant.DEFAULT_ITERATION_NUM;
    private int currentBatchIter;
    private int maxBatchIter = 1;
    private double bestScore;
    private double bestBatchScore;
    private double currentEpochScore;

    public Iterable<Integer> runEpoch() {
        return () -> new Iterator<>() {
            private int nextEpoch = startEpoch + 1;
            private boolean finalized;

            @Override
            /**
             * Auto-generated for codecheck compliance.
             */
            public boolean hasNext() {
                if (nextEpoch <= maxEpoch) {
                    return true;
                }
                finalizeEpochs();
                return false;
            }

            @Override
            /**
             * Auto-generated for codecheck compliance.
             */
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                currentEpoch = nextEpoch;
                nextEpoch += 1;
                return currentEpoch;
            }

            private void finalizeEpochs() {
                if (!finalized && currentEpoch < maxEpoch) {
                    currentEpoch = maxEpoch;
                }
                finalized = true;
            }
        };
    }

    public Iterable<Integer> runBatch() {
        return () -> new Iterator<>() {
            private int nextBatchIter;

            {
                bestBatchScore = 0.0d;
            }

            @Override
            /**
             * Auto-generated for codecheck compliance.
             */
            public boolean hasNext() {
                return nextBatchIter < maxBatchIter;
            }

            @Override
            /**
             * Auto-generated for codecheck compliance.
             */
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                currentBatchIter = nextBatchIter;
                nextBatchIter += 1;
                return currentBatchIter;
            }
        };
    }

    public int getStartEpoch() {
        return startEpoch;
    }

    public void setStartEpoch(int startEpoch) {
        this.startEpoch = requireNonNegative(startEpoch, "start_epoch");
    }

    public int getCurrentEpoch() {
        return currentEpoch;
    }

    public void setCurrentEpoch(int currentEpoch) {
        this.currentEpoch = requireNonNegative(currentEpoch, "current_epoch");
    }

    public int getMaxEpoch() {
        return maxEpoch;
    }

    public void setMaxEpoch(int maxEpoch) {
        this.maxEpoch = requireNonNegative(maxEpoch, "max_epoch");
    }

    public int getCurrentBatchIter() {
        return currentBatchIter;
    }

    public void setCurrentBatchIter(int currentBatchIter) {
        this.currentBatchIter = requireNonNegative(currentBatchIter, "current_batch_iter");
    }

    public int getMaxBatchIter() {
        return maxBatchIter;
    }

    public void setMaxBatchIter(int maxBatchIter) {
        this.maxBatchIter = requireNonNegative(maxBatchIter, "max_batch_iter");
    }

    public double getBestScore() {
        return bestScore;
    }

    public void setBestScore(double bestScore) {
        this.bestScore = requireScore(bestScore, "best_score");
    }

    public double getBestBatchScore() {
        return bestBatchScore;
    }

    public void setBestBatchScore(double bestBatchScore) {
        this.bestBatchScore = requireScore(bestBatchScore, "best_batch_score");
    }

    public double getCurrentEpochScore() {
        return currentEpochScore;
    }

    public void setCurrentEpochScore(double currentEpochScore) {
        this.currentEpochScore = requireScore(currentEpochScore, "current_epoch_score");
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must be >= 0");
        }
        return value;
    }

    private static double requireScore(double value, String fieldName) {
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " must be between 0.0 and 1.0");
        }
        return value;
    }
}
