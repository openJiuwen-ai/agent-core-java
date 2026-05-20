/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.*;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.trajectory.Trajectory}.
 * 
 * Trajectory representing a task execution with feedback.
 */
public class Trajectory {
    private String query;
    private String response;
    private FeedbackType feedback = FeedbackType.NEUTRAL;
    private Map<String, Object> context = new HashMap<>();
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory() {}
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory(String query, String response) {
        this.query = query;
        this.response = response;
    }
    
    /**
     * Auto-generated for codecheck compliance.
     */
    public Trajectory(String query, String response, FeedbackType feedback) {
        this.query = query;
        this.response = response;
        this.feedback = feedback;
    }
    
    // Getters and setters
    /**
     * Auto-generated for codecheck compliance.
     */
    public String getQuery() {
        return query;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setQuery(String query) {
        this.query = query;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getResponse() {
        return response;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setResponse(String response) {
        this.response = response;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public FeedbackType getFeedback() {
        return feedback;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setFeedback(FeedbackType feedback) {
        this.feedback = feedback;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }
    
    /**
     * Check if trajectory was successful.
     */
    public boolean isSuccess() {
        return feedback == FeedbackType.HELPFUL;
    }
    
    /**
     * Check if trajectory was a failure.
     */
    public boolean isFailure() {
        return feedback == FeedbackType.HARMFUL;
    }
    
    /**
     * Convert to dictionary.
     */
    public Map<String, Object> toDict() {
        Map<String, Object> dict = new HashMap<>();
        dict.put("query", query);
        dict.put("response", response);
        dict.put("feedback", feedback.getValue());
        dict.put("context", context);
        return dict;
    }
    
    /**
     * Create from dictionary.
     */
    public static Trajectory fromDict(Map<String, Object> data) {
        Trajectory trajectory = new Trajectory();
        trajectory.query = (String) data.get("query");
        trajectory.response = (String) data.get("response");
        trajectory.feedback = FeedbackType.fromValue((String) data.get("feedback"));
        @SuppressWarnings("unchecked")
        Map<String, Object> ctx = (Map<String, Object>) data.get("context");
        if (ctx != null) {
            trajectory.context = ctx;
        }
        return trajectory;
    }
    
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public String toString() {
        String queryPreview = query != null && query.length() > 50 
            ? query.substring(0, 50) + "..." 
            : query;
        return "Trajectory(query='" + queryPreview + "', feedback=" + feedback + ")";
    }
}
