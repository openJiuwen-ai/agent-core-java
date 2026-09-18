/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver;

import java.util.List;

/**
 * Input parameters for summarize_trajectories method.
 * <p>
 * Mirrors Python's
 * {@code openjiuwen.extensions.context_evolver.context_evolving_react_agent.SummarizeTrajectoriesInput}.
 * 
 * @since 0.1.7
 */
public class SummarizeTrajectoriesInput {
    private String query;
    private Object trajectory; // String or List<String>
    private String mattsMode;
    private Object feedback; // String, Boolean, or List
    private List<Integer> scores;

    /**
     * SummarizeTrajectoriesInput.
     * 
     * @since 0.1.7
     */
    public SummarizeTrajectoriesInput() {
    }

    /**
     * SummarizeTrajectoriesInput.
     * 
     * @param query query
     * @param trajectory trajectory
     * @param mattsMode mattsMode
     * @since 0.1.7
     */
    public SummarizeTrajectoriesInput(String query, Object trajectory, String mattsMode) {
        this.query = query;
        this.trajectory = trajectory;
        this.mattsMode = mattsMode;
    }

    /**
     * SummarizeTrajectoriesInput.
     * 
     * @param query query
     * @param trajectory trajectory
     * @param mattsMode mattsMode
     * @param feedback feedback
     * @since 0.1.7
     */
    public SummarizeTrajectoriesInput(String query, Object trajectory, String mattsMode, Object feedback) {
        this(query, trajectory, mattsMode);
        this.feedback = feedback;
    }

    /**
     * SummarizeTrajectoriesInput.
     * 
     * @param query query
     * @param trajectory trajectory
     * @param mattsMode mattsMode
     * @param feedback feedback
     * @param scores scores
     * @since 0.1.7
     */
    public SummarizeTrajectoriesInput(String query, Object trajectory, String mattsMode, Object feedback,
            List<Integer> scores) {
        this(query, trajectory, mattsMode, feedback);
        this.scores = scores;
    }

    // Getters and Setters
    /**
     * getQuery.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getQuery() {
        return query;
    }

    /**
     * setQuery.
     * 
     * @param query query
     * @since 0.1.7
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * getTrajectory.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getTrajectory() {
        return trajectory;
    }

    /**
     * setTrajectory.
     * 
     * @param trajectory trajectory
     * @since 0.1.7
     */
    public void setTrajectory(Object trajectory) {
        this.trajectory = trajectory;
    }

    /**
     * getMattsMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMattsMode() {
        return mattsMode;
    }

    /**
     * setMattsMode.
     * 
     * @param mattsMode mattsMode
     * @since 0.1.7
     */
    public void setMattsMode(String mattsMode) {
        this.mattsMode = mattsMode;
    }

    /**
     * getFeedback.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getFeedback() {
        return feedback;
    }

    /**
     * setFeedback.
     * 
     * @param feedback feedback
     * @since 0.1.7
     */
    public void setFeedback(Object feedback) {
        this.feedback = feedback;
    }

    /**
     * getScores.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Integer> getScores() {
        return scores;
    }

    /**
     * setScores.
     * 
     * @param scores scores
     * @since 0.1.7
     */
    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }
}
