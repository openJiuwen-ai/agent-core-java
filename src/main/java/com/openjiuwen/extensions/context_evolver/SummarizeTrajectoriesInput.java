/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.extensions.context_evolver;

import java.util.List;

/**
 * Input parameters for summarize_trajectories method.
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.context_evolver.context_evolving_react_agent.SummarizeTrajectoriesInput}.
 */
public class SummarizeTrajectoriesInput {

    private String query;
    private Object trajectory;  // String or List<String>
    private String mattsMode;
    private Object feedback;    // String, Boolean, or List
    private List<Integer> scores;

    public SummarizeTrajectoriesInput() {
    }

    public SummarizeTrajectoriesInput(String query, Object trajectory, String mattsMode) {
        this.query = query;
        this.trajectory = trajectory;
        this.mattsMode = mattsMode;
    }

    public SummarizeTrajectoriesInput(String query, Object trajectory, String mattsMode, Object feedback) {
        this(query, trajectory, mattsMode);
        this.feedback = feedback;
    }

    public SummarizeTrajectoriesInput(
            String query,
            Object trajectory,
            String mattsMode,
            Object feedback,
            List<Integer> scores) {
        this(query, trajectory, mattsMode, feedback);
        this.scores = scores;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Object getTrajectory() {
        return trajectory;
    }

    public void setTrajectory(Object trajectory) {
        this.trajectory = trajectory;
    }

    public String getMattsMode() {
        return mattsMode;
    }

    public void setMattsMode(String mattsMode) {
        this.mattsMode = mattsMode;
    }

    public Object getFeedback() {
        return feedback;
    }

    public void setFeedback(Object feedback) {
        this.feedback = feedback;
    }

    public List<Integer> getScores() {
        return scores;
    }

    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }
}
