  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.agent_evolving.trainer;

import com.openjiuwen.agent_evolving.TuneConstant;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Training progress and callbacks.
 *
 * <p>Progress records epochs and scores, Callbacks provides train/epoch lifecycle hooks.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_evolving.trainer.progress.Progress}.
 */
public class Progress {

    private int startEpoch = 0;
    private int currentEpoch = 0;
    private int maxEpoch = TuneConstant.DEFAULT_ITERATION_NUM;
    private int currentBatchIter = 0;
    private int maxBatchIter = 1;
    private double bestScore = 0.0;
    private double bestBatchScore = 0.0;
    private double currentEpochScore = 0.0;
    private Integer seed;

    /**
     * Create with default max epoch.
     */
    public Progress() {
    }

    /**
     * Create with specified max epoch.
     *
     * @param maxEpoch Maximum number of epochs
     */
    public Progress(int maxEpoch) {
        this.maxEpoch = Math.max(0, maxEpoch);
    }

    /**
     * Iterate through epochs from startEpoch+1 to maxEpoch.
     *
     * @return Iterable of epoch numbers
     */
    public Iterable<Integer> runEpoch() {
        return () -> new Iterator<>() {
            private int nextEpoch = startEpoch + 1;
            private boolean exhausted;

            @Override
            public boolean hasNext() {
                boolean hasNext = nextEpoch <= maxEpoch;
                if (!hasNext && !exhausted && currentEpoch < maxEpoch) {
                    currentEpoch = maxEpoch;
                    exhausted = true;
                }
                return hasNext;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more epochs");
                }
                currentEpoch = nextEpoch;
                exhausted = currentEpoch >= maxEpoch;
                return nextEpoch++;
            }
        };
    }

    /**
     * Iterate through batch iterations from 0 to maxBatchIter - 1.
     *
     * @return Iterable of batch iteration numbers
     */
    public Iterable<Integer> runBatch() {
        bestBatchScore = 0.0;
        return () -> new Iterator<>() {
            private int nextBatchIter = 0;

            @Override
            public boolean hasNext() {
                return nextBatchIter < maxBatchIter;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No more batch iterations");
                }
                currentBatchIter = nextBatchIter;
                return nextBatchIter++;
            }
        };
    }

    /**
     * Get current epoch number.
     */
    public int getCurrentEpoch() {
        return currentEpoch;
    }

    /**
     * Set current epoch number.
     */
    public void setCurrentEpoch(int currentEpoch) {
        this.currentEpoch = Math.max(0, currentEpoch);
    }

    /**
     * Get max epoch.
     */
    public int getMaxEpoch() {
        return maxEpoch;
    }

    /**
     * Set max epoch.
     */
    public void setMaxEpoch(int maxEpoch) {
        this.maxEpoch = Math.max(0, maxEpoch);
    }

    /**
     * Get start epoch.
     */
    public int getStartEpoch() {
        return startEpoch;
    }

    /**
     * Set start epoch.
     */
    public void setStartEpoch(int startEpoch) {
        this.startEpoch = Math.max(0, startEpoch);
    }

    /**
     * Get best score.
     */
    public double getBestScore() {
        return bestScore;
    }

    /**
     * Set best score.
     */
    public void setBestScore(double bestScore) {
        this.bestScore = clamp(bestScore);
    }

    /**
     * Get current epoch score.
     */
    public double getCurrentEpochScore() {
        return currentEpochScore;
    }

    /**
     * Set current epoch score.
     */
    public void setCurrentEpochScore(double currentEpochScore) {
        this.currentEpochScore = clamp(currentEpochScore);
    }

    /**
     * Get current batch iteration.
     */
    public int getCurrentBatchIter() {
        return currentBatchIter;
    }

    /**
     * Set current batch iteration.
     */
    public void setCurrentBatchIter(int currentBatchIter) {
        this.currentBatchIter = Math.max(0, currentBatchIter);
    }

    /**
     * Get max batch iteration.
     */
    public int getMaxBatchIter() {
        return maxBatchIter;
    }

    /**
     * Set max batch iteration.
     */
    public void setMaxBatchIter(int maxBatchIter) {
        this.maxBatchIter = Math.max(0, maxBatchIter);
    }

    /**
     * Get best batch score.
     */
    public double getBestBatchScore() {
        return bestBatchScore;
    }

    /**
     * Set best batch score.
     */
    public void setBestBatchScore(double bestBatchScore) {
        this.bestBatchScore = clamp(bestBatchScore);
    }

    /**
     * Get seed (for compatibility).
     */
    public Integer getSeed() {
        return seed;
    }

    /**
     * Set seed.
     */
    public void setSeed(Integer seed) {
        this.seed = seed;
    }

    private double clamp(double score) {
        return Math.max(0.0d, Math.min(1.0d, score));
    }
}
