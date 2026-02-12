/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for user interaction handling.
 * 
 * <p>Manages interactive inputs from the session state and provides
 * methods for waiting on and processing user inputs.
 * 
 * @author OpenJiuwen
 * @since 1.0.0
 */
public abstract class BaseInteraction {
    
    /**
     * The session state accessor.
     */
    protected final SessionStateAccessor stateAccessor;
    
    /**
     * List of interactive inputs.
     */
    protected List<Object> interactiveInputs;
    
    /**
     * The latest interactive input.
     */
    protected Object latestInteractiveInput;
    
    /**
     * Current index in the interactive inputs list.
     */
    protected int idx;
    
    /**
     * Creates a new BaseInteraction.
     * 
     * @param stateAccessor accessor for session state
     * @param defaultInput the default input, or null
     */
    protected BaseInteraction(SessionStateAccessor stateAccessor, Object defaultInput) {
        this.stateAccessor = stateAccessor;
        
        if (defaultInput != null) {
            this.interactiveInputs = new ArrayList<>();
            this.interactiveInputs.add(defaultInput);
        } else {
            this.interactiveInputs = null;
        }
        
        this.latestInteractiveInput = null;
        this.idx = 0;
        
        initInteractiveInputs();
    }
    
    /**
     * Creates a new BaseInteraction without default input.
     * 
     * @param stateAccessor accessor for session state
     */
    protected BaseInteraction(SessionStateAccessor stateAccessor) {
        this(stateAccessor, null);
    }
    
    /**
     * Initializes interactive inputs from session state.
     */
    @SuppressWarnings("unchecked")
    protected void initInteractiveInputs() {
        Object inputs = stateAccessor.get(Constant.INTERACTIVE_INPUT);
        if (inputs instanceof List<?> inputList) {
            if (interactiveInputs != null) {
                List<Object> combined = new ArrayList<>((List<Object>) inputList);
                combined.addAll(interactiveInputs);
                interactiveInputs = combined;
            } else {
                interactiveInputs = new ArrayList<>((List<Object>) inputList);
            }
        }
        
        if (interactiveInputs != null && !interactiveInputs.isEmpty()) {
            stateAccessor.update(Map.of(Constant.INTERACTIVE_INPUT, interactiveInputs));
            latestInteractiveInput = interactiveInputs.get(interactiveInputs.size() - 1);
        }
    }
    
    /**
     * Gets the next interactive input.
     * 
     * @return the next input, or null if none available
     */
    protected Object getNextInteractiveInput() {
        if (interactiveInputs != null && idx < interactiveInputs.size()) {
            return interactiveInputs.get(idx++);
        }
        return null;
    }
    
    /**
     * Waits for user inputs.
     * 
     * @param value the value to present to the user
     * @return a CompletableFuture that completes with the user's input
     */
    public abstract CompletableFuture<Object> waitUserInputs(Object value);
    
    /**
     * Processes the user's latest input.
     * 
     * @param value the latest input value
     * @return a CompletableFuture that completes when processing is done
     */
    public CompletableFuture<Void> userLatestInput(Object value) {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Interface for accessing session state.
     */
    public interface SessionStateAccessor {
        /**
         * Gets a value from the session state.
         * 
         * @param key the key
         * @return the value, or null if not found
         */
        Object get(String key);
        
        /**
         * Updates the session state.
         * 
         * @param data the data to update
         */
        void update(Map<String, Object> data);
    }
}

