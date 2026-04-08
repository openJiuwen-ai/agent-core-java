/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
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
    
    public Trajectory() {}
    
    public Trajectory(String query, String response) {
        this.query = query;
        this.response = response;
    }
    
    public Trajectory(String query, String response, FeedbackType feedback) {
        this.query = query;
        this.response = response;
        this.feedback = feedback;
    }
    
    // Getters and setters
    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
    public FeedbackType getFeedback() { return feedback; }
    public void setFeedback(FeedbackType feedback) { this.feedback = feedback; }
    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }
    
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
    public String toString() {
        String queryPreview = query != null && query.length() > 50 
            ? query.substring(0, 50) + "..." 
            : query;
        return "Trajectory(query='" + queryPreview + "', feedback=" + feedback + ")";
    }
}