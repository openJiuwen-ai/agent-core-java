/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.schema;

import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.extensions.context_evolver.schema.trajectory.Trajectory}.
 * Trajectory representing a task execution with feedback.
 * 
 * @since 0.1.7
 */
public class Trajectory {
    private String query;
    private String response;
    private FeedbackType feedback = FeedbackType.NEUTRAL;

    /**
     * HashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> context = new HashMap<>();

    /**
     * Trajectory.
     * 
     * @since 0.1.7
     */
    public Trajectory() {
    }

    /**
     * Trajectory.
     * 
     * @param query query
     * @param response response
     * @since 0.1.7
     */
    public Trajectory(String query, String response) {
        this.query = query;
        this.response = response;
    }

    /**
     * Trajectory.
     * 
     * @param query query
     * @param response response
     * @param feedback feedback
     * @since 0.1.7
     */
    public Trajectory(String query, String response, FeedbackType feedback) {
        this.query = query;
        this.response = response;
        this.feedback = feedback;
    }

    // Getters and setters
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
     * getResponse.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getResponse() {
        return response;
    }

    /**
     * setResponse.
     * 
     * @param response response
     * @since 0.1.7
     */
    public void setResponse(String response) {
        this.response = response;
    }

    /**
     * getFeedback.
     * 
     * @return the result
     * @since 0.1.7
     */
    public FeedbackType getFeedback() {
        return feedback;
    }

    /**
     * setFeedback.
     * 
     * @param feedback feedback
     * @since 0.1.7
     */
    public void setFeedback(FeedbackType feedback) {
        this.feedback = feedback;
    }

    /**
     * getContext.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getContext() {
        return context;
    }

    /**
     * setContext.
     * 
     * @param context context
     * @since 0.1.7
     */
    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    /**
     * Check if trajectory was successful.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSuccess() {
        return feedback == FeedbackType.HELPFUL;
    }

    /**
     * Check if trajectory was a failure.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isFailure() {
        return feedback == FeedbackType.HARMFUL;
    }

    /**
     * Convert to dictionary.
     * 
     * @return the result
     * @since 0.1.7
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
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
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

    /**
     * toString.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String toString() {
        String queryPreview = query != null && query.length() > 50 ? query.substring(0, 50) + "..." : query;
        return "Trajectory(query='" + queryPreview + "', feedback=" + feedback + ")";
    }
}
